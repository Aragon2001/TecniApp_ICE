package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
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

    fun averiasPendingIntent(context: Context): PendingIntent =
        NavDeepLinkBuilder(context)
            .setGraph(R.navigation.mobile_navigation)
            .setDestination(R.id.nav_averias)
            .setComponentName(ActivityMain::class.java)
            .createPendingIntent()

    fun notificationPreferencesPendingIntent(context: Context): PendingIntent =
        NavDeepLinkBuilder(context)
            .setGraph(R.navigation.mobile_navigation)
            .setDestination(R.id.nav_settings)
            .setComponentName(ActivityMain::class.java)
            .createPendingIntent()
            // TODO(Codex): Definir intent directo a ajustes de notificaciones

    fun bubbleMetadata(context: Context): NotificationCompat.BubbleMetadata? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val intent = averiasPendingIntent(context)
        val icon = IconCompat.createWithResource(context, R.drawable.ic_notification)
        return NotificationCompat.BubbleMetadata.Builder(intent.toString())
            .setDesiredHeight(context.resources.getDimensionPixelSize(R.dimen.averia_notification_map_height))
            .setIcon(icon)
            .setSuppressNotification(false)
            .build()
    }

    fun mapAction(
        context: Context,
        lat: Double?,
        lng: Double?,
        label: String?,
        requestCode: Int
    ): NotificationCompat.Action? {
        if (lat == null || lng == null) return null
        if (lat == 0.0 && lng == 0.0) return null
        val safeLabel = label?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.averia_notificacion_map_label_default)
        val pendingIntent = AveriaMapLauncher.pendingIntent(context, lat, lng, safeLabel, requestCode)
            ?: return null
        return NotificationCompat.Action.Builder(
            R.drawable.ic_map_placeholder,
            context.getString(R.string.averia_notificacion_map_action),
            pendingIntent
        ).build()
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
        val smallIcon = if (enabled) R.drawable.ic_notification else R.drawable.ic_notification_off
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
