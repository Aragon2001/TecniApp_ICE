package com.Arasoftsolutions.tecniapp_ice.ui.averias

import org.json.JSONArray
import org.json.JSONObject
import java.io.Serializable

/**
 * Representa un material utilizado en la atención de una avería.
 */
data class MaterialUso(
    val codigo: String,
    val descripcion: String,
    val cantidad: Int
) : Serializable

/**
 * Datos de acción al atender/cerrar una avería.
 */
data class AveriaActionData(
    val causa: String,
    val observaciones: String?,
    val vehiculo: String?,
    val materiales: List<MaterialUso>,
    val atendidoPorUid: String?,
    val atendidoPorNombre: String?,
    val horaInicioMillis: Long?,
    val horaFinalMillis: Long?,
    val kilometrajeInicio: Double?,
    val kilometrajeFinal: Double?
)

/**
 * Utilidad para serializar/deserializar materiales.
 */
object MaterialesSerializer {

    fun toSummary(materiales: List<MaterialUso>): String =
        materiales.filter { it.cantidad > 0 }
            .joinToString(separator = ", ") { uso ->
                val cantidad = uso.cantidad
                val desc = uso.descripcion.ifBlank { uso.codigo }
                if (cantidad <= 1) desc else "${cantidad}x $desc"
            }

    fun toJson(materiales: List<MaterialUso>): String? {
        if (materiales.isEmpty()) return null
        val array = JSONArray()
        materiales.forEach { uso ->
            if (uso.cantidad <= 0) return@forEach
            val obj = JSONObject().apply {
                put("codigo", uso.codigo)
                put("descripcion", uso.descripcion)
                put("cantidad", uso.cantidad)
            }
            array.put(obj)
        }
        return if (array.length() == 0) null else array.toString()
    }

    fun fromJson(json: String?): List<MaterialUso> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val codigo = obj.optString("codigo")
                    val descripcion = obj.optString("descripcion")
                    val cantidad = obj.optInt("cantidad", 0)
                    if (codigo.isBlank() || cantidad <= 0) continue
                    add(MaterialUso(codigo, descripcion, cantidad))
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
