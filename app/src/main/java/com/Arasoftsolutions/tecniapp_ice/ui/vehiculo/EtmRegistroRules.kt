package com.Arasoftsolutions.tecniapp_ice.ui.vehiculo

import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculosEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class EtmEstadoVehiculo(
    val vehiculo: VehiculosEntity?,
    val registroPendienteCierre: RegistroDiarioVehiculo?,
    val tieneRegistroHoy: Boolean
)

suspend fun RoomRepository.obtenerEstadoEtmVehiculo(uid: String): EtmEstadoVehiculo {
    val usuario = obtenerUsuario(uid)
    val placa = usuario?.placaVehiculo?.trim().orEmpty()
    val placaLong = VehiculoPlacaUtils.parsePlacaLong(placa)
    val vehiculo = placaLong?.let { obtenerVehiculoPorPlaca(it) }
    if (vehiculo == null) {
        return EtmEstadoVehiculo(null, null, false)
    }

    val hoy = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
    val registros = parseRegistrosDiarios(vehiculo.registrosDiariosJson)
    val registroPendiente = registros
        .filter { !it.cerrado && it.fecha.isNotBlank() && it.fecha < hoy }
        .maxByOrNull { it.fecha }
    val tieneRegistroHoy = registros.any { it.fecha == hoy }
    return EtmEstadoVehiculo(vehiculo, registroPendiente, tieneRegistroHoy)
}

