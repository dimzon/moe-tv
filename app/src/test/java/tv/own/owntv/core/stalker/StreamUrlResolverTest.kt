package tv.own.owntv.core.stalker

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.network.HttpClient
import tv.own.owntv.core.sync.RutubeApi

/**
 * StreamUrlResolver unit tests, RuTube-only: the routing decision ([StreamUrlResolver.needsResolve]),
 * the fresh `play/options` resolve, and the "every resolve is fresh" contract — all against a canned
 * answer served by an OkHttp interceptor, no network.
 */
class StreamUrlResolverTest {

    private val source = SourceEntity(name = "rutube", type = SourceType.RUTUBE, url = "https://rutube.test")

    // --- needsResolve gating ---

    @Test
    fun needsResolve_isTrueForRutubeOnly() {
        val r = StreamUrlResolver(null)
        // RuTube stores a rutube:// marker and asks play/options at tune time.
        assertTrue(r.needsResolve(source))
        assertFalse("no source: nothing to resolve", r.needsResolve(null))
    }

    // --- resolve ---

    @Test
    fun resolve_withoutAnApiRefusesRatherThanGuessing() = runBlocking {
        val r = StreamUrlResolver(null)
        val e = runCatching { r.resolve(source, "rutube://7/abc123") }.exceptionOrNull()
        assertTrue("expected a refusal", e is java.io.IOException && e.message!!.contains("not configured"))
    }

    @Test
    fun resolve_mintsTheFreshPlayUrl() = runBlocking {
        val r = StreamUrlResolver(canned().api)
        assertEquals("https://cdn.example.test/live.m3u8", r.resolve(source, "rutube://7/abc123"))
    }

    @Test
    fun resolvePlayback_returnsUrlAndCaptionsFromOneRequest() = runBlocking {
        val canned = canned()
        val r = StreamUrlResolver(canned.api)
        val playback = r.resolvePlayback(source, "rutube://7/abc123")

        assertEquals("https://cdn.example.test/live.m3u8", playback.url)
        assertEquals(listOf("https://pic.example.test/1.vtt"), playback.captions.map { it.file })
        assertEquals("URL and captions must share one play/options answer", 1, canned.calls.get())
    }

    @Test
    fun resolvePlayback_everyCallHitsPlayOptionsAgain() = runBlocking {
        val canned = canned()
        val r = StreamUrlResolver(canned.api)
        repeat(2) { r.resolvePlayback(source, "rutube://7/abc123") }

        assertEquals(2, canned.calls.get())
    }

    @Test
    fun resolvePlayback_aSubscriptionChannelStillRefusesWithItsReason() = runBlocking {
        val r = StreamUrlResolver(canned(code = 404, playJson = "{}").api)
        val e = runCatching { r.resolvePlayback(source, "rutube://7/abc123") }.exceptionOrNull()

        assertTrue(e is java.io.IOException && e.message!!.contains("LOGIN_REQUIRED"))
    }

    @Test
    fun resolve_acceptsTheOneSegmentMarkerStoredByChannelEntity() = runBlocking {
        val r = StreamUrlResolver(canned().api)
        assertEquals("https://cdn.example.test/live.m3u8", r.resolve(source, "rutube://abc123"))
    }

    @Test
    fun resolve_aMarkerWithoutAVideoIdIsRefused() = runBlocking {
        val r = StreamUrlResolver(canned().api)
        val e = runCatching { r.resolve(source, "rutube://7/") }.exceptionOrNull()
        assertTrue("expected a refusal", e is java.io.IOException && e.message!!.contains("no video id"))
    }

    // --- C-3: every resolve is a FRESH resolve (never cached) ---

    @Test
    fun resolve_everyCallHitsPlayOptionsAgain() = runBlocking {
        val canned = canned()
        val r = StreamUrlResolver(canned.api)
        repeat(3) {
            assertEquals("https://cdn.example.test/live.m3u8", r.resolve(source, "rutube://7/abc123"))
        }
        assertEquals("reconnect must re-resolve a fresh URL each time", 3, canned.calls.get())
    }

    @Test
    fun resolve_aSubscriptionChannelRefusesWithItsReason() = runBlocking {
        val r = StreamUrlResolver(canned(code = 404, playJson = "{}").api)
        val e = runCatching { r.resolve(source, "rutube://7/abc123") }.exceptionOrNull()
        assertTrue("expected a refusal", e is java.io.IOException && e.message!!.contains("LOGIN_REQUIRED"))
    }

    // --- captions ---

    @Test
    fun captions_followTheSameFreshResolveAndCarryTheSidecars() = runBlocking {
        val canned = canned()
        val r = StreamUrlResolver(canned.api)
        assertEquals(
            listOf("https://pic.example.test/1.vtt"),
            r.resolveCaptions(source, "rutube://7/abc123").map { it.file },
        )
        assertEquals(1, canned.calls.get())
    }

    @Test
    fun captions_withoutAnApiAreEmpty() = runBlocking {
        val r = StreamUrlResolver(null)
        assertTrue(r.resolveCaptions(source, "rutube://7/abc123").isEmpty())
    }

    // --- ReconnectUrlProvider contract (C-3) ---

    @Test
    fun reconnectUrlProvider_nullReturnMeansReplayAsIs() = runBlocking {
        // The provider is the engine's "ask for a fresh URL" hook. A null return = "no fresh URL
        // available" → the engine replays the stored URL. This is the "absent provider" semantic
        // the engines rely on for sources whose URLs never expire.
        val provider = ReconnectUrlProvider { null }
        assertNull(provider.freshUrl())
    }

    @Test
    fun reconnectUrlProvider_returnsFreshUrl() = runBlocking {
        val r = StreamUrlResolver(canned().api)
        // The provider the VM installs resolves the current marker via the resolver.
        val provider = ReconnectUrlProvider {
            runCatching { r.resolve(source, "rutube://7/abc123") }.getOrNull()
        }
        assertEquals("https://cdn.example.test/live.m3u8", provider.freshUrl())
    }

    // --- helpers ---

    private class Canned(val calls: AtomicInteger, val api: RutubeApi)

    /** A RutubeApi whose HTTP answers are canned by an interceptor, and which counts its requests. */
    private fun canned(code: Int = 200, playJson: String = PLAY_READY_JSON): Canned {
        val calls = AtomicInteger()
        val okhttp = OkHttpClient.Builder()
            .addInterceptor { chain ->
                calls.incrementAndGet()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("test")
                    .body(playJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        return Canned(calls, RutubeApi(HttpClient(okhttp), baseUrl = "https://rutube.test"))
    }

    private companion object {
        /** The `play/options` answer the canned server serves: an HLS URL and one sidecar subtitle. */
        const val PLAY_READY_JSON =
            """{"live_streams":{"hls":[{"url":"https://cdn.example.test/live.m3u8"}]},
                "captions":[{"sub_id":1,"format":"vtt","file":"https://pic.example.test/1.vtt",
                             "langTitle":"Russian","is_autogenerated":true}]}"""
    }
}
