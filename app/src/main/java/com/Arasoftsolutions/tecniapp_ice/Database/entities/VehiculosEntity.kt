package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.database.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
@Entity(tableName = "vehiculos")
data class VehiculosEntity(
    @PrimaryKey
    val vehiculoId: String,
    val placaRaw: String = "",
    val agencia: String = "",
    val placa: Long = 0,
    val tipo: String = "",
    val subregion: String? = null,
    val kilometrajeActual: Double? = null,
    val orimetroActual: Double? = null,
    val updatedAt: Long = System.currentTimeMillis(),
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
        agencia = "",
        placa = 0,
        tipo = "",
        subregion = null,
        kilometrajeActual = null,
        orimetroActual = null,
        updatedAt = 0L,
        id = 0,
        registroFecha = null,
        registroInicial = null,
        registroFinal = null,
        registroCerrado = false,
        registrosDiariosJson = null,
        mantenimientoUltimo = null,
        mantenimientoProximo = null
    )
}
