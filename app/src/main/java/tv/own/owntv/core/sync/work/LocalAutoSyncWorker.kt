package tv.own.owntv.core.sync.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import tv.own.owntv.core.network.ConnectivityObserver
import tv.own.owntv.core.sync.local.LocalAutoSyncRunner

/**
 * The phone's hourly check ([LocalAutoSyncScheduler]): runs the due agreements, if any. A device that
 * was not there is the agreement's backoff to handle, not WorkManager's — the job itself succeeds.
 * The old UNMETERED constraint is checked here instead: WorkManager tracks it through a default
 * network callback, which throws SecurityException on some Android TV ROMs.
 */
class LocalAutoSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val runner: LocalAutoSyncRunner,
    private val connectivity: ConnectivityObserver,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // A metered connection means "wait for the next hourly window", not "fail" — the agreement's
        // own retry clock must not advance because the network happened to be expensive.
        if (connectivity.isMeteredNow()) return Result.success()
        runner.runDue("hourly")
        return Result.success()
    }
}
