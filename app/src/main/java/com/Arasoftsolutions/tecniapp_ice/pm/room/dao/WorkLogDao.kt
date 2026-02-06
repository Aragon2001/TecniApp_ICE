package com.Arasoftsolutions.tecniapp_ice.pm.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.Arasoftsolutions.tecniapp_ice.pm.model.SyncStatus
import com.Arasoftsolutions.tecniapp_ice.pm.model.entities.WorkLogEntity

@Dao
interface WorkLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WorkLogEntity)

    @Update
    suspend fun update(entity: WorkLogEntity)

    @Query("SELECT * FROM pm_work_logs WHERE logId = :logId")
    suspend fun getById(logId: String): WorkLogEntity?

    @Query("SELECT * FROM pm_work_logs WHERE groupId = :groupId")
    suspend fun getByGroup(groupId: String): List<WorkLogEntity>

    @Query("SELECT * FROM pm_work_logs WHERE syncStatus = :status ORDER BY createdAt LIMIT :limit")
    suspend fun getBySyncStatus(status: SyncStatus, limit: Int = 100): List<WorkLogEntity>

    @Query("SELECT * FROM pm_work_logs WHERE fechaDia = :fechaDia AND uidTecnico = :uid")
    suspend fun getByUsuarioDia(uid: String, fechaDia: String): List<WorkLogEntity>
}
