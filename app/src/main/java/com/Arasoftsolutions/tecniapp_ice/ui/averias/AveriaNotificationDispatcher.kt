package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.Arasoftsolutions.tecniapp_ice.ActivityMain
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AveriaEntity
import com.Arasoftsolutions.tecniapp_ice.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AveriaNotificationDispatcher {

    private const val TAG = "AveriaDispatcher"

    fun notifyNewCases(context: Context, averias: List<AveriaEntity>) {
        notify(context, averias, isResolved = false, isAssigned = false)
    }

    fun notifyResolvedCases(context: Context, averias: List<AveriaEntity>) {
        notify(context, averias, isResolved = true, isAssigned = false)
    }

    fun notifyAssignedCase(context: Context, averia: AveriaEntity) {
        notify(context, listOf(averia), isResolved = false, isAssigned = true)
    }

    private fun notify(
        context: Context,
        averias: List<AveriaEntity>,
        isResolved: Boolean,
        isAssigned: Boolean
    ) {
        if (averias.isEmpty()) return

        AveriaNotifications.ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Sin permiso POST_NOTIFICATIONS — notificación cancelada")
            return
        }

        averias.forEach { averia ->
            val notification = buildNotification(context, averia, isResolved, isAssigned)
            manager.notify(averia.caseId.hashCode(), notification)
            Log.d(TAG, "Notificación enviada caseId=${averia.caseId} resolved=$isResolved assigned=$isAssigned")
        }
    }

    private fun buildNotification(
        context: Context,
        averia: AveriaEntity,
        isResolved: Boolean,
        isAssigned: Boolean
    ): Notification {
        val titulo = when {
            isAssigned -> "Avería asignada"
            isResolved -> "Avería resuelta"
            else -> "Nueva avería"
        }

        val estadoColor = if (isResolved)
            ContextCompat.getColor(context, R.color.averia_notification_resolved)
        else
            ContextCompat.getColor(context, R.color.averia_notification_pending)

        val corta = buildString {
            append("Caso ${averia.caseId}")
            averia.nombreAgencia?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
            averia.estado.takeIf { it.isNotBlank() }?.let { append(" · $it") }
        }

        val cuerpo = buildNotificationBody(averia)
        val openAppPendingIntent = buildOpenAppIntent(context, averia)

        val builder = NotificationCompat.Builder(context, AveriaNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_bolt)
            .setContentTitle(titulo)
            .setContentText(corta)
            .setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setColor(estadoColor)
            .setContentIntent(openAppPendingIntent)
            .addAction(R.drawable.ic_outage_assign, "Ver avería", openAppPendingIntent)

        // Map action only when coordinates are valid
        val lat = averia.lat
        val lng = averia.lng
        if (lat != null && lng != null && lat != 0.0 && lng != 0.0) {
            val mapPendingIntent = buildMapIntent(context, averia, lat, lng)
            builder.addAction(R.drawable.ic_map_placeholder, "Ver mapa", mapPendingIntent)
        }

        return builder.build()
    }

    private fun buildNotificationBody(averia: AveriaEntity): String = buildString {
        appendLine("Caso: ${averia.caseId}")
        averia.nombreAgencia?.takeIf { it.isNotBlank() }?.let { appendLine("Agencia: $it") }
        averia.estado.takeIf { it.isNotBlank() }?.let { appendLine("Estado: $it") }
        averia.nise?.takeIf { it.isNotBlank() }?.let { appendLine("NISE: $it") }
        averia.localizacion?.takeIf { it.isNotBlank() }?.let { appendLine("Localización: $it") }
        averia.direccion?.takeIf { it.isNotBlank() }?.let { appendLine("Dirección: $it") }
        averia.observaciones?.takeIf { it.isNotBlank() }?.let { appendLine("Observación: $it") }
        val fechaMillis = averia.fechaInicioMillis.takeIf { it > 0 }
        if (fechaMillis != null) {
            val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            appendLine("Ingreso: ${fmt.format(Date(fechaMillis))}")
        }
    }.trimEnd()

    private fun buildOpenAppIntent(context: Context, averia: AveriaEntity): PendingIntent {
        val intent = Intent(context, ActivityMain::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_averia", averia.caseId)
            putExtra("caseId", averia.caseId)
            putExtra("openAveriaDetail", true)
            putExtra("source", "fcm_averia")
        }
        return PendingIntent.getActivity(
            context,
            averia.caseId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildMapIntent(
        context: Context,
        averia: AveriaEntity,
        lat: Double,
        lng: Double
    ): PendingIntent {
        val geoUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${averia.caseId})")
        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
        return PendingIntent.getActivity(
            context,
            averia.caseId.hashCode() + 1,
            mapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
