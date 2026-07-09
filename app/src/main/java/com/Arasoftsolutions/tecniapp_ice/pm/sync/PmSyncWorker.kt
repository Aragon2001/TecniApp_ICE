package com.Arasoftsolutions.tecniapp_ice.pm.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.Arasoftsolutions.tecniapp_ice.pm.repository.OperacionRepository
import com.Arasoftsolutions.tecniapp_ice.pm.room.PmDatabase

class PmSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val regionKey = inputData.getString(KEY_REGION) ?: return Result.failure()
        val subregionKey = inputData.getString(KEY_SUBREGION) ?: return Result.failure()

        // Singleton compartido con la UI (ver AUDITORIA.md §B4): no se abre ni se cierra
        // una instancia propia para no tener dos handles de Room sobre el mismo archivo.
        val database = PmDatabase.getInstance(applicationContext)

        val operacionRepository = OperacionRepository(applicationContext, database)
        val syncManager = PmSyncManager(database, operacionRepository)

        return try {
            syncManager.processQueue(regionKey, subregionKey)
            Result.success()
        } catch (ex: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_REGION = "regionKey"
        const val KEY_SUBREGION = "subregionKey"
    }
}
