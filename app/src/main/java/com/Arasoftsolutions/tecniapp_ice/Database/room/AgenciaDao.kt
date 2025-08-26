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

    // Observa las agencias filtradas por subregión
    @Query("SELECT * FROM agencias WHERE subregion = :subregionId")
    fun observarPorSubregion(subregionId: String): Flow<List<AgenciaEntity>>
}