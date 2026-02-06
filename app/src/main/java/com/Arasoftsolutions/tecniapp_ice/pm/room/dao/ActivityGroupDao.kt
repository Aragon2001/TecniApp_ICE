package com.Arasoftsolutions.tecniapp_ice.pm.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.Arasoftsolutions.tecniapp_ice.pm.model.SyncStatus
import com.Arasoftsolutions.tecniapp_ice.pm.model.entities.ActivityGroupEntity

@Dao
interface ActivityGroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ActivityGroupEntity)

    @Update
    suspend fun update(entity: ActivityGroupEntity)

    @Query("SELECT * FROM pm_activity_groups WHERE groupId = :groupId")
    suspend fun getById(groupId: String): ActivityGroupEntity?

    @Query("SELECT * FROM pm_activity_groups WHERE syncStatus = :status ORDER BY createdAt LIMIT :limit")
    suspend fun getBySyncStatus(status: SyncStatus, limit: Int = 100): List<ActivityGroupEntity>
}
