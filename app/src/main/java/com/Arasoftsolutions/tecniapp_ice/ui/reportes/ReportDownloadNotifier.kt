package com.Arasoftsolutions.tecniapp_ice.ui.reportes

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.Arasoftsolutions.tecniapp_ice.R
import java.io.File

object ReportDownloadNotifier {
    private const val CHANNEL_ID = "reportes_downloads"

    fun show(
        context: Context,
        fileName: String,
        fileUri: Uri,
        locationUri: Uri?
    ) {
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (!hasNotificationPermission(context, manager)) return
        val message = context.getString(R.string.reportes_download_notification_text, fileName)
        val mimeType = context.contentResolver.getType(fileUri) ?: "application/octet-stream"
        val viewPending = PendingIntent.getActivity(
            context,
            fileName.hashCode(),
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val locationPending = PendingIntent.getActivity(
            context,
            fileName.hashCode() + 1,
            Intent(Intent.ACTION_VIEW).apply {
                val targetUri = locationUri ?: fileUri
                if (locationUri != null) {
                    setDataAndType(targetUri, DocumentsContract.Document.MIME_TYPE_DIR)
                } else {
                    setDataAndType(targetUri, mimeType)
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_bolt)
            .setContentTitle(context.getString(R.string.reportes_download_notification_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(viewPending)
            .addAction(
                R.drawable.ic_notification_bolt,
                context.getString(R.string.reportes_download_action_view),
                viewPending
            )
            .addAction(
                R.drawable.ic_notification_bolt,
                context.getString(R.string.reportes_download_action_location),
                locationPending
            )
            .build()
        manager.notify(fileName.hashCode(), notification)
    }

    fun buildLocationUriForFile(context: Context, file: File): Uri? {
        val parent = file.parentFile ?: return null
        val authority = "${context.packageName}.fileprovider"
        return runCatching { FileProvider.getUriForFile(context, authority, parent) }.getOrNull()
    }

    fun buildLocationUriForDocument(context: Context, documentUri: Uri): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val authority = documentUri.authority ?: return null
        val resolver = context.contentResolver
        val path = runCatching { DocumentsContract.findDocumentPath(resolver, documentUri) }
            .getOrNull() ?: return null
        val parentId = path.path.dropLast(1).lastOrNull() ?: return null
        return DocumentsContract.buildDocumentUri(authority, parentId)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reportes_download_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.reportes_download_channel_description)
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
