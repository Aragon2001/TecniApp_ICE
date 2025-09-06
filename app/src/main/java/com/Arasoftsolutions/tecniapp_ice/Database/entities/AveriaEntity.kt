package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.room.*

// Database/entities/AveriaEntity.kt
@Entity(
  tableName = "averias",
  indices = [
    Index(value = ["caseId"], unique = true),
    Index(value = ["estado"]),
    Index(value = ["agenciaTag"]),
    Index(value = ["fechaInicioMillis"]) // nuevo índice sugerido
  ]
)

data class AveriaEntity(
  @PrimaryKey val caseId: String,      // ICE: noCaso
  val region: String?,                 // ICE: region
  val provincia: Int?,                 // ICE: provincia
  val agencia: String?,                // ICE: agencia
  val nombreAgencia: String?,          // ICE: nombreAgencia
  val nise: String?,                   // ICE: nise
  val causa: String?,                  // ICE: causa
  val observaciones: String?,          // ICE: observaciones
  val estado: String,                  // ICE: estado (ej. "Aceptado")
  val idEstadoAve: Int?,               // ICE: idEstadoAve
  val idEstadoAranda: Int?,            // ICE: idEstadoAranda
  val lat: Double?,                    // ICE: latitud
  val lng: Double?,                    // ICE: longitud
  val clientesAfectados: String?,      // ICE: clientesAfectados
  val fechaInicioMillis: Long,         // ICE: fechaInicio → epoch
  val horaInicioMillis: Long?,         // ICE: manualSalidaVehiculo → epoch
  val horaFinalMillis: Long?,          // ICE: horaCierreInterrupcion → epoch
  val agenciaTag: String,              // Normalizado: Guapiles|Guacimo|Cariari|Otra
  // Operación al asignar/atender:
  val vehiculoAsignado: String?,
  val tecnicoAsignadoUid: String?,
  val tecnicoAsignadoNombre: String?
)
