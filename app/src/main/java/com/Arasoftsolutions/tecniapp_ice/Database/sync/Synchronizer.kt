package com.Arasoftsolutions.tecniapp_ice.Database.sync

import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriasRepository

class Synchronizer(
    private val repository: RoomRepository,
    private val averiasRepository: AveriasRepository
) {

    companion object {
        private const val EXTRA_STEPS = 6
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
            // ----------- 0. PENDIENTES ----------------
            onSyncStart("Sincronizando pendientes…")
            try {
                averiasRepository.syncPendientesConFirebase()
                onSyncProgress(++done, total, "Subiendo averías pendientes…", downloadedBytes)
            } catch (e: Exception) {
                throw Exception("Error en syncPendientesConFirebase(): ${e.message}", e)
            }

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

            // ----------- 3. AVERÍAS ----------------
            try {
                averiasRepository.pullFromFirebaseOnce()
                onSyncProgress(++done, total, "Descargando averías…", downloadedBytes)
            } catch (e: Exception) {
                throw Exception("Error al cargar averías: ${e.message}", e)
            }

            // ----------- 4. INVENTARIO ----------------
            try {
                downloadedBytes += repository.syncInventario()
                onSyncProgress(++done, total, "Sincronizando inventario…", downloadedBytes)
            } catch (e: Exception) {
                throw Exception("Error en syncInventario(): ${e.message}", e)
            }

            // ----------- 5. LUMINARIAS ----------------
            try {
                downloadedBytes += repository.syncLuminarias()
                onSyncProgress(++done, total, "Sincronizando luminarias…", downloadedBytes)
            } catch (e: Exception) {
                throw Exception("Error en syncLuminarias(): ${e.message}", e)
            }

            // ----------- 6. SUBREGIÓN COMPLETA ----------------
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

            // FINAL
            onSyncSuccess()

        } catch (t: Throwable) {
            // Aquí cae cualquier error del proceso completo
            onSyncError(t)
        }
    }
}
