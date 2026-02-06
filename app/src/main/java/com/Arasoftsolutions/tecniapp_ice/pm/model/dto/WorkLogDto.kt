package com.Arasoftsolutions.tecniapp_ice.pm.model.dto

data class WorkLogDto(
    val logId: String,
    val groupId: String,
    val ordenSap: Long,
    val ordenSapManual: String?,
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
    val horasOrdMin: Int,
    val observaciones: String?,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "logId" to logId,
        "groupId" to groupId,
        "ordenSap" to ordenSap,
        "ordenSapManual" to ordenSapManual,
        "regionKey" to regionKey,
        "subregionKey" to subregionKey,
        "circuitoId" to circuitoId,
        "agenciaId" to agenciaId,
        "modulo" to modulo,
        "uidTecnico" to uidTecnico,
        "fechaDia" to fechaDia,
        "horaEntraMin" to horaEntraMin,
        "horaSaleMin" to horaSaleMin,
        "pausaMin" to pausaMin,
        "horasOrdMin" to horasOrdMin,
        "observaciones" to observaciones,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )
}
