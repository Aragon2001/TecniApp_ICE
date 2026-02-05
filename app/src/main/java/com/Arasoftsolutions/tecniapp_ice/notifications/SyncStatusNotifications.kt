package com.Arasoftsolutions.tecniapp_ice.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.Arasoftsolutions.tecniapp_ice.ActivityMain
import com.Arasoftsolutions.tecniapp_ice.R
import java.text.DateFormat
import java.util.Date

object SyncStatusNotifications {
    private const val CHANNEL_ID = "sync_status_channel"
    private const val NOTIFICATION_ID = 3101

    fun notifySynced(context: Context) {
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (!hasNotificationPermission(context, manager)) return
        val timestamp = DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.SHORT
        ).format(Date())
        val message = context.getString(R.string.sync_notification_message, timestamp)
        val intent = Intent(context, ActivityMain::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, flags)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_bolt)
            .setContentTitle(context.getString(R.string.sync_notification_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.sync_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.sync_notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun hasNotificationPermission(
        context: Context,
        manager: NotificationManagerCompat
    ): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
