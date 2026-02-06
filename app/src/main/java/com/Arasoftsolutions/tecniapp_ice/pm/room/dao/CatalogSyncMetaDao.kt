package com.Arasoftsolutions.tecniapp_ice.pm.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.Arasoftsolutions.tecniapp_ice.pm.model.entities.CatalogSyncMetaEntity

@Dao
interface CatalogSyncMetaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: CatalogSyncMetaEntity)

    @Query("SELECT * FROM catalog_sync_meta WHERE catalogId = :catalogId")
    suspend fun getMeta(catalogId: String): CatalogSyncMetaEntity?
}
