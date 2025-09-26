package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.util.Log
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AveriaEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class AveriasRepository(private val db: AppDatabase) {
  private val dao get() = db.averiaDao()
  private val firebaseRef = FirebaseDatabase
    .getInstance("https://tecniapp-ice-default-rtdb.firebaseio.com")
    .reference
    .child("averias")

  /** Stream de averías con filtros por agencias/estado/búsqueda. */
  fun observe(agencias: List<String>, estado: String, q: String): Flow<List<AveriaEntity>> =
    dao.observe(agencias, agencias.size, estado, q)

  private val datePatterns = listOf(
    "yyyy-MM-dd'T'HH:mm",
    "yyyy-MM-dd HH:mm:ss"
  )

  private fun parseMillis(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    val trimmed = value.trim()
    for (pattern in datePatterns) {
      val formatter = SimpleDateFormat(pattern, Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("America/Costa_Rica")
      }
      val parsed = runCatching { formatter.parse(trimmed) }.getOrNull()
      if (parsed != null) return parsed.time
    }
    return null
  }

  // --- Normalización de etiquetas de agencia/zona ---
  private fun agenciaTag(region: String?, nombreAgencia: String?): String {
    val n = (nombreAgencia ?: "").lowercase(Locale.ROOT)
    val isHuetarAtlantica =
      (region ?: "").contains("huetar atl", true) || (region ?: "").contains("atlánt", true)
    return when {
      n.contains("guápiles") || n.contains("guapiles") -> "Guapiles"
      n.contains("guácimo")  || n.contains("guacimo")  -> "Guacimo"
      n.contains("cariari")                            -> "Cariari"
      n.contains("tortuguero")                         -> "Tortuguero"
      else -> if (isHuetarAtlantica) "Guapiles" else "Otra"
    }
  }

  private fun estadoFromIce(idEstadoAve: Int?, estadoTexto: String?): String =
    when (idEstadoAve) {
      1 -> "Pendiente"
      2 -> "Asignada"
      3 -> "En atención"
      4 -> "Resuelta"
      else -> estadoTexto?.ifBlank { "Pendiente" } ?: "Pendiente"
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
      tecnicoAsignadoNombre = null,
      atendidoPorUid = null,
      atendidoPorNombre = null,
      materialesTexto = null,
      isSynced = true,
      lastUpdated = System.currentTimeMillis()
    )
  }

  /**
   * Sincroniza desde ICE y devuelve los caseId NUEVOS (para notificación).
   */
  suspend fun syncFromIce(bearer: String?): List<String> = withContext(Dispatchers.IO) {
    val auth = bearer?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
    val envelope = IceApi.service.getAverias(auth)
    val incoming = envelope.payload().mapNotNull { map(it) }

    val current = dao.all().associateBy { it.caseId }
    val merged = incoming.map { remote ->
      val existing = current[remote.caseId]
      when {
        existing == null -> remote
        !existing.isSynced -> existing
        else -> existing.copy(
          region = remote.region,
          provincia = remote.provincia,
          agencia = remote.agencia,
          nombreAgencia = remote.nombreAgencia,
          nise = remote.nise,
          causa = remote.causa ?: existing.causa,
          observaciones = remote.observaciones ?: existing.observaciones,
          estado = remote.estado,
          idEstadoAve = remote.idEstadoAve,
          idEstadoAranda = remote.idEstadoAranda,
          lat = remote.lat,
          lng = remote.lng,
          clientesAfectados = remote.clientesAfectados,
          fechaInicioMillis = remote.fechaInicioMillis,
          horaInicioMillis = existing.horaInicioMillis ?: remote.horaInicioMillis,
          horaFinalMillis = remote.horaFinalMillis ?: existing.horaFinalMillis,
          agenciaTag = remote.agenciaTag,
          lastUpdated = remote.lastUpdated,
          isSynced = true
        )
      }
    }

    dao.upsertAll(merged)
    incoming.map { it.caseId }.filter { !current.containsKey(it) }
  }

  suspend fun syncPendientesConFirebase() = withContext(Dispatchers.IO) {
    dao.pendingSync().forEach { entity ->
      try {
        pushToFirebase(entity)
        dao.marcarSincronizado(entity.caseId)
      } catch (t: Throwable) {
        Log.e("AveriasRepo", "Firebase sync failed for ${entity.caseId}", t)
      }
    }
  }

  suspend fun asignar(caseId: String, uid: String, nombre: String?, vehiculo: String?) =
    withContext(Dispatchers.IO) {
      val now = System.currentTimeMillis()
      dao.marcarAsignada(caseId, uid, nombre, vehiculo, now, now)
      syncSingle(caseId)
    }

  suspend fun revertirAPendiente(caseId: String) = withContext(Dispatchers.IO) {
    val now = System.currentTimeMillis()
    dao.revertirAPendiente(caseId, now)
    syncSingle(caseId)
  }

  suspend fun enAtencion(caseId: String, data: AveriaActionData) = withContext(Dispatchers.IO) {
    val now = System.currentTimeMillis()
    dao.actualizarAtencion(
      caseId = caseId,
      causa = data.causa,
      obs = data.observaciones,
      horaInicio = now,
      horaFinal = null,
      atendidoPorUid = data.atendidoPorUid,
      atendidoPorNombre = data.atendidoPorNombre,
      vehiculo = data.vehiculo,
      materiales = data.materiales,
      lastUpdated = now,
      nuevoEstado = "En atención"
    )
    syncSingle(caseId)
  }

  suspend fun cerrar(caseId: String, data: AveriaActionData) = withContext(Dispatchers.IO) {
    val now = System.currentTimeMillis()
    dao.actualizarAtencion(
      caseId = caseId,
      causa = data.causa,
      obs = data.observaciones,
      horaInicio = now,
      horaFinal = now,
      atendidoPorUid = data.atendidoPorUid,
      atendidoPorNombre = data.atendidoPorNombre,
      vehiculo = data.vehiculo,
      materiales = data.materiales,
      lastUpdated = now,
      nuevoEstado = "Resuelta"
    )
    syncSingle(caseId)
  }

  private suspend fun syncSingle(caseId: String) {
    val entity = dao.getByCaseId(caseId) ?: return
    try {
      pushToFirebase(entity)
      dao.marcarSincronizado(caseId)
    } catch (t: Throwable) {
      Log.e("AveriasRepo", "Firebase push failed for $caseId", t)
    }
  }

  private suspend fun pushToFirebase(entity: AveriaEntity) {
    val payload = hashMapOf<String, Any?>(
      "caseId" to entity.caseId,
      "region" to entity.region,
      "provincia" to entity.provincia,
      "agencia" to entity.agencia,
      "nombreAgencia" to entity.nombreAgencia,
      "nise" to entity.nise,
      "causa" to entity.causa,
      "observaciones" to entity.observaciones,
      "estado" to entity.estado,
      "idEstadoAve" to entity.idEstadoAve,
      "idEstadoAranda" to entity.idEstadoAranda,
      "lat" to entity.lat,
      "lng" to entity.lng,
      "clientesAfectados" to entity.clientesAfectados,
      "fechaInicioMillis" to entity.fechaInicioMillis,
      "horaInicioMillis" to entity.horaInicioMillis,
      "horaFinalMillis" to entity.horaFinalMillis,
      "agenciaTag" to entity.agenciaTag,
      "vehiculoAsignado" to entity.vehiculoAsignado,
      "tecnicoAsignadoUid" to entity.tecnicoAsignadoUid,
      "tecnicoAsignadoNombre" to entity.tecnicoAsignadoNombre,
      "atendidoPorUid" to entity.atendidoPorUid,
      "atendidoPorNombre" to entity.atendidoPorNombre,
      "materialesTexto" to entity.materialesTexto,
      "lastUpdated" to entity.lastUpdated
    )
    firebaseRef.child(entity.caseId).updateChildren(payload).await()
  }
}
