package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculoLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehiculoLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: VehiculoLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(logs: List<VehiculoLogEntity>)

    @Query("SELECT * FROM vehiculo_log WHERE vehiculoId = :vehiculoId ORDER BY timestamp DESC")
    fun observeTimeline(vehiculoId: String): Flow<List<VehiculoLogEntity>>

    @Query("SELECT * FROM vehiculo_log WHERE vehiculoId = :vehiculoId AND tipo = :tipo ORDER BY timestamp DESC")
    fun observeByTipo(vehiculoId: String, tipo: String): Flow<List<VehiculoLogEntity>>

    @Query("SELECT * FROM vehiculo_log WHERE vehiculoId = :vehiculoId AND tipo = :tipo ORDER BY timestamp DESC LIMIT 1")
    suspend fun findLastByTipo(vehiculoId: String, tipo: String): VehiculoLogEntity?

    @Query("SELECT * FROM vehiculo_log WHERE vehiculoId = :vehiculoId AND syncState = :syncState ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getPending(vehiculoId: String, syncState: String = "PENDING", limit: Int = 100): List<VehiculoLogEntity>

    @Query("UPDATE vehiculo_log SET syncState = :newState, updatedAt = :updatedAt WHERE logId IN (:logIds)")
    suspend fun markState(logIds: List<String>, newState: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE vehiculo_log SET syncState = :newState, payloadJson = :payloadJson, updatedAt = :updatedAt WHERE logId = :logId")
    suspend fun markSingle(logId: String, newState: String, payloadJson: String, updatedAt: Long = System.currentTimeMillis())
}
