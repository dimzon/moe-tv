package tv.own.owntv.core.sync.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * The phone's hourly automatic-sync check. One periodic work, with the battery not low; the worker
 * does nothing unless an agreement is due, so most runs cost a DataStore read. The old UNMETERED
 * constraint is gone: WorkManager tracks it by registering a default network callback, which throws
 * SecurityException on some Android TV ROMs. LocalAutoSyncWorker checks isMeteredNow() instead.
 */
class LocalAutoSyncScheduler(private val context: Context) {

    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<LocalAutoSyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .addTag(TAG)
            .build()
        // UPDATE, not KEEP: an install upgraded from a version with the UNMETERED constraint must not
        // keep the old spec in WorkManager's DB — that stale spec is what re-triggers the crash.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        const val TAG = "local-auto-sync"
        const val WORK_NAME = "owntv-local-auto-sync-hourly"
    }
}
