// ui/averias/AveriasSyncWorker.kt
package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.Arasoftsolutions.tecniapp_ice.BuildConfig
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.concurrent.TimeUnit

class AveriasSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase.getInstance(applicationContext)
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
        val regionNormalizada = regionObjetivo?.let { normalize(it) }

        val porRegion = if (regionNormalizada.isNullOrBlank()) {
            nuevos
        } else {
            nuevos.filter { averia ->
                val regionAveria = normalize(averia.region)
                regionAveria.contains(regionNormalizada)
            }
        }

        // 3. Notifica si hay nuevos casos
        if (porRegion.isNotEmpty()) {
            val nm = NotificationManagerCompat.from(applicationContext)
            porRegion.forEach forEachId@{ averia ->
                val id = averia.caseId
                if (
                    ActivityCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) return@forEachId
                nm.notify(
                    id.hashCode(),
                    NotificationCompat.Builder(applicationContext, "averias_channel")
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(applicationContext.getString(R.string.averia_notificacion_nueva_title))
                        .setContentText(
                            applicationContext.getString(
                                R.string.averia_notificacion_nueva_body,
                                id,
                                averia.nombreAgencia ?: averia.agencia ?: ""
                            )
                        )
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setSound(
                            Uri.parse(
                                "android.resource://${applicationContext.packageName}/${R.raw.beep}"
                            )
                        )
                        .build()
                )
            }
        }
        Result.success()
    }

    private fun normalize(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
        return normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase()
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
    }
}
