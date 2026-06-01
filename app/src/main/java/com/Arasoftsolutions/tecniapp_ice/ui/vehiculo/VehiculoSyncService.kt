package com.Arasoftsolutions.tecniapp_ice.ui.vehiculo

import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculoEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculoLogEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import org.json.JSONObject

// ─────────────────────────────────────────────────────────────────────────────
// CORRECCIONES aplicadas en este archivo:
//
// FIX-6  reconcileKmPull: antes solo jalaba de Firebase si remoto > local.
//        El problema es que si este teléfono tiene el km más alto (porque
//        Firebase aún no recibió el push), nunca subía el valor.
//        Ahora: si local > remoto, se hace un push activo a Firebase para que
//        el servidor quede actualizado y otros dispositivos lo reciban.
//
// FIX-3b pullKmActual ahora recibe localMin para que Firebase nunca devuelva
//        un valor menor al mínimo conocido localmente.
// ─────────────────────────────────────────────────────────────────────────────

class VehiculoSyncService(
    private val repository: RoomRepository,
    private val remote: FirebaseVehicleDataSource = FirebaseVehicleDataSource()
) {

    suspend fun syncVehiculo(vehiculo: VehiculoEntity) {
        // 1. Subir datos base del vehículo a Firebase
        remote.pushVehiculoBase(vehiculo)

        // 2. Subir logs pendientes y marcarlos como sincronizados
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

        // 3. Reconciliar km entre Firebase y local (bidireccional)
        reconcileKmPull(vehiculo)
    }

    /**
     * FIX-6: reconciliación bidireccional del kilómetro/orímetro.
     *
     * Caso A (antes solo este existía):
     *   Firebase tiene un km MAYOR que el local → se actualiza local.
     *   Puede ocurrir cuando otro técnico usó el mismo vehículo.
     *
     * Caso B (nuevo):
     *   El local tiene un km MAYOR que Firebase → se hace push a Firebase.
     *   Ocurre cuando el push anterior falló o cuando este es el teléfono
     *   más actualizado. Sin este caso, Firebase quedaba desactualizado y
     *   otros dispositivos nunca recibían el km correcto.
     */
    suspend fun reconcileKmPull(local: VehiculoEntity) {
        // pullKmActual con localMin garantiza que Firebase nunca nos
        // "baje" el km si su valor está desactualizado (FIX-3b).
        val remoteKm = remote.pullKmActual(local.vehiculoId, localMin = local.kmActual)
            ?: return

        when {
            // Caso A: Firebase tiene un km mayor → actualizar local
            remoteKm > local.kmActual -> {
                repository.upsertVehiculo(
                    local.copy(
                        kmActual  = remoteKm,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                repository.addLogAndUpdateKm(
                    VehiculoLogEntity(
                        logId       = "sync_pull_${local.vehiculoId}_${System.currentTimeMillis()}",
                        vehiculoId  = local.vehiculoId,
                        tipo        = "SYNC_PULL",
                        timestamp   = System.currentTimeMillis(),
                        km          = remoteKm,
                        payloadJson = JSONObject().put("source", "firebase").toString(),
                        syncState   = "SYNCED"
                    )
                )
            }

            // Caso B: local tiene un km mayor → empujar a Firebase
            local.kmActual > remoteKm -> {
                remote.updateKmActual(local.vehiculoId, local.kmActual)
                // No creamos log extra; el push base ya lo registró arriba.
            }

            // Caso C: iguales → no hacer nada
            else -> { /* en sincronía */ }
        }
    }
}