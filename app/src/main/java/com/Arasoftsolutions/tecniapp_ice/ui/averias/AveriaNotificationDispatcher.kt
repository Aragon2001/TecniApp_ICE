package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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

        AveriaNotifications.ensureChannel(context)
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

        // ── Colores y texto según tipo ──────────────────────────────────────
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
                "✅ ¡Avería resuelta! Buen trabajo",
                buildShortline(averia)
            )
        }

        val accentColor = ContextCompat.getColor(context, colorRes)
        val cuerpo = buildBody(averia, type)

        // ── Intents ─────────────────────────────────────────────────────────
        val openAveriaIntent = buildOpenAveriaIntent(context, averia)
        val mapPendingIntent = if (hasCoords) {
            AveriaMapLauncher.pendingIntent(
                context,
                averia.lat,
                averia.lng,
                averia.caseId,
                requestCode = averia.caseId.hashCode() + 1
            )
        } else null

        // ── Mapa estático expandido ─────────────────────────────────────────
        val mapUrl = if (hasCoords) {
            AveriaStaticMapProvider.buildUrl(
                context, averia.lat, averia.lng,
                label = averia.caseId
            )
        } else null

        val builder = NotificationCompat.Builder(context, AveriaNotifications.CHANNEL_ID)
            .setSmallIcon(smallIconRes)
            .setContentTitle(titulo)
            .setContentText(subtitulo)
            .setColor(accentColor)
            .setColorized(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(
                if (type == NotifType.RESOLVED) NotificationCompat.CATEGORY_STATUS
                else NotificationCompat.CATEGORY_ALARM
            )
            .setAutoCancel(true)
            .setContentIntent(openAveriaIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // Añadir acción Ver avería
        builder.addAction(
            NotificationCompat.Action.Builder(
                smallIconRes,
                "Ver avería",
                openAveriaIntent
            ).build()
        )

        // Añadir acción Ver mapa (si hay coordenadas)
        if (mapPendingIntent != null) {
            builder.addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_map_placeholder,
                    "Ver en mapa",
                    mapPendingIntent
                ).build()
            )
        }

        // BigPictureStyle: mapa cuando se expande la notificación
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
                Log.w(TAG, "No se pudo cargar mapa estático para notificación", it)
                builder.setStyle(
                    NotificationCompat.BigTextStyle().bigText(cuerpo)
                )
            }
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
        }

        return builder.build()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun buildShortline(averia: AveriaEntity): String = buildString {
        append("Caso ${averia.caseId}")
        averia.nombreAgencia?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
        averia.estado.takeIf { it.isNotBlank() }?.let { append(" · $it") }
    }

    private fun buildBody(averia: AveriaEntity, type: NotifType): String = buildString {
        val icon = when (type) {
            NotifType.NEW -> "🚨"
            NotifType.ASSIGNED -> "📋"
            NotifType.RESOLVED -> "✅"
        }
        appendLine("$icon Caso: ${averia.caseId}")
        averia.nombreAgencia?.takeIf { it.isNotBlank() }
            ?.let { appendLine("🏢 Agencia: $it") }
        averia.estado.takeIf { it.isNotBlank() }
            ?.let { appendLine("📌 Estado: $it") }
        averia.nise?.takeIf { it.isNotBlank() }
            ?.let { appendLine("🔢 NISE: $it") }
        averia.localizacion?.takeIf { it.isNotBlank() }
            ?.let { appendLine("📍 Localización: $it") }
        averia.direccion?.takeIf { it.isNotBlank() }
            ?.let { appendLine("🗺 Dirección: $it") }
        averia.observaciones?.takeIf { it.isNotBlank() }
            ?.let { appendLine("📝 $it") }
        averia.fechaInicioMillis.takeIf { it > 0 }?.let {
            val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            appendLine("🕐 Ingreso: ${fmt.format(Date(it))}")
        }
        when (type) {
            NotifType.NEW -> appendLine("👆 Toca para ver el detalle y asignar.")
            NotifType.ASSIGNED -> appendLine("👆 Tu cuadrilla tiene una nueva asignación.")
            NotifType.RESOLVED -> appendLine("🎉 ¡Excelente gestión del equipo!")
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

    // Hack-free tuple para destructuring local
    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
    private operator fun <A, B, C, D> Quad<A, B, C, D>.component1() = a
    private operator fun <A, B, C, D> Quad<A, B, C, D>.component2() = b
    private operator fun <A, B, C, D> Quad<A, B, C, D>.component3() = c
    private operator fun <A, B, C, D> Quad<A, B, C, D>.component4() = d
}
