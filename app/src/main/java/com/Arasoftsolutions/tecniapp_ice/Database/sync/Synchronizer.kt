package com.Arasoftsolutions.tecniapp_ice.Database.sync

import android.util.Log
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.google.firebase.auth.FirebaseAuth

class Synchronizer(
    private val repository: RoomRepository
) {

    companion object {
        private const val BASE_EXTRA_STEPS = 2 // técnicos + materiales
        private const val TAG = "Synchronizer"
    }

    suspend fun syncSubregion(
        subregionId: String,
        onSyncStart: (msg: String) -> Unit,
        onSyncProgress: (done: Int, total: Int, msg: String?, downloadedBytes: Long) -> Unit,
        onSyncSuccess: () -> Unit,
        onSyncError: (Throwable) -> Unit
    ) {

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val scope = uid?.let { repository.buildUserScope(it) }
        Log.i(TAG, "[INV_DIAG][SYNC_START] uid=${uid ?: "null"} scopeVehiculoKey=${scope?.vehiculoKey ?: "null"}")

        val shouldSyncVehiculo = scope?.vehiculoKey
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.toIntOrNull() != null
        val shouldSyncAgencia = !scope?.agenciaTag.isNullOrBlank()

        val scopedSteps = (if (shouldSyncVehiculo) 1 else 0) + (if (shouldSyncAgencia) 1 else 0)
        val totalSteps = RoomRepository.SUBREGION_SYNC_STEPS + BASE_EXTRA_STEPS + scopedSteps
        val total = totalSteps * 100
        var done = 0
        var downloadedBytes = 0L

        val syncStartedAt = System.currentTimeMillis()
        Log.i(TAG, "[SYNC_FLOW] start subregion=$subregionId totalSteps=$total scopedSteps=$scopedSteps")
        try {
            val executed = AppSyncCoordinator.runExclusiveDebounced {

                // ----------- 1. TÉCNICOS ----------------
                onSyncStart("Descargando Datos…")
                try {
                    downloadedBytes += repository.syncTecnicos()
                    done += 100
                    onSyncProgress(done, total, "Descargando técnicos…", downloadedBytes)
                } catch (e: Exception) {
                    throw Exception("Error en syncTecnicos(): ${e.message}", e)
                }

                // ----------- 2. MATERIALES ----------------
                try {
                    downloadedBytes += repository.syncMateriales()
                    done += 100
                    onSyncProgress(done, total, "Descargando materiales…", downloadedBytes)
                } catch (e: Exception) {
                    throw Exception("Error en syncMateriales(): ${e.message}", e)
                }

                // ----------- 3. SUBREGIÓN COMPLETA ----------------
                try {
                    val baseDone = done
                    val bytesBeforeSubregion = downloadedBytes
                    repository.syncSubregion(subregionId) { subDone, subTotal, msg, bytes ->
                        downloadedBytes = bytesBeforeSubregion + bytes
                        onSyncProgress(baseDone + subDone, baseDone + subTotal, msg, downloadedBytes)
                    }
                    done = baseDone + (RoomRepository.SUBREGION_SYNC_STEPS * 100)
                    onSyncProgress(done, total, "Subregión sincronizada", downloadedBytes)
                } catch (e: Exception) {
                    throw Exception("Error en syncSubregion(): ${e.message}", e)
                }

                // ----------- 4. INVENTARIO/LUMINARIAS SCOPED ----------------
                scope?.vehiculoKey
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { vehiculoKey ->
                        Log.i(TAG, "[INV_DIAG][SYNC_SCOPED] uid=${uid ?: "null"} vehiculoKeyRaw=$vehiculoKey")
                        val vehiculoId = vehiculoKey.toIntOrNull()
                        if (vehiculoId != null) {
                            try {
                                Log.i(TAG, "[INV_DIAG][SYNC_SCOPED] vehiculoKeyParsedInt=$vehiculoId")
                                downloadedBytes += repository.syncInventarioVehiculo(vehiculoId, vehiculoKey)
                                done += 100
                                onSyncProgress(done, total, "Sincronizando inventario del vehículo…", downloadedBytes)
                            } catch (e: Exception) {
                                throw Exception("Error en syncInventarioVehiculo(): ${e.message}", e)
                            }
                        } else {
                            Log.w(TAG, "[INV_DIAG][SYNC_SCOPED_SKIP] reason=vehiculoKey_not_numeric uid=${uid ?: "null"} vehiculoKeyRaw=$vehiculoKey")
                            Log.w(TAG, "[SYNC_FLOW] scoped_inventory_skipped reason=vehiculoKey_not_numeric key=$vehiculoKey")
                        }
                    }

                scope?.agenciaTag
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { agencia ->
                        try {
                            downloadedBytes += repository.syncLuminariasAgencia(agencia)
                            done += 100
                            onSyncProgress(done, total, "Sincronizando luminarias de agencia…", downloadedBytes)
                        } catch (e: Exception) {
                            throw Exception("Error en syncLuminariasAgencia(): ${e.message}", e)
                        }
                    }

                // FINAL
                Log.i(TAG, "[SYNC_FLOW] completed bytes=$downloadedBytes tookMs=${System.currentTimeMillis()-syncStartedAt}")
                onSyncSuccess()
            }
            if (executed == null) {
                Log.i(TAG, "[SYNC_FLOW] skipped reason=debounced/running bytes=$downloadedBytes tookMs=${System.currentTimeMillis()-syncStartedAt}")
                onSyncSuccess()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "[SYNC_FLOW] failed bytes=$downloadedBytes tookMs=${System.currentTimeMillis()-syncStartedAt} detail=${t.message}", t)
            // Aquí cae cualquier error del proceso completo
            onSyncError(t)
        }
    }
}
