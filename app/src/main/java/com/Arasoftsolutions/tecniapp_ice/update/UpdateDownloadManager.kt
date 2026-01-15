package com.Arasoftsolutions.tecniapp_ice.update

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.Arasoftsolutions.tecniapp_ice.R
import java.io.File
import java.security.MessageDigest

class UpdateDownloadManager(private val context: Context) {

    fun startDownload(activity: Activity, info: UpdateInfo) {
        val downloadManager = context.getSystemService(DownloadManager::class.java)
        if (downloadManager == null) {
            Toast.makeText(context, R.string.update_download_failed, Toast.LENGTH_LONG).show()
            return
        }

        val updatesDir = context.getExternalFilesDir(UPDATES_DIR)
        if (updatesDir == null) {
            Toast.makeText(context, R.string.update_download_failed, Toast.LENGTH_LONG).show()
            return
        }
        val fileName = "tecniapp-ice-${info.versionName}.apk"
        val destinationDir = File(updatesDir, fileName)
        if (destinationDir.exists()) {
            destinationDir.delete()
        }

        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle(context.getString(R.string.update_download_title, info.versionName))
            .setDescription(context.getString(R.string.update_download_description))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, UPDATES_DIR, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)

        val downloadId = runCatching { downloadManager.enqueue(request) }.getOrElse { error ->
            Log.e(TAG, "Error iniciando descarga", error)
            Toast.makeText(context, R.string.update_download_failed, Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(context, R.string.update_download_started, Toast.LENGTH_SHORT).show()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != downloadId) return
                receiverContext.unregisterReceiver(this)

                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                cursor.use { cursorResult ->
                    if (!cursorResult.moveToFirst()) {
                        Toast.makeText(context, R.string.update_download_failed, Toast.LENGTH_LONG).show()
                        return
                    }

                    val status = cursorResult.getInt(
                        cursorResult.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    )
                    if (status != DownloadManager.STATUS_SUCCESSFUL) {
                        Toast.makeText(context, R.string.update_download_failed, Toast.LENGTH_LONG).show()
                        return
                    }
                }

                if (!destinationDir.exists()) {
                    Toast.makeText(context, R.string.update_download_failed, Toast.LENGTH_LONG).show()
                    return
                }

                if (!info.sha256.isNullOrBlank()) {
                    val sha = computeSha256(destinationDir)
                    if (!info.sha256.equals(sha, ignoreCase = true)) {
                        destinationDir.delete()
                        Toast.makeText(context, R.string.update_checksum_mismatch, Toast.LENGTH_LONG).show()
                        return
                    }
                }

                launchInstaller(activity, destinationDir)
            }
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun launchInstaller(activity: Activity, apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canInstall = activity.packageManager.canRequestPackageInstalls()
            if (!canInstall) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}")
                )
                activity.startActivity(intent)
                Toast.makeText(context, R.string.update_install_permission_required, Toast.LENGTH_LONG).show()
                return
            }
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching { activity.startActivity(intent) }.onFailure { error ->
            Log.e(TAG, "Error abriendo instalador", error)
            Toast.makeText(context, R.string.update_install_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read = input.read(buffer)
            while (read >= 0) {
                if (read > 0) digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    companion object {
        private const val TAG = "UpdateDownloadManager"
        private const val UPDATES_DIR = "updates"
        private const val APK_MIME = "application/vnd.android.package-archive"
    }
}
