package com.Arasoftsolutions.tecniapp_ice.Database.sync

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

        SyncLog.i(TAG, "[INV_DIAG][SYNC_START] uid=${uid ?: "null"} scopeVehiculoKey=${scope?.vehiculoKey ?: "null"}")
        SyncLog.i(TAG, "[LUM_SYNC][START] uid=${uid ?: "null"} agenciaTag=${scope?.agenciaTag ?: "null"} subregion=$subregionId")

        // ─────────────────────────────────────────────────────────────────────
        // FIX: Diagnóstico mejorado de agenciaTag para detectar el problema
        // de "no se descargan luminarias" a tiempo y con mensaje claro.
        // ─────────────────────────────────────────────────────────────────────
        val agenciaTagRaw = scope?.agenciaTag?.trim()
        if (agenciaTagRaw.isNullOrBlank()) {
            SyncLog.e(
                TAG,
                "[LUM_SYNC][AGENCIA_MISSING] uid=${uid ?: "null"} — " +
                        "El usuario no tiene el campo 'agencia' asignado en Firebase " +
                        "(nodo: usuarios/$uid/agencia). " +
                        "Las luminarias NO se descargarán en este sync. " +
                        "Acción requerida: asignar agencia al usuario en Firebase Console."
            )
        } else {
            SyncLog.i(TAG, "[LUM_SYNC][AGENCIA_OK] uid=${uid ?: "null"} agenciaTag=$agenciaTagRaw")
        }
        // ─────────────────────────────────────────────────────────────────────

        val shouldSyncVehiculo = scope?.vehiculoKey
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.toIntOrNull() != null
        val shouldSyncAgencia = !agenciaTagRaw.isNullOrBlank()

        val scopedSteps = (if (shouldSyncVehiculo) 1 else 0) + (if (shouldSyncAgencia) 1 else 0)
        val totalSteps = RoomRepository.SUBREGION_SYNC_STEPS + BASE_EXTRA_STEPS + scopedSteps
        val total = totalSteps * 100
        var done = 0
        var downloadedBytes = 0L

        val syncStartedAt = System.currentTimeMillis()
        SyncLog.i(TAG, "[SYNC_FLOW] start subregion=$subregionId totalSteps=$total scopedSteps=$scopedSteps")

        try {
            AppSyncCoordinator.runExclusive {

                // ----------- 1. TÉCNICOS ----------------------------------------
                onSyncStart("Descargando Datos…")
                try {
                    downloadedBytes += repository.syncTecnicos()
                    done += 100
                    onSyncProgress(done, total, "Descargando técnicos…", downloadedBytes)
                } catch (e: Exception) {
                    throw Exception("Error en syncTecnicos(): ${e.message}", e)
                }

                // ----------- 2. MATERIALES --------------------------------------
                try {
                    downloadedBytes += repository.syncMateriales()
                    done += 100
                    onSyncProgress(done, total, "Descargando materiales…", downloadedBytes)
                } catch (e: Exception) {
                    throw Exception("Error en syncMateriales(): ${e.message}", e)
                }

                // ----------- 3. SUBREGIÓN COMPLETA ------------------------------
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

                // ----------- 4. INVENTARIO SCOPED (vehículo del usuario) --------
                scope?.vehiculoKey
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { vehiculoKey ->
                        SyncLog.i(TAG, "[INV_DIAG][SYNC_SCOPED] uid=${uid ?: "null"} vehiculoKeyRaw=$vehiculoKey")
                        val vehiculoId = vehiculoKey.toIntOrNull()
                        if (vehiculoId != null) {
                            try {
                                SyncLog.i(TAG, "[INV_DIAG][SYNC_SCOPED] vehiculoKeyParsedInt=$vehiculoId")
                                downloadedBytes += repository.syncInventarioVehiculo(vehiculoId, vehiculoKey)
                                done += 100
                                onSyncProgress(done, total, "Sincronizando inventario del vehículo…", downloadedBytes)
                            } catch (e: Exception) {
                                throw Exception("Error en syncInventarioVehiculo(): ${e.message}", e)
                            }
                        } else {
                            SyncLog.w(TAG, "[INV_DIAG][SYNC_SCOPED_SKIP] reason=vehiculoKey_not_numeric uid=${uid ?: "null"} vehiculoKeyRaw=$vehiculoKey")
                        }
                    }

                // ----------- 5. LUMINARIAS SCOPED (agencia del usuario) ---------
                //
                // FIX: Agregar try-catch individual para que un fallo en luminarias
                // no cancele toda la sync (los otros datos ya están guardados).
                // Antes: el ?: SyncLog.w hacía que el bloque se saltara sin reportar bien.
                // Ahora: diagnóstico claro y fallo no-fatal.
                //
                if (!agenciaTagRaw.isNullOrBlank()) {
                    try {
                        SyncLog.i(TAG, "[LUM_SYNC][AGENCIA_SCOPE_START] agencia=$agenciaTagRaw")
                        downloadedBytes += repository.syncLuminariasAgencia(agenciaTagRaw)
                        done += 100
                        onSyncProgress(done, total, "Sincronizando luminarias de agencia…", downloadedBytes)
                        SyncLog.i(TAG, "[LUM_SYNC][DONE] agencia=$agenciaTagRaw bytes=$downloadedBytes")
                    } catch (e: Exception) {
                        // No-fatal: logueamos pero no detenemos el sync completo
                        SyncLog.e(
                            TAG,
                            "[LUM_SYNC][ERROR] agencia=$agenciaTagRaw detail=${e.message}",
                            e
                        )
                        // Si querés que sea fatal (abortar sync), reemplaza con:
                        // throw Exception("Error en syncLuminariasAgencia(): ${e.message}", e)
                    }
                } else {
                    // agenciaTag vacío — ya se logueó arriba con nivel ERROR
                    // Sumamos el step igual para que la barra de progreso no se trabe
                    if (shouldSyncAgencia) done += 100
                }

                // FINAL
                SyncLog.i(
                    TAG,
                    "[SYNC_FLOW] completed bytes=$downloadedBytes tookMs=${System.currentTimeMillis() - syncStartedAt}"
                )
                onSyncSuccess()
            }
        } catch (t: Throwable) {
            SyncLog.e(
                TAG,
                "[SYNC_FLOW] failed bytes=$downloadedBytes tookMs=${System.currentTimeMillis() - syncStartedAt} detail=${t.message}",
                t
            )
            onSyncError(t)
        }
    }
}