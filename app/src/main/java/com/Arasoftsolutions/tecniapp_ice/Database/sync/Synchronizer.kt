package com.Arasoftsolutions.tecniapp_ice.Database.sync

import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriasRepository

class Synchronizer(
    private val repository: RoomRepository,
    private val averiasRepository: AveriasRepository
) {

    companion object {
        private const val EXTRA_STEPS = 4
    }

    suspend fun syncSubregion(
        subregionId: String,
        onSyncStart: (msg: String) -> Unit,
        onSyncProgress: (done: Int, total: Int, msg: String?) -> Unit,
        onSyncSuccess: () -> Unit,
        onSyncError: (Throwable) -> Unit
    ) {

        val total = RoomRepository.SUBREGION_SYNC_STEPS + EXTRA_STEPS
        var done = 0

        try {

            // ----------- 1. TÉCNICOS ----------------
            onSyncStart("Sincronizando técnicos…")
            try {
                repository.syncTecnicos()
                onSyncProgress(++done, total, "Sincronizando técnicos…")
            } catch (e: Exception) {
                throw Exception("Error en syncTecnicos(): ${e.message}", e)
            }

            // ----------- 2. MATERIALES ----------------
            onSyncProgress(++done, total, "Descargando materiales…")
            try {
                repository.syncMateriales()
            } catch (e: Exception) {
                throw Exception("Error en syncMateriales(): ${e.message}", e)
            }

            // ----------- 3. AVERÍAS ----------------
            onSyncProgress(++done, total, "Cargando averías…")
            try {
                averiasRepository.pullFromFirebaseOnce()
            } catch (e: Exception) {
                throw Exception("Error al cargar averías: ${e.message}", e)
            }

            // ----------- 4. INVENTARIO ----------------
            onSyncProgress(++done, total, "Sincronizando inventario…")
            try {
                repository.syncInventario()
            } catch (e: Exception) {
                throw Exception("Error en syncInventario(): ${e.message}", e)
            }

            // ----------- 5. SUBREGIÓN COMPLETA ----------------
            try {
                repository.syncSubregion(subregionId) { subDone, _, msg ->
                    val adjustedDone = EXTRA_STEPS + subDone
                    onSyncProgress(adjustedDone, total, msg)
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
