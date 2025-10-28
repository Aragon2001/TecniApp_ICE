package com.Arasoftsolutions.tecniapp_ice.ui.averias

import java.io.Serializable

/**
 * Representación de una avería lista para mostrarse en la UI.
 */
data class AveriaUI(
    val id: String,
    val descripcion: String,
    val fechaMillis: Long,
    val causa: String,
    val estado: String,
    val tecnico: String,
    val tecnicoUid: String?,
    val atendidoPor: String,
    val atendidoPorUid: String?,
    val observaciones: String,
    val nise: String,
    val agencia: String,
    val region: String,
    val zonaTag: String?,
    val lat: Double,
    val lng: Double,
    val vehiculo: String?,
    val materialesResumen: String,
    val materialesDetalle: List<MaterialUso>,
    val horaAtencionInicio: Long?,
    val horaAtencionFinal: Long?,
    val kilometrajeInicio: Double?,
    val kilometrajeFinal: Double?,
    val horaInicio: Long?,
    val horaFinal: Long?,
    val cliente: String?,
    val localizacion: String?,
    val direccion: String?,
    val tecnicosAtendieron: List<TecnicoAtencion>,
    val tipoAfectacion: TipoAfectacion,
    val numeroMedidor: String?,
    val medidorCalle: String?,
    val medidorPueblo: String?,
    val medidorMetros: String?,
    val medidorPoste: String?,
) : Serializable {

    fun resolvedAtendidoDisplay(emptyValue: String): String {
        if (atendidoPor.isNotBlank()) return atendidoPor
        val tecnicos = resolvedTechniciansDisplay()
        return if (tecnicos.isNotEmpty()) tecnicos.joinToString(", ") else emptyValue
    }

    fun resolvedAtendidoLines(emptyValue: String): List<String> {
        if (atendidoPor.isNotBlank()) return listOf(atendidoPor)
        val tecnicos = resolvedTechniciansDisplay()
        return if (tecnicos.isNotEmpty()) tecnicos else listOf(emptyValue)
    }

    fun resolvedTechniciansDisplay(): List<String> = tecnicosAtendieron
        .mapNotNull { tecnico ->
            val nombre = tecnico.nombre.trim()
            val cedula = tecnico.cedula.trim()
            when {
                nombre.isNotBlank() && cedula.isNotBlank() -> "$nombre ($cedula)"
                nombre.isNotBlank() -> nombre
                cedula.isNotBlank() -> cedula
                else -> null
            }
        }
}
