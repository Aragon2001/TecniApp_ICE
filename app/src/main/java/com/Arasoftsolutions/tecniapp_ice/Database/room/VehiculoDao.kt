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

    // Observa vehículos por agencia
    @Query("SELECT * FROM vehiculos WHERE agencia = :agencia COLLATE NOCASE")
    fun observarPorAgencia(agencia: String): Flow<List<VehiculosEntity>>

    @Query("SELECT * FROM vehiculos")
    fun observarTodos(): Flow<List<VehiculosEntity>>

    @Query("SELECT * FROM vehiculos WHERE id = :id LIMIT 1")
    suspend fun buscarPorId(id: Int): VehiculosEntity?

    @Query("SELECT * FROM vehiculos WHERE placa = :placa LIMIT 1")
    suspend fun buscarPorPlaca(placa: Long): VehiculosEntity?

    @Query("SELECT * FROM vehiculos WHERE placa = :placa LIMIT 1")
    fun observarPorPlaca(placa: Long): Flow<VehiculosEntity?>

    @Query("DELETE FROM vehiculos WHERE id = :id")
    suspend fun eliminarPorId(id: Int)

    @Query("DELETE FROM vehiculos WHERE subregion = :subregionId COLLATE NOCASE")
    suspend fun eliminarPorSubregion(subregionId: String)

    @Query("DELETE FROM vehiculos WHERE id NOT IN (:ids)")
    suspend fun eliminarFueraDeIds(ids: List<Int>)

    @Query("DELETE FROM vehiculos")
    suspend fun limpiarTodo()

    @Query(
        """
        UPDATE vehiculos
        SET registroFecha = :fecha,
            registroInicial = :inicial,
            registroFinal = :final,
            registroCerrado = :cerrado,
            kilometrajeActual = :kilometrajeActual,
            orimetroActual = :orimetroActual,
            registrosDiariosJson = :registrosJson
        WHERE id = :vehiculoId
        """
    )
    suspend fun actualizarRegistroDiario(
        vehiculoId: Int,
        fecha: String,
        inicial: Double,
        final: Double?,
        cerrado: Boolean,
        kilometrajeActual: Double?,
        orimetroActual: Double?,
        registrosJson: String?
    )

    @Query("UPDATE vehiculos SET kilometrajeActual = :kilometrajeActual WHERE placa = :placa")
    suspend fun actualizarKilometrajeActual(placa: Long, kilometrajeActual: Double)

    @Query("UPDATE vehiculos SET orimetroActual = :orimetroActual WHERE placa = :placa")
    suspend fun actualizarOrimetroActual(placa: Long, orimetroActual: Double)
}
