package com.Arasoftsolutions.tecniapp_ice.pm.model.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ordenes_sap",
    indices = [
        Index(value = ["descripcion"]),
        Index(value = ["regionKey", "subregionKey"]),
        Index(value = ["agenciaId"]),
        Index(value = ["circuitoId"]),
        Index(value = ["modulo"]),
        Index(value = ["updatedAt"])
    ]
)
data class OrdenSapEntity(
    @PrimaryKey
    val ordenSap: Long,
    val descripcion: String,
    val regionKey: String,
    val subregionKey: String,
    val circuitoId: String?,
    val agenciaId: String?,
    val modulo: String?,
    val updatedAt: Long
)
