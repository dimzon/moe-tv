package tv.own.myowntv.features.live

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Deferred CH+/- on virtual time: exit/direct/new tunes supersede it, and only the final press opens. */
@OptIn(ExperimentalCoroutinesApi::class)
class DeferredLiveTuneTest {
    @Test
    fun `a cancelled deferred tune never starts`() = runTest {
        val tune = DeferredLiveTune(backgroundScope)
        val started = mutableListOf<String>()
        val generation = tune.cancel()
        tune.launch(generation) { delay(500); started += "old" }
        runCurrent()
        tune.cancel() // fullscreen exit, stopPreview, numeric/direct/external or a deliberate tune
        advanceTimeBy(1_000); runCurrent()
        assertTrue(started.isEmpty())
    }

    @Test
    fun `a stale generation cannot even be launched`() = runTest {
        val tune = DeferredLiveTune(backgroundScope)
        val stale = tune.cancel()
        tune.cancel()
        val job = tune.launch(stale) { error("stale tune ran") }
        assertNull(job)
        advanceTimeBy(1_000); runCurrent()
    }

    @Test
    fun `only the final press in a burst opens`() = runTest {
        val tune = DeferredLiveTune(backgroundScope)
        val started = mutableListOf<String>()
        val first = tune.cancel()
        tune.launch(first) { delay(500); started += "first" }
        advanceTimeBy(200); runCurrent()
        val second = tune.cancel()
        tune.launch(second) { delay(500); started += "second" }
        advanceTimeBy(300); runCurrent() // first tune's original deadline
        assertTrue(started.isEmpty())
        advanceTimeBy(200); runCurrent()
        assertEquals(listOf("second"), started)
    }

    @Test
    fun `handover clears the handle without cancelling its own block`() = runTest {
        val tune = DeferredLiveTune(backgroundScope)
        val generation = tune.cancel()
        var handedOver = false
        tune.launch(generation) {
            handedOver = tune.finish(generation)
        }
        runCurrent()
        assertTrue(handedOver)
        assertTrue(tune.isCurrent(generation))
    }

    @Test
    fun `an old completion cannot clear a newer job`() = runTest {
        val tune = DeferredLiveTune(backgroundScope)
        val started = mutableListOf<String>()
        val oldGeneration = tune.cancel()
        tune.launch(oldGeneration) {
            withContext(NonCancellable) { delay(1_000) }
        }
        runCurrent()
        val newGeneration = tune.cancel()
        tune.launch(newGeneration) { delay(2_000); started += "new" }
        advanceTimeBy(1_000); runCurrent()
        assertTrue(tune.isCurrent(newGeneration))
        advanceTimeBy(1_000); runCurrent()
        assertEquals(listOf("new"), started)
    }
}
