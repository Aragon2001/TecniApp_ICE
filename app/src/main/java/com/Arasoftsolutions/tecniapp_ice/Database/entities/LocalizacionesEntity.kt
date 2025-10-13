package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.firebase.database.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
@Entity(
    tableName = "localizaciones",
    indices = [
        Index(value = ["pueblo"])
    ]
)
data class LocalizacionesEntity(
    @PrimaryKey val id: Int = 0,
    val calle: Int = 0,
    val direccion: String = "",
    val latitud: Double = 0.0,
    val longitud: Double = 0.0,
    val pueblo: Int = 0,
    val alPoste: Int = 0,
    val delPoste: Int = 0,
    val subregion: String? = null
) {
    constructor() : this(
        id = 0,
        calle = 0,
        direccion = "",
        latitud = 0.0,
        longitud = 0.0,
        pueblo = 0,
        alPoste = 0,
        delPoste = 0,
        subregion = null
    )
}
