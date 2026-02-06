package com.Arasoftsolutions.tecniapp_ice.pm.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.Arasoftsolutions.tecniapp_ice.pm.model.SyncStatus
import com.Arasoftsolutions.tecniapp_ice.pm.model.entities.PlanillaEntity

@Dao
interface PlanillaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlanillaEntity)

    @Update
    suspend fun update(entity: PlanillaEntity)

    @Query("SELECT * FROM pm_planillas WHERE planillaId = :planillaId")
    suspend fun getById(planillaId: String): PlanillaEntity?

    @Query("SELECT * FROM pm_planillas WHERE syncStatus = :status ORDER BY createdAt LIMIT :limit")
    suspend fun getBySyncStatus(status: SyncStatus, limit: Int = 50): List<PlanillaEntity>
}
