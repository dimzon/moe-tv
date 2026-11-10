package tv.own.owntv.core.sync

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.own.owntv.core.database.BulkInsertHelper
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.network.HttpClient
import tv.own.owntv.core.parser.M3uParser
import tv.own.owntv.core.parser.XtreamClient
import tv.own.owntv.core.settings.SettingsRepository

/**
 * Imports a source into the database — a thin dispatcher over the per-source-type syncers
 * [XtreamSyncer] preserves existing rows via
 * hash-diffed stable upserts; [M3uSyncer] uses clear-then-insert because playlists do not provide
 * stable item ids. Shared machinery (chunked inserts, upserts, pruning) lives in [SyncSupport].
 */
class SyncManager(
    context: android.content.Context,
    private val sourceDao: SourceDao,
    categoryDao: CategoryDao,
    channelDao: ChannelDao,
    xtream: XtreamClient,
    m3u: M3uParser,
    http: HttpClient,
    bulkInsertHelper: BulkInsertHelper,
    stalkerClient: tv.own.owntv.core.stalker.StalkerClient,
    stalkerAuth: tv.own.owntv.core.stalker.StalkerAuthManager,
    private val activityTracker: SyncActivityTracker,
    customize: CustomizationStore,
    settings: SettingsRepository,
    /**
     * Measures how many streams the provider allows, for the providers that never say.
     *
     * Read from here to decide *whether* a playlist needs measuring; the measuring itself happens
     * in `ConnectionMeasurementWorker`. It used to run at the front of the very first sync, because
     * that was the only moment nothing could be playing for the probe to cut off — and that cost
     * 55 seconds of every fresh Stalker add. `WatchSession` now answers "is anything
     * playing?" directly, so the work can wait for a quiet moment instead of taking one.
     */
    private val connectionLimits: tv.own.owntv.core.live.ConnectionLimits,
    /**
     * Enqueues the connection-limit measurement and the content-index build in the background.
     *
     * Deliberately hooked here rather than at an import call site: a source is imported from the
     * setup wizard, from the Settings "Add source" screen — which has its own copy of the import
     * flow in the TV app — and from `CatalogSyncWorker`. All of them funnel through [sync], so this
     * is the one place those enqueues cannot be forgotten by a caller that core does not own.
     *
     * (The TV app's private copy of the import flow is gone, so `SourceImporter` would
     * now be reachable from every path too. This hook stays here regardless: [sync] is the narrower
     * and more durable guarantee, and it costs nothing.)
     */
    private val catalogSyncScheduler: tv.own.owntv.core.sync.work.CatalogSyncScheduler,
    private val rutubeApi: tv.own.owntv.core.sync.RutubeApi? = null,
) {
    private val support = SyncSupport(categoryDao, channelDao, sourceDao, customize, settings)
    private val xtreamSyncer = XtreamSyncer(xtream, bulkInsertHelper, support)
    private val m3uSyncer = M3uSyncer(context, sourceDao, categoryDao, channelDao, m3u, http, bulkInsertHelper, support)
    private val stalkerSyncer = StalkerSyncer(stalkerClient, stalkerAuth, bulkInsertHelper, support, sourceDao, PortalCeilingStore(context))
    private val rutubeSyncer = rutubeApi?.let { RuTubeSyncer(it, bulkInsertHelper, support) }
    /** Late-bound EPG syncer, created by DataModule after EpgRepository exists. */
    var rutubeEpgSyncer: RuTubeEpgSyncer? = null

    private val lastSyncStats = java.util.concurrent.ConcurrentHashMap<Long, SyncRunStats>()

    fun getLastSyncStats(sourceId: Long): SyncRunStats? = lastSyncStats[sourceId]

    suspend fun sync(
        source: SourceEntity,
        onProgress: (ImportStage) -> Unit,
        contentTypes: SyncContentTypes = SyncContentTypes(),
        /**
         * User-requested clean resync. Scoped to this one run — it is never persisted and never set
         * by an automatic sync, so the catalog-shrink guard protects every other pass as before.
         */
        forcePrune: Boolean = false,
    ): Pair<SyncResult, SyncRunStats> =
        withContext(Dispatchers.IO) {
            val syncStartedAt = SystemClock.elapsedRealtime()
            val stats = SyncStatsCollector(source.id).apply { this.forcePrune = forcePrune }
            // Single derivation: request ∩ enabledScope, then type-constrained (replaces the old
            // trackedContentTypes source-type switch). A stale enqueue can't revive an Off section.
            val effective = contentTypes.effectiveFor(source)
            val target = SyncContentTypes.enabledFor(source)
            Log.i(
                TAG,
                "sync start sourceId=${source.id} name=${source.name} type=${source.type} " +
                    "requestedContentTypes=$contentTypes effective=$effective target=$target forcePrune=$forcePrune",
            )
            activityTracker.started(source.id, source.name)
            val progress = SyncCounters(effective) { stage ->
                activityTracker.progress(source.id, stage)
                onProgress(stage)
            }
            // The stream-limit measurement used to run *here*, blocking the first sync.
            // Instrumentation put a number on it: 55,489 ms of a fresh Stalker add, before a
            // single channel was fetched. It is now enqueued instead, and the reason it could not be
            // before is gone — see ConnectionMeasurementWorker.
            //
            // Still gated on `lastSyncAt == null`: without it every playlist that existed before this
            // feature would measure on its next ordinary re-sync.
            val prePhaseStartedAt = SystemClock.elapsedRealtime()
            if (source.lastSyncAt == null && connectionLimits.needsMeasuring(source)) {
                catalogSyncScheduler.enqueueConnectionMeasurement(source.id)
            }
            Log.i(TAG, "pre-phase done sourceId=${source.id} ms=${SystemClock.elapsedRealtime() - prePhaseStartedAt}")
            var result: SyncResult = SyncResult.Cancelled
            try {
                if (!effective.hasAny) {
                    // Empty effective (e.g. a live-only remainder after Live turned Off): clean
                    // no-op — do not stamp lastSyncAt (scope edit enqueues a reconcile resync).
                    Log.i(TAG, "sync no-op empty effective sourceId=${source.id}")
                    progress.completeAll()
                    result = SyncResult.Success()
                } else {
                    // Concurrent catalog syncs are safe without an app-wide lock: the only cross-source
                    // race was BulkInsertHelper's pre-lock tableIsEmpty bypass (a second source writing
                    // into a half-indexed table while the first restored it). That is now closed inside
                    // BulkInsertHelper itself — a joining sync registers as a writer and index restore
                    // waits for the last writer — so sources fetch/parse/insert fully in parallel here.
                    when (source.type) {
                        SourceType.XTREAM -> xtreamSyncer.sync(source, progress, stats, effective)
                        SourceType.M3U -> m3uSyncer.sync(source, progress, stats)
                        SourceType.LOCAL_BACKUP -> Unit
                        SourceType.STALKER -> stalkerSyncer.sync(source, progress, stats, effective)
                        SourceType.RUTUBE -> rutubeSyncer?.sync(source, progress, stats)
                            ?: run { stats.phaseErrors["live"] = "RuTube provider not wired"; Log.w(TAG, "RuTube sync requested without a RutubeApi") }
                    }
                    // Stamp lastSyncAt only when this pass covered every enabled+constrained section.
                    // A staged partial pass leaves it null; the background remainder worker stamps via
                    // completesInitialSync. Comparing against enabledFor (not enabledOf) keeps M3U
                    // live-only passes from never marking synced.
                    if (effective.isCompleteFor(target)) {
                        val markStartedAt = SystemClock.elapsedRealtime()
                        sourceDao.markSynced(source.id, System.currentTimeMillis())
                        Log.d(TAG, "markSynced sourceId=${source.id} ms=${SystemClock.elapsedRealtime() - markStartedAt}")
                    }
                    progress.completeAll()
                    result = SyncResult.Success(
                        warnings = stats.warnings(),
                        categoriesAdded = stats.processedCounts[SyncSupport.CATEGORIES_ADDED_KEY] ?: 0,
                        categoriesRemoved = stats.processedCounts[SyncSupport.CATEGORIES_REMOVED_KEY] ?: 0,
                    )
                }
            } catch (c: CancellationException) {
                result = SyncResult.Cancelled
                throw c
            } catch (e: Exception) {
                result = SyncResult.Failed(e.message.orEmpty())
            } finally {
                activityTracker.finished(source.id, source.name, result) // also on cancellation — never leave a stuck pill
            }
            val runStats = stats.build(result)
            lastSyncStats[source.id] = runStats
            Log.i(TAG, "sync end sourceId=${source.id} totalElapsedMs=${SystemClock.elapsedRealtime() - syncStartedAt}")
            logStats(runStats)
            result to runStats
        }

    private fun logStats(stats: SyncRunStats) {
        val tag = "SyncManager"
        val duration = stats.finishedAt - stats.startedAt
        val result = when (stats.result) {
            is SyncResult.Success -> {
                if (stats.result.warnings.isEmpty()) "Success" else "Success with ${stats.result.warnings.size} warning(s)"
            }
            SyncResult.Cancelled -> "Cancelled"
            is SyncResult.Failed -> "Failed: ${stats.result.message}"
        }
        android.util.Log.i(tag, "── Sync stats for source ${stats.sourceId} ──")
        android.util.Log.i(tag, "Result: $result | Duration: ${duration}ms | Fallback: ${stats.usedFallback}")
        if (stats.phaseTiming.isNotEmpty()) {
            android.util.Log.i(tag, "Phases: ${stats.phaseTiming.entries.joinToString { "${it.key}=${it.value}ms" }}")
        }
        if (stats.processedCounts.isNotEmpty()) {
            android.util.Log.i(tag, "Counts: ${stats.processedCounts.entries.joinToString { "${it.key}=${it.value}" }}")
        }
        if (stats.phaseErrors.isNotEmpty()) {
            android.util.Log.w(tag, "Phase errors: ${stats.phaseErrors.entries.joinToString { "${it.key}=${it.value}" }}")
        }
    }

    companion object {
        private const val TAG = SyncSupport.TAG
    }
}
