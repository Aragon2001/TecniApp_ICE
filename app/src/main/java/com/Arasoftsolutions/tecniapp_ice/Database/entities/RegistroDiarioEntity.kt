package com.Arasoftsolutions.tecniapp_ice.Database.entities

data class RegistroDiarioEntity(
    val id: Long = 0,
    val vehiculoId: Int,
    val fecha: String,
    val valor: Double,
    val unidad: String,
    val registradoEn: Long,
    val registradoPor: String? = null,
    val syncStatus: String = "PENDING"
)
