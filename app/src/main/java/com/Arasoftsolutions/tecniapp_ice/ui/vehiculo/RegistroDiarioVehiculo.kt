package com.Arasoftsolutions.tecniapp_ice.ui.vehiculo

import org.json.JSONArray
import org.json.JSONObject

data class RegistroDiarioVehiculo(
    val fecha: String,
    val valorInicial: Double,
    val valorFinal: Double? = null,
    val cerrado: Boolean = false,
    val registradoEn: Long = System.currentTimeMillis()
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
                registradoEn = obj.optLong("registradoEn")
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
        array.put(obj)
    }
    return array.toString()
}
