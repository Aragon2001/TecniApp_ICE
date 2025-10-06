// Database/entities/UserEntity.kt
package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.firebase.database.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
@Entity(
    tableName = "usuarios",
    indices = [
        Index(value = ["email_lower"]),
        Index(value = ["cedula"]),
        Index(value = ["telefono"])
    ]
)
data class UserEntity(
    @PrimaryKey var uid: String = "",
    var email: String? = null,
    var email_lower: String? = null,
    var nombre: String? = null,
    var apellidos: String? = null,
    var cedula: String? = null,
    var subregion: String? = null,
    var agencia: String? = null,
    var placaVehiculo: String? = null,
    var telefono: String? = null,
    var password: String? = null,
    var fotoUrl: String? = null
) {
    constructor() : this(
        uid = "",
        email = null,
        email_lower = null,
        nombre = null,
        apellidos = null,
        cedula = null,
        subregion = null,
        agencia = null,
        placaVehiculo = null,
        telefono = null,
        password = null,
        fotoUrl = null
    )
}

