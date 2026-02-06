package com.Arasoftsolutions.tecniapp_ice.pm.sync

import android.content.Context
import androidx.room.Room
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

        val database = Room.databaseBuilder(
            applicationContext,
            PmDatabase::class.java,
            DB_NAME
        ).build()

        val operacionRepository = OperacionRepository(applicationContext, database)
        val syncManager = PmSyncManager(database, operacionRepository)

        return try {
            syncManager.processQueue(regionKey, subregionKey)
            Result.success()
        } catch (ex: Exception) {
            Result.retry()
        } finally {
            database.close()
        }
    }

    companion object {
        const val KEY_REGION = "regionKey"
        const val KEY_SUBREGION = "subregionKey"
        const val DB_NAME = "pm_operacion.db"
    }
}
