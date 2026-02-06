package com.Arasoftsolutions.tecniapp_ice.pm.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.Arasoftsolutions.tecniapp_ice.pm.model.entities.SyncErrorLogEntity

@Dao
interface SyncErrorLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SyncErrorLogEntity)

    @Query("SELECT * FROM pm_sync_errors WHERE entityId = :entityId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getErrors(entityId: String, limit: Int = 20): List<SyncErrorLogEntity>
}
