package com.Arasoftsolutions.tecniapp_ice.ui.averias

import org.json.JSONArray
import org.json.JSONObject
import java.io.Serializable

/**
 * Representa un material utilizado en la atención de una avería.
 */
data class MedidorInstalacion(
    val numero: String?,
    val lectura: String?
) : Serializable

data class MaterialUso(
    val codigo: String,
    val descripcion: String,
    val cantidad: Int,
    val medidorInstalado: MedidorInstalacion? = null,
    val selloNumero: String? = null
) : Serializable

data class TecnicoAtencion(
    val cedula: String,
    val nombre: String
) : Serializable

data class EvidenciaFoto(
    val uri: String,
    val fuente: String
) : Serializable

enum class TipoAfectacion : Serializable {
    SECTOR,
    CLIENTE;

    companion object {
        fun fromRaw(raw: String?): TipoAfectacion = when (raw?.trim()?.lowercase()) {
            "cliente" -> CLIENTE
            else -> SECTOR
        }

        fun toRaw(tipo: TipoAfectacion?): String? = tipo?.name
    }
}

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
    val horaLlegadaMillis: Long?,
    val kilometrajeInicio: Double?,
    val kilometrajeLlegada: Double?,
    val kilometrajeFinal: Double?,
    val cliente: String?,
    val localizacion: String?,
    val tecnicos: List<TecnicoAtencion>,
    val tipoAfectacion: TipoAfectacion,
    val numeroMedidor: String?,
    val medidorCalle: String?,
    val medidorPueblo: String?,
    val medidorMetros: String?,
    val medidorPoste: String?,
    val evidencias: List<EvidenciaFoto>
)

/**
 * Utilidad para serializar/deserializar materiales.
 */
object MaterialesSerializer {

    private fun parseLecturas(raw: String?): Pair<String?, String?> {
        if (raw.isNullOrBlank()) return null to null
        val parts = raw.split("|", limit = 2)
        val nueva = parts.getOrNull(0)?.takeIf { it.isNotBlank() }
        val anterior = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
        return nueva to anterior
    }

    fun toSummary(materiales: List<MaterialUso>): String =
        materiales.filter { it.cantidad > 0 }
            .joinToString(separator = ", ") { uso ->
                val cantidad = uso.cantidad
                val base = uso.descripcion.ifBlank { uso.codigo }
                val detalles = buildList {
                    uso.selloNumero?.takeIf { it.isNotBlank() }?.let { add("Sello: $it") }
                    uso.medidorInstalado?.let { meta ->
                        meta.numero?.takeIf { it.isNotBlank() }?.let { add("N° $it") }
                        val (lecturaNueva, lecturaAnterior) = parseLecturas(meta.lectura)
                        lecturaNueva?.let { add("Lectura $it") }
                        lecturaAnterior?.let { add("Lectura anterior $it") }
                    }
                }.takeIf { it.isNotEmpty() }
                val etiqueta = if (!detalles.isNullOrEmpty()) {
                    "$base (${detalles.joinToString(" • ")})"
                } else {
                    base
                }
                if (cantidad <= 1) etiqueta else "${cantidad}x $etiqueta"
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
                uso.medidorInstalado?.let { meta ->
                    val metaObj = JSONObject().apply {
                        put("numero", meta.numero)
                        put("lectura", meta.lectura)
                    }
                    put("medidorInstalado", metaObj)
                }
                uso.selloNumero?.takeIf { it.isNotBlank() }?.let {
                    put("selloNumero", it)
                }
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
                    val meta = obj.optJSONObject("medidorInstalado")?.let { metaObj ->
                        val numero = metaObj.optString("numero").takeIf { it.isNotBlank() }
                        val lectura = metaObj.optString("lectura").takeIf { it.isNotBlank() }
                        if (numero != null || lectura != null) {
                            MedidorInstalacion(numero, lectura)
                        } else {
                            null
                        }
                    }
                    val selloNumero = obj.optString("selloNumero").takeIf { it.isNotBlank() }
                    if (codigo.isBlank() || cantidad <= 0) continue
                    add(MaterialUso(codigo, descripcion, cantidad, meta, selloNumero))
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}

object TecnicosSerializer {

    fun toJson(tecnicos: List<TecnicoAtencion>): String? {
        if (tecnicos.isEmpty()) return null
        val array = JSONArray()
        tecnicos.forEach { tecnico ->
            if (tecnico.cedula.isBlank() && tecnico.nombre.isBlank()) return@forEach
            val obj = JSONObject().apply {
                put("cedula", tecnico.cedula)
                put("nombre", tecnico.nombre)
            }
            array.put(obj)
        }
        return if (array.length() == 0) null else array.toString()
    }

    fun fromJson(json: String?): List<TecnicoAtencion> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val cedula = obj.optString("cedula")
                    val nombre = obj.optString("nombre")
                    if (cedula.isBlank() && nombre.isBlank()) continue
                    add(TecnicoAtencion(cedula, nombre))
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}

object EvidenciasSerializer {
    fun toJson(evidencias: List<EvidenciaFoto>): String? {
        if (evidencias.isEmpty()) return null
        val array = JSONArray()
        evidencias.forEach { evidencia ->
            val uri = evidencia.uri.trim()
            if (uri.isBlank()) return@forEach
            array.put(
                JSONObject().apply {
                    put("uri", uri)
                    put("fuente", evidencia.fuente)
                }
            )
        }
        return if (array.length() == 0) null else array.toString()
    }

    fun fromJson(json: String?): List<EvidenciaFoto> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val uri = obj.optString("uri").trim()
                    if (uri.isBlank()) continue
                    add(
                        EvidenciaFoto(
                            uri = uri,
                            fuente = obj.optString("fuente").ifBlank { "galeria" }
                        )
                    )
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
