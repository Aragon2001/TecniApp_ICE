package com.Arasoftsolutions.tecniapp_ice.ui.vehiculo

import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculosEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ─────────────────────────────────────────────────────────────────────────────
// CORRECCIONES aplicadas en este archivo:
//
// FIX-4: obtenerEstadoEtmVehiculo() ahora lee los registros desde
//        vehiculo_log (fuente canónica) a través del repositorio,
//        en lugar de parsear directamente registrosDiariosJson.
//
//        Flujo anterior (problemático):
//          VehiculoEntity.registrosDiariosJson → parseRegistrosDiarios()
//          → solo visible para quien escribió ese JSON (RegistroVehiculoPendienteDialogExt)
//
//        Flujo corregido:
//          vehiculo_log → repository.obtenerRegistrosDiariosSnapshot()
//          → única fuente de verdad, la misma que lee el ViewModel.
//
//        Se mantiene un fallback a registrosDiariosJson para compatibilidad
//        con vehículos que aún no han migrado todos sus registros a vehicle_log.
// ─────────────────────────────────────────────────────────────────────────────

data class EtmEstadoVehiculo(
    val vehiculo: VehiculosEntity?,
    val registroPendienteCierre: RegistroDiarioVehiculo?,
    val tieneRegistroHoy: Boolean
)

suspend fun RoomRepository.obtenerEstadoEtmVehiculo(uid: String): EtmEstadoVehiculo {
    val usuario  = obtenerUsuario(uid)
    val placa    = usuario?.placaVehiculo?.trim().orEmpty()
    val placaLong = VehiculoPlacaUtils.parsePlacaLong(placa)
    val vehiculo  = placaLong?.let { obtenerVehiculoPorPlaca(it) }

    if (vehiculo == null) return EtmEstadoVehiculo(null, null, false)

    val hoy      = LocalDate.now().format(DateTimeFormatter.ISO_DATE)

    // FIX-4: leer desde vehiculo_log como fuente canónica
    val registrosDeLog = obtenerRegistrosDiariosSnapshot(vehiculo.vehiculoId)

    // Convertir RegistroDiarioEntity → RegistroDiarioVehiculo para mantener
    // la interfaz de EtmEstadoVehiculo sin romper el resto del código.
    val registrosEtm: List<RegistroDiarioVehiculo> = if (registrosDeLog.isNotEmpty()) {
        registrosDeLog.map { entity ->
            RegistroDiarioVehiculo(
                fecha          = entity.fecha,
                valorInicial   = entity.valor,
                valorFinal     = entity.kmFinal,
                cerrado        = entity.cerrado,
                registradoEn   = entity.registradoEn,
                registradoPor  = entity.registradoPor,
                combustible    = entity.combustible,
                observaciones  = entity.observaciones,
                actividad      = entity.actividad,
                cuenta         = entity.cuenta,
                numeroCaso     = entity.numeroCaso,
                lugar          = entity.lugar,
                horasLaboradas = entity.horasLaboradas
            )
        }
    } else {
        // Fallback: si vehiculo_log está vacío (instalación antigua sin migrar),
        // usar el blob registrosDiariosJson para no quedar sin datos.
        parseRegistrosDiarios(vehiculo.registrosDiariosJson)
    }

    val registroPendiente = registrosEtm
        .filter { !it.cerrado && it.fecha.isNotBlank() && it.fecha < hoy }
        .maxByOrNull { it.fecha }

    val tieneRegistroHoy = registrosEtm.any { it.fecha == hoy }

    return EtmEstadoVehiculo(vehiculo, registroPendiente, tieneRegistroHoy)
}

/**
 * Obtiene una snapshot (no-Flow) de los registros diarios del vehículo
 * desde vehiculo_log, ordenados por fecha descendente.
 */
suspend fun RoomRepository.obtenerRegistrosDiariosSnapshot(
    vehiculoId: String
): List<RegistroDiarioEntity> {
    // Usa la función que ya existe en VehiculoLogDao para el último registro,
    // pero aquí necesitamos todos. Delegamos al log para obtener la lista completa.
    return obtenerTodosRegistrosDiarios(vehiculoId)
}