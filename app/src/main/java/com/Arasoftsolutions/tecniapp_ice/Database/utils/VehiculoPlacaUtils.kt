package com.Arasoftsolutions.tecniapp_ice.Database.utils

object VehiculoPlacaUtils {
    fun parsePlacaLong(raw: String?): Long? {
        val digits = raw?.filter { it.isDigit() }.orEmpty()
        return digits.takeIf { it.isNotBlank() }?.toLongOrNull()
    }
}
