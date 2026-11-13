package tv.own.owntv.core.sync.work

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import tv.own.owntv.core.network.ConnectivityObserver
import tv.own.owntv.core.util.isTransientSyncError
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.sync.ImportFinalizer
import tv.own.owntv.core.sync.ImportStage
import tv.own.owntv.core.sync.SyncContentTypes
import tv.own.owntv.core.sync.SyncResult
import tv.own.owntv.core.settings.SettingsRepository

class CatalogSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val sourceRepository: SourceRepository,
    private val importFinalizer: ImportFinalizer,
    private val catalogSyncScheduler: CatalogSyncScheduler,
    private val connectivity: ConnectivityObserver,
    private val settings: SettingsRepository,
    private val watchSession: tv.own.owntv.core.live.WatchSession,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sourceId = inputData.getLong(KEY_SOURCE_ID, -1L)
        val reason = inputData.getString(KEY_REASON) ?: "unknown"
        val baseItemCount = inputData.getInt(KEY_BASE_ITEM_COUNT, 0)
        if (sourceId < 0) return Result.failure()

        val contentTypes = SyncContentTypes(
            live = inputData.getBoolean(KEY_LIVE, true),
        )
        // Set when this run is the background remainder of a staged (priority) initial sync: the
        // foreground pass plus this one cover all content types, so together they count as a full
        // sync and the source must get its lastSyncAt — otherwise every later sync would take the
        // fresh-import fast path forever (row-id churn, no stale-row pruning).
        val completesInitialSync = inputData.getBoolean(KEY_COMPLETES_INITIAL_SYNC, false)
        // User picked "clean resync": this run may prune past the catalog-shrink guard. Read from the
        // work data (not persisted anywhere) so it applies to exactly this run.
        val forcePrune = inputData.getBoolean(KEY_FORCE_PRUNE, false)

        // One built-in source: the DTO is always available, no row lookup can fail.
        val source = sourceRepository.currentSource()
        // An automatic refresh never runs under a film or channel: it waits until playback stops
        // (WorkManager restarts it later if the wait outlives the job's time limit).
        if (reason == tv.own.owntv.core.sync.AutoRefresh.REASON && watchSession.watching.value.isNotEmpty()) {
            Log.i(TAG, "auto refresh waiting for playback to stop sourceId=$sourceId")
            watchSession.awaitIdle()
        }

        val effective = contentTypes.effectiveFor(source)
        Log.i(
            TAG,
            "Starting sync for source ${source.id} (${source.name}) reason=$reason " +
                "contentTypes=$contentTypes effective=$effective baseItemCount=$baseItemCount forcePrune=$forcePrune",
        )
        val progressPublisher = ProgressPublisher(effective, baseItemCount)
        progressPublisher.publishStarting()

        // Stale enqueue after Live was turned Off: clean pill, no syncer calls, no lastSyncAt stamp.
        if (!effective.hasAny) {
            progressPublisher.flush()
            Log.i(TAG, "Sync no-op empty effective sourceId=${source.id} reason=$reason")
            return Result.success()
        }

        val syncStartedAt = SystemClock.elapsedRealtime()
        val result = sourceRepository.sync(source, onProgress = { stage ->
            progressPublisher.publish(stage)
        }, contentTypes = contentTypes, forcePrune = forcePrune)
        progressPublisher.flush()
        Log.i(TAG, "SourceRepository.sync finished sourceId=${source.id} result=${result.name()} ms=${SystemClock.elapsedRealtime() - syncStartedAt}")

        return when (result) {
            is SyncResult.Success -> {
                val warningText = result.warnings.takeIf { it.isNotEmpty() }?.joinToString { it.phase }
                Log.i(TAG, "Sync succeeded for source ${source.id} (${source.name}) warnings=$warningText")
                // Remainder of a staged initial sync: stamp lastSyncAt once priority+remainder together
                // cover the enabled catalog (SyncManager alone won't — each pass is incomplete).
                if (completesInitialSync) {
                    settings.markSourceSynced(System.currentTimeMillis())
                    Log.i(TAG, "Staged initial sync complete — markSynced sourceId=${source.id}")
                }
                val finalizeStartedAt = SystemClock.elapsedRealtime()
                val deferIndexes = source.lastSyncAt == null
                runCatching { importFinalizer.finalize(source, deferIndexes = deferIndexes) }
                    .onSuccess { Log.i(TAG, "Import finalizer sourceId=${source.id} counts=$it ms=${SystemClock.elapsedRealtime() - finalizeStartedAt}") }
                    .onFailure { Log.w(TAG, "Import finalizer failed sourceId=${source.id} ms=${SystemClock.elapsedRealtime() - finalizeStartedAt}", it) }
                if (deferIndexes) {
                    catalogSyncScheduler.enqueueContentIndexBuild(reason = "fresh_sync")
                }
                Result.success()
            }
            is SyncResult.Failed -> {
                // A network blip / server 5xx shouldn't leave the catalog stale until the next
                // scheduled window — ask WorkManager to retry with backoff. Auth/URL failures stay terminal.
                if (isTransientSyncError(result.message, connectivity.isOnlineNow()) && runAttemptCount < MAX_RETRY_ATTEMPTS) {
                    Log.w(TAG, "Sync failed transiently for source ${source.id} attempt=$runAttemptCount — will retry: ${result.message}")
                    Result.retry()
                } else {
                    Log.w(TAG, "Sync failed for source ${source.id}: ${result.message}")
                    Result.failure()
                }
            }
            SyncResult.Cancelled -> Result.failure()
        }
    }

    private inner class ProgressPublisher(
        private val contentTypes: SyncContentTypes,
        private val baseItemCount: Int,
    ) {
        private var lastEmitAtMs = 0L
        private var emittedLiveCount = false
        private var lastLiveProcessed = 0
        private var lastTotalProcessed = 0
        private var pending: ImportStage? = null

        fun publishStarting() {
            val now = SystemClock.elapsedRealtime()
            setProgress(
                workDataOf(
                    KEY_BASE_ITEM_COUNT to baseItemCount,
                    KEY_PROGRESS_LIVE_PROCESSED to 0,
                    KEY_PROGRESS_LIVE_ACTIVE to contentTypes.live,
                ),
            )
            lastEmitAtMs = now
            lastTotalProcessed = 0
        }

        fun publish(stage: ImportStage) {
            pending = stage
            val now = SystemClock.elapsedRealtime()
            if (shouldEmit(stage, now)) {
                emit(stage, now)
            }
        }

        fun flush() {
            pending?.takeUnless { it.matchesLastEmit() }?.let { emit(it, SystemClock.elapsedRealtime()) }
            pending = null
        }

        private fun emit(stage: ImportStage, now: Long) {
            Log.d(
                TAG,
                "Progress emit total=${stage.totalProcessed} live=${stage.liveProcessed} " +
                    "sinceLastMs=${now - lastEmitAtMs}",
            )
            setProgress(stage.toWorkData(baseItemCount))
            lastEmitAtMs = now
            lastLiveProcessed = stage.liveProcessed
            lastTotalProcessed = stage.totalProcessed
            if (stage.liveProcessed > 0) emittedLiveCount = true
            pending = null
        }

        private fun shouldEmit(stage: ImportStage, now: Long): Boolean =
            (stage.liveProcessed > 0 && !emittedLiveCount) ||
                (now - lastEmitAtMs >= PROGRESS_MIN_INTERVAL_MS && stage.hasMeaningfulCountDelta())

        private fun ImportStage.hasMeaningfulCountDelta(): Boolean =
            totalProcessed - lastTotalProcessed >= PROGRESS_ITEM_STEP

        private fun ImportStage.matchesLastEmit(): Boolean =
            liveProcessed == lastLiveProcessed

        private fun setProgress(data: Data) {
            setProgressAsync(data)
        }
    }

    companion object {
        const val TAG = "CatalogSyncWorker"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val PROGRESS_MIN_INTERVAL_MS = 750L
        private const val PROGRESS_ITEM_STEP = 1_000
        const val KEY_SOURCE_ID = "sourceId"
        const val KEY_REASON = "reason"
        const val KEY_BASE_ITEM_COUNT = "baseItemCount"
        const val KEY_LIVE = "live"
        const val KEY_COMPLETES_INITIAL_SYNC = "completesInitialSync"
        const val KEY_FORCE_PRUNE = "forcePrune"
        const val KEY_PROGRESS_LIVE_PROCESSED = "liveProcessed"
        const val KEY_PROGRESS_LIVE_ACTIVE = "liveActive"
    }
}

private fun SyncResult.name(): String = when (this) {
    is SyncResult.Success -> "Success"
    is SyncResult.Failed -> "Failed"
    SyncResult.Cancelled -> "Cancelled"
}

private fun ImportStage.toWorkData(baseItemCount: Int): Data =
    workDataOf(
        CatalogSyncWorker.KEY_BASE_ITEM_COUNT to baseItemCount,
        CatalogSyncWorker.KEY_PROGRESS_LIVE_PROCESSED to liveProcessed,
        CatalogSyncWorker.KEY_PROGRESS_LIVE_ACTIVE to liveActive,
    )
