package com.Arasoftsolutions.tecniapp_ice.ui.vehiculo

import org.json.JSONArray
import org.json.JSONObject

data class RegistroDiarioVehiculo(
    val fecha: String,
    val valorInicial: Double,
    val valorFinal: Double? = null,
    val cerrado: Boolean = false,
    val registradoEn: Long = System.currentTimeMillis(),
    val circuito: String? = null,
    val actividad: String? = null,
    val cuenta: String? = null,
    val numeroCaso: String? = null,
    val lugar: String? = null,
    val horasLaboradas: Int? = null
) {
    val diferencia: Double?
        get() = valorFinal?.let { it - valorInicial }
}

fun parseRegistrosDiarios(json: String?): List<RegistroDiarioVehiculo> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            RegistroDiarioVehiculo(
                fecha = obj.optString("fecha"),
                valorInicial = obj.optDouble("valorInicial"),
                valorFinal = obj.optDouble("valorFinal").takeIf { !it.isNaN() },
                cerrado = obj.optBoolean("cerrado"),
                registradoEn = obj.optLong("registradoEn"),
                circuito = obj.optString("circuito").takeIf { it.isNotBlank() },
                actividad = obj.optString("actividad").takeIf { it.isNotBlank() },
                cuenta = obj.optString("cuenta").takeIf { it.isNotBlank() },
                numeroCaso = obj.optString("numeroCaso").takeIf { it.isNotBlank() },
                lugar = obj.optString("lugar").takeIf { it.isNotBlank() },
                horasLaboradas = obj.optInt("horasLaboradas").takeIf { it > 0 }
            )
        }.filter { it.fecha.isNotBlank() }
    }.getOrDefault(emptyList())
}

fun serializeRegistrosDiarios(registros: List<RegistroDiarioVehiculo>): String {
    val array = JSONArray()
    registros.forEach { registro ->
        val obj = JSONObject()
        obj.put("fecha", registro.fecha)
        obj.put("valorInicial", registro.valorInicial)
        obj.put("valorFinal", registro.valorFinal)
        obj.put("cerrado", registro.cerrado)
        obj.put("registradoEn", registro.registradoEn)
        obj.put("circuito", registro.circuito)
        obj.put("actividad", registro.actividad)
        obj.put("cuenta", registro.cuenta)
        obj.put("numeroCaso", registro.numeroCaso)
        obj.put("lugar", registro.lugar)
        obj.put("horasLaboradas", registro.horasLaboradas)
        array.put(obj)
    }
    return array.toString()
}
