package com.Arasoftsolutions.tecniapp_ice.Database.sync.vehicle

import kotlinx.coroutines.flow.Flow

class VehicleRepository(
    private val dataSource: FirebaseVehicleDataSource = FirebaseVehicleDataSource(),
) {
    fun observeVehicle(vehiculoId: String): Flow<VehicleOdometerSnapshot?> =
        dataSource.observeVehicle(vehiculoId)

    suspend fun registerKmEvent(
        vehiculoId: String,
        km: Double,
        tipo: String,
        refPath: String,
        nota: String? = null,
        allowDecrease: Boolean = false,
        motivo: String? = null,
        isAdmin: Boolean = false,
    ) {
        dataSource.registerKmEvent(
            KmEventRequest(
                vehiculoId = vehiculoId,
                km = km,
                tipo = tipo,
                refPath = refPath,
                nota = nota,
                allowDecrease = allowDecrease,
                motivo = motivo,
                isAdmin = isAdmin,
            )
        )
    }

    suspend fun registerMaintenance(
        vehiculoId: String,
        mantenimientoId: String,
        payload: Map<String, Any?>,
        km: Double?,
    ) {
        dataSource.registerMaintenance(
            MaintenanceEventRequest(vehiculoId, mantenimientoId, payload, km)
        )
    }

    suspend fun registerEtmEvent(
        vehiculoId: String,
        etmId: String,
        kmInicio: Double?,
        kmFin: Double?,
        refPath: String,
    ) {
        dataSource.registerEtmEvent(EtmEventRequest(vehiculoId, etmId, kmInicio, kmFin, refPath))
    }

    suspend fun registerAveriaEvent(
        vehiculoId: String,
        averiaId: String,
        km: Double?,
        refPath: String,
    ) {
        dataSource.registerAveriaEvent(AveriaEventRequest(vehiculoId, averiaId, km, refPath))
    }
}
