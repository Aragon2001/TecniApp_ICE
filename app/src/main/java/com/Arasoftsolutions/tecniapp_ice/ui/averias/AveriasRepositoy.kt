// ui/averias/AveriasRepository.kt
package com.Arasoftsolutions.tecniapp_ice.ui.averias

import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AveriaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*

class AveriasRepository(private val db: AppDatabase) {
  private val dao get() = db.averiaDao()

  fun observe(agencias: List<String>, estado: String, q: String): Flow<List<AveriaEntity>> =
    dao.observe(agencias, agencias.size, estado, q)

  private val fmtA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
  private val fmtB = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  private fun parseMillis(s: String?): Long? {
    if (s.isNullOrBlank()) return null
    return runCatching { LocalDateTime.parse(s.trim(), fmtA) }
      .recoverCatching { LocalDateTime.parse(s.trim(), fmtB) }
      .getOrNull()
      ?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
  }

  private fun agenciaTag(region: String?, nombreAgencia: String?): String {
    val n = (nombreAgencia ?: "").lowercase(Locale.ROOT)
    val isHuetarAtlantica = (region ?: "").contains("huetar atl", true) || (region ?: "").contains("atlánt", true)
    val tag = when {
      n.contains("guápiles") || n.contains("guapiles") -> "Guapiles"
      n.contains("guácimo")  || n.contains("guacimo")  -> "Guacimo"
      n.contains("cariari")                            -> "Cariari"
      n.contains("tortuguero")                         -> "Tortuguero"
      else                                             -> if (isHuetarAtlantica) "Guapiles" else "Otra"
    }
    return tag
  }

  private fun estadoFromIce(idEstadoAve: Int?, estadoTexto: String?): String {
    // Mapa seguro (ajústalo si ICE documenta códigos distintos)
    return when (idEstadoAve) {
      1 -> "Pendiente"
      2 -> "Asignada"
      3 -> "En atención"
      4 -> "Resuelta"
      else -> estadoTexto?.ifBlank { "Pendiente" } ?: "Pendiente"
    }
  }

  private fun map(e: IceAveria): AveriaEntity? {
    val id = e.noCaso?.trim().orEmpty()
    if (id.isBlank()) return null

    val lat = e.latitud?.replace(",", ".")?.toDoubleOrNull()
    val lng = e.longitud?.replace(",", ".")?.toDoubleOrNull()
    val estado = estadoFromIce(e.idEstadoAve, e.estado)

    return AveriaEntity(
      caseId = id,
      region = e.region,
      provincia = e.provincia,
      agencia = e.agencia,
      nombreAgencia = e.nombreAgencia,
      nise = e.nise,
      causa = e.causa,
      observaciones = e.observaciones,
      estado = estado,
      idEstadoAve = e.idEstadoAve,
      idEstadoAranda = e.idEstadoAranda,
      lat = lat?.takeIf { it in -90.0..90.0 },
      lng = lng?.takeIf { it in -180.0..180.0 },
      clientesAfectados = e.clientesAfectados,
      fechaInicioMillis = parseMillis(e.fechaInicio) ?: System.currentTimeMillis(),
      horaInicioMillis = parseMillis(e.manualSalidaVehiculo),
      horaFinalMillis = parseMillis(e.horaCierreInterrupcion),
      agenciaTag = agenciaTag(e.region, e.nombreAgencia),
      vehiculoAsignado = null,
      tecnicoAsignadoUid = null,
      tecnicoAsignadoNombre = null
    )
  }

  /** Devuelve los caseId NUEVOS para notificar. */
  suspend fun syncFromIce(bearer: String?): List<String> = withContext(Dispatchers.IO) {
    val list = IceApi.service.getAverias(bearer?.takeIf { it.isNotBlank() }?.let { "Bearer $it" })
      .mapNotNull { map(it) }
      .filter { it.idEstadoAve == 1 }
    val before = dao.allIds().toSet()
    dao.upsertAll(list)
    val after = dao.allIds().toSet()
    (after - before).toList()
  }

  suspend fun asignar(caseId: String, uid: String, nombre: String?, vehiculo: String?) =
    withContext(Dispatchers.IO) {
      dao.marcarAsignada(caseId, uid, nombre, vehiculo, System.currentTimeMillis())
    }

  suspend fun enAtencion(caseId: String, causa: String?, obs: String?) =
    withContext(Dispatchers.IO) {
      dao.cerrarAveria(caseId, causa, obs, System.currentTimeMillis(), null, "En atención")
    }

  suspend fun cerrar(caseId: String, causa: String?, obs: String?) =
    withContext(Dispatchers.IO) {
      val ahora = System.currentTimeMillis()
      dao.cerrarAveria(caseId, causa, obs, ahora, ahora, "Resuelta")
    }
}
