package tv.own.myowntv.features.live

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * The one deferred CH+/- tune. Every navigation path calls [cancel] before starting its own tune;
 * the generation is captured by the delayed job so a cancelled job can neither start after a newer
 * choice nor clear the newer job when it finally completes.
 */
internal class DeferredLiveTune(private val scope: CoroutineScope) {
    private var generation = 0L
    private var job: Job? = null

    fun isCurrent(generation: Long): Boolean = this.generation == generation

    /** Supersede whatever was waiting and return the token for the replacement, if it is deferred. */
    fun cancel(): Long {
        generation++
        job?.cancel()
        job = null
        return generation
    }

    /** Hand the current delayed job over without cancelling the coroutine that is running it. */
    fun finish(generation: Long): Boolean {
        if (!isCurrent(generation)) return false
        job = null
        return true
    }

    /** Start a deferred tune only while [generation] is still the latest navigation request. */
    fun launch(generation: Long, block: suspend () -> Unit): Job? {
        if (!isCurrent(generation)) return null
        lateinit var launched: Job
        launched = scope.launch(start = CoroutineStart.LAZY) {
            try {
                coroutineContext.ensureActive()
                if (isCurrent(generation)) block()
            } finally {
                // A stale completion must never clear the replacement's handle.
                if (job === launched) job = null
            }
        }
        job = launched
        launched.invokeOnCompletion { if (job === launched) job = null }
        launched.start()
        return launched
    }
}
