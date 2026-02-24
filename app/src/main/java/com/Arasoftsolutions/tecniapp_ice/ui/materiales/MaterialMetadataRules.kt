package com.Arasoftsolutions.tecniapp_ice.ui.materiales

import java.util.Locale

object MaterialMetadataRules {
    fun requiresSealNumber(codigo: String, descripcion: String): Boolean {
        val texto = "$codigo $descripcion".lowercase(Locale.getDefault())
        return texto.contains("sello")
    }
}
