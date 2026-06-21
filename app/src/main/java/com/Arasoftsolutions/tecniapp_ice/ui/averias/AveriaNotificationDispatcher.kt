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
        notify(context, averias, type = NotifType.NEW)
    }

    fun notifyResolvedCases(context: Context, averias: List<AveriaEntity>) {
        notify(context, averias, type = NotifType.RESOLVED)
    }

    fun notifyAssignedCase(context: Context, averia: AveriaEntity) {
        notify(context, listOf(averia), type = NotifType.ASSIGNED)
    }

    private enum class NotifType { NEW, RESOLVED, ASSIGNED }

    private fun notify(context: Context, averias: List<AveriaEntity>, type: NotifType) {
        if (averias.isEmpty()) return

        AveriaNotifications.ensureChannels(context)
        val manager = NotificationManagerCompat.from(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Sin permiso POST_NOTIFICATIONS — notificación cancelada")
            return
        }

        averias.forEach { averia ->
            val notification = buildNotification(context, averia, type)
            manager.notify(averia.caseId.hashCode(), notification)
            Log.d(TAG, "Notificación enviada caseId=${averia.caseId} type=$type")
        }
    }

    private fun buildNotification(
        context: Context,
        averia: AveriaEntity,
        type: NotifType
    ): Notification {
        val hasCoords = averia.lat != null && averia.lng != null &&
            averia.lat != 0.0 && averia.lng != 0.0

        // Canal diferenciado: nueva/asignada con alarma, resuelta silenciosa
        val channelId = if (type == NotifType.RESOLVED)
            AveriaNotifications.CHANNEL_ID_RESUELTA
        else
            AveriaNotifications.CHANNEL_ID_NUEVA

        val (colorRes, smallIconRes, titulo, subtitulo) = when (type) {
            NotifType.NEW -> Quad(
                R.color.averia_notification_pending,
                R.drawable.ic_notification_bolt,
                "⚡ Nueva avería reportada",
                buildShortline(averia)
            )
            NotifType.ASSIGNED -> Quad(
                R.color.averia_notification_accent,
                R.drawable.ic_outage_assign,
                "🔔 Avería asignada a tu cuadrilla",
                buildShortline(averia)
            )
            NotifType.RESOLVED -> Quad(
                R.color.averia_notification_resolved,
                R.drawable.ic_notification,
                "✅ Avería resuelta",
                buildShortline(averia)
            )
        }

        val accentColor = ContextCompat.getColor(context, colorRes)
        val cuerpo = buildBody(averia, type)

        val openAveriaIntent = buildOpenAveriaIntent(context, averia)

        // Para notificaciones: preferir Field Maps; si no está, mostrar chooser
        val mapPendingIntent = if (hasCoords) {
            buildMapPendingIntent(context, averia)
        } else null

        // Mapa estático expandido — label debe ser alfanumérico (límite de Static Maps API)
        val mapUrl = if (hasCoords) {
            AveriaStaticMapProvider.buildUrl(
                context, averia.lat, averia.lng,
                label = averia.caseId
            )
        } else null

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIconRes)
            .setContentTitle(titulo)
            .setContentText(subtitulo)
            .setColor(accentColor)
            .setColorized(true)
            .setPriority(
                if (type == NotifType.RESOLVED) NotificationCompat.PRIORITY_DEFAULT
                else NotificationCompat.PRIORITY_HIGH
            )
            .setCategory(
                if (type == NotifType.RESOLVED) NotificationCompat.CATEGORY_STATUS
                else NotificationCompat.CATEGORY_ALARM
            )
            .setAutoCancel(true)
            .setContentIntent(openAveriaIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setWhen(
                // Notificaciones de cambio de estado deben aparecer con la hora de envío actual,
                // no con la fecha de inicio de la avería (que puede ser horas/días antes)
                if (type == NotifType.NEW) averia.fechaInicioMillis.takeIf { it > 0 }
                    ?: System.currentTimeMillis()
                else System.currentTimeMillis()
            )
            .setShowWhen(true)

        builder.addAction(
            NotificationCompat.Action.Builder(
                smallIconRes,
                "Ver avería",
                openAveriaIntent
            ).build()
        )

        if (mapPendingIntent != null) {
            builder.addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_map_placeholder,
                    "Ver en mapa",
                    mapPendingIntent
                ).build()
            )
        }

        // BigPictureStyle con mapa expandido
        if (mapUrl != null) {
            runCatching {
                val bitmap = AveriaStaticMapProvider.bitmapOrPlaceholder(context, mapUrl)
                val style = NotificationCompat.BigPictureStyle()
                    .bigPicture(bitmap)
                    .bigLargeIcon(null as android.graphics.Bitmap?)
                    .setSummaryText(cuerpo)
                builder.setStyle(style)
                builder.setLargeIcon(bitmap)
            }.onFailure {
                Log.w(TAG, "No se pudo cargar mapa estático", it)
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
            }
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
        }

        return builder.build()
    }

    private fun buildMapPendingIntent(context: Context, averia: AveriaEntity): PendingIntent? {
        val lat = averia.lat ?: return null
        val lng = averia.lng ?: return null
        val coordStr = String.format(Locale.US, "%.6f,%.6f", lat, lng)

        // Intentar Field Maps primero (abre directamente con zoom en la coordenada)
        val fieldMapsUri = Uri.parse("arcgis-fieldmaps://?referenceContext=center&center=$coordStr&scale=2257")
        val fieldMapsIntent = Intent(Intent.ACTION_VIEW, fieldMapsUri).apply {
            setPackage("com.esri.fieldmaps")
        }
        if (fieldMapsIntent.resolveActivity(context.packageManager) != null) {
            return PendingIntent.getActivity(
                context,
                averia.caseId.hashCode() + 10,
                fieldMapsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        // Fallback: Google Maps con número de avería como etiqueta del pin
        val labelEncoded = try {
            java.net.URLEncoder.encode("⚡ Avería #${averia.caseId}", "UTF-8")
        } catch (e: Exception) { averia.caseId }
        val geoUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($labelEncoded)")
        val gmapsIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
            setPackage("com.google.android.apps.maps")
        }
        if (gmapsIntent.resolveActivity(context.packageManager) != null) {
            return PendingIntent.getActivity(
                context,
                averia.caseId.hashCode() + 11,
                gmapsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        // Último recurso: chooser con todas las apps disponibles
        return AveriaMapLauncher.pendingIntent(
            context, lat, lng, averia.caseId,
            requestCode = averia.caseId.hashCode() + 1
        )
    }

    private fun buildShortline(averia: AveriaEntity): String = buildString {
        append("Caso #${averia.caseId}")
        averia.nombreAgencia?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
        averia.direccion?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
            ?: averia.localizacion?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
    }

    private fun buildBody(averia: AveriaEntity, type: NotifType): String = buildString {
        val icon = when (type) {
            NotifType.NEW -> "🚨"
            NotifType.ASSIGNED -> "📋"
            NotifType.RESOLVED -> "✅"
        }
        appendLine("$icon Avería #${averia.caseId}")
        averia.nombreAgencia?.takeIf { it.isNotBlank() }
            ?.let { appendLine("🏢 $it") }
        averia.nise?.takeIf { it.isNotBlank() }
            ?.let { appendLine("🔢 NISE: $it") }
        averia.direccion?.takeIf { it.isNotBlank() }
            ?.let { appendLine("📍 $it") }
            ?: averia.localizacion?.takeIf { it.isNotBlank() }
                ?.let { appendLine("📍 $it") }
        averia.observaciones?.takeIf { it.isNotBlank() }
            ?.let { appendLine("📝 $it") }
        averia.causa?.takeIf { it.isNotBlank() && type == NotifType.RESOLVED }
            ?.let { appendLine("🔧 Causa: $it") }
        averia.fechaInicioMillis.takeIf { it > 0 }?.let {
            val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
            appendLine("🕐 ${fmt.format(Date(it))}")
        }
        when (type) {
            NotifType.NEW -> appendLine("👆 Toca para ver el detalle.")
            NotifType.ASSIGNED -> appendLine("👆 Nueva asignación para tu cuadrilla.")
            NotifType.RESOLVED -> appendLine("🎉 ¡Buen trabajo!")
        }
    }.trimEnd()

    private fun buildOpenAveriaIntent(context: Context, averia: AveriaEntity): PendingIntent {
        val intent = Intent(context, ActivityMain::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_averia", averia.caseId)
            putExtra("caseId", averia.caseId)
            putExtra("openAveriaDetail", true)
            putExtra("source", "notification")
        }
        return PendingIntent.getActivity(
            context,
            averia.caseId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
    private operator fun <A, B, C, D> Quad<A, B, C, D>.component1() = a
    private operator fun <A, B, C, D> Quad<A, B, C, D>.component2() = b
    private operator fun <A, B, C, D> Quad<A, B, C, D>.component3() = c
    private operator fun <A, B, C, D> Quad<A, B, C, D>.component4() = d
}
