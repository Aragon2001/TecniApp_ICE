package com.Arasoftsolutions.tecniapp_ice.ui.luminarias

import org.json.JSONArray
import org.json.JSONObject

data class LuminariaMaterialUso(
    val codigo: String,
    val descripcion: String,
    val cantidad: Double,
    val selloNumero: String? = null
)

object LuminariaMaterialSerializer {

    fun toJson(materiales: List<LuminariaMaterialUso>): String? {
        if (materiales.isEmpty()) return null
        val array = JSONArray()
        materiales.forEach { uso ->
            if (uso.cantidad <= 0) return@forEach
            val obj = JSONObject().apply {
                put("codigo", uso.codigo)
                put("descripcion", uso.descripcion)
                put("cantidad", uso.cantidad)
                uso.selloNumero?.takeIf { it.isNotBlank() }?.let { put("selloNumero", it) }
            }
            array.put(obj)
        }
        return if (array.length() == 0) null else array.toString()
    }

    fun fromJson(json: String?): List<LuminariaMaterialUso> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val codigo = obj.optString("codigo")
                    val descripcion = obj.optString("descripcion")
                    val cantidad = obj.optDouble("cantidad", 0.0)
                    val selloNumero = obj.optString("selloNumero").takeIf { it.isNotBlank() }
                    if (codigo.isNotBlank() && cantidad > 0) {
                        add(LuminariaMaterialUso(codigo, descripcion, cantidad, selloNumero))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun toSummary(materiales: List<LuminariaMaterialUso>): String =
        materiales.filter { it.cantidad > 0 }
            .joinToString(separator = ", ") { uso ->
                val base = uso.descripcion.ifBlank { uso.codigo }
                val baseConDetalle = uso.selloNumero?.takeIf { it.isNotBlank() }?.let { "$base (Sello: $it)" } ?: base
                val etiqueta = if (uso.cantidad % 1.0 == 0.0) {
                    val cantidad = uso.cantidad.toInt()
                    if (cantidad <= 1) baseConDetalle else "${cantidad}x $baseConDetalle"
                } else {
                    "${uso.cantidad}x $baseConDetalle"
                }
                etiqueta
            }
}
