package tv.own.owntv.player

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decisions that keep a live channel alive through a provider hiccup: how long we wait
 * between reconnects, which loads ride an outage forever, and how a provider back-off countdown
 * is read off the response.
 */
class LiveReconnectLadderTest {

    @Test
    fun `the ladder widens instead of flat-lining at four seconds`() {
        assertEquals(1_500L, LivePreviewEngine.reconnectDelayMs(1))
        assertEquals(3_000L, LivePreviewEngine.reconnectDelayMs(2))
        assertEquals(6_000L, LivePreviewEngine.reconnectDelayMs(3))
        assertEquals(10_000L, LivePreviewEngine.reconnectDelayMs(4))
        assertEquals(15_000L, LivePreviewEngine.reconnectDelayMs(5))
    }

    @Test
    fun `the engine's death verdict is its slowest watchdog plus the first reconnect`() {
        // The stall watchdog's hand-off deadline is derived from this, so the two can no longer drift
        // apart the way they had: a flat hand-off against a shorter verdict meant sitting through two
        // of the engine's own reconnect attempts before the tune ladder was ever consulted.
        // 12s buffering stall (the slowest of the three checks) + 1.5s before the first retry.
        assertEquals(13_500L, LivePreviewEngine.DEATH_VERDICT_MS)
        assertEquals(12_000L + LivePreviewEngine.reconnectDelayMs(1), LivePreviewEngine.DEATH_VERDICT_MS)
    }

    @Test
    fun `attempts past the ladder hold at the last step`() {
        assertEquals(15_000L, LivePreviewEngine.reconnectDelayMs(6))
        assertEquals(15_000L, LivePreviewEngine.reconnectDelayMs(8))
    }

    @Test
    fun `a defensive out-of-range attempt still yields the first step`() {
        assertEquals(1_500L, LivePreviewEngine.reconnectDelayMs(0))
        assertEquals(1_500L, LivePreviewEngine.reconnectDelayMs(-3))
    }

    @Test
    fun `the ladder outlives a minute-long outage`() {
        // The pre-fix rule (1500 * n capped at 4 s) spent its eight attempts in ~26 s, so a router
        // reboot always ended in "Lost connection". Five attempts alone now span ~35 s.
        val fiveAttempts = (1..5).sumOf { LivePreviewEngine.reconnectDelayMs(it) }
        assertEquals(35_500L, fiveAttempts)
        assertTrue((1..8).sumOf { LivePreviewEngine.reconnectDelayMs(it) } > 60_000L)
    }

    @Test
    fun `recovery is only credited after a sustained healthy window`() {
        assertEquals(60_000L, LivePreviewEngine.HEALTHY_MS)
    }

    @Test
    fun `fatal HLS HTTP recovery stays short instead of widening to fifteen seconds`() {
        assertEquals(1_500L, LivePreviewEngine.hlsHttpReconnectDelayMs(1))
        assertEquals(1_500L, LivePreviewEngine.hlsHttpReconnectDelayMs(5))
        assertEquals(1_500L, LivePreviewEngine.hlsHttpReconnectDelayMs(8))
    }

    @Test
    fun `redirected playlist is recognized from final URL or response content type`() {
        assertTrue(LivePreviewEngine.isHlsResponse("http://cdn.test/live/7.m3u8?token=x", "application/octet-stream"))
        assertTrue(LivePreviewEngine.isHlsResponse("http://cdn.test/live/7", "application/x-mpegURL; charset=UTF-8"))
        assertTrue(LivePreviewEngine.isHlsResponse("http://cdn.test/live/7", "application/vnd.apple.mpegurl"))
        assertFalse(LivePreviewEngine.isHlsResponse("http://cdn.test/live/7.ts", "video/MP2T"))
    }

    @Test
    fun `a played DASH channel rides out an outage on segments and the MPD, like raw TS`() {
        assertTrue(LivePreviewEngine.retriesOutageForever(StreamRoute.DASH, C.DATA_TYPE_MEDIA))
        assertTrue(LivePreviewEngine.retriesOutageForever(StreamRoute.DASH, C.DATA_TYPE_MANIFEST))
        assertTrue(LivePreviewEngine.retriesOutageForever(StreamRoute.PROGRESSIVE, C.DATA_TYPE_MEDIA))
        assertTrue(LivePreviewEngine.retriesOutageForever(StreamRoute.PROGRESSIVE, C.DATA_TYPE_MEDIA_PROGRESSIVE_LIVE))
        // Licence and init loads keep the stock count; HLS has its own policy.
        assertFalse(LivePreviewEngine.retriesOutageForever(StreamRoute.DASH, C.DATA_TYPE_DRM))
        assertFalse(LivePreviewEngine.retriesOutageForever(StreamRoute.PROGRESSIVE, C.DATA_TYPE_MANIFEST))
        assertFalse(LivePreviewEngine.retriesOutageForever(StreamRoute.HLS, C.DATA_TYPE_MEDIA))
    }

    @Test
    fun `a refused segment is retried once, not hammered until fatal`() {
        assertEquals(LivePreviewEngine.EDGE_REFUSAL_RETRY_MS, LivePreviewEngine.edgeRefusalRetryDelayMs(1))
        assertEquals(C.TIME_UNSET, LivePreviewEngine.edgeRefusalRetryDelayMs(2))
        assertEquals(C.TIME_UNSET, LivePreviewEngine.edgeRefusalRetryDelayMs(5))
    }

    @Test
    fun `a numeric Retry-After becomes the wait, anything else becomes no wait at all`() {
        // The panel counts the seconds down across successive refusals ("13", "12", "10"), and each one
        // replaces the pending countdown — see LivePreviewEngine.maybeBackOffForProvider.
        assertEquals(13, LivePreviewEngine.retryAfterSecs("13"))
        assertEquals(10, LivePreviewEngine.retryAfterSecs(" 10 "))
        // "come back now" still costs one tick, so the countdown always has something to show.
        assertEquals(1, LivePreviewEngine.retryAfterSecs("0"))
        // Up to the ceiling is waited; longer reads as a hang behind a spinner, so the error screen shows.
        assertEquals(LivePreviewEngine.MAX_RETRY_AFTER_SECS, LivePreviewEngine.retryAfterSecs("60"))
        assertNull(LivePreviewEngine.retryAfterSecs("61"))
        assertNull(LivePreviewEngine.retryAfterSecs("3600"))
        // The HTTP-date form and junk name no deadline we can honestly count down, so there is no wait.
        assertNull(LivePreviewEngine.retryAfterSecs("Wed, 21 Oct 2015 07:28:00 GMT"))
        assertNull(LivePreviewEngine.retryAfterSecs("-5"))
        assertNull(LivePreviewEngine.retryAfterSecs(""))
        assertNull(LivePreviewEngine.retryAfterSecs(null))
    }
}
