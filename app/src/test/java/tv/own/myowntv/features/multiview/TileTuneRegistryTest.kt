package tv.own.myowntv.features.multiview

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TileTuneRegistryTest {
    @Test
    fun `refusing a replacement cancels the old tile job`() = runTest {
        val registry = TileTuneRegistry()
        val started = mutableListOf<String>()
        val oldGeneration = registry.begin(0)
        val old = backgroundScope.launch { delay(500); started += "old" }
        registry.attach(0, oldGeneration, old)
        runCurrent()

        val newGeneration = registry.begin(0)
        assertFalse(registry.isCurrent(0, oldGeneration))
        assertTrue(registry.isCurrent(0, newGeneration))
        advanceTimeBy(1_000); runCurrent()
        assertTrue(started.isEmpty())
    }

    @Test
    fun `a job created for an invalidated tile is cancelled on attach`() = runTest {
        val registry = TileTuneRegistry()
        val stale = registry.begin(0)
        registry.begin(0)
        val job = backgroundScope.launch { error("stale tile tune ran") }
        assertNull(registry.attach(0, stale, job))
        runCurrent()
        assertTrue(job.isCancelled)
    }

    @Test
    fun `clear and releaseAll cancel every tile`() = runTest {
        val registry = TileTuneRegistry()
        val jobs = HashMap<Int, Job>()
        for (tile in 0..1) {
            val generation = registry.begin(tile)
            jobs[tile] = backgroundScope.launch { delay(5_000) }.also { registry.attach(tile, generation, it) }
        }
        registry.cancel(0)
        registry.cancelAll()
        runCurrent()
        assertTrue(jobs.values.all { it.isCancelled })
        assertFalse(registry.isCurrent(1, 1))
    }

    @Test
    fun `an old completion does not remove the replacement generation`() = runTest {
        val registry = TileTuneRegistry()
        val stale = registry.begin(0)
        val old = backgroundScope.launch { withContext(NonCancellable) { delay(100) } }
        registry.attach(0, stale, old)
        runCurrent()
        val current = registry.begin(0)
        val replacement = backgroundScope.launch { delay(500) }
        registry.attach(0, current, replacement)
        advanceTimeBy(100); runCurrent()
        assertTrue(registry.isCurrent(0, current))
        assertEquals(2L, current)
    }
}
