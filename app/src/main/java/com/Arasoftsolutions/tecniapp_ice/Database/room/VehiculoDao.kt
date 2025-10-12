// ======================
// VehiculoDao.kt
// ======================
package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("SELECT * FROM vehiculos WHERE id = :id LIMIT 1")
    suspend fun buscarPorId(id: Int): VehiculosEntity?

    @Query("SELECT * FROM vehiculos WHERE placa = :placa LIMIT 1")
    suspend fun buscarPorPlaca(placa: Long): VehiculosEntity?

    @Query("DELETE FROM vehiculos WHERE id = :id")
    suspend fun eliminarPorId(id: Int)
}
