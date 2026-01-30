// ======================
// SubregionDao.kt
// ======================
package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.*
import com.Arasoftsolutions.tecniapp_ice.Database.entities.SubregionesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubregionDao {
    // Inserta o actualiza subregiones
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(subregiones: List<SubregionesEntity>)

    // Devuelve todas las subregiones disponibles
    @Query("SELECT * FROM subregiones")
    suspend fun getAll(): List<SubregionesEntity>

    @Query("SELECT * FROM subregiones")
    fun observarTodas(): Flow<List<SubregionesEntity>>

    @Query("SELECT COUNT(*) FROM subregiones")
    suspend fun count(): Int

    @Query("DELETE FROM subregiones WHERE id NOT IN (:ids)")
    suspend fun eliminarFueraDeIds(ids: List<String>)

    @Query("DELETE FROM subregiones")
    suspend fun limpiarTodo()
}
