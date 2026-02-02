// ======================
// VehiculoDao.kt
// ======================
package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.Arasoftsolutions.tecniapp_ice.Database.entities.EtmRegistroEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculoKilometrajeEntity
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

    // Observa vehículos por agencia
    @Query("SELECT * FROM vehiculos WHERE agencia = :agencia COLLATE NOCASE")
    fun observarPorAgencia(agencia: String): Flow<List<VehiculosEntity>>

    @Query("SELECT * FROM vehiculos")
    fun observarTodos(): Flow<List<VehiculosEntity>>

    @Query("SELECT * FROM vehiculos WHERE id = :id LIMIT 1")
    suspend fun buscarPorId(id: Int): VehiculosEntity?

    @Query("SELECT * FROM vehiculos WHERE placa = :placa LIMIT 1")
    suspend fun buscarPorPlaca(placa: Long): VehiculosEntity?

    @Query("DELETE FROM vehiculos WHERE id = :id")
    suspend fun eliminarPorId(id: Int)

    @Query("DELETE FROM vehiculos WHERE subregion = :subregionId COLLATE NOCASE")
    suspend fun eliminarPorSubregion(subregionId: String)

    @Query("DELETE FROM vehiculos WHERE id NOT IN (:ids)")
    suspend fun eliminarFueraDeIds(ids: List<Int>)

    @Query("DELETE FROM vehiculos")
    suspend fun limpiarTodo()

    // --- Kilometraje ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarKilometraje(registro: VehiculoKilometrajeEntity)

    @Query(
        "SELECT * FROM vehiculo_kilometrajes WHERE placaNormalizada = :placaNormalizada ORDER BY registradoEn DESC LIMIT 1"
    )
    fun observarUltimoKilometraje(placaNormalizada: String): Flow<VehiculoKilometrajeEntity?>

    @Query(
        "SELECT * FROM vehiculo_kilometrajes WHERE placaNormalizada = :placaNormalizada ORDER BY registradoEn DESC LIMIT 1"
    )
    suspend fun obtenerUltimoKilometraje(placaNormalizada: String): VehiculoKilometrajeEntity?

    // --- ETM ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarEtmRegistro(registro: EtmRegistroEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarEtmRegistros(registros: List<EtmRegistroEntity>)

    @Query("SELECT * FROM etm_registros WHERE placa = :placa AND fecha = :fecha LIMIT 1")
    suspend fun obtenerEtmPorPlacaYFecha(placa: String, fecha: String): EtmRegistroEntity?

    @Query("SELECT * FROM etm_registros WHERE placa = :placa ORDER BY fecha DESC LIMIT :limite")
    fun observarEtmUltimos(placa: String, limite: Int = 30): Flow<List<EtmRegistroEntity>>

    @Query("SELECT * FROM etm_registros WHERE placa = :placa ORDER BY fecha DESC, registradoEn DESC LIMIT 1")
    suspend fun obtenerUltimoEtm(placa: String): EtmRegistroEntity?

    @Query("SELECT * FROM etm_registros WHERE placa = :placa AND fecha >= :fechaDesde AND fecha <= :fechaHasta ORDER BY fecha DESC")
    fun observarEtmPorRango(placa: String, fechaDesde: String, fechaHasta: String): Flow<List<EtmRegistroEntity>>

    @Query("SELECT * FROM etm_registros ORDER BY fecha DESC, registradoEn DESC LIMIT :limite")
    fun observarEtmTodos(limite: Int = 100): Flow<List<EtmRegistroEntity>>
}
