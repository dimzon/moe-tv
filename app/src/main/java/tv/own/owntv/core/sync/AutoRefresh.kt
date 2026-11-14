package tv.own.owntv.core.sync

import android.os.SystemClock
import android.util.Log
import androidx.work.ExistingWorkPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.SingleSource
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.settings.EpgAutoRefresh
import tv.own.owntv.core.settings.EpgRefresh
import tv.own.owntv.core.settings.PlaylistAutoRefresh
import tv.own.owntv.core.settings.PlaylistRefresh
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sync.work.CatalogSyncScheduler
import tv.own.owntv.core.sync.work.EpgSyncScheduler

/**
 * The startup and resume maintenance for the one built-in source: catalogue and guide.
 *
 * A catalogue or guide that is missing is loaded regardless of any preference. Existing data is
 * refreshed by its per-source policy, defaulting to every six hours when no explicit entry exists.
 * The default for both is deliberately the same, so a fresh install ends up with a usable
 * catalogue and guide without opening onboarding's old content steps.
 *
 * - `includeStartup = true` (cold start): runs once per process, refreshes STARTUP entries and
 *   interval entries whose data is at least as old as their threshold. Never time-throttled.
 * - `includeStartup = false` (app back in the foreground): skips STARTUP entries and refreshes
 *   interval entries past their threshold. Throttled to once per [RESUME_THROTTLE_MS].
 *
 * Enqueues use [ExistingWorkPolicy.KEEP], so a source already syncing or queued is left alone.
 * A queued job carries [REASON] and the workers hold it back while something is playing
 * ([tv.own.owntv.core.live.WatchSession]).
 *
 * [start] is called by the application process, not by a screen: a wizard, a recovery screen or a
 * finished onboarding must not decide whether the catalogue is allowed to load.
 */
class AutoRefresh(
    private val settings: SettingsRepository,
    private val sourceRepository: SourceRepository,
    private val catalogSyncScheduler: CatalogSyncScheduler,
    private val epgSyncScheduler: EpgSyncScheduler,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
) {
    private var coldStartCheckDone = false
    private var lastResumeCheckAtElapsed = 0L
    private var started = false

    /** Starts the once-per-process check and keeps watching catalogue completion for the guide. */
    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        scope.launch { check(includeStartup = true) }
        // A catalogue sync can be the first moment channels exist; the guide must follow it even
        // though no EPG row changed to wake the guide screen.
        scope.launch {
            catalogSyncScheduler.catalogueSettled(SingleSource.id).collect {
                checkEpg(includeStartup = true)
            }
        }
    }

    suspend fun check(includeStartup: Boolean) {
        if (!admit(includeStartup)) return
        val nowMs = System.currentTimeMillis()
        val source = sourceRepository.currentSource()
        val channels = channelDao.countAll().first()
        val playlistModes = settings.playlistAutoRefresh.first()
        val playlistRefresh = playlistModes[source.id] ?: DEFAULT_PLAYLIST_REFRESH
        val catalogMissing = catalogueIsMissing(channels, source.lastSyncAt)

        if (catalogMissing || playlistRefreshDue(playlistRefresh, source.lastSyncAt, nowMs, includeStartup)) {
            Log.d(TAG, "checkAutoRefresh catalogue sourceId=${source.id} missing=$catalogMissing mode=$playlistRefresh — enqueuing")
            catalogSyncScheduler.enqueueSync(
                source.id,
                reason = REASON,
                contentTypes = SyncContentTypes.enabledOf(source),
                baseItemCount = channels,
                policy = ExistingWorkPolicy.KEEP,
            )
        }

        checkEpg(includeStartup)
    }

    private suspend fun checkEpg(includeStartup: Boolean) {
        val channels = channelDao.countAll().first()
        if (channels == 0) return
        val nowMs = System.currentTimeMillis()
        val epgCount = epgDao.countAll()
        val lastEpg = settings.sourceLastEpgSyncAt.first()
        val modes = settings.epgAutoRefresh.first()
        val refresh = modes[SingleSource.id] ?: DEFAULT_EPG_REFRESH
        // Empty and never attempted is always repaired, even for an explicit "only first" choice.
        // After an attempt, the normal policy decides (the default six-hour interval is the retry).
        val missing = guideIsMissing(epgCount, lastEpg)
        if (missing || epgRefreshDue(refresh, lastEpg, nowMs, includeStartup)) {
            Log.d(TAG, "checkAutoRefresh guide sourceId=${SingleSource.id} empty=$missing mode=$refresh — enqueuing")
            epgSyncScheduler.enqueueSync(
                SingleSource.id,
                reason = REASON,
                baseProgrammes = epgCount,
                policy = ExistingWorkPolicy.KEEP,
            )
        }
    }

    private fun admit(includeStartup: Boolean): Boolean = synchronized(this) {
        if (includeStartup) {
            if (coldStartCheckDone) return@synchronized false
            coldStartCheckDone = true
        } else {
            val now = SystemClock.elapsedRealtime()
            if (now - lastResumeCheckAtElapsed < RESUME_THROTTLE_MS) return@synchronized false
            lastResumeCheckAtElapsed = now
        }
        true
    }

    companion object {
        private const val TAG = "OwnTVHome"

        /** Six hours is the default for both data sets when no policy was chosen. */
        internal val DEFAULT_PLAYLIST_REFRESH = PlaylistRefresh(PlaylistAutoRefresh.HOURS_6)
        internal val DEFAULT_EPG_REFRESH = EpgRefresh(EpgAutoRefresh.HOURS_6)

        /** The work reason the sync workers recognise as automatic, and hold back during playback. */
        const val REASON = "auto_refresh"

        /** Minimum gap between resume-triggered checks (rotation, rapid background/foreground). */
        private const val RESUME_THROTTLE_MS = 60_000L
    }
}

/** A catalogue with no channels, or that has never completed a sync, is always loaded. */
internal fun catalogueIsMissing(channels: Int, lastSyncAt: Long?): Boolean =
    channels == 0 || lastSyncAt == null

/** A guide with no programmes that has never been attempted is always loaded. */
internal fun guideIsMissing(programmes: Int, lastEpgSyncAt: Long?): Boolean =
    programmes == 0 && lastEpgSyncAt == null

internal fun playlistRefreshDue(
    refresh: PlaylistRefresh,
    lastSyncAt: Long?,
    now: Long,
    includeStartup: Boolean,
): Boolean = when (refresh.mode) {
    PlaylistAutoRefresh.OFF -> false
    PlaylistAutoRefresh.STARTUP -> includeStartup
    else -> (now - (lastSyncAt ?: 0L)) >= (refresh.thresholdMs ?: Long.MAX_VALUE)
}

internal fun epgRefreshDue(
    refresh: EpgRefresh,
    lastSyncAt: Long?,
    now: Long,
    includeStartup: Boolean,
): Boolean = when (refresh.mode) {
    EpgAutoRefresh.OFF -> false
    EpgAutoRefresh.STARTUP -> includeStartup
    else -> (now - (lastSyncAt ?: 0L)) >= (refresh.thresholdMs ?: Long.MAX_VALUE)
}
