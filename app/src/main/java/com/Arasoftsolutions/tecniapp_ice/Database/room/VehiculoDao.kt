// ======================
// VehiculoDao.kt
// ======================
package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.*
import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculosEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehiculoDao {
    // Inserta todos los vehículos con conflicto por reemplazo
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vehiculos: List<VehiculosEntity>)

    // Retorna todos los vehículos existentes
    @Query("SELECT * FROM vehiculos")
    suspend fun getAll(): List<VehiculosEntity>

    // Observa vehículos por subregión
    @Query("SELECT * FROM vehiculos WHERE subregion = :subregionId")
    fun observarPorSubregion(subregionId: String): Flow<List<VehiculosEntity>>

    @Query("SELECT * FROM vehiculos")
    fun observarTodos(): Flow<List<VehiculosEntity>>
}