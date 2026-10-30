package app.pulse.spike.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.pulse.spike.ble.BandSync
import app.pulse.spike.ble.MessageLog
import app.pulse.spike.ble.hasBlePermissions
import app.pulse.spike.data.PulseDb
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runs about every 30 minutes, even when Pulse is closed: syncs the band if the last sync is
 * older than 55 minutes, then sends any notifications that are due.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        val db = PulseDb(app)
        val address = db.getMeta("band_address") ?: return Result.success()

        val lastSync = db.getMeta("last_sync")?.toLongOrNull() ?: 0L
        if (System.currentTimeMillis() - lastSync > 55 * 60_000L && hasBlePermissions(app)) {
            val log = MessageLog(File(app.filesDir, "logs"))
            BandSync(app, db, log, onStatus = { _, _ -> }, onDataChanged = {}).run(address, db.getMeta("band_name"))
        }
        Notifier(app, db).check()
        return Result.success()
    }
}

object BackgroundSync {
    private const val NAME = "pulse-sync"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }
}
