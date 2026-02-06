package com.Arasoftsolutions.tecniapp_ice.pm.model.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.Arasoftsolutions.tecniapp_ice.pm.model.SyncStatus

@Entity(
    tableName = "pm_work_logs",
    indices = [
        Index(value = ["groupId", "uidTecnico", "fechaDia"], unique = true),
        Index(value = ["ordenSap"]),
        Index(value = ["regionKey", "subregionKey"]),
        Index(value = ["syncStatus"]),
        Index(value = ["updatedAt"])
    ]
)
data class WorkLogEntity(
    @PrimaryKey
    val logId: String,
    val groupId: String,
    val ordenSap: Long,
    val ordenSapManual: String?,
    val regionKey: String,
    val subregionKey: String,
    val circuitoId: String?,
    val agenciaId: String?,
    val modulo: String?,
    val uidTecnico: String,
    val fechaDia: String,
    val horaEntraMin: Int,
    val horaSaleMin: Int,
    val pausaMin: Int,
    val horasOrdMin: Int,
    val observaciones: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: SyncStatus,
    val retryCount: Int,
    val lastError: String?
)
