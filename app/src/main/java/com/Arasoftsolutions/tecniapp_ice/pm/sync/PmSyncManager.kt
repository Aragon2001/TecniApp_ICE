package com.Arasoftsolutions.tecniapp_ice.pm.sync

import com.Arasoftsolutions.tecniapp_ice.pm.model.SyncQueueType
import com.Arasoftsolutions.tecniapp_ice.pm.model.SyncStatus
import com.Arasoftsolutions.tecniapp_ice.pm.model.entities.SyncErrorLogEntity
import com.Arasoftsolutions.tecniapp_ice.pm.model.entities.SyncQueueEntity
import com.Arasoftsolutions.tecniapp_ice.pm.repository.OperacionRepository
import com.Arasoftsolutions.tecniapp_ice.pm.room.PmDatabase
import java.util.UUID

class PmSyncManager(
    private val database: PmDatabase,
    private val operacionRepository: OperacionRepository,
    private val retryPolicy: RetryPolicy = RetryPolicy()
) {
    suspend fun processQueue(regionKey: String, subregionKey: String) {
        processType(regionKey, subregionKey, SyncQueueType.GROUP)
        processType(regionKey, subregionKey, SyncQueueType.WORKLOG)
        processType(regionKey, subregionKey, SyncQueueType.PLANILLA_PDF)
    }

    private suspend fun processType(regionKey: String, subregionKey: String, type: SyncQueueType) {
        val queueDao = database.syncQueueDao()
        val now = System.currentTimeMillis()
        val pending = queueDao.getAvailable(listOf(SyncStatus.PENDING, SyncStatus.ERROR), now)
            .filter { it.type == type }

        for (item in pending) {
            try {
                when (type) {
                    SyncQueueType.GROUP -> operacionRepository.syncPendingGroups(regionKey, subregionKey)
                    SyncQueueType.WORKLOG -> operacionRepository.syncPendingWorkLogs(regionKey, subregionKey)
                    SyncQueueType.PLANILLA_PDF -> operacionRepository.syncPendingPlanillas(regionKey, subregionKey)
                }
                queueDao.deleteById(item.queueId)
            } catch (ex: Exception) {
                logError(item, ex)
                val nextDelay = retryPolicy.nextDelay(item.retryCount)
                val updated = item.copy(
                    status = SyncStatus.ERROR,
                    retryCount = item.retryCount + 1,
                    lastError = ex.message,
                    availableAt = now + nextDelay
                )
                queueDao.update(updated)
            }
        }
    }

    private suspend fun logError(item: SyncQueueEntity, ex: Exception) {
        database.syncErrorLogDao().insert(
            SyncErrorLogEntity(
                errorId = UUID.randomUUID().toString(),
                type = item.type.name,
                entityId = item.entityId,
                message = ex.message ?: "Error desconocido",
                stacktrace = ex.stackTraceToString(),
                createdAt = System.currentTimeMillis()
            )
        )
    }
}
