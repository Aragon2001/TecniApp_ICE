package com.Arasoftsolutions.tecniapp_ice.ui.averias

import java.text.Normalizer

fun normalizeAveriaText(value: String?): String {
    if (value.isNullOrBlank()) return ""
    val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        .lowercase()

    return normalized.replace("[^a-z0-9]+".toRegex(), "")
}
