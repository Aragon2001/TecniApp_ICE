package com.Arasoftsolutions.tecniapp_ice.ui.vehiculo

object VehiculoPlacaUtils {
    fun parsePlacaLong(raw: String?): Long? {
        val digits = raw?.filter { it.isDigit() }.orEmpty()
        return digits.takeIf { it.isNotBlank() }?.toLongOrNull()
    }
}
