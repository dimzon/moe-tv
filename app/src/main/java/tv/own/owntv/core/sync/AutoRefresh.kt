package tv.own.owntv.core.sync

import android.os.SystemClock
import android.util.Log
import androidx.work.ExistingWorkPolicy
import kotlinx.coroutines.flow.first
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.settings.PlaylistAutoRefresh
import tv.own.owntv.core.settings.PlaylistRefresh
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sync.work.CatalogSyncScheduler

/**
 * The playlist "Auto refresh" settings, for both apps. It used to live in the TV app's shell, so
 * the phone stored the setting and never acted on it.
 *
 * - `includeStartup = true` (cold start): runs once per process, refreshes STARTUP sources
 *   unconditionally and interval sources whose data is at least as old as their threshold. Never
 *   time-throttled, so STARTUP always fires on a real cold start.
 * - `includeStartup = false` (app back in the foreground): skips STARTUP sources and refreshes
 *   interval sources past their threshold. Throttled to once per [RESUME_THROTTLE_MS].
 *
 * Enqueues use [ExistingWorkPolicy.KEEP], so a source already syncing or queued is left alone.
 * Manual re-syncs use REPLACE at their own call sites. A job enqueued here carries
 * [REASON], and the workers hold such a job back while something is playing ([tv.own.owntv.core.live.WatchSession]).
 *
 * The guide keeps its own refresh paths ([EpgSemiAutoSync] on the settings side,
 * [tv.own.owntv.core.sync.work.EpgSyncWorker] as the WorkManager entry) — RuTube's EPG has no
 * standalone source list to schedule over any more.
 *
 * A singleton, so the once-per-process rule holds even when the Activity is rebuilt.
 */
class AutoRefresh(
    private val settings: SettingsRepository,
    private val sourceRepository: SourceRepository,
    private val catalogSyncScheduler: CatalogSyncScheduler,
    private val importFinalizer: ImportFinalizer,
) {
    private var coldStartCheckDone = false
    private var lastResumeCheckAtElapsed = 0L

    suspend fun check(includeStartup: Boolean) {
        synchronized(this) {
            if (includeStartup) {
                if (coldStartCheckDone) return
                coldStartCheckDone = true
            } else {
                val now = SystemClock.elapsedRealtime()
                if (now - lastResumeCheckAtElapsed < RESUME_THROTTLE_MS) return
                lastResumeCheckAtElapsed = now
            }
        }
        val nowMs = System.currentTimeMillis()
        // --- Playlist sources ---
        val playlistModes = settings.playlistAutoRefresh.first()
        if (playlistModes.isNotEmpty()) {
            sourceRepository.observeSources().first().forEach { source ->
                val mode = playlistModes[source.id] ?: PlaylistRefresh.OFF
                if (shouldRefresh(mode, source.lastSyncAt, nowMs, includeStartup)) {
                    val counts = importFinalizer.contentCounts(source.id)
                    Log.d(TAG, "checkAutoRefresh playlist sourceId=${source.id} mode=$mode — enqueuing")
                    catalogSyncScheduler.enqueueSync(
                        source.id,
                        reason = REASON,
                        contentTypes = SyncContentTypes.enabledOf(source),
                        baseItemCount = counts.channels,
                        policy = ExistingWorkPolicy.KEEP,
                    )
                }
            }
        }
    }

    /**
     * Whether a playlist source should auto-refresh now. OFF never; STARTUP only on cold start
     * ([includeStartup]); interval modes when `now - lastSyncAt >= threshold` (a null lastSyncAt — never
     * successfully synced — counts as infinitely stale so recovery happens).
     */
    private fun shouldRefresh(
        refresh: PlaylistRefresh,
        lastSyncAt: Long?,
        now: Long,
        includeStartup: Boolean,
    ): Boolean = when (refresh.mode) {
        PlaylistAutoRefresh.OFF -> false
        PlaylistAutoRefresh.STARTUP -> includeStartup
        else -> (now - (lastSyncAt ?: 0L)) >= (refresh.thresholdMs ?: Long.MAX_VALUE)
    }

    companion object {
        private const val TAG = "OwnTVHome"
        /** The work reason the sync workers recognise as automatic, and hold back during playback. */
        const val REASON = "auto_refresh"
        /** Minimum gap between resume-triggered checks (rotation, rapid background/foreground). */
        private const val RESUME_THROTTLE_MS = 60_000L
    }
}
