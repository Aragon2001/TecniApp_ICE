package com.Arasoftsolutions.tecniapp_ice.Database.sync

import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriasRepository

/**
 * Orquesta la sincronización de una subregión utilizando el repositorio y
 * expone callbacks simples para reportar el progreso al llamador (UI).
 */
class Synchronizer(
    private val repository: RoomRepository,
    private val averiasRepository: AveriasRepository
) {

    companion object {
        private const val EXTRA_STEPS = 3
    }

    suspend fun syncSubregion(
        subregionId: String,
        onSyncStart: (msg: String) -> Unit,
        onSyncProgress: (done: Int, total: Int, msg: String?) -> Unit,
        onSyncSuccess: () -> Unit,
        onSyncError: (Throwable) -> Unit
    ) {
        try {
            val total = RoomRepository.SUBREGION_SYNC_STEPS + EXTRA_STEPS
            var done = 0

            onSyncStart("Sincronizando técnicos…")
            repository.syncTecnicos()
            onSyncProgress(++done, total, "Sincronizando técnicos…")

            onSyncProgress(++done, total, "Descargando materiales…")
            repository.syncMateriales()

            onSyncProgress(++done, total, "Cargando averías…")
            averiasRepository.pullFromFirebaseOnce()

            repository.syncSubregion(subregionId) { subDone, _, msg ->
                val adjustedDone = EXTRA_STEPS + subDone
                onSyncProgress(adjustedDone, total, msg)
            }
            onSyncSuccess()
        } catch (t: Throwable) {
            onSyncError(t)
        }
    }
}
