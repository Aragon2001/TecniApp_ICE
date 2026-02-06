package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.Arasoftsolutions.tecniapp_ice.Database.utils.SyncStatus

@Entity(
    tableName = "registro_mantenimiento",
    indices = [Index(value = ["vehiculoId"])]
)
data class RegistroMantenimientoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val vehiculoId: Int,
    val tipoMantenimiento: String,
    val valorActual: Double,
    val unidad: String,
    val observaciones: String?,
    val proximoMantenimiento: Double,
    val creadoEn: Long,
    val syncStatus: String = SyncStatus.PENDING
)
