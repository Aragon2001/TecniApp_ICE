package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.Arasoftsolutions.tecniapp_ice.Database.entities.InventarioConVehiculo
import com.Arasoftsolutions.tecniapp_ice.Database.entities.InventarioItemEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.InventarioMovimientoAveriaEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaReparacionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventarioDao {

    @Query(
        "SELECT i.*, v.placa AS vehiculoPlaca, v.agencia AS vehiculoAgencia " +
            "FROM inventario_material i " +
            "LEFT JOIN vehiculo v ON v.id = i.vehiculoId " +
            "WHERE i.vehiculoId = :vehiculoId " +
            "ORDER BY i.descripcionMaterial"
    )
    fun observarInventarioPorVehiculo(vehiculoId: Int): Flow<List<InventarioConVehiculo>>

    @Query("SELECT * FROM inventario_material WHERE vehiculoId = :vehiculoId ORDER BY descripcionMaterial")
    suspend fun obtenerPorVehiculo(vehiculoId: Int): List<InventarioItemEntity>

    @Query(
        "SELECT i.*, v.placa AS vehiculoPlaca, v.agencia AS vehiculoAgencia " +
            "FROM inventario_material i " +
            "LEFT JOIN vehiculo v ON v.id = i.vehiculoId " +
            "ORDER BY v.placa, i.descripcionMaterial"
    )
    fun observarInventarioGeneral(): Flow<List<InventarioConVehiculo>>

    @Query("SELECT * FROM inventario_material WHERE vehiculoId = :vehiculoId AND codigoMaterial = :codigo LIMIT 1")
    suspend fun obtenerItem(vehiculoId: Int, codigo: String): InventarioItemEntity?

    @Query("SELECT * FROM inventario_material WHERE id = :id LIMIT 1")
    suspend fun obtenerItemPorId(id: Long): InventarioItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: InventarioItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<InventarioItemEntity>)

    @Query("DELETE FROM inventario_material WHERE id = :id")
    suspend fun eliminarPorId(id: Long)

    @Query("DELETE FROM inventario_material WHERE vehiculoId = :vehiculoId")
    suspend fun eliminarPorVehiculo(vehiculoId: Int)

    @Query("DELETE FROM inventario_material WHERE vehiculoId = :vehiculoId AND codigoMaterial = :codigo")
    suspend fun eliminarPorVehiculoYCodigo(vehiculoId: Int, codigo: String)

    @Query("DELETE FROM inventario_material")
    suspend fun limpiarTodo()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun registrarReparacion(entity: LuminariaReparacionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReparacion(entity: LuminariaReparacionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarReparaciones(items: List<LuminariaReparacionEntity>)

    @Transaction
    @Query("SELECT * FROM luminaria_reparacion ORDER BY fechaRegistro DESC")
    fun observarReparaciones(): Flow<List<LuminariaReparacionEntity>>

    @Query("SELECT * FROM luminaria_reparacion WHERE id = :id LIMIT 1")
    suspend fun obtenerReparacion(id: Long): LuminariaReparacionEntity?

    @Query(
        "SELECT * FROM luminaria_reparacion " +
            "WHERE localizacion = :localizacion AND estado = :estado AND vehiculoId = :vehiculoId " +
            "ORDER BY fechaRegistro DESC LIMIT 1"
    )
    suspend fun obtenerReparacionPorLocalizacionYEstado(
        localizacion: String,
        estado: String,
        vehiculoId: Int
    ): LuminariaReparacionEntity?

    @Update
    suspend fun actualizarReparacion(entity: LuminariaReparacionEntity)

    @Query("DELETE FROM luminaria_reparacion WHERE id = :id")
    suspend fun eliminarReparacion(id: Long)

    @Query("DELETE FROM luminaria_reparacion WHERE id = :id")
    suspend fun eliminarReparacionPorId(id: Long)

    @Query("DELETE FROM luminaria_reparacion")
    suspend fun limpiarReparaciones()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun registrarMovimientoAveria(entity: InventarioMovimientoAveriaEntity)
}
