package com.Arasoftsolutions.tecniapp_ice.pm.model.dto

data class ActivityGroupDto(
    val groupId: String,
    val ordenSap: Long,
    val ordenSapManual: String?,
    val descripcionOrden: String?,
    val regionKey: String,
    val subregionKey: String,
    val circuitoId: String?,
    val agenciaId: String?,
    val modulo: String?,
    val uidTecnico: String,
    val fechaDia: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "groupId" to groupId,
        "ordenSap" to ordenSap,
        "ordenSapManual" to ordenSapManual,
        "descripcionOrden" to descripcionOrden,
        "regionKey" to regionKey,
        "subregionKey" to subregionKey,
        "circuitoId" to circuitoId,
        "agenciaId" to agenciaId,
        "modulo" to modulo,
        "uidTecnico" to uidTecnico,
        "fechaDia" to fechaDia,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )
}
