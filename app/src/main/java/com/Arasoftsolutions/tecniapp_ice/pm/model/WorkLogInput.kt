package com.Arasoftsolutions.tecniapp_ice.pm.model

data class WorkLogInput(
    val groupId: String,
    val ordenSap: String?,
    val ordenSapManual: String?,
    val descripcionOrden: String?,
    val regionKey: String,
    val subregionKey: String,
    val circuitoId: String?,
    val agenciaId: String?,
    val modulo: String?,
    val uidTecnico: String,
    val fechaDia: String,
    val horaEntraMin: Int,
    val horaSaleMin: Int,
    val pausaMin: Int,
    val observaciones: String?
)
