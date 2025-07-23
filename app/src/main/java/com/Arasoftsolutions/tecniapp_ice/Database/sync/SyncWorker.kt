package com.Arasoftsolutions.tecniapp_ice.Database

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.util.Log

/**
 * Worker para sincronización periódica.
 */
class SyncWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val synchronizer = Synchronizer(applicationContext)
            synchronizer.initialize() // Ejecuta la sincronización
            Log.d("SyncWorker", "Sincronización completada exitosamente.")
            Result.success() // Devuelve un resultado exitoso
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error en la sincronización: ${e.message}")
            Result.retry() // En caso de error, reintenta
        }
    }
}

