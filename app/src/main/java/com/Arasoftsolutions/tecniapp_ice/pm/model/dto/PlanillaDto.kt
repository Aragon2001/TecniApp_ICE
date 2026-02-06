package com.Arasoftsolutions.tecniapp_ice.pm.model.dto

data class PlanillaDto(
    val planillaId: String,
    val fechaDia: String,
    val uidTecnico: String,
    val regionKey: String,
    val subregionKey: String,
    val mode: String,
    val totalHorasMin: Int,
    val logIds: List<String>,
    val pdfRemoteUrl: String?,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "planillaId" to planillaId,
        "fechaDia" to fechaDia,
        "uidTecnico" to uidTecnico,
        "regionKey" to regionKey,
        "subregionKey" to subregionKey,
        "mode" to mode,
        "totalHorasMin" to totalHorasMin,
        "logIds" to logIds,
        "pdfRemoteUrl" to pdfRemoteUrl,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )
}
