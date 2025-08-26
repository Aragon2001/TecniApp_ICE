package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "localizaciones")
data class LocalizacionesEntity(
    @PrimaryKey val id: Int,
    val calle: Int,
    val direccion: String,
    val latitud: Double,
    val longitud: Double,
    val pueblo: Int,
    val alPoste: Int,
    val delPoste: Int,
    // Subregión asociada a la localización.
    val subregion: String? = null
)
