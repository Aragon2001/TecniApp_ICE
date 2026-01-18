// Database/entities/UserEntity.kt
package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.PropertyName

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
    @get:PropertyName("primer_apellido") @set:PropertyName("primer_apellido")
    var primerApellido: String? = null,
    @get:PropertyName("segundo_apellido") @set:PropertyName("segundo_apellido")
    var segundoApellido: String? = null,
    var cedula: String? = null,
    var region: String? = null,
    @get:PropertyName("region_nombre") @set:PropertyName("region_nombre")
    var regionNombre: String? = null,
    var subregion: String? = null,
    @get:PropertyName("subregion_nombre") @set:PropertyName("subregion_nombre")
    var subregionNombre: String? = null,
    @get:PropertyName("agencia_id") @set:PropertyName("agencia_id")
    var agenciaId: String? = null,
    var agencia: String? = null,
    var placaVehiculo: String? = null,
    var telefono: String? = null,
    var password: String? = null,
    var fotoUrl: String? = null,
    var rol: String? = null
) {
    constructor() : this(
        uid = "",
        email = null,
        email_lower = null,
        nombre = null,
        apellidos = null,
        primerApellido = null,
        segundoApellido = null,
        cedula = null,
        region = null,
        regionNombre = null,
        subregion = null,
        subregionNombre = null,
        agenciaId = null,
        agencia = null,
        placaVehiculo = null,
        telefono = null,
        password = null,
        fotoUrl = null,
        rol = null
    )
}

val UserEntity.apellidosCompletos: String?
    get() {
        val partes = listOfNotNull(
            primerApellido?.takeIf { it.isNotBlank() },
            segundoApellido?.takeIf { it.isNotBlank() }
        )
        val combinados = partes.joinToString(" ").trim()
        return when {
            combinados.isNotEmpty() -> combinados
            else -> apellidos?.takeIf { it.isNotBlank() }
        }
    }
