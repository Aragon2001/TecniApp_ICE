package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehiculoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vehiculos: List<VehiculoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVehiculo(vehiculo: VehiculoEntity)

    @Query("SELECT * FROM vehiculo")
    suspend fun getAll(): List<VehiculoEntity>

    @Query("SELECT * FROM vehiculo WHERE agencia = :agencia COLLATE NOCASE")
    fun observarPorAgencia(agencia: String): Flow<List<VehiculoEntity>>

    @Query("SELECT * FROM vehiculo")
    fun observarTodos(): Flow<List<VehiculoEntity>>

    @Query("SELECT * FROM vehiculo WHERE id = :id LIMIT 1")
    suspend fun buscarPorId(id: Int): VehiculoEntity?

    @Query("SELECT * FROM vehiculo WHERE vehiculoId = :vehiculoId LIMIT 1")
    suspend fun buscarPorVehiculoId(vehiculoId: String): VehiculoEntity?

    @Query("SELECT * FROM vehiculo WHERE CAST(placaRaw AS INTEGER) = :placa OR placa = :placa LIMIT 1")
    suspend fun buscarPorPlaca(placa: Long): VehiculoEntity?

    @Query("SELECT * FROM vehiculo WHERE CAST(placaRaw AS INTEGER) = :placa OR placa = :placa LIMIT 1")
    fun observarPorPlaca(placa: Long): Flow<VehiculoEntity?>

    @Query("SELECT * FROM vehiculo WHERE vehiculoId = :vehiculoId LIMIT 1")
    fun observarPorVehiculoId(vehiculoId: String): Flow<VehiculoEntity?>

    @Query("DELETE FROM vehiculo WHERE id = :id")
    suspend fun eliminarPorId(id: Int)

    @Query("DELETE FROM vehiculo WHERE subregion = :subregionId COLLATE NOCASE")
    suspend fun eliminarPorSubregion(subregionId: String)

    @Query("DELETE FROM vehiculo WHERE id NOT IN (:ids)")
    suspend fun eliminarFueraDeIds(ids: List<Int>)

    @Query("DELETE FROM vehiculo")
    suspend fun limpiarTodo()

    @Query("UPDATE vehiculo SET kmActual = :kmActual, updatedAt = :updatedAt WHERE vehiculoId = :vehiculoId")
    suspend fun actualizarKilometrajeByVehiculoId(
        vehiculoId: String,
        kmActual: Double,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE vehiculo SET  kmActual = :kilometrajeActual, updatedAt = :updatedAt WHERE placa = :placa")
    suspend fun actualizarKilometrajeActual(placa: Long, kilometrajeActual: Double, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE vehiculo SET orimetroActual = :orimetroActual, updatedAt = :updatedAt WHERE placa = :placa")
    suspend fun actualizarOrimetroActual(placa: Long, orimetroActual: Double, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE vehiculo SET mantenimientoUltimo = :mantenimientoUltimo, mantenimientoProximo = :mantenimientoProximo, updatedAt = :updatedAt WHERE id = :vehiculoId")
    suspend fun actualizarMantenimiento(vehiculoId: Int, mantenimientoUltimo: String?, mantenimientoProximo: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE vehiculo SET registroFecha = :fecha, registroInicial = :inicial, registroFinal = :final, registroCerrado = :cerrado, kmActual = :kmActual, orimetroActual = :orimetroActual, registrosDiariosJson = :registrosJson, updatedAt = :updatedAt WHERE id = :vehiculoId")
    suspend fun actualizarRegistroDiario(
        vehiculoId: Int,
        fecha: String,
        inicial: Double,
        final: Double?,
        cerrado: Boolean,
        kmActual: Double?,
        orimetroActual: Double?,
        registrosJson: String?,
        updatedAt: Long = System.currentTimeMillis()
    )
}
