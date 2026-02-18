package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.navigation.NavDeepLinkBuilder
import com.Arasoftsolutions.tecniapp_ice.ActivityMain
import com.Arasoftsolutions.tecniapp_ice.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


object AveriaNotifications {
    const val CHANNEL_ID = "averias_channel"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val sound = Uri.parse("android.resource://${context.packageName}/${R.raw.beep}")
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.averia_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.averia_channel_description)
                enableVibration(true)
                setSound(sound, attributes)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun averiasPendingIntent(
        context: Context,
        mutable: Boolean = false
    ): PendingIntent {
        val taskStackBuilder = NavDeepLinkBuilder(context)
            .setGraph(R.navigation.mobile_navigation)
            .setDestination(R.id.nav_averias)
            .setComponentName(ActivityMain::class.java)
            .createTaskStackBuilder()
        val mutabilityFlag = if (mutable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_IMMUTABLE
        }
        return requireNotNull(
            taskStackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or mutabilityFlag)
        )
    }

    fun notificationPreferencesPendingIntent(context: Context): PendingIntent {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    fun bubbleMetadata(context: Context): NotificationCompat.BubbleMetadata? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val intent = averiasPendingIntent(context, mutable = true)
        val icon = IconCompat.createWithResource(context, R.drawable.ic_notification_bolt)
        return NotificationCompat.BubbleMetadata.Builder(intent, icon)
            .setDesiredHeight(context.resources.getDimensionPixelSize(R.dimen.averia_notification_map_height))
            .setSuppressNotification(false)
            .build()
    }



    fun formatDateTime(millis: Long?): String? {
        if (millis == null || millis <= 0) return null
        val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        return formatter.format(Date(millis))
    }

    fun notifyPreferenceToggle(context: Context, enabled: Boolean) {
        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val title = if (enabled) {
            context.getString(R.string.averia_notification_pref_enabled_title)
        } else {
            context.getString(R.string.averia_notification_pref_disabled_title)
        }
        val body = if (enabled) {
            context.getString(R.string.averia_notification_pref_enabled_body)
        } else {
            context.getString(R.string.averia_notification_pref_disabled_body)
        }
        val smallIcon = if (enabled) R.drawable.ic_notification_bolt else R.drawable.ic_notification_off
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(notificationPreferencesPendingIntent(context))
            .build()
        manager.notify(2001, notification)
        // TODO(Codex): Emitir notificación informativa al cambiar estado de la campana
    }
}
