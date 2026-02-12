package com.Arasoftsolutions.tecniapp_ice.ui.vehiculo


data class RegistroDiarioEntity(
    val vehiculoId: Int,
    val fecha: String,
    val valor: Double,
    val unidad: String = "km",
    val registradoEn: Long = System.currentTimeMillis(),
    val registradoPor: String? = null,
    val syncStatus: String = "PENDING",
)

data class RegistroMantenimientoEntity(
    val vehiculoId: Int,
    val tipoMantenimiento: String,
    val valorActual: Double,
    val unidad: String = "km",
    val observaciones: String? = null,
    val proximoMantenimiento: Double,
    val creadoEn: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING",
)
