package tv.own.myowntv.features.home

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType

@OptIn(ExperimentalCoroutinesApi::class)
class HeroPreviewResolutionTest {
    private val source = SourceEntity(name = "RuTube", type = SourceType.RUTUBE, url = "https://example")

    @Test
    fun `a resolving source gets a fresh playable URL`() = runTest {
        val resolved = resolveHeroPreviewUrl(
            storedUrl = "rutube://abc123",
            source = source,
            needsResolve = { true },
            resolve = { "https://stream/live.m3u8" },
        )
        assertEquals("https://stream/live.m3u8", resolved)
    }

    @Test
    fun `a direct URL bypasses the resolver`() = runTest {
        var calls = 0
        val resolved = resolveHeroPreviewUrl(
            storedUrl = "https://stream/live.m3u8",
            source = source,
            needsResolve = { false },
            resolve = { calls++; error("must not resolve") },
        )
        assertEquals("https://stream/live.m3u8", resolved)
        assertEquals(0, calls)
    }

    @Test
    fun `resolution failure leaves the poster fallback`() = runTest {
        val resolved = resolveHeroPreviewUrl(
            storedUrl = "rutube://abc123",
            source = source,
            needsResolve = { true },
            resolve = { throw IllegalStateException("offline") },
        )
        assertNull(resolved)
    }

    @Test
    fun `cancellation is rethrown instead of becoming a fallback`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var cancelled = false
        val job = launch {
            try {
                resolveHeroPreviewUrl(
                    storedUrl = "rutube://abc123",
                    source = source,
                    needsResolve = { true },
                    resolve = { gate.await(); "https://stream/live.m3u8" },
                )
            } catch (_: CancellationException) {
                cancelled = true
            }
        }
        runCurrent()
        job.cancelAndJoin()
        assertTrue(cancelled)
        assertTrue(job.isCompleted)
    }
}
