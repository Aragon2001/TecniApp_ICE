package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.Arasoftsolutions.tecniapp_ice.Database.entities.InventarioConVehiculo
import com.Arasoftsolutions.tecniapp_ice.Database.entities.InventarioItemEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaReparacionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventarioDao {

    @Query(
        "SELECT i.*, v.placa AS vehiculoPlaca, v.agencia AS vehiculoAgencia " +
            "FROM inventario_material i " +
            "LEFT JOIN vehiculos v ON v.id = i.vehiculoId " +
            "WHERE i.vehiculoId = :vehiculoId " +
            "ORDER BY i.descripcionMaterial"
    )
    fun observarInventarioPorVehiculo(vehiculoId: Int): Flow<List<InventarioConVehiculo>>

    @Query(
        "SELECT i.*, v.placa AS vehiculoPlaca, v.agencia AS vehiculoAgencia " +
            "FROM inventario_material i " +
            "LEFT JOIN vehiculos v ON v.id = i.vehiculoId " +
            "ORDER BY v.placa, i.descripcionMaterial"
    )
    fun observarInventarioGeneral(): Flow<List<InventarioConVehiculo>>

    @Query("SELECT * FROM inventario_material WHERE vehiculoId = :vehiculoId AND codigoMaterial = :codigo LIMIT 1")
    suspend fun obtenerItem(vehiculoId: Int, codigo: String): InventarioItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: InventarioItemEntity)

    @Query("DELETE FROM inventario_material WHERE id = :id")
    suspend fun eliminarPorId(id: Long)

    @Query("DELETE FROM inventario_material WHERE vehiculoId = :vehiculoId")
    suspend fun eliminarPorVehiculo(vehiculoId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun registrarReparacion(entity: LuminariaReparacionEntity): Long

    @Transaction
    @Query("SELECT * FROM luminaria_reparacion ORDER BY fechaRegistro DESC")
    fun observarReparaciones(): Flow<List<LuminariaReparacionEntity>>

    @Query("SELECT * FROM luminaria_reparacion WHERE id = :id LIMIT 1")
    suspend fun obtenerReparacion(id: Long): LuminariaReparacionEntity?

    @Query(
        "SELECT * FROM luminaria_reparacion " +
            "WHERE localizacion = :localizacion AND estado = :estado " +
            "ORDER BY fechaRegistro DESC LIMIT 1"
    )
    suspend fun obtenerReparacionPorLocalizacionYEstado(
        localizacion: String,
        estado: String
    ): LuminariaReparacionEntity?

    @Update
    suspend fun actualizarReparacion(entity: LuminariaReparacionEntity)

    @Query("DELETE FROM luminaria_reparacion WHERE id = :id")
    suspend fun eliminarReparacion(id: Long)
}
