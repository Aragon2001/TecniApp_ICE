package com.Arasoftsolutions.tecniapp_ice.pm.model.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.Arasoftsolutions.tecniapp_ice.pm.model.SyncStatus

@Entity(
    tableName = "pm_activity_groups",
    indices = [
        Index(value = ["ordenSap"]),
        Index(value = ["regionKey", "subregionKey"]),
        Index(value = ["fechaDia"]),
        Index(value = ["syncStatus"]),
        Index(value = ["updatedAt"])
    ]
)
data class ActivityGroupEntity(
    @PrimaryKey
    val groupId: String,
    val ordenSap: Long,
    val ordenSapManual: String?,
    val descripcionOrden: String?,
    val regionKey: String,
    val subregionKey: String,
    val circuitoId: String?,
    val agenciaId: String?,
    val modulo: String?,
    val uidTecnico: String,
    val fechaDia: String,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: SyncStatus,
    val retryCount: Int,
    val lastError: String?
)
