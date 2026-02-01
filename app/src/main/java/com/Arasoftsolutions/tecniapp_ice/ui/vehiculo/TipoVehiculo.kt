package com.Arasoftsolutions.tecniapp_ice.ui.vehiculo

/**
 * Tipo de vehículo para control ETM.
 * Define si se usa kilometraje (km) u orímetro (horas).
 */
enum class TipoVehiculo(val usaKilometraje: Boolean, val usaOrimetro: Boolean) {
    /** Vehículo liviano: control por km */
    LIVIANO(usaKilometraje = true, usaOrimetro = false),

    /** Camión o grúa: control por km */
    CAMION_GRUA(usaKilometraje = true, usaOrimetro = false),

    /** Maquinaria pesada: control por orímetro (horas) */
    MAQUINARIA_PESADA(usaKilometraje = false, usaOrimetro = true);

    val unidadTexto: String get() = if (usaKilometraje) "km" else "horas"
}

/**
 * Mapea el campo `tipo` de la BD (ej. "Grua Pequeña", "Doble Canasta")
 * al TipoVehiculo del sistema.
 */
fun inferirTipoVehiculo(tipoRaw: String?): TipoVehiculo {
    val t = tipoRaw?.trim()?.lowercase().orEmpty()
    if (t.isEmpty()) return TipoVehiculo.LIVIANO

    val maquinariaPesada = listOf(
        "retro", "excavador", "bulldozer", "tractor", "cargador",
        "maquinaria", "pesada", "orimetro", "horómetro"
    )
    if (maquinariaPesada.any { t.contains(it) }) return TipoVehiculo.MAQUINARIA_PESADA

    val camionGrua = listOf("grua", "grúa", "canasta", "camion", "camión")
    if (camionGrua.any { t.contains(it) }) return TipoVehiculo.CAMION_GRUA

    return TipoVehiculo.LIVIANO
}
