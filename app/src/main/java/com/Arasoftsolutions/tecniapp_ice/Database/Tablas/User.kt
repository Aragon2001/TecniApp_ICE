package com.Arasoftsolutions.tecniapp_ice.Database.Tablas

data class User(
    val id: Int,
    val agencia: String,
    val apellidos: String,
    val cedula: String,  // Asegurado como Long
    val email: String,
    val nombre: String,
    val placaVehiculo: String,  // Asegurado como Long
    val subregion: String,
    val telefono: String // Asegurado como Long
)

