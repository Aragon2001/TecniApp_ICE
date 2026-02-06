package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.Arasoftsolutions.tecniapp_ice.Database.entities.RegistroDiarioEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.RegistroMantenimientoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RegistroVehiculoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegistroDiario(registro: RegistroDiarioEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegistroMantenimiento(registro: RegistroMantenimientoEntity): Long

    @Query(
        """
        SELECT * FROM registro_diario
        WHERE vehiculoId = :vehiculoId
        ORDER BY fecha DESC, registradoEn DESC
        """
    )
    fun observarRegistrosDiarios(vehiculoId: Int): Flow<List<RegistroDiarioEntity>>

    @Query(
        """
        SELECT * FROM registro_mantenimiento
        WHERE vehiculoId = :vehiculoId
        ORDER BY creadoEn DESC
        """
    )
    fun observarMantenimientos(vehiculoId: Int): Flow<List<RegistroMantenimientoEntity>>

    @Query(
        """
        SELECT * FROM registro_diario
        WHERE vehiculoId = :vehiculoId
        ORDER BY fecha DESC, registradoEn DESC
        LIMIT 1
        """
    )
    suspend fun obtenerUltimoRegistroDiario(vehiculoId: Int): RegistroDiarioEntity?

    @Query(
        """
        SELECT * FROM registro_mantenimiento
        WHERE vehiculoId = :vehiculoId
        ORDER BY creadoEn DESC
        LIMIT 1
        """
    )
    suspend fun obtenerUltimoMantenimiento(vehiculoId: Int): RegistroMantenimientoEntity?

    @Query(
        """
        SELECT * FROM registro_diario
        WHERE vehiculoId = :vehiculoId AND fecha = :fecha
        LIMIT 1
        """
    )
    suspend fun obtenerRegistroDiarioPorFecha(vehiculoId: Int, fecha: String): RegistroDiarioEntity?

    @Query("SELECT * FROM registro_mantenimiento WHERE syncStatus = :status")
    suspend fun obtenerMantenimientosPendientes(status: String): List<RegistroMantenimientoEntity>

    @Query("SELECT * FROM registro_diario WHERE syncStatus = :status")
    suspend fun obtenerRegistrosDiariosPendientes(status: String): List<RegistroDiarioEntity>

    @Query("UPDATE registro_mantenimiento SET syncStatus = :status WHERE id IN (:ids)")
    suspend fun actualizarMantenimientoSyncStatus(ids: List<Long>, status: String)

    @Query("UPDATE registro_diario SET syncStatus = :status WHERE id IN (:ids)")
    suspend fun actualizarRegistroDiarioSyncStatus(ids: List<Long>, status: String)
}
