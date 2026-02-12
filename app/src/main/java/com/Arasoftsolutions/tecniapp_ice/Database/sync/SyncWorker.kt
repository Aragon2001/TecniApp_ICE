package com.Arasoftsolutions.tecniapp_ice.Database.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository

/**
 * Worker que ejecuta la sincronización en segundo plano.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private fun isInternetAvailable(): Boolean {
        val connectivityManager =
            applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "🔄 Iniciando sincronización en segundo plano…")

        if (!isInternetAvailable()) {
            Log.w(TAG, "⚠️ No hay conexión a internet. Reintentando más tarde.")
            return Result.retry()
        }

        val subregionId = inputData.getString(KEY_SUBREGION) ?: return Result.failure()

        return try {
            val repo = RoomRepository.getInstance(applicationContext)
            AppSyncCoordinator.runExclusive {
                repo.syncSubregion(subregionId)
            }
            Log.d(TAG, "✅ Sincronización completada exitosamente.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error durante sincronización: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        const val KEY_SUBREGION = "subregionId"
        private const val TAG = "SyncWorker"
    }
}
