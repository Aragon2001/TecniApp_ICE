package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.firebase.database.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
@Entity(tableName = "vehiculo")
data class VehiculoEntity(
    @PrimaryKey
    val vehiculoId: String,
    val placaRaw: String = "",
    val subregion: String? = null,
    val tipo: String = "",
    val agencia: String = "",
    val kmActual: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis(),
    // Legacy compatibility fields (kept for current module integration)
    val placa: Long = 0,

    val orimetroActual: Double? = null,
    val id: Int = 0,
    val registroFecha: String? = null,
    val registroInicial: Double? = null,
    val registroFinal: Double? = null,
    val registroCerrado: Boolean = false,
    val registrosDiariosJson: String? = null,
    val mantenimientoUltimo: String? = null,
    val mantenimientoProximo: String? = null,
) {


    constructor() : this(
        vehiculoId = "",
        placaRaw = "",
        subregion = null,
        tipo = "",
        agencia = "",
        kmActual = 0.0,
        updatedAt = 0L
    )
}

typealias VehiculosEntity = VehiculoEntity
