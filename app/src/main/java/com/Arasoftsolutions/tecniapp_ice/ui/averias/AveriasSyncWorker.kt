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
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.session.SessionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.tasks.await


class AveriasSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val reloadResult = runCatching { currentUser.reload().await() }
            if (reloadResult.exceptionOrNull() is FirebaseAuthInvalidUserException) {
                SessionManager.signOutAndClear(applicationContext)
                return@withContext Result.success()
            }
        }

        runCatching {
            val db = AppDatabase.getInstance(applicationContext)
            val repo = AveriasRepository(db)
            // 1. Sube los pendientes a Firebase
            repo.syncPendientesConFirebase()

            // 2. Refresca Room desde Firebase cuando aplique
            val pullResult = repo.pullFromFirebaseOnce()
            if (pullResult.hadLocalData && pullResult.newCases.isNotEmpty()) {
                if (!AveriasForegroundTracker.isAveriasVisible &&
                    AveriaNotificationPreferences.areNotificationsEnabled(applicationContext)
                ) {
                    val agencyFilters =
                        AveriaNotificationPreferences.normalizedAgencies(applicationContext)
                    val filtered = pullResult.newCases.filter { averia ->
                        shouldNotifyForAgency(averia, agencyFilters)
                    }
                    if (filtered.isNotEmpty()) {
                        AveriaNotificationDispatcher.notifyNewCases(applicationContext, filtered)
                    }
                }
            }

            val uid = auth.currentUser?.uid
            if (!uid.isNullOrBlank()) {
                RoomRepository.getInstance(applicationContext).upsertUserFromFirebase(uid)
            }
        }.onFailure { return@withContext Result.retry() }
        Result.success()
    }

    companion object {
        const val UNIQUE_PERIODIC_WORK = "averias_sync"
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
                ExistingPeriodicWorkPolicy.KEEP,
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
