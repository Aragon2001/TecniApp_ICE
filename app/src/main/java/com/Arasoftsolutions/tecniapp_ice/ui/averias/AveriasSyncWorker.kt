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
import androidx.work.workDataOf
import androidx.work.WorkInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.Database.sync.AppSyncCoordinator
import com.Arasoftsolutions.tecniapp_ice.session.SessionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.tasks.await
import com.Arasoftsolutions.tecniapp_ice.notifications.SyncStatusNotifications


class AveriasSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val shouldNotifySync = inputData.getBoolean(KEY_NOTIFY_SYNC, false)
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser == null) return@withContext Result.success()

        val reloadResult = runCatching { currentUser.reload().await() }
        if (reloadResult.exceptionOrNull() is FirebaseAuthInvalidUserException) {
            SessionManager.signOutAndClear(applicationContext)
            return@withContext Result.success()
        }

        runCatching {
            AppSyncCoordinator.runExclusive {
            val db = AppDatabase.getInstance(applicationContext)
            val repo = AveriasRepository(db)
            // 1. Sube los pendientes a Firebase
            repo.syncPendientesConFirebase()

            // 2. Refresca Room desde Firebase cuando aplique
            repo.pullFromFirebaseOnce()

            val uid = auth.currentUser?.uid
            if (!uid.isNullOrBlank()) {
                RoomRepository.getInstance(applicationContext).upsertUserFromFirebase(uid)
            }
            }
        }.onFailure { return@withContext Result.retry() }
        if (shouldNotifySync) {
            SyncStatusNotifications.notifySynced(applicationContext)
        }
        Result.success()
    }

    companion object {
        const val UNIQUE_PERIODIC_WORK = "averias_sync"
        const val UNIQUE_MANUAL_WORK = "averias_sync_now"
        private const val KEY_NOTIFY_SYNC = "notify_sync"

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
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }

        fun triggerNow(ctx: Context, showSyncNotification: Boolean = false) {
            val manager = WorkManager.getInstance(ctx)
            val runningOrEnqueued = runCatching {
                manager.getWorkInfosForUniqueWork(UNIQUE_MANUAL_WORK).get().any { info ->
                    info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED
                }
            }.getOrDefault(false)
            if (runningOrEnqueued) return

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<AveriasSyncWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_NOTIFY_SYNC to showSyncNotification))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            manager.enqueueUniqueWork(
                UNIQUE_MANUAL_WORK,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
