package com.Arasoftsolutions.tecniapp_ice.ui.vehiculo.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.Database.sync.RtdbInstances
import com.Arasoftsolutions.tecniapp_ice.Database.utils.SyncStatus
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class VehiculoSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repository = RoomRepository.getInstance(context)
    private val auth = FirebaseAuth.getInstance()

    override suspend fun doWork(): Result {
        if (!isInternetAvailable()) return Result.retry()

        if (auth.currentUser?.uid == null) return Result.success()
        val mantenimientoPendientes = repository.obtenerMantenimientosPendientes(SyncStatus.PENDING)
        val registrosPendientes = repository.obtenerRegistrosDiariosPendientes(SyncStatus.PENDING)
        if (mantenimientoPendientes.isEmpty() && registrosPendientes.isEmpty()) {
            return Result.success()
        }

        val database = RtdbInstances.datosGenerales().reference.child("vehiculos")
        return try {
            mantenimientoPendientes.forEach { registro ->
                val payload = mapOf(
                    "vehiculoId" to registro.vehiculoId,
                    "tipoMantenimiento" to registro.tipoMantenimiento,
                    "valorActual" to registro.valorActual,
                    "unidad" to registro.unidad,
                    "observaciones" to (registro.observaciones ?: ""),
                    "proximoMantenimiento" to registro.proximoMantenimiento,
                    "creadoEn" to registro.creadoEn
                )
                database
                    .child(registro.vehiculoId.toString())
                    .child("historial")
                    .child("mantenimientos")
                    .child(registro.id.toString())
                    .setValue(payload)
                    .await()
            }
            registrosPendientes.forEach { registro ->
                val payload = mapOf(
                    "vehiculoId" to registro.vehiculoId,
                    "fecha" to registro.fecha,
                    "valor" to registro.valor,
                    "unidad" to registro.unidad,
                    "registradoEn" to registro.registradoEn,
                    "registradoPor" to (registro.registradoPor ?: "")
                )
                database
                    .child(registro.vehiculoId.toString())
                    .child("historial")
                    .child("lecturasKm")
                    .child(registro.fecha)
                    .setValue(payload)
                    .await()
            }
            repository.actualizarMantenimientoSyncStatus(mantenimientoPendientes.map { it.id }, SyncStatus.SYNCED)
            repository.actualizarRegistroDiarioSyncStatus(registrosPendientes.map { it.id }, SyncStatus.SYNCED)
            Result.success()
        } catch (error: Exception) {
            Result.retry()
        }
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager =
            applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        private const val UNIQUE_PERIODIC = "vehiculo_sync_periodic"
        private const val UNIQUE_NOW = "vehiculo_sync_now"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<VehiculoSyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun triggerNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<VehiculoSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NOW,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
