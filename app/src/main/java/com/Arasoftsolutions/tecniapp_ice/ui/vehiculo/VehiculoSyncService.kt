package com.Arasoftsolutions.tecniapp_ice.ui.vehiculo

import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculoEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculoLogEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import org.json.JSONObject
import java.util.UUID

class VehiculoSyncService(
    private val repository: RoomRepository,
    private val remote: FirebaseVehicleDataSource = FirebaseVehicleDataSource()
) {

    /**
     * Ciclo completo de sincronización para un vehículo:
     * 1. Sube datos base a Firebase
     * 2. Sube logs pendientes (ETM + mantenimientos)
     * 3. Reconcilia el km de forma bidireccional
     */
    suspend fun syncVehiculo(vehiculo: VehiculoEntity) {
        // 1. Push datos base (sin mantenimientoUltimo / mantenimientoProximo)
        remote.pushVehiculoBase(vehiculo)

        // 2. Push logs pendientes
        val pending = repository.getPendingLogs(vehiculo.vehiculoId)
        if (pending.isNotEmpty()) {
            try {
                remote.pushLogs(vehiculo.vehiculoId, pending)
                repository.markLogsSynced(pending.map { it.logId })
            } catch (e: Exception) {
                pending.firstOrNull()?.let {
                    repository.markLogError(it.logId, e.message ?: "sync_error")
                }
            }
        }

        // 3. Reconciliar km bidireccional
        reconcileKmPull(vehiculo)
    }

    /**
     * Reconciliación bidireccional del km:
     *
     * Caso A — Firebase tiene km mayor que local:
     *   Otro técnico usó el vehículo. Se actualiza Room y se crea un log SYNC_PULL.
     *
     * Caso B — Local tiene km mayor que Firebase:
     *   Este dispositivo es el más actualizado (push anterior falló o no llegó aún).
     *   Se empuja el valor local a Firebase para que otros dispositivos lo reciban.
     *
     * En ambos casos, el km nunca puede bajar: pullKmActual() recibe localMin
     * para que Firebase no devuelva un valor menor al local conocido.
     */
    suspend fun reconcileKmPull(local: VehiculoEntity) {
        val remoteKm = remote.pullKmActual(
            vehiculoId = local.vehiculoId,
            localMin   = local.kmActual
        ) ?: return

        when {
            // Caso A: Firebase tiene un km mayor → actualizar Room
            remoteKm > local.kmActual -> {
                repository.upsertVehiculo(
                    local.copy(
                        kmActual  = remoteKm,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                val uniqueId = UUID.randomUUID().toString().replace("-", "").take(8)
                repository.addLogAndUpdateKm(
                    VehiculoLogEntity(
                        logId       = "sync_pull_${local.vehiculoId}_${System.currentTimeMillis()}_$uniqueId",
                        vehiculoId  = local.vehiculoId,
                        tipo        = "SYNC_PULL",
                        timestamp   = System.currentTimeMillis(),
                        km          = remoteKm,
                        payloadJson = JSONObject().put("source", "firebase").toString(),
                        syncState   = "SYNCED"
                    )
                )
            }

            // Caso B: local tiene km mayor → empujar a Firebase
            local.kmActual > remoteKm -> {
                remote.updateKmActual(local.vehiculoId, local.kmActual)
            }

            // Caso C: iguales → nada que hacer
            else -> { /* en sincronía */ }
        }
    }
}