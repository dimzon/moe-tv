package tv.own.owntv.core.sync.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import org.koin.core.context.GlobalContext

class KoinWorkerFactory : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        val koin = GlobalContext.get()
        return when (workerClassName) {
            EpgSyncWorker::class.java.name -> EpgSyncWorker(
                appContext,
                workerParameters,
                koin.get(),
                koin.get(),
                koin.get(),
                koin.get(),
                koin.get(),
            )
            CatalogSyncWorker::class.java.name -> CatalogSyncWorker(
                appContext,
                workerParameters,
                koin.get(),
                koin.get(),
                koin.get(),
                koin.get(),
                koin.get(),
                koin.get(),
                koin.get(), // tvHomePublisher
            )
            ContentIndexWorker::class.java.name -> ContentIndexWorker(
                appContext,
                workerParameters,
                koin.get(),
            )
            tv.own.owntv.core.sync.work.LocalAutoSyncWorker::class.java.name -> tv.own.owntv.core.sync.work.LocalAutoSyncWorker(
                appContext,
                workerParameters,
                koin.get(),
                koin.get(),
            )
            else -> null
        }
    }
}
