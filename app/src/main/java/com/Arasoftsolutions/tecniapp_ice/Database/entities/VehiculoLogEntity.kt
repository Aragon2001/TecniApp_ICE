package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vehiculo_log",
    indices = [
        Index(value = ["vehiculoId", "timestamp"]),
        Index(value = ["vehiculoId", "syncState"])
    ]
)
data class VehiculoLogEntity(
    @PrimaryKey
    val logId: String,
    val vehiculoId: String,
    val tipo: String,
    val timestamp: Long,
    val km: Double? = null,
    val payloadJson: String = "{}",
    val syncState: String = "PENDING",
    val updatedAt: Long = System.currentTimeMillis(),
)
