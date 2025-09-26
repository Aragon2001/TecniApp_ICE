// ui/averias/AveriasSyncWorker.kt
package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import com.Arasoftsolutions.tecniapp_ice.BuildConfig
import com.Arasoftsolutions.tecniapp_ice.R
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi

class AveriasSyncWorker(ctx: Context, params: WorkerParameters): CoroutineWorker(ctx, params) {
  @RequiresApi(Build.VERSION_CODES.O)
  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    val db = com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase.getInstance(applicationContext)
    val repo = com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriasRepository(db)

    val nuevos = repo.syncFromIce(BuildConfig.ICE_BEARER)
    if (nuevos.isNotEmpty()) {
      val nm = NotificationManagerCompat.from(applicationContext)
      nuevos.forEach forEachId@{ id ->
        if (ActivityCompat.checkSelfPermission(
            applicationContext, Manifest.permission.POST_NOTIFICATIONS
          ) != PackageManager.PERMISSION_GRANTED
        ) return@forEachId
        nm.notify(
          id.hashCode(),
          NotificationCompat.Builder(applicationContext, "averias_channel")
            .setSmallIcon(R.drawable.ic_notification) // asegúrate de tener este recurso
            .setContentTitle("Nueva avería")
            .setContentText("Caso $id")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(
              Uri.parse(
                "android.resource://${'$'}{applicationContext.packageName}/${R.raw.beep}"
              )
            )
            .build()
        )
      }
    }
    Result.success()
  }

  companion object {
    fun schedule(ctx: Context) {
      val req = PeriodicWorkRequestBuilder<AveriasSyncWorker>(15, TimeUnit.MINUTES).build()
      WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
        "averias_sync", ExistingPeriodicWorkPolicy.KEEP, req
      )
    }
  }
}
