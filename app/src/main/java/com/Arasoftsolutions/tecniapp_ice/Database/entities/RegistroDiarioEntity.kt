package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.Arasoftsolutions.tecniapp_ice.Database.utils.SyncStatus

@Entity(
    tableName = "registro_diario",
    indices = [
        Index(value = ["vehiculoId", "fecha"], unique = true),
        Index(value = ["vehiculoId"])
    ]
)
data class RegistroDiarioEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val vehiculoId: Int,
    val fecha: String,
    val valor: Double,
    val unidad: String,
    val registradoEn: Long,
    val registradoPor: String? = null,
    val syncStatus: String = SyncStatus.PENDING
)
