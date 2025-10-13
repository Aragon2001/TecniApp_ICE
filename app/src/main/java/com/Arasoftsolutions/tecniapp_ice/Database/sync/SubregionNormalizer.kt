package com.Arasoftsolutions.tecniapp_ice.Database.sync

import java.text.Normalizer
import java.util.Locale

/**
 * Utilidad centralizada para normalizar nombres de subregión provenientes de Firebase.
 */
object SubregionNormalizer {

    private val canonicalIds = setOf(
        "S.GUAPILES",
        "S.SIQUIRRES",
        "S.LIMON.TALAMANCA"
    )

    private val equivalencias: Map<String, Set<String>> = mapOf(
        "S.GUAPILES" to setOf(
            "S.GUAPILES",
            "GUAPILES",
            "GUÁPILES",
            "SUBREGION GUAPILES",
            "SUBREGIÓN GUÁPILES",
            "S GUAPILES",
            "S-GUAPILES",
            "S_GUAPILES"
        ),
        "S.SIQUIRRES" to setOf(
            "S.SIQUIRRES",
            "SIQUIRRES",
            "S SIQUIRRES",
            "SUBREGION SIQUIRRES",
            "SUBREGIÓN SIQUIRRES"
        ),
        "S.LIMON.TALAMANCA" to setOf(
            "S.LIMON.TALAMANCA",
            "S.S.LIMON.TALAMANCA",
            "LIMON TALAMANCA",
            "LIMÓN TALAMANCA",
            "LIMON-TALAMANCA",
            "LIMÓN-TALAMANCA",
            "S LIMON TALAMANCA",
            "SUBREGION LIMON TALAMANCA",
            "SUBREGIÓN LIMON TALAMANCA"
        )
    )

    private val normalizadoACanonico: Map<String, String> = buildMap {
        equivalencias.forEach { (canonico, variaciones) ->
            (variaciones + canonico).forEach { variante ->
                normalizarCadena(variante)?.let { clave -> put(clave, canonico) }
            }
        }
        canonicalIds.forEach { canonico ->
            normalizarCadena(canonico)?.let { putIfAbsent(it, canonico) }
        }
    }

    fun canonicalIdOrNull(input: String?): String? {
        val clave = normalizarCadena(input) ?: return null
        return normalizadoACanonico[clave]
    }

    fun canonicalIdOrSelf(input: String?): String? {
        val canonical = canonicalIdOrNull(input)
        if (canonical != null) return canonical
        val trimmed = input?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return trimmed.takeIf { canonicalIds.contains(it) }
    }

    fun knownCanonicalIds(): Set<String> = canonicalIds

    private fun normalizarCadena(valor: String?): String? {
        if (valor.isNullOrBlank()) return null
        var texto = Normalizer.normalize(valor, Normalizer.Form.NFD)
        texto = texto.replace(Regex("\\p{Mn}+"), "")
        texto = texto.uppercase(Locale.getDefault()).trim()
        texto = texto.replace("SUBREGIONES", "S")
        texto = texto.replace("SUBREGION", "S")
        texto = texto.replace("SUBREGIÓN", "S")
        texto = texto.replace(Regex("[\\s_-]+"), ".")
        texto = texto.replace(Regex("[^A-Z0-9.]"), "")
        texto = texto.replace(Regex("\\.+"), ".")
        texto = texto.trim('.')
        if (texto.startsWith("S.S.")) {
            texto = texto.removePrefix("S.")
        }
        return texto.takeIf { it.isNotBlank() }
    }
}

