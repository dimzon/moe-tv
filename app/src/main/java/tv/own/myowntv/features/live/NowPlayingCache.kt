package tv.own.myowntv.features.live

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import tv.own.owntv.core.parser.XtEpgEntry

/**
 * Shared, bounded now-playing cache for the Live list, Search and the player overlays. Requests run
 * independently: opening an overlay must not cancel the list's in-flight read and leave its rows
 * blank. Per-channel request versions stop an older answer from overwriting a newer one.
 */
internal class NowPlayingCache(
    private val scope: CoroutineScope,
    private val maxEntries: Int,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private val cache = object : LinkedHashMap<String, XtEpgEntry?>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, XtEpgEntry?>?) =
            size > maxEntries
    }
    private val versions = HashMap<String, Long>()
    private var nextVersion = 0L
    private val jobs = CopyOnWriteArrayList<Job>()

    private val _programmes = MutableStateFlow<Map<String, XtEpgEntry?>>(emptyMap())
    val programmes: StateFlow<Map<String, XtEpgEntry?>> = _programmes.asStateFlow()

    fun load(
        channelIds: List<String>,
        force: Boolean = false,
        loader: suspend (List<String>) -> Map<String, XtEpgEntry>,
    ) {
        val requested = channelIds.distinct()
        if (requested.isEmpty()) return
        val now = nowMs()
        val missing = synchronized(lock) {
            requested.filter { id ->
                force || !cache.containsKey(id) || cache[id]?.let { it.startMs > now || it.stopMs <= now } == true
            }
        }
        if (missing.isEmpty()) return
        val requestVersions = synchronized(lock) {
            val assigned = missing.associateWith { ++nextVersion }
            versions.putAll(assigned)
            assigned
        }

        lateinit var launched: Job
        launched = scope.launch(start = CoroutineStart.LAZY) {
            try {
                coroutineContext.ensureActive()
                val found = try {
                    loader(missing)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // A failed read is not evidence that the guide is absent. Retry on the next ensure.
                    return@launch
                }
                coroutineContext.ensureActive()
                val loadedAt = nowMs()
                val airing = found.filterValues { it.startMs <= loadedAt && it.stopMs > loadedAt }
                synchronized(lock) {
                    missing.forEach { id ->
                        if (versions[id] != requestVersions.getValue(id)) return@forEach
                        // Replace the requested snapshot as one unit. An expired programme that
                        // vanished from the fresh answer must not survive as a stale title.
                        cache.remove(id)
                        airing[id]?.let { cache[id] = it }
                        if (id !in found) cache[id] = null
                        versions.remove(id)
                    }
                    _programmes.value = HashMap(cache)
                }
            } finally {
                jobs.remove(launched)
            }
        }
        jobs.add(launched)
        launched.invokeOnCompletion { jobs.remove(launched) }
        launched.start()
    }

    fun clear() {
        synchronized(lock) {
            versions.clear()
            cache.clear()
            _programmes.value = emptyMap()
        }
        jobs.forEach { it.cancel() }
        jobs.clear()
    }
}
