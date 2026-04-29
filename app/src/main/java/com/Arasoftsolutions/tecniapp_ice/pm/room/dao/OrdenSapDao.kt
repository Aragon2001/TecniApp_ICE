package com.Arasoftsolutions.tecniapp_ice.pm.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.Arasoftsolutions.tecniapp_ice.pm.model.entities.OrdenSapEntity

@Dao
interface OrdenSapDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<OrdenSapEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(entities: List<OrdenSapEntity>)

    @Query("SELECT ordenSap FROM ordenes_sap WHERE ordenSap IN (:ordenes)")
    suspend fun getExistingOrdenes(ordenes: List<Long>): List<Long>

    @Query("SELECT * FROM ordenes_sap WHERE ordenSap = :ordenSap")
    suspend fun getByOrden(ordenSap: Long): OrdenSapEntity?

    @Query("SELECT * FROM ordenes_sap WHERE descripcion LIKE '%' || :query || '%' ORDER BY descripcion LIMIT :limit")
    suspend fun searchByDescripcion(query: String, limit: Int = 50): List<OrdenSapEntity>

    @Query("DELETE FROM ordenes_sap")
    suspend fun clearAll()
}
