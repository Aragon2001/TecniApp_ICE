// ======================
// AgenciaDao.kt
// ======================
package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.*
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AgenciaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AgenciaDao {
    // Inserta o reemplaza todas las agencias en la base de datos
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(agencias: List<AgenciaEntity>)

    // Obtiene todas las agencias almacenadas
    @Query("SELECT * FROM agencias")
    suspend fun getAll(): List<AgenciaEntity>

    // Observa todas las agencias almacenadas
    @Query("SELECT * FROM agencias")
    fun observarTodas(): Flow<List<AgenciaEntity>>

    // Observa las agencias filtradas por región
    @Query("SELECT * FROM agencias WHERE regionId = :regionId COLLATE NOCASE")
    fun observarPorRegion(regionId: String): Flow<List<AgenciaEntity>>

    @Query("SELECT COUNT(*) FROM agencias")
    suspend fun count(): Int

    @Query("DELETE FROM agencias WHERE subregion = :subregionId COLLATE NOCASE")
    suspend fun eliminarPorSubregion(subregionId: String)

    @Query("DELETE FROM agencias WHERE id NOT IN (:ids)")
    suspend fun eliminarFueraDeIds(ids: List<String>)

    @Query("DELETE FROM agencias")
    suspend fun limpiarTodo()
}
