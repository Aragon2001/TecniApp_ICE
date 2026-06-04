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
        Index(value = ["updatedAt"]),
        Index(value = ["eliminada"]),
        Index(value = ["tecnicoPrincipalUid"]),
        Index(value = ["agenciaTag"])
    ]
)
data class ProgramacionEntity(
    // === Campos originales (se mantienen sin cambio) ===
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
    val updatedAt: Long,

    // === Campos nuevos — Planificación (supervisor) ===
    val localizacionOriginal: String? = null,
    val localizacionNormalizada: String = "",
    val agenciaTag: String = "",
    val cuadrillaAsignada: String? = null,
    val supervisorNombre: String? = null,
    val fotosSupervisorJson: String? = null,

    // === Campos nuevos — Atención (técnico) ===
    val tecnicoPrincipalUid: String? = null,
    val tecnicoPrincipalNombre: String? = null,
    val tecnicosAtendieronJson: String? = null,
    val descripcionAtencion: String? = null,
    val fotosAtencionJson: String? = null,
    val gastoMateriales: Boolean = false,
    val materialesJson: String? = null,

    // === Campos nuevos — Fechas de atención ===
    val fechaInicioAtencion: Long? = null,
    val fechaFinalizacion: Long? = null,

    // === Campos nuevos — Reapertura ===
    val reabierta: Boolean = false,
    val fechaReapertura: Long? = null,
    val reabiertaPorUid: String? = null,
    val motivoReapertura: String? = null,

    // === Campos nuevos — Cancelación ===
    val canceladaPorUid: String? = null,
    val fechaCancelacion: Long? = null,
    val motivoCancelacion: String? = null,

    // === Campos nuevos — Eliminación lógica ===
    val eliminada: Boolean = false,
    val eliminadaPorUid: String? = null,
    val fechaEliminacion: Long? = null,
    val motivoEliminacion: String? = null,

    // === Campos nuevos — Sincronización ===
    val syncState: String = "PENDING"
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
