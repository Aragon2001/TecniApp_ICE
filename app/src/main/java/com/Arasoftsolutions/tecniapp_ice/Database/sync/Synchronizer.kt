package com.Arasoftsolutions.tecniapp_ice.Database.sync

import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository

/**
 * Orquesta la sincronización de una subregión utilizando el repositorio y
 * expone callbacks simples para reportar el progreso al llamador (UI).
 */
class Synchronizer(private val repository: RoomRepository) {

    suspend fun syncSubregion(
        subregionId: String,
        onSyncStart: (msg: String) -> Unit,
        onSyncProgress: (done: Int, total: Int, msg: String?) -> Unit,
        onSyncSuccess: () -> Unit,
        onSyncError: (Throwable) -> Unit
    ) {
        try {
            onSyncStart("Sincronizando…")
            repository.syncSubregion(subregionId) { done, total, msg ->
                onSyncProgress(done, total, msg)
            }
            onSyncSuccess()
        } catch (t: Throwable) {
            onSyncError(t)
        }
    }
}
