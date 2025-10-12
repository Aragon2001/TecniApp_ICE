// ui/averias/AveriasSyncWorker.kt
package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.Arasoftsolutions.tecniapp_ice.BuildConfig
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase


class AveriasSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(applicationContext)
        val repo = AveriasRepository(db)
        val roomRepo = RoomRepository.getInstance(applicationContext)

        // 1. Sube los pendientes a Firebase
        repo.syncPendientesConFirebase()

        // 2. Descarga averías nuevas desde ICE
        val nuevos = repo.syncFromIce(BuildConfig.ICE_BEARER)

        val usuario = FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            runCatching { roomRepo.obtenerUsuario(uid) }.getOrNull()
        }

        val regionObjetivo = usuario?.regionNombre?.takeIf { !it.isNullOrBlank() }
            ?: usuario?.region?.takeIf { !it.isNullOrBlank() }
        val regionNormalizada = regionObjetivo?.let { normalizeAveriaText(it) }

        val filters = AveriaNotificationPreferences.normalizedAgencies(applicationContext)
        val notificationsEnabled = AveriaNotificationPreferences.areNotificationsEnabled(applicationContext)

        val porRegion = filterAveriasByRegion(nuevos, regionNormalizada)
        val filtradas = filterAveriasByAgencies(porRegion, filters)

        // 3. Notifica si hay nuevos casos
        if (notificationsEnabled && filtradas.isNotEmpty()) {
            AveriaNotificationDispatcher.notifyNewCases(applicationContext, filtradas)
        }
        Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<AveriasSyncWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                "averias_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }

        fun triggerNow(ctx: Context) {
            val request = OneTimeWorkRequestBuilder<AveriasSyncWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                "averias_sync_now",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
