// ===========================
// SyncWorker.kt - Comentado + Validación de Red
// ===========================
package com.Arasoftsolutions.tecniapp_ice.Database.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

// Worker que ejecuta sincronización en segundo plano usando WorkManager y corrutinas
class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    // Verifica si hay conexión a Internet disponible antes de intentar sincronizar
    private fun isInternetAvailable(): Boolean {
        val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override suspend fun doWork(): Result {
        Log.d("SyncWorker", "🔄 Iniciando sincronización en segundo plano...")

        if (!isInternetAvailable()) {
            Log.w("SyncWorker", "⚠️ No hay conexión a internet. Cancelando sincronización.")
            return Result.success() // Evita reintentos innecesarios si no hay red
        }

        return try {
            val firebaseSyncManager = FirebaseSyncManager(applicationContext)
            firebaseSyncManager.sincronizarFirebaseConLocal()
            Log.d("SyncWorker", "✅ Sincronización completada exitosamente.")
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "❌ Error durante sincronización: ${e.message}", e)
            Result.failure()
        }
    }
} // Fin de clase
