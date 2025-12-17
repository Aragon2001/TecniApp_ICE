// ui/averias/AveriasSyncWorker.kt
package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase


class AveriasSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            val db = AppDatabase.getInstance(applicationContext)
            val repo = AveriasRepository(db)
            // 1. Sube los pendientes a Firebase
            repo.syncPendientesConFirebase()

            // 2. Refresca Room desde Firebase cuando aplique
            repo.pullFromFirebaseOnce()
        }.onFailure { return@withContext Result.retry() }
        Result.success()
    }

    companion object {
        private const val UNIQUE_PERIODIC_WORK = "averias_sync"
        const val UNIQUE_MANUAL_WORK = "averias_sync_now"

        fun schedule(ctx: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val req = PeriodicWorkRequestBuilder<AveriasSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                req
            )
        }

        fun triggerNow(ctx: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<AveriasSyncWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                UNIQUE_MANUAL_WORK,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
