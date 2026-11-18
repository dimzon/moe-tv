package tv.own.owntv.core.sync.work

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.work.ForegroundInfo
import kotlinx.coroutines.CancellationException
import tv.own.owntv.core.i18n.LocaleStore
import tv.own.owntv.core.network.ConnectivityObserver
import tv.own.owntv.core.repository.EpgRepository
import tv.own.owntv.core.sync.EpgActivityTracker
import tv.own.owntv.core.util.isTransientSyncError

/**
 * The WorkManager entry for the guide refresh. RuTube is the only source, so this runs the RuTube
 * EPG refresh ([EpgRepository.refresh]) — the API-driven grid written straight into the
 * shared EPG tables by [tv.own.owntv.core.sync.RuTubeEpgSyncer]. The old XMLTV-feed branch over a
 * standalone EPG-source store is gone with that system.
 */
class EpgSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val epgRepository: EpgRepository,
    private val connectivity: ConnectivityObserver,
    private val activityTracker: EpgActivityTracker,
    private val localeStore: LocaleStore,
    private val watchSession: tv.own.owntv.core.live.WatchSession,
) : CoroutineWorker(context, params) {

    /**
     * WorkManager asks for this when it promotes the work; [doWork] also sets it directly, because
     * only there is the source's name known. A guide fetch is minutes of network and parsing, and a
     * plain background worker does not survive that with the screen off — see [EpgSyncNotifications].
     */
    override suspend fun getForegroundInfo(): ForegroundInfo =
        EpgSyncNotifications.foregroundInfo(
            applicationContext,
            inputData.getLong(KEY_SOURCE_ID, 0L),
            sourceName = null,
            programmes = inputData.getInt(KEY_BASE_PROGRAMMES, 0),
            localeStore = localeStore,
        )

    override suspend fun doWork(): Result {
        val sourceId = inputData.getLong(KEY_SOURCE_ID, Long.MIN_VALUE)
        val reason = inputData.getString(KEY_REASON) ?: "unknown"
        if (sourceId == Long.MIN_VALUE) return Result.failure()

        // The NetworkType.CONNECTED constraint is gone: WorkManager tracks it through a default
        // network callback, which throws SecurityException on some Android TV ROMs. Defer here
        // instead — retry with WorkManager's backoff until the device is online again.
        if (!connectivity.isOnlineNow()) {
            Log.i(TAG, "offline, deferring EPG sync sourceId=$sourceId")
            return Result.retry()
        }

        // One built-in source: the DTO needs no row lookup, so a stale source id can't skip the run.
        val source = tv.own.owntv.core.repository.BuiltInSource.current

        // An automatic refresh never runs under playback: it waits until playback stops
        // (WorkManager restarts it later if the wait outlives the job's time limit).
        if (reason == tv.own.owntv.core.sync.AutoRefresh.REASON && watchSession.watching.value.isNotEmpty()) {
            Log.i(TAG, "auto refresh waiting for playback to stop sourceId=$sourceId")
            watchSession.awaitIdle()
        }
        val baseProgrammes = inputData.getInt(KEY_BASE_PROGRAMMES, 0)
        val progress = ProgressPublisher(baseProgrammes, source.id, source.name) { channels, programmes ->
            activityTracker.progress(source.id, channels, programmes)
        }
        val startedAt = SystemClock.elapsedRealtime()
        Log.i(TAG, "Starting EPG sync sourceId=${source.id} reason=$reason")
        activityTracker.started(source.id, source.name)
        // Best effort, always: Android 12 and later refuse a foreground service started from the
        // background in some states, and a guide that syncs the old way is what shipped until now.
        // Losing the promotion must never lose the sync with it.
        runCatching { setForeground(progress.foregroundInfo()) }
            .onFailure { Log.i(TAG, "EPG sync stays in the background: ${it.message}") }

        try {
            val programmes = epgRepository.refresh { channels, count ->
                progress.publish(channels, count)
            }
            progress.flush()
            Log.i(
                TAG,
                "EPG sync finished sourceId=${source.id} reason=$reason programmes=$programmes ms=${SystemClock.elapsedRealtime() - startedAt}",
            )
            return Result.success()
        } catch (c: CancellationException) {
            Log.i(TAG, "EPG sync cancelled sourceId=${source.id} reason=$reason")
            throw c
        } catch (e: Exception) {
            val online = connectivity.isOnlineNow()
            // Persist the original exception text. Classification belongs to the EPG Compose renderer;
            // storing a translated sentence would freeze the language active during the background run.
            // The English comparison needles remain in ErrorMessages.kt and are stable protocol keys.
            val rawMessage = e.message
            // Transient network trouble → WorkManager retry with backoff instead of staying stale
            // until the next scheduled window. Permanent errors stay terminal.
            return if (shouldRetryEpgSync(e, online, runAttemptCount)) {
                Log.w(TAG, "EPG sync failed transiently sourceId=${source.id} reason=$reason attempt=$runAttemptCount — will retry", e)
                Result.retry()
            } else {
                Log.w(TAG, "EPG sync failed sourceId=${source.id} reason=$reason message=$rawMessage", e)
                Result.failure()
            }
        } finally {
            // Always clear the pill entry — success, failure, retry, or cancellation — so it never sticks.
            activityTracker.finished(source.id)
        }
    }

    private inner class ProgressPublisher(
        private val baseProgrammes: Int,
        private val sourceId: Long,
        private val sourceName: String?,
        private val onEmit: (channels: Int, programmes: Int) -> Unit,
    ) {
        private var lastEmitAtMs = 0L
        private var lastChannels = -1
        private var lastProgrammes = -1
        private var pendingChannels = 0
        private var pendingProgrammes = 0
        private var hasPending = false

        /** Its own clock: the notification is for a human reading it, not for the progress bar. */
        private var lastNotifiedAtMs = 0L

        fun foregroundInfo(programmes: Int = baseProgrammes) =
            EpgSyncNotifications.foregroundInfo(
                applicationContext,
                sourceId,
                sourceName,
                programmes,
                localeStore,
            )

        fun publish(channels: Int, programmes: Int) {
            pendingChannels = channels
            pendingProgrammes = programmes
            hasPending = true
            val now = SystemClock.elapsedRealtime()
            if (lastEmitAtMs == 0L || (now - lastEmitAtMs >= PROGRESS_MIN_INTERVAL_MS && shouldEmit())) {
                emit(now)
            }
        }

        fun flush() {
            if (hasPending) emit(SystemClock.elapsedRealtime())
        }

        private fun shouldEmit(): Boolean =
            pendingChannels != lastChannels || pendingProgrammes != lastProgrammes

        private fun emit(now: Long) {
            if (!hasPending) return
            val channels = pendingChannels
            val programmes = pendingProgrammes
            if (channels == lastChannels && programmes == lastProgrammes && lastEmitAtMs != 0L) {
                hasPending = false
                return
            }
            setProgressAsync(
                workDataOf(
                    KEY_PROGRESS_CHANNELS to channels,
                    KEY_PROGRESS_PROGRAMMES to programmes,
                    KEY_BASE_PROGRAMMES to baseProgrammes,
                ),
            )
            // Also push the counts to the shell status pill (independent of WorkManager progress).
            onEmit(channels, programmes)
            // And to the ongoing notification, far more slowly — it is read by a person, and
            // rebuilding it at the parser's rate is work for nothing. Async and swallowed: the
            // service is already running by now, and a refused update must not end the sync.
            if (now - lastNotifiedAtMs >= NOTIFICATION_INTERVAL_MS) {
                lastNotifiedAtMs = now
                runCatching { setForegroundAsync(foregroundInfo(programmes)) }
            }
            lastEmitAtMs = now
            lastChannels = channels
            lastProgrammes = programmes
            hasPending = false
        }
    }

    companion object {
        const val TAG = "EpgSyncWorker"
        private const val PROGRESS_MIN_INTERVAL_MS = 750L

        /** The cadence the shell status pill reads at — slow enough to never matter, fast enough to move. */
        private const val NOTIFICATION_INTERVAL_MS = 2_000L
        const val KEY_SOURCE_ID = "sourceId"
        const val KEY_REASON = "reason"
        const val KEY_PROGRESS_CHANNELS = "channels"
        const val KEY_PROGRESS_PROGRAMMES = "programmes"
        const val KEY_BASE_PROGRAMMES = "baseProgrammes"
    }
}

internal const val MAX_EPG_RETRY_ATTEMPTS = 3

/**
 * Whether a failed EPG sync is worth another attempt.
 *
 * Extracted so the classification can be tested without WorkManager.
 *
 * The RuTube guide reports "no schedule upstream" as empty data, not as an error, so anything that
 * reaches this classification as an exception is real trouble. Genuinely broken mid-flight (a
 * dropped connection, a truncated download) arrives as an IOException and is retried with backoff
 * up to [MAX_EPG_RETRY_ATTEMPTS]; anything else is reported once and stays terminal.
 */
internal fun shouldRetryEpgSync(error: Throwable, online: Boolean, runAttemptCount: Int): Boolean {
    val transient = error is java.io.IOException || isTransientSyncError(error.message, online)
    return transient && runAttemptCount < MAX_EPG_RETRY_ATTEMPTS
}
