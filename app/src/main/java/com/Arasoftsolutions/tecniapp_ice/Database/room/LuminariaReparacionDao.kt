package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaReparacionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LuminariaReparacionDao {

    @Transaction
    @Query("SELECT * FROM luminaria_reparacion ORDER BY fechaRegistro DESC")
    fun observarTodas(): Flow<List<LuminariaReparacionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(entity: LuminariaReparacionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LuminariaReparacionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTodas(items: List<LuminariaReparacionEntity>)

    @Update
    suspend fun actualizar(entity: LuminariaReparacionEntity)

    @Query("DELETE FROM luminaria_reparacion WHERE id = :id")
    suspend fun eliminar(id: Long)

    // Usado en syncLuminariasAgencia para el swap atómico via db.withTransaction
    @Query(
        "DELETE FROM luminaria_reparacion " +
            "WHERE vehiculoId IN (SELECT id FROM vehiculo WHERE LOWER(agencia) = LOWER(:agencia))"
    )
    suspend fun eliminarPorAgencia(agencia: String)

    @Query("SELECT * FROM luminaria_reparacion WHERE id = :id LIMIT 1")
    suspend fun obtenerPorId(id: Long): LuminariaReparacionEntity?

    @Query("SELECT COUNT(*) FROM luminaria_reparacion")
    suspend fun contar(): Int

    @Query(
        "SELECT * FROM luminaria_reparacion " +
            "WHERE localizacion = :localizacion AND estado = :estado AND vehiculoId = :vehiculoId " +
            "ORDER BY fechaRegistro DESC LIMIT 1"
    )
    suspend fun obtenerPorLocalizacionYEstado(
        localizacion: String,
        estado: String,
        vehiculoId: Int
    ): LuminariaReparacionEntity?
}