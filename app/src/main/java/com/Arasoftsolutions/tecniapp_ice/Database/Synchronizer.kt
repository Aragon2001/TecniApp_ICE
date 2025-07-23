package com.Arasoftsolutions.tecniapp_ice.Database

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.*
import com.Arasoftsolutions.tecniapp_ice.Database.sync.FirebaseSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

// Clase Synchronizer que maneja la sincronización
class Synchronizer(private val context: Context) {

    private var isSyncing = false // Indicador de sincronización en progreso
    private var isUserLoggedIn = false // Indicador del estado del usuario

    /**
     * Método principal de inicialización. Verifica el estado del usuario y sincroniza datos si es necesario.
     */
    suspend fun initialize() {
        // Obtiene las preferencias compartidas
        // Utiliza las mismas preferencias compartidas que en LoginActivity
        val sharedPreferences: SharedPreferences =
            context.getSharedPreferences("TecniAppPrefs", Context.MODE_PRIVATE)
        // Verifica si el usuario está logueado
        isUserLoggedIn = sharedPreferences.getBoolean("isLoggedIn", false)

        if (isUserLoggedIn) {
            // Verifica conexión a Internet antes de sincronizar
            if (isNetworkAvailable()) {
                syncData() // Sincroniza los datos si hay conexión
            } else {
                Log.e("Synchronizer", "No hay conexión a Internet. La sincronización está en espera.")
            }
        }
    }

    /**
     * Verifica si hay conexión a Internet.
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = ContextCompat.getSystemService(context, ConnectivityManager::class.java)
        val activeNetwork = connectivityManager?.activeNetwork
        val networkCapabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)
        return networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    /**
     * Realiza la sincronización de datos con Firebase y la base de datos local.
     */
    private suspend fun syncData() {
        if (isSyncing) return // Evita sincronizaciones concurrentes
        isSyncing = true
        try {
            Log.d("Synchronizer", "Iniciando sincronización de datos...")

            // Inicializa la base de datos local
            val databaseHelper = TecniAppDatabaseHelper(context)

            // Inicializa el gestor de sincronización con Firebase
            val firebaseSyncManager = FirebaseSyncManager(databaseHelper)

            // Sincroniza en un hilo de IO
            withContext(Dispatchers.IO) {
                firebaseSyncManager.sincronizarFirebaseConLocal()
            }

            Log.d("Synchronizer", "Sincronización completada exitosamente.")
        } catch (e: Exception) {
            Log.e("Synchronizer", "Error durante la sincronización: ${e.message}")
        } finally {
            isSyncing = false
        }
    }

    /**
     * Configura un Worker para sincronización periódica con constraints definidos.
     */
    fun scheduleSync() {
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED) // Solo con conexión a Internet
                    .build()
            )
            .build()

        // Encola el WorkRequest en WorkManager
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            "SyncWork",
            ExistingPeriodicWorkPolicy.REPLACE,
            syncRequest
        )
        Log.d("Synchronizer", "Sincronización periódica programada cada 1 hora.")
    }
}
