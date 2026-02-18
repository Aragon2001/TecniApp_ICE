package com.Arasoftsolutions.tecniapp_ice.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.Arasoftsolutions.tecniapp_ice.ActivityMain
import com.Arasoftsolutions.tecniapp_ice.R

object VehiculoNotifications {
    private const val CHANNEL_ID = "vehiculo_reminders"
    private const val CHANNEL_NAME = "Recordatorios de vehículo"
    const val EXTRA_OPEN_MIVEHICULO = "extra_open_mi_vehiculo"

    fun notifyMantenimientoProximo(
        context: Context,
        title: String,
        message: String,
        bigText: String = message
    ) {
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (!hasNotificationPermission(context, manager)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_car)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setColor(ContextCompat.getColor(context, R.color.ice_blue))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(buildMiVehiculoPendingIntent(context))
            .build()
        manager.notify(NOTIFICATION_ID_MANTENIMIENTO, notification)
    }

    fun notifyRegistroPendiente(context: Context, title: String, message: String) {
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (!hasNotificationPermission(context, manager)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setColor(ContextCompat.getColor(context, R.color.ice_yellow))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(buildMiVehiculoPendingIntent(context))
            .build()
        manager.notify(NOTIFICATION_ID_REGISTRO, notification)
    }

    private fun buildMiVehiculoPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ActivityMain::class.java).apply {
            putExtra(EXTRA_OPEN_MIVEHICULO, true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, REQUEST_OPEN_MI_VEHICULO, intent, flags)
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.vehiculo_notificacion_channel_desc)
        }
        manager.createNotificationChannel(channel)
    }

    private fun hasNotificationPermission(
        context: Context,
        manager: NotificationManagerCompat
    ): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        val permission = android.Manifest.permission.POST_NOTIFICATIONS
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            permission
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return hasPermission || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU
    }

    private const val REQUEST_OPEN_MI_VEHICULO = 6301
    private const val NOTIFICATION_ID_MANTENIMIENTO = 2301
    private const val NOTIFICATION_ID_REGISTRO = 2302
}
