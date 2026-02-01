package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.Arasoftsolutions.tecniapp_ice.Database.entities.EtmRegistroEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EtmRegistroDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(registro: EtmRegistroEntity): Long

    @Query("SELECT * FROM etm_registros WHERE placa = :placa AND fecha = :fecha LIMIT 1")
    suspend fun obtenerPorPlacaYFecha(placa: String, fecha: String): EtmRegistroEntity?

    @Query("SELECT * FROM etm_registros WHERE placa = :placa ORDER BY fecha DESC LIMIT :limite")
    fun observarUltimos(placa: String, limite: Int = 30): Flow<List<EtmRegistroEntity>>

    @Query("SELECT * FROM etm_registros WHERE placa = :placa AND fecha >= :fechaDesde AND fecha <= :fechaHasta ORDER BY fecha DESC")
    fun observarPorRango(placa: String, fechaDesde: String, fechaHasta: String): Flow<List<EtmRegistroEntity>>

    @Query("SELECT * FROM etm_registros ORDER BY fecha DESC, registradoEn DESC LIMIT :limite")
    fun observarTodos(limite: Int = 100): Flow<List<EtmRegistroEntity>>
}
