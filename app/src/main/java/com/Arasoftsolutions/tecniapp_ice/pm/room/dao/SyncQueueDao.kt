package com.Arasoftsolutions.tecniapp_ice.pm.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.Arasoftsolutions.tecniapp_ice.pm.model.SyncQueueType
import com.Arasoftsolutions.tecniapp_ice.pm.model.SyncStatus
import com.Arasoftsolutions.tecniapp_ice.pm.model.entities.SyncQueueEntity

@Dao
interface SyncQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SyncQueueEntity)

    @Update
    suspend fun update(entity: SyncQueueEntity)

    @Query("SELECT * FROM pm_sync_queue WHERE status IN (:statuses) AND availableAt <= :now ORDER BY createdAt LIMIT :limit")
    suspend fun getAvailable(statuses: List<SyncStatus>, now: Long, limit: Int = 50): List<SyncQueueEntity>

    @Query("SELECT * FROM pm_sync_queue WHERE type = :type AND entityId = :entityId")
    suspend fun getByTypeEntity(type: SyncQueueType, entityId: String): SyncQueueEntity?

    @Query("DELETE FROM pm_sync_queue WHERE queueId = :queueId")
    suspend fun deleteById(queueId: String)
}
