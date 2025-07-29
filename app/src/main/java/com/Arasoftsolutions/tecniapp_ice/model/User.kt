package com.Arasoftsolutions.tecniapp_ice.model

data class User(
    val id: Int,
    val agencia: String,
    val apellidos: String,
    val cedula: String,
    val email: String,
    val nombre: String,
    val placaVehiculo: String,
    val subregion: String,
    val telefono: String,
    val password: String = ""
)
