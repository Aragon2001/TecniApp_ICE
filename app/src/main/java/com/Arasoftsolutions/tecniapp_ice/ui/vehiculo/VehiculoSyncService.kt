package com.Arasoftsolutions.tecniapp_ice.ui.vehiculo

import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculoEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculoLogEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import org.json.JSONObject

class VehiculoSyncService(
    private val repository: RoomRepository,
    private val remote: FirebaseVehicleDataSource = FirebaseVehicleDataSource()
) {

    suspend fun syncVehiculo(vehiculo: VehiculoEntity) {
        remote.pushVehiculoBase(vehiculo)

        val pending = repository.getPendingLogs(vehiculo.vehiculoId)
        if (pending.isNotEmpty()) {
            try {
                remote.pushLogs(vehiculo.vehiculoId, pending)
                repository.markLogsSynced(pending.map { it.logId })
            } catch (e: Exception) {
                pending.firstOrNull()?.let { repository.markLogError(it.logId, e.message ?: "sync_error") }
            }
        }

        reconcileKmPull(vehiculo)
    }

    suspend fun reconcileKmPull(local: VehiculoEntity) {
        val remoteKm = remote.pullKmActual(local.vehiculoId) ?: return
        if (remoteKm <= local.kmActual) return

        repository.upsertVehiculo(local.copy(kmActual = remoteKm, kilometrajeActual = remoteKm, updatedAt = System.currentTimeMillis()))
        repository.addLogAndUpdateKm(
            VehiculoLogEntity(
                logId = "sync_pull_${local.vehiculoId}_${System.currentTimeMillis()}",
                vehiculoId = local.vehiculoId,
                tipo = "SYNC_PULL",
                timestamp = System.currentTimeMillis(),
                km = remoteKm,
                payloadJson = JSONObject().put("source", "firebase").toString(),
                syncState = "SYNCED"
            )
        )
    }
}
