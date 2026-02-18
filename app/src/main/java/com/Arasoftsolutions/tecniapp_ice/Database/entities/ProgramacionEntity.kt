package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "programaciones",
    indices = [
        Index(value = ["vehiculoId"]),
        Index(value = ["tecnicoId"]),
        Index(value = ["subregion"]),
        Index(value = ["estado"]),
        Index(value = ["updatedAt"])
    ]
)
data class ProgramacionEntity(
    @PrimaryKey val programacionId: String,
    val vehiculoId: String,
    val placa: String,
    val localizacion: String,
    val circuito: String,
    val cuenta: String,
    val actividad: String,
    val descripcion: String?,
    val lat: Double?,
    val lng: Double?,
    val estado: String,
    val observaciones: String?,
    val fechaAsignacion: Long,
    val fechaEjecucion: Long?,
    val supervisorId: String,
    val tecnicoId: String,
    val subregion: String,
    val updatedAt: Long
)

@Entity(
    tableName = "programacion_fotos",
    foreignKeys = [
        ForeignKey(
            entity = ProgramacionEntity::class,
            parentColumns = ["programacionId"],
            childColumns = ["programacionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["programacionId"])]
)
data class ProgramacionFotoEntity(
    @PrimaryKey val fotoId: String,
    val programacionId: String,
    val url: String,
    val tipo: String
)
