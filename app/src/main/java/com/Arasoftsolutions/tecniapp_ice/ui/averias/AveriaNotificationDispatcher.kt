package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AveriaEntity
import com.Arasoftsolutions.tecniapp_ice.R

object AveriaNotificationDispatcher {

    fun notifyNewCases(context: Context, averias: List<AveriaEntity>) {
        if (averias.isEmpty()) return
        AveriaNotifications.ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (!hasNotificationPermission(context, manager)) return
        averias.forEach { averia ->
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            manager.notify(averia.caseId.hashCode(), buildNotification(context, averia, NotificationType.NEW))
        }
    }

    fun notifyResolvedCases(context: Context, averias: List<AveriaEntity>) {
        if (averias.isEmpty()) return
        AveriaNotifications.ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (!hasNotificationPermission(context, manager)) return
        averias.forEach { averia ->
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            manager.notify(averia.caseId.hashCode(), buildNotification(context, averia, NotificationType.RESOLVED))
        }
    }

    fun notifyResolvedCases(context: Context, averias: List<AveriaEntity>) {
        if (averias.isEmpty()) return
        AveriaNotifications.ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (!hasNotificationPermission(context, manager)) return
        averias.forEach { averia ->
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            manager.notify(averia.caseId.hashCode(), buildResolvedNotification(context, averia))
        }
    }

    private fun hasNotificationPermission(
        context: Context,
        manager: NotificationManagerCompat
    ): Boolean {
        val enabled = manager.areNotificationsEnabled()
        if (!enabled) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        val granted = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        return granted
    }

    private fun buildNotification(
        context: Context,
        averia: AveriaEntity,
        type: NotificationType
    ): Notification {
        val hora = AveriaNotifications.formatDateTime(
            when (type) {
                NotificationType.NEW -> averia.horaInicioMillis ?: averia.fechaInicioMillis
                NotificationType.RESOLVED -> averia.horaFinalMillis ?: averia.fechaInicioMillis
            }
        ) ?: context.getString(R.string.averia_notificacion_sin_hora)

        val lugar = resolveLugar(context, averia)
        val agencia = resolveAgencia(context, averia)
        val caseLine = context.getString(
            R.string.averia_notificacion_caso_nise,
            averia.caseId,
            averia.nise ?: context.getString(R.string.averia_notificacion_sin_cliente)
        )
        val estadoColor = when (type) {
            NotificationType.NEW -> ContextCompat.getColor(context, R.color.averia_notification_pending)
            NotificationType.RESOLVED -> ContextCompat.getColor(context, R.color.averia_notification_resolved)
        }
        val technician = averia.tecnicoAsignadoNombre?.takeIf { it.isNotBlank() }
            ?.let { context.getString(R.string.averia_notificacion_tecnico, it) }
            ?: context.getString(R.string.averia_notificacion_tecnico_no_asignado)

        val clientesTexto = averia.clientesAfectados?.takeIf { it.isNotBlank() }
            ?: "0"
        val estadoLabel = context.getString(
            if (clientesTexto == "0") R.string.averia_notificacion_estado else R.string.averia_notificacion_estado_clientes,
            averia.estado,
            clientesTexto
        )
        val cause = averia.causa?.takeIf { it.isNotBlank() }
            ?.let { context.getString(R.string.averia_notificacion_causa, it) }
            ?: context.getString(
                R.string.averia_notificacion_causa,
                context.getString(R.string.averia_notificacion_causa_no_definida)
            )

        val collapsed = RemoteViews(context.packageName, R.layout.notification_averia_compact).apply {
            setImageViewResource(R.id.icon, type.icon)
            setTextViewText(
                R.id.title,
                when (type) {
                    NotificationType.NEW -> context.getString(R.string.averia_notificacion_header_nueva, lugar)
                    NotificationType.RESOLVED -> context.getString(R.string.averia_notificacion_header_resuelta, lugar)
                }
            )
            setTextViewText(R.id.address, lugar)
            setTextViewText(R.id.case_line, caseLine)
            setTextViewText(R.id.state_line, estadoLabel)
            setTextColor(R.id.state_line, estadoColor)
        }

        val mapPreview = AveriaMapPreviewRenderer.render(context, averia.lat, averia.lng, lugar)
        val expanded = RemoteViews(context.packageName, R.layout.notification_averia_expanded).apply {
            setImageViewResource(R.id.icon_large, type.icon)
            setTextViewText(R.id.title, context.getString(type.titleRes, agencia))
            setTextViewText(R.id.case_line, caseLine)
            setTextViewText(R.id.state_line, estadoLabel)
            setTextColor(R.id.state_line, estadoColor)
            setTextViewText(R.id.address, context.getString(R.string.averia_notificacion_direccion, lugar))
            setTextViewText(R.id.cause, cause)
            setTextViewText(R.id.technician, technician)
            setImageViewBitmap(R.id.map_preview, mapPreview)

            setOnClickPendingIntent(R.id.action_detail, AveriaNotifications.averiasPendingIntent(context))
            AveriaMapLauncher.pendingIntent(
                context,
                averia.lat,
                averia.lng,
                lugar,
                averia.caseId.hashCode()
            )?.let { mapPending ->
                setOnClickPendingIntent(R.id.action_map, mapPending)
            }
            setOnClickPendingIntent(
                R.id.action_assign,
                AveriaNotifications.notificationPreferencesPendingIntent(context)
            )
        }

        val baseIntent = AveriaNotifications.averiasPendingIntent(context)
        val builder = NotificationCompat.Builder(context, AveriaNotifications.CHANNEL_ID)
            .setSmallIcon(type.icon)
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(estadoColor)
            .setColorized(true)
            .setLargeIcon(mapPreview)
            .setContentIntent(baseIntent)
            .setBubbleMetadata(AveriaNotifications.bubbleMetadata(context))
            .setSound(
                Uri.parse("android.resource://${context.packageName}/${R.raw.beep}")
            )

        AveriaMapLauncher.pendingIntent(
            context,
            averia.lat,
            averia.lng,
            lugar,
            averia.caseId.hashCode()
        )?.let { mapPending ->
            builder.addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_map_placeholder,
                    context.getString(R.string.averia_notificacion_action_ver_mapa),
                    mapPending
                ).build()
            )
        }
        builder.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.ic_outage_assign,
                context.getString(R.string.averia_notificacion_action_ver_detalle),
                baseIntent
            ).build()
        )

        return builder.build()
    }

    private fun buildResolvedNotification(context: Context, averia: AveriaEntity): Notification {
        val hora = AveriaNotifications.formatDateTime(averia.horaFinalMillis ?: averia.fechaInicioMillis)
            ?: context.getString(R.string.averia_notificacion_sin_hora)
        val lugar = resolveLugar(context, averia)
        val detalles = context.getString(
            R.string.averia_notificacion_resuelta_body,
            averia.caseId
        )

        return NotificationCompat.Builder(context, AveriaNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.averia_notificacion_resuelta_title))
            .setContentText(detalles)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        context.getString(
                            R.string.averia_notificacion_nueva_summary,
                            hora,
                            lugar
                        )
                    )
                    .setSummaryText(averia.caseId)
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(AveriaNotifications.averiasPendingIntent(context))
            .build()
    }

    private fun resolveLugar(context: Context, averia: AveriaEntity): String =
        averia.localizacion?.takeIf { it.isNotBlank() }
            ?: averia.nombreAgencia?.takeIf { it.isNotBlank() }
            ?: averia.agencia?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.averia_notificacion_sin_lugar)

    private fun resolveAgencia(context: Context, averia: AveriaEntity): String =
        averia.nombreAgencia?.takeIf { it.isNotBlank() }
            ?: averia.agencia?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.averia_notificacion_sin_agencia)

    private enum class NotificationType(val icon: Int, val titleRes: Int) {
        NEW(R.drawable.ic_outage_new, R.string.averia_notificacion_header_nueva),
        RESOLVED(R.drawable.ic_outage_resolved, R.string.averia_notificacion_header_resuelta)
    }
}
