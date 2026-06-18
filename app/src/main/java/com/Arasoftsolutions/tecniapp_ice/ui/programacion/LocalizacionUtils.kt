package com.Arasoftsolutions.tecniapp_ice.ui.programacion

object LocalizacionUtils {

    fun validarYNormalizar(input: String): Result<String> {
        val limpia = input.filter { it.isDigit() }
        return when (limpia.length) {
            9 -> Result.success("0${limpia}00")
            10 -> Result.success("${limpia}00")
            12 -> Result.success(limpia)
            11 -> Result.failure(
                IllegalArgumentException(
                    "La localización no puede tener 11 dígitos. Debe ingresar 9, 10 o 12 dígitos."
                )
            )
            else -> Result.failure(
                IllegalArgumentException(
                    "La localización debe tener 9, 10 o 12 dígitos."
                )
            )
        }
    }

    fun formatearParaMostrar(normalizada: String): String {
        if (normalizada.length != 12) return normalizada
        return "${normalizada.substring(0, 4)}-${normalizada.substring(4, 7)}-${normalizada.substring(7, 10)}-${normalizada.substring(10)}"
    }
}
