package com.Arasoftsolutions.tecniapp_ice.ui.averias

data class AveriaDraft(
    val localizacion: String?,
    val causa: String?,
    val observaciones: String?,
    val vehiculo: String?,
    val horaLlegada: String?,
    val horaInicio: String?,
    val horaFinal: String?,
    val kmLlegada: String?,
    val kmInicio: String?,
    val kmFinal: String?,
    val tipoAfectacion: TipoAfectacion,
    val numeroMedidor: String?,
    val cliente: String?,
    val materiales: List<MaterialUso>,
    val tecnicos: List<TecnicoAtencion>
) {
    fun isEmpty(): Boolean {
        return localizacion.isNullOrBlank() &&
            causa.isNullOrBlank() &&
            observaciones.isNullOrBlank() &&
            vehiculo.isNullOrBlank() &&
            horaLlegada.isNullOrBlank() &&
            horaInicio.isNullOrBlank() &&
            horaFinal.isNullOrBlank() &&
            kmLlegada.isNullOrBlank() &&
            kmInicio.isNullOrBlank() &&
            kmFinal.isNullOrBlank() &&
            numeroMedidor.isNullOrBlank() &&
            cliente.isNullOrBlank() &&
            materiales.isEmpty() &&
            tecnicos.isEmpty()
    }
}
