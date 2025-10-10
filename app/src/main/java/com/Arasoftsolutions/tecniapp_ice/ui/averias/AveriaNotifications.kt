package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
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
        val geoUri = Uri.parse("geo:$lat,$lng?q=${Uri.encode(safeLabel)}")
        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            mapIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
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
}
