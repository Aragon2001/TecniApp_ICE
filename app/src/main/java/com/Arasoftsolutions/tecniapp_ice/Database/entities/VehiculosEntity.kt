package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehiculos")
data class VehiculosEntity(
    @PrimaryKey val id: Int,
    val agencia: String,
    val placa: String,
    val tipo: String,
    // Subregión a la que pertenece el vehículo. Se usa para filtrar datos localmente.
    val subregion: String? = null
)
