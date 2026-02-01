package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculoMantenimientoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehiculoMantenimientoDao {

    @Insert
    suspend fun insertar(mantenimiento: VehiculoMantenimientoEntity): Long

    @Query("SELECT * FROM vehiculo_mantenimientos WHERE placa = :placa ORDER BY registradoEn DESC")
    fun observarPorPlaca(placa: String): Flow<List<VehiculoMantenimientoEntity>>

    @Query("SELECT * FROM vehiculo_mantenimientos ORDER BY registradoEn DESC LIMIT :limite")
    fun observarTodos(limite: Int = 50): Flow<List<VehiculoMantenimientoEntity>>
}
