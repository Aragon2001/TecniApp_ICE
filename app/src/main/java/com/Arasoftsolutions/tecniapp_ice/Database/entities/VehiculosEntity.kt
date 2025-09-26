package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.database.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
@Entity(tableName = "vehiculos")
data class VehiculosEntity(
    @PrimaryKey val id: Int = 0,
    val agencia: String = "",
    val placa: Long = 0,
    val tipo: String = "",
    val subregion: String? = null
) {
    constructor() : this(
        id = 0,
        agencia = "",
        placa = 0,
        tipo = "",
        subregion = null
    )
}
