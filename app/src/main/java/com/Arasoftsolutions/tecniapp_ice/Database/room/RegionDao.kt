// ======================
// RegionDao.kt
// ======================
package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.Arasoftsolutions.tecniapp_ice.Database.entities.RegionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RegionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(regiones: List<RegionEntity>)

    @Query("SELECT * FROM regiones")
    fun observarTodas(): Flow<List<RegionEntity>>

    @Query("SELECT * FROM regiones")
    suspend fun getAll(): List<RegionEntity>

    @Query("SELECT COUNT(*) FROM regiones")
    suspend fun count(): Int

    @Query("DELETE FROM regiones WHERE id NOT IN (:ids)")
    suspend fun eliminarFueraDeIds(ids: List<String>)

    @Query("DELETE FROM regiones")
    suspend fun limpiarTodo()
}
