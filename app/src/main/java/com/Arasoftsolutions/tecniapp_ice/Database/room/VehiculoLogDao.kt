package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculoLogEntity
import kotlinx.coroutines.flow.Flow

// ─────────────────────────────────────────────────────────────────────────────
// CORRECCIONES aplicadas en este archivo:
//
// FIX-4: se agrega getAll() — consulta SUSPEND (no-Flow) que devuelve todos
//        los logs de un vehiculoId y tipo dados, ordenados por timestamp DESC.
//        Es necesario para que EtmRegistroRules y RoomRepository puedan leer
//        el historial completo de registros diarios sin suscribirse a un Flow.
// ─────────────────────────────────────────────────────────────────────────────

@Dao
interface VehiculoLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: VehiculoLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(logs: List<VehiculoLogEntity>)

    // ─── OBSERVADORES (Flow) ──────────────────────────────────────────────────

    @Query("SELECT * FROM vehiculo_log WHERE vehiculoId = :vehiculoId ORDER BY timestamp DESC")
    fun observeTimeline(vehiculoId: String): Flow<List<VehiculoLogEntity>>

    @Query("SELECT * FROM vehiculo_log WHERE vehiculoId = :vehiculoId AND tipo = :tipo ORDER BY timestamp DESC")
    fun observeByTipo(vehiculoId: String, tipo: String): Flow<List<VehiculoLogEntity>>

    // ─── LECTURAS PUNTUALES (suspend) ─────────────────────────────────────────

    @Query("SELECT * FROM vehiculo_log WHERE vehiculoId = :vehiculoId AND tipo = :tipo ORDER BY timestamp DESC LIMIT 1")
    suspend fun findLastByTipo(vehiculoId: String, tipo: String): VehiculoLogEntity?

    /**
     * FIX-4: devuelve TODOS los registros de un tipo dado para un vehículo,
     * ordenados por timestamp DESC.
     * Necesario para leer el historial completo sin Flow (e.g. en EtmRegistroRules).
     */
    @Query("SELECT * FROM vehiculo_log WHERE vehiculoId = :vehiculoId AND tipo = :tipo ORDER BY timestamp DESC")
    suspend fun getAll(vehiculoId: String, tipo: String): List<VehiculoLogEntity>

    @Query("SELECT * FROM vehiculo_log WHERE vehiculoId = :vehiculoId AND syncState = :syncState ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getPending(vehiculoId: String, syncState: String = "PENDING", limit: Int = 100): List<VehiculoLogEntity>

    // ─── ACTUALIZACIONES ──────────────────────────────────────────────────────

    @Query("UPDATE vehiculo_log SET syncState = :newState, updatedAt = :updatedAt WHERE logId IN (:logIds)")
    suspend fun markState(logIds: List<String>, newState: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE vehiculo_log SET syncState = :newState, payloadJson = :payloadJson, updatedAt = :updatedAt WHERE logId = :logId")
    suspend fun markSingle(logId: String, newState: String, payloadJson: String, updatedAt: Long = System.currentTimeMillis())
}