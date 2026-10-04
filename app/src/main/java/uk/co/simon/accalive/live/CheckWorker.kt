package uk.co.simon.accalive.live

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import uk.co.simon.accalive.App
import java.util.concurrent.TimeUnit

/** Fallback check every 15 minutes so results still alert if live tracking was stopped. */
class CheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as App).repo
        if (repo.hasActive()) {
            try {
                repo.refresh(force = true)
            } catch (_: Exception) {
                return Result.retry()
            }
        }
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<CheckWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("acca-check", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
