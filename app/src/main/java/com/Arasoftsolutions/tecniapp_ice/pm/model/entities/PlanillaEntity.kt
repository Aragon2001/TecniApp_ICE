package com.Arasoftsolutions.tecniapp_ice.pm.model.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.Arasoftsolutions.tecniapp_ice.pm.model.SyncStatus

@Entity(
    tableName = "pm_planillas",
    indices = [
        Index(value = ["fechaDia"]),
        Index(value = ["uidTecnico"]),
        Index(value = ["regionKey", "subregionKey"]),
        Index(value = ["mode"]),
        Index(value = ["syncStatus"])
    ]
)
data class PlanillaEntity(
    @PrimaryKey
    val planillaId: String,
    val fechaDia: String,
    val uidTecnico: String,
    val regionKey: String,
    val subregionKey: String,
    val mode: String,
    val totalHorasMin: Int,
    val logIdsCsv: String,
    val pdfLocalPath: String,
    val pdfRemoteUrl: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: SyncStatus,
    val retryCount: Int,
    val lastError: String?
)
