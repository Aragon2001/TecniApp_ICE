package com.Arasoftsolutions.tecniapp_ice.Database.entities

data class RegistroMantenimientoEntity(
    val id: Long = 0,
    val vehiculoId: Int,
    val tipoMantenimiento: String,
    val valorActual: Double,
    val unidad: String,
    val observaciones: String?,
    val proximoMantenimiento: Double,
    val creadoEn: Long,
    val syncStatus: String = "PENDING"
)
