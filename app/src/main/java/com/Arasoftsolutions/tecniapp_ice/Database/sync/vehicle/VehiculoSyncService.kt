package com.Arasoftsolutions.tecniapp_ice.Database.sync.vehicle

import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculoLogEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculosEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository

class VehiculoSyncService(
    private val repository: RoomRepository,
    private val dataSource: FirebaseVehicleDataSource = FirebaseVehicleDataSource(),
) {
    suspend fun syncVehiculo(vehiculo: VehiculosEntity) {
        dataSource.pushVehiculoBase(vehiculo)
        val pending = repository.getPendingLogs(vehiculo.vehiculoId)
        if (pending.isNotEmpty()) {
            runCatching {
                dataSource.pushLogs(vehiculo.vehiculoId, pending)
            }.onSuccess {
                repository.markLogsSynced(pending.map { it.logId })
            }
        }
        val remoteKm = dataSource.pullKmActual(vehiculo.vehiculoId)
        if (remoteKm != null && remoteKm > (vehiculo.kilometrajeActual ?: 0.0)) {
            val pullLog = VehiculoLogEntity(
                logId = "sync_pull_${System.currentTimeMillis()}",
                vehiculoId = vehiculo.vehiculoId,
                tipo = "SYNC_PULL",
                timestamp = System.currentTimeMillis(),
                km = remoteKm,
                payloadJson = "{\"source\":\"firebase\"}",
                syncState = "SYNCED"
            )
            repository.addLogAndUpdateKm(pullLog)
        }
    }
}
