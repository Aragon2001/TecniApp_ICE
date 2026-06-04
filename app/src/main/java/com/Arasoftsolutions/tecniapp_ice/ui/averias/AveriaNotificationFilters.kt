package com.Arasoftsolutions.tecniapp_ice.ui.averias

import com.Arasoftsolutions.tecniapp_ice.Database.entities.AveriaEntity

fun filterAveriasByRegion(
    averias: List<AveriaEntity>,
    normalizedRegion: String?
): List<AveriaEntity> {
    if (normalizedRegion.isNullOrBlank()) return averias
    return averias.filter { averia ->
        val regionAveria = normalizeAveriaText(averia.region)
        regionAveria.contains(normalizedRegion)
    }
}

fun filterAveriasByAgencies(
    averias: List<AveriaEntity>,
    normalizedAgencies: Set<String>
): List<AveriaEntity> {
    if (normalizedAgencies.isEmpty()) return averias
    return averias.filter { averia -> shouldNotifyForAgency(averia, normalizedAgencies) }
}

fun shouldNotifyForAgency(averia: AveriaEntity, filters: Set<String>): Boolean {
    if (filters.isEmpty()) return true
    val candidatos = listOfNotNull(
        averia.agencia,
        averia.nombreAgencia,
        averia.agenciaTag
    ).map { normalizeAveriaText(it) }
    if (candidatos.isEmpty()) return false
    return candidatos.any { agency ->
        filters.any { filter -> agency.contains(filter) || filter.contains(agency) }
    }
}