package com.Arasoftsolutions.tecniapp_ice.ui.averias

import java.text.Normalizer

fun normalizeAveriaText(value: String?): String {
    if (value.isNullOrBlank()) return ""
    val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
    return normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        .lowercase()
}
