package com.themis.mobile.sync

import android.content.Context
import androidx.work.*
import com.themis.mobile.ThemisApp
import java.util.concurrent.TimeUnit

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        (applicationContext as ThemisApp).syncEngine.sync()
        return Result.success()   // "desktop not found" is normal when away from home; just try next period
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("themis-sync", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
