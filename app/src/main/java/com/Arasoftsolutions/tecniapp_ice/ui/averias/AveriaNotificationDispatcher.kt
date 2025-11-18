package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
            manager.notify(averia.caseId.hashCode(), buildNotification(context, averia))
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

    private fun buildNotification(context: Context, averia: AveriaEntity): Notification {
        val hora = AveriaNotifications.formatDateTime(averia.horaInicioMillis ?: averia.fechaInicioMillis)
            ?: context.getString(R.string.averia_notificacion_sin_hora)
        val lugar = resolveLugar(context, averia)
        val agencia = resolveAgencia(context, averia)
        val cliente = averia.cliente?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.averia_notificacion_sin_cliente)
        val tipo = averia.tipoAfectacion?.let { raw ->
            when (TipoAfectacion.fromRaw(raw)) {
                TipoAfectacion.CLIENTE -> context.getString(R.string.averia_tipo_cliente)
                TipoAfectacion.SECTOR -> context.getString(R.string.averia_tipo_sector)
            }
        }
        val clientesAfectados = averia.clientesAfectados?.takeIf { it.isNotBlank() }

        val detalles = buildString {
            appendLine(context.getString(R.string.averia_notificacion_detalle_agencia, agencia))
            appendLine(context.getString(R.string.averia_notificacion_detalle_lugar, lugar))
            appendLine(context.getString(R.string.averia_notificacion_detalle_hora, hora))
            appendLine(context.getString(R.string.averia_notificacion_detalle_cliente, cliente))
            tipo?.let {
                appendLine(context.getString(R.string.averia_notificacion_detalle_tipo, it))
            }
            clientesAfectados?.let {
                append(context.getString(R.string.averia_notificacion_detalle_afectados, it))
            }
        }.trim()

        return NotificationCompat.Builder(context, AveriaNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                context.getString(
                    R.string.averia_notificacion_nueva_title_case,
                    averia.caseId
                )
            )
            .setContentText(
                context.getString(
                    R.string.averia_notificacion_nueva_summary,
                    hora,
                    lugar
                )
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(detalles)
                    .setSummaryText(averia.caseId)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(AveriaNotifications.averiasPendingIntent(context))
            .setSound(
                Uri.parse(
                    "android.resource://${context.packageName}/${R.raw.beep}"
                )
            )
            .apply {
                AveriaMapLauncher.pendingIntent(
                    context,
                    averia.lat,
                    averia.lng,
                    lugar,
                    averia.caseId.hashCode()
                )?.let { pending ->
                    addAction(
                        NotificationCompat.Action.Builder(
                            R.drawable.ic_map_placeholder,
                            context.getString(R.string.averia_notificacion_map_action),
                            pending
                        ).build()
                    )
                }
            }
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
}
