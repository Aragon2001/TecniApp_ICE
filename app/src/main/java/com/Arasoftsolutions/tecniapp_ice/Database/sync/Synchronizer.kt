package com.Arasoftsolutions.tecniapp_ice.Database.sync

import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.google.firebase.auth.FirebaseAuth

class Synchronizer(
    private val repository: RoomRepository
) {

    companion object {
        private const val EXTRA_STEPS = 2
    }

    suspend fun syncSubregion(
        subregionId: String,
        onSyncStart: (msg: String) -> Unit,
        onSyncProgress: (done: Int, total: Int, msg: String?, downloadedBytes: Long) -> Unit,
        onSyncSuccess: () -> Unit,
        onSyncError: (Throwable) -> Unit
    ) {

        val total = RoomRepository.SUBREGION_SYNC_STEPS + EXTRA_STEPS
        var done = 0
        var downloadedBytes = 0L

        try {
            val executed = AppSyncCoordinator.runExclusiveDebounced {

                // ----------- 1. TÉCNICOS ----------------
                onSyncStart("Descargando Datos…")
                try {
                    downloadedBytes += repository.syncTecnicos()
                    onSyncProgress(++done, total, "Descargando técnicos…", downloadedBytes)
                } catch (e: Exception) {
                    throw Exception("Error en syncTecnicos(): ${e.message}", e)
                }

                // ----------- 2. MATERIALES ----------------
                try {
                    downloadedBytes += repository.syncMateriales()
                    onSyncProgress(++done, total, "Descargando materiales…", downloadedBytes)
                } catch (e: Exception) {
                    throw Exception("Error en syncMateriales(): ${e.message}", e)
                }

                // ----------- 3. SUBREGIÓN COMPLETA ----------------
                try {
                    val bytesBeforeSubregion = downloadedBytes
                    repository.syncSubregion(subregionId) { subDone, _, msg, bytes ->
                        downloadedBytes = bytesBeforeSubregion + bytes
                        val adjustedDone = EXTRA_STEPS + subDone
                        onSyncProgress(adjustedDone, total, msg, downloadedBytes)
                    }
                } catch (e: Exception) {
                    throw Exception("Error en syncSubregion(): ${e.message}", e)
                }

                // ----------- 4. INVENTARIO/LUMINARIAS SCOPED ----------------
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                val scope = uid?.let { repository.buildUserScope(it) }

                scope?.vehiculoKey
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { vehiculoKey ->
                        val vehiculoId = vehiculoKey.toIntOrNull()
                        if (vehiculoId != null) {
                            try {
                                downloadedBytes += repository.syncInventarioVehiculo(vehiculoId, vehiculoKey)
                                onSyncProgress(++done, total, "Sincronizando inventario del vehículo…", downloadedBytes)
                            } catch (e: Exception) {
                                throw Exception("Error en syncInventarioVehiculo(): ${e.message}", e)
                            }
                        }
                    }

                scope?.agenciaTag
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { agencia ->
                        try {
                            downloadedBytes += repository.syncLuminariasAgencia(agencia)
                            onSyncProgress(++done, total, "Sincronizando luminarias de agencia…", downloadedBytes)
                        } catch (e: Exception) {
                            throw Exception("Error en syncLuminariasAgencia(): ${e.message}", e)
                        }
                    }

                // FINAL
                onSyncSuccess()
            }
            if (executed == null) {
                onSyncSuccess()
            }
        } catch (t: Throwable) {
            // Aquí cae cualquier error del proceso completo
            onSyncError(t)
        }
    }
}
