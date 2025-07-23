package com.Arasoftsolutions.tecniapp_ice.Database.Tablas

data class Localizaciones(
    val id: Int,
    val calle: Int,
    val direccion: String,
    val latitud: Double,
    val longitud: Double,
    val pueblo: Int,
    val alPoste: Int,
    val delPoste: Int
)
