package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usuarios")
data class UserEntity(
    /**
     * Identificador único del usuario en Firebase Auth.
     * Se usa como clave primaria local para poder consultar por UID.
     */
    @PrimaryKey val uid: String,
    val agencia: String = "",
    val apellidos: String = "",
    val cedula: String = "",
    val email: String = "",
    val nombre: String = "",
    val placaVehiculo: String = "",
    val subregion: String = "",
    val telefono: String = "",
    val password: String = ""
)
