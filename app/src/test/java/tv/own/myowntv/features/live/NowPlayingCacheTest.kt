package tv.own.myowntv.features.live

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.parser.XtEpgEntry

@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingCacheTest {
    private fun programme(title: String, start: Long = 0, stop: Long = 10_000) =
        XtEpgEntry(title, null, start, stop)

    @Test
    fun `cancellation never publishes null and a newer load still wins`() = runTest {
        val cache = NowPlayingCache(backgroundScope, maxEntries = 10, nowMs = { 1_000L })
        val gate = CompletableDeferred<Unit>()
        cache.load(listOf("old")) {
            gate.await()
            throw CancellationException("superseded")
        }
        runCurrent()
        cache.load(listOf("new")) { mapOf("new" to programme("New")) }
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(mapOf("new" to programme("New")), cache.programmes.value)
        assertFalse(cache.programmes.value.containsKey("old"))
    }

    @Test
    fun `a second consumer does not cancel the first consumer's load`() = runTest {
        val cache = NowPlayingCache(backgroundScope, maxEntries = 10, nowMs = { 1_000L })
        val gate = CompletableDeferred<Unit>()
        cache.load(listOf("list")) {
            gate.await()
            mapOf("list" to programme("List"))
        }
        runCurrent()
        cache.load(listOf("overlay")) { mapOf("overlay" to programme("Overlay")) }
        runCurrent()
        assertEquals("Overlay", cache.programmes.value["overlay"]?.title)
        gate.complete(Unit)
        runCurrent()
        assertEquals("List", cache.programmes.value["list"]?.title)
        assertEquals("Overlay", cache.programmes.value["overlay"]?.title)
    }

    @Test
    fun `an older response cannot overwrite a newer request for the same channel`() = runTest {
        val cache = NowPlayingCache(backgroundScope, maxEntries = 10, nowMs = { 1_000L })
        val gate = CompletableDeferred<Unit>()
        cache.load(listOf("channel"), force = true) {
            gate.await()
            mapOf("channel" to programme("Old"))
        }
        runCurrent()
        cache.load(listOf("channel"), force = true) { mapOf("channel" to programme("New")) }
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals("New", cache.programmes.value["channel"]?.title)
    }

    @Test
    fun `a failed read preserves good entries and does not cache no-guide`() = runTest {
        val cache = NowPlayingCache(backgroundScope, maxEntries = 10, nowMs = { 1_000L })
        cache.load(listOf("a")) { mapOf("a" to programme("A")) }
        runCurrent()
        cache.load(listOf("b"), force = true) { throw IllegalStateException("database failed") }
        runCurrent()
        assertEquals(programme("A"), cache.programmes.value["a"])
        assertFalse(cache.programmes.value.containsKey("b"))

        var attempts = 0
        cache.load(listOf("b")) {
            attempts++
            mapOf("b" to programme("B"))
        }
        runCurrent()
        assertEquals(1, attempts)
        assertEquals(programme("B"), cache.programmes.value["b"])
    }

    @Test
    fun `clear cancels an in-flight read and empties the cache`() = runTest {
        val cache = NowPlayingCache(backgroundScope, maxEntries = 10, nowMs = { 1_000L })
        cache.load(listOf("a")) { mapOf("a" to programme("A")) }
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        cache.load(listOf("b"), force = true) {
            gate.await()
            mapOf("b" to programme("B"))
        }
        runCurrent()
        cache.clear()
        gate.complete(Unit)
        runCurrent()
        assertTrue(cache.programmes.value.isEmpty())
    }

    @Test
    fun `a successful empty answer is remembered as no-guide`() = runTest {
        val cache = NowPlayingCache(backgroundScope, maxEntries = 10, nowMs = { 1_000L })
        var attempts = 0
        cache.load(listOf("a")) { attempts++; emptyMap() }
        runCurrent()
        cache.load(listOf("a")) { attempts++; emptyMap() }
        runCurrent()
        assertEquals(1, attempts)
        assertTrue(cache.programmes.value.containsKey("a"))
        assertNull(cache.programmes.value["a"])
    }

    @Test
    fun `a programme that expired during the query is not cached as no-guide`() = runTest {
        var now = 100L
        val cache = NowPlayingCache(backgroundScope, maxEntries = 10, nowMs = { now })
        cache.load(listOf("a")) {
            now = 200L
            mapOf("a" to programme("A", start = 100, stop = 200))
        }
        runCurrent()
        assertFalse(cache.programmes.value.containsKey("a"))
        var attempts = 0
        cache.load(listOf("a")) { attempts++; mapOf("a" to programme("A", start = 200, stop = 300)) }
        runCurrent()
        assertEquals(1, attempts)
        assertEquals("A", cache.programmes.value["a"]?.title)
    }

    @Test
    fun `an expired cached programme is refreshed by a normal ensure`() = runTest {
        var now = 100L
        val cache = NowPlayingCache(backgroundScope, maxEntries = 10, nowMs = { now })
        cache.load(listOf("a")) { mapOf("a" to programme("Old", start = 0, stop = 200)) }
        runCurrent()
        now = 200L
        cache.load(listOf("a")) { mapOf("a" to programme("New", start = 200, stop = 300)) }
        runCurrent()
        assertEquals("New", cache.programmes.value["a"]?.title)
    }

    @Test
    fun `a successful refresh cannot leave an expired title behind`() = runTest {
        var now = 100L
        val cache = NowPlayingCache(backgroundScope, maxEntries = 10, nowMs = { now })
        cache.load(listOf("a")) { mapOf("a" to programme("Old", start = 0, stop = 200)) }
        runCurrent()
        now = 200L
        cache.load(listOf("a"), force = true) { emptyMap() }
        runCurrent()
        assertNull(cache.programmes.value["a"])
    }
}
