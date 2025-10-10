// ui/averias/AveriasSyncWorker.kt
package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
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
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AveriaEntity

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
            AveriaNotifications.ensureChannel(applicationContext)
            val nm = NotificationManagerCompat.from(applicationContext)
            porRegion.forEach { averia ->
                if (!hasNotificationPermission()) return@forEach
                nm.notify(
                    averia.caseId.hashCode(),
                    buildNewCaseNotification(averia)
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

    private fun hasNotificationPermission(): Boolean {
        val manager = NotificationManagerCompat.from(applicationContext)
        val enabled = manager.areNotificationsEnabled()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return enabled
        }
        val granted = ActivityCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        return enabled && granted
    }

    private fun buildNewCaseNotification(averia: AveriaEntity): android.app.Notification {
        val hora = AveriaNotifications.formatDateTime(averia.horaInicioMillis ?: averia.fechaInicioMillis)
            ?: applicationContext.getString(R.string.averia_notificacion_sin_hora)
        val lugar = resolveLugar(averia)
        val agencia = resolveAgencia(averia)
        val cliente = averia.cliente?.takeIf { it.isNotBlank() }
            ?: applicationContext.getString(R.string.averia_notificacion_sin_cliente)

        return NotificationCompat.Builder(applicationContext, AveriaNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                applicationContext.getString(
                    R.string.averia_notificacion_nueva_title_case,
                    averia.caseId
                )
            )
            .setContentText(
                applicationContext.getString(
                    R.string.averia_notificacion_nueva_summary,
                    hora,
                    lugar
                )
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    applicationContext.getString(
                        R.string.averia_notificacion_nueva_bigtext,
                        agencia,
                        lugar,
                        hora,
                        cliente
                    )
                ).setSummaryText(averia.caseId)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(AveriaNotifications.averiasPendingIntent(applicationContext))
            .setSound(
                Uri.parse(
                    "android.resource://${applicationContext.packageName}/${R.raw.beep}"
                )
            )
            .apply {
                AveriaNotifications.mapAction(
                    applicationContext,
                    averia.lat,
                    averia.lng,
                    lugar,
                    averia.caseId.hashCode()
                )?.let { addAction(it) }
            }
            .build()
    }

    private fun resolveLugar(averia: AveriaEntity): String =
        averia.localizacion?.takeIf { it.isNotBlank() }
            ?: averia.nombreAgencia?.takeIf { it.isNotBlank() }
            ?: averia.agencia?.takeIf { it.isNotBlank() }
            ?: applicationContext.getString(R.string.averia_notificacion_sin_lugar)

    private fun resolveAgencia(averia: AveriaEntity): String =
        averia.nombreAgencia?.takeIf { it.isNotBlank() }
            ?: averia.agencia?.takeIf { it.isNotBlank() }
            ?: applicationContext.getString(R.string.averia_notificacion_sin_agencia)

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
