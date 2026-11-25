package tv.own.myowntv.features.multiview

import kotlinx.coroutines.Job

/**
 * Per-tile tune generations. A tile's resolve belongs to one fill only: replacing, clearing,
 * refusing, shrinking or releasing the grid invalidates that fill before its old engine is touched.
 * The view model creates the job; this registry owns its identity and cancellation.
 */
internal class TileTuneRegistry {
    private data class Entry(val generation: Long, var job: Job? = null)

    private val entries = HashMap<Int, Entry>()
    private val generations = HashMap<Int, Long>()

    /** Invalidate the previous fill before the caller stops or replaces its engine. */
    fun begin(tile: Int): Long {
        entries.remove(tile)?.job?.cancel()
        val generation = (generations[tile] ?: 0L) + 1L
        generations[tile] = generation
        entries[tile] = Entry(generation)
        return generation
    }

    fun isCurrent(tile: Int, generation: Long): Boolean =
        entries[tile]?.generation == generation

    /** Attach the job the view model returned. A fill invalidated in the meantime is cancelled here. */
    fun attach(tile: Int, generation: Long, job: Job): Job? {
        val entry = entries[tile] ?: run {
            job.cancel()
            return null
        }
        if (entry.generation != generation) {
            job.cancel()
            return null
        }
        entry.job = job
        job.invokeOnCompletion {
            // Completion of an old job must not remove a newer fill for the same tile.
            if (entries[tile] === entry && entry.job === job) entries.remove(tile)
        }
        return job
    }

    fun cancel(tile: Int) {
        entries.remove(tile)?.job?.cancel()
    }

    fun cancelAll() {
        entries.values.forEach { it.job?.cancel() }
        entries.clear()
    }
}
