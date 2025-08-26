// ===========================
// Synchronizer.kt - Comentado + Validación de red
// ===========================
package com.Arasoftsolutions.tecniapp_ice.Database.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

// Clase que inicializa y programa la sincronización periódica con Firebase
class Synchronizer(private val context: Context) {

    private val workManager = WorkManager.getInstance(context)

    // Verifica si hay conexión a Internet disponible
    private fun isInternetAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // Método para lanzar la sincronización manualmente
    suspend fun syncData() {
        Log.d("Synchronizer", "🟡 Iniciando sincronización de datos...")

        if (!isInternetAvailable()) {
            Log.w("Synchronizer", "⚠️ Sin conexión a internet. Sincronización cancelada.")
            return
        }

        try {
            val firebaseSyncManager = FirebaseSyncManager(context)
            firebaseSyncManager.sincronizarFirebaseConLocal()
            Log.d("Synchronizer", "✅ Sincronización completada exitosamente.")
        } catch (e: Exception) {
            Log.e("Synchronizer", "❌ Error durante sincronización manual: ${e.message}", e)
        }
    }

    // Método para programar sincronización periódica (cada 1 hora)
    fun scheduleSync() {
        Log.d("Synchronizer", "⏰ Sincronización periódica programada cada 1 hora.")

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
            .build()

        // Garantiza que solo exista una tarea periódica activa a la vez
        workManager.enqueueUniquePeriodicWork(
            "firebase_sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            syncRequest
        )
    }

    // Método para iniciar sincronización manual y luego programar la periódica
    suspend fun initialize() {
        syncData()      // Hace una sincronización inicial si hay red
        scheduleSync()  // Y programa las futuras
    }
}
