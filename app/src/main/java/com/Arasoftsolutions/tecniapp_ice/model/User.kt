package com.Arasoftsolutions.tecniapp_ice.model

/**
 * Representa al usuario almacenado localmente y sincronizado con Firebase.
 */
data class User(
    val id: Int = 0,
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
