package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.Arasoftsolutions.tecniapp_ice.ActivityMain
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AveriaEntity
import com.Arasoftsolutions.tecniapp_ice.R

object AveriaNotificationDispatcher {

    fun notifyNewCases(context: Context, averias: List<AveriaEntity>) {
        notify(context, averias, false)
    }

    fun notifyResolvedCases(context: Context, averias: List<AveriaEntity>) {
        notify(context, averias, true)
    }

    private fun notify(context: Context, averias: List<AveriaEntity>, resolved: Boolean) {

        if (averias.isEmpty()) return

        AveriaNotifications.ensureChannel(context)

        val manager = NotificationManagerCompat.from(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        averias.forEach { averia ->
            manager.notify(
                averia.caseId.hashCode(),
                buildNotification(context, averia, resolved)
            )
        }
    }

    private fun buildNotification(
        context: Context,
        averia: AveriaEntity,
        resolved: Boolean
    ): Notification {

        val estadoColor = if (resolved)
            ContextCompat.getColor(context, R.color.averia_notification_resolved)
        else
            ContextCompat.getColor(context, R.color.averia_notification_pending)

        val titulo = if (resolved) "Avería resuelta" else "Nueva avería"

        val collapsed = RemoteViews(context.packageName, R.layout.notification_averia_compact).apply {
            setTextViewText(R.id.title, titulo)
            setTextViewText(R.id.address, averia.localizacion ?: "")
            setTextViewText(R.id.case_line, "Caso ${averia.caseId}")
            setTextViewText(R.id.state_line, averia.estado)
            setTextColor(R.id.state_line, estadoColor)
        }

        val expanded = RemoteViews(context.packageName, R.layout.notification_averia_expanded).apply {
            setTextViewText(R.id.title, titulo)
            setTextViewText(R.id.case_line, "Caso ${averia.caseId}")
            setTextViewText(R.id.address, averia.localizacion ?: "")
            setTextViewText(R.id.customer, averia.cliente ?: "Sin cliente")
            setTextViewText(R.id.agency, averia.nombreAgencia ?: "")
            setTextViewText(R.id.state_line, averia.estado)
            setTextColor(R.id.state_line, estadoColor)
            setTextViewText(R.id.cause, averia.causa ?: "Sin causa")
            setTextViewText(R.id.affected, averia.clientesAfectados ?: "0")
        }

        val openAppIntent = Intent(context, ActivityMain::class.java).apply {
            putExtra("open_averia", averia.caseId)
        }

        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            averia.caseId.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mapIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:${averia.lat},${averia.lng}?q=${averia.lat},${averia.lng}")
        )

        val mapPendingIntent = PendingIntent.getActivity(
            context,
            averia.caseId.hashCode() + 1,
            mapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, AveriaNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_bolt)
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setColor(estadoColor)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                R.drawable.ic_map_placeholder,
                "Ver mapa",
                mapPendingIntent
            )
            .addAction(
                R.drawable.ic_outage_assign,
                "Abrir",
                openAppPendingIntent
            )
            .build()
    }
}
