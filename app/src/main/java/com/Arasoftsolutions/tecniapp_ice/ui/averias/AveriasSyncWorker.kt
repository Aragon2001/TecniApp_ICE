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
import com.Arasoftsolutions.tecniapp_ice.BuildConfig
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase


class AveriasSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            val db = AppDatabase.getInstance(applicationContext)
            val repo = AveriasRepository(db)
            val roomRepo = RoomRepository.getInstance(applicationContext)

            // 1. Sube los pendientes a Firebase
            repo.syncPendientesConFirebase()

            val shouldDownload = inputData.getBoolean(KEY_INCLUDE_DOWNLOAD, false)

            if (shouldDownload) {
                val usuario = FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                    runCatching { roomRepo.obtenerUsuario(uid) }.getOrNull()
                }

                val regionObjetivo = usuario?.regionNombre?.takeIf { !it.isNullOrBlank() }
                    ?: usuario?.region?.takeIf { !it.isNullOrBlank() }
                val regionNormalizada = regionObjetivo?.let { normalizeAveriaText(it) }

                val filters = AveriaNotificationPreferences.normalizedAgencies(applicationContext)
                val notificationsEnabled = AveriaNotificationPreferences.areNotificationsEnabled(applicationContext)

                // 2. Descarga averías nuevas desde ICE cuando se solicita manualmente
                val syncResult = repo.syncFromIce(
                    bearer = BuildConfig.ICE_BEARER,
                    normalizedRegion = regionNormalizada,
                    agencyFilters = filters
                )

                val porRegion = filterAveriasByRegion(syncResult.nuevas, regionNormalizada)
                val filtradas = filterAveriasByAgencies(porRegion, filters)

                // 3. Notifica si hay nuevos casos
                if (notificationsEnabled && filtradas.isNotEmpty()) {
                    AveriaNotificationDispatcher.notifyNewCases(applicationContext, filtradas)
                }

                val resueltasRegion = filterAveriasByRegion(syncResult.resueltas, regionNormalizada)
                val resueltasFiltradas = filterAveriasByAgencies(resueltasRegion, filters)
                if (notificationsEnabled && resueltasFiltradas.isNotEmpty()) {
                    AveriaNotificationDispatcher.notifyResolvedCases(applicationContext, resueltasFiltradas)
                }
            }
        }.onFailure { return@withContext Result.retry() }
        Result.success()
    }

    companion object {
        private const val KEY_INCLUDE_DOWNLOAD = "include_download"
        private const val UNIQUE_PERIODIC_WORK = "averias_sync"
        const val UNIQUE_MANUAL_WORK = "averias_sync_now"

        fun schedule(ctx: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val req = PeriodicWorkRequestBuilder<AveriasSyncWorker>(15, TimeUnit.MINUTES)
                .setInputData(workDataOf(KEY_INCLUDE_DOWNLOAD to true))
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
                .setInputData(workDataOf(KEY_INCLUDE_DOWNLOAD to true))
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                UNIQUE_MANUAL_WORK,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
