package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.util.Log
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AveriaEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class AveriasRepository(private val db: AppDatabase) {
  private val dao get() = db.averiaDao()
  private val firebaseRef = FirebaseDatabase
    .getInstance("https://tecniapp-ice-default-rtdb.firebaseio.com")
    .reference
    .child("averias")

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var realtimeListener: ValueEventListener? = null

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
          atencionHoraInicioMillis = existing.atencionHoraInicioMillis,
          atencionHoraFinalMillis = existing.atencionHoraFinalMillis,
          kilometrajeInicio = existing.kilometrajeInicio,
          kilometrajeFinal = existing.kilometrajeFinal,
          materialesTexto = existing.materialesTexto,
          materialesDetalleJson = existing.materialesDetalleJson,
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
    val horaInicio = data.horaInicioMillis ?: now
    val resumen = MaterialesSerializer.toSummary(data.materiales).ifBlank { null }
    val detalle = MaterialesSerializer.toJson(data.materiales)
    dao.actualizarAtencion(
      caseId = caseId,
      causa = data.causa,
      obs = data.observaciones,
      horaInicio = horaInicio,
      horaFinal = data.horaFinalMillis,
      kmInicio = data.kilometrajeInicio,
      kmFinal = data.kilometrajeFinal,
      atendidoPorUid = data.atendidoPorUid,
      atendidoPorNombre = data.atendidoPorNombre,
      vehiculo = data.vehiculo,
      materialesResumen = resumen,
      materialesDetalle = detalle,
      lastUpdated = now,
      nuevoEstado = "En atención"
    )
    syncSingle(caseId)
  }

  suspend fun cerrar(caseId: String, data: AveriaActionData) = withContext(Dispatchers.IO) {
    val now = System.currentTimeMillis()
    val horaInicio = data.horaInicioMillis ?: now
    val horaFinal = data.horaFinalMillis ?: now
    val resumen = MaterialesSerializer.toSummary(data.materiales).ifBlank { null }
    val detalle = MaterialesSerializer.toJson(data.materiales)
    dao.actualizarAtencion(
      caseId = caseId,
      causa = data.causa,
      obs = data.observaciones,
      horaInicio = horaInicio,
      horaFinal = horaFinal,
      kmInicio = data.kilometrajeInicio,
      kmFinal = data.kilometrajeFinal,
      atendidoPorUid = data.atendidoPorUid,
      atendidoPorNombre = data.atendidoPorNombre,
      vehiculo = data.vehiculo,
      materialesResumen = resumen,
      materialesDetalle = detalle,
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
      "atencionHoraInicioMillis" to entity.atencionHoraInicioMillis,
      "atencionHoraFinalMillis" to entity.atencionHoraFinalMillis,
      "kilometrajeInicio" to entity.kilometrajeInicio,
      "kilometrajeFinal" to entity.kilometrajeFinal,
      "agenciaTag" to entity.agenciaTag,
      "vehiculoAsignado" to entity.vehiculoAsignado,
      "tecnicoAsignadoUid" to entity.tecnicoAsignadoUid,
      "tecnicoAsignadoNombre" to entity.tecnicoAsignadoNombre,
      "atendidoPorUid" to entity.atendidoPorUid,
      "atendidoPorNombre" to entity.atendidoPorNombre,
      "materialesTexto" to entity.materialesTexto,
      "materialesDetalleJson" to entity.materialesDetalleJson,
      "lastUpdated" to entity.lastUpdated
    )
    firebaseRef.child(entity.caseId).updateChildren(payload).await()
  }

  suspend fun pullFromFirebaseOnce() = withContext(Dispatchers.IO) {
    try {
      val snapshot = firebaseRef.get().await()
      val current = dao.all().associateBy { it.caseId }
      val updated = mutableListOf<AveriaEntity>()
      snapshot.children.forEach { child ->
        val remote = child.getValue(AveriaEntity::class.java) ?: return@forEach
        val estadoRemoto = remote.estado ?: ""
        val normalizedEstado = when (estadoRemoto.lowercase(Locale.getDefault())) {
          "nuevo" -> "Pendiente"
          else -> estadoRemoto.ifBlank { "Pendiente" }
        }
        val remoteEntity = remote.copy(
          estado = normalizedEstado,
          isSynced = true
        )
        val existing = current[remoteEntity.caseId]
        if (existing == null) {
          updated += remoteEntity
        } else if (existing.isSynced && (remoteEntity.lastUpdated >= existing.lastUpdated)) {
          updated += remoteEntity
        }
      }
      if (updated.isNotEmpty()) {
        dao.upsertAll(updated)
      }
    } catch (t: Throwable) {
      Log.e("AveriasRepo", "Firebase pull failed", t)
    }
  }

  fun startRealtimeListener() {
    if (realtimeListener != null) return
    realtimeListener = object : ValueEventListener {
      override fun onDataChange(snapshot: DataSnapshot) {
        scope.launch {
          val current = dao.all().associateBy { it.caseId }
          val toUpsert = mutableListOf<AveriaEntity>()
          snapshot.children.forEach { child ->
            val remote = child.getValue(AveriaEntity::class.java) ?: return@forEach
            val estadoRemoto = remote.estado ?: ""
            val normalizedEstado = when (estadoRemoto.lowercase(Locale.getDefault())) {
              "nuevo" -> "Pendiente"
              else -> estadoRemoto.ifBlank { "Pendiente" }
            }
            val remoteEntity = remote.copy(
              estado = normalizedEstado,
              isSynced = true
            )
            val existing = current[remoteEntity.caseId]
            if (existing == null) {
              toUpsert += remoteEntity
            } else if (!existing.isSynced) {
              if (remoteEntity.lastUpdated > existing.lastUpdated) {
                toUpsert += remoteEntity
              }
            } else if (remoteEntity.lastUpdated >= existing.lastUpdated) {
              toUpsert += existing.copy(
                region = remoteEntity.region,
                provincia = remoteEntity.provincia,
                agencia = remoteEntity.agencia,
                nombreAgencia = remoteEntity.nombreAgencia,
                nise = remoteEntity.nise,
                causa = remoteEntity.causa ?: existing.causa,
                observaciones = remoteEntity.observaciones ?: existing.observaciones,
                estado = remoteEntity.estado,
                idEstadoAve = remoteEntity.idEstadoAve,
                idEstadoAranda = remoteEntity.idEstadoAranda,
                lat = remoteEntity.lat,
                lng = remoteEntity.lng,
                clientesAfectados = remoteEntity.clientesAfectados,
                fechaInicioMillis = remoteEntity.fechaInicioMillis,
                horaInicioMillis = remoteEntity.horaInicioMillis,
                horaFinalMillis = remoteEntity.horaFinalMillis,
                atencionHoraInicioMillis = remoteEntity.atencionHoraInicioMillis,
                atencionHoraFinalMillis = remoteEntity.atencionHoraFinalMillis,
                kilometrajeInicio = remoteEntity.kilometrajeInicio,
                kilometrajeFinal = remoteEntity.kilometrajeFinal,
                vehiculoAsignado = remoteEntity.vehiculoAsignado,
                tecnicoAsignadoUid = remoteEntity.tecnicoAsignadoUid,
                tecnicoAsignadoNombre = remoteEntity.tecnicoAsignadoNombre,
                atendidoPorUid = remoteEntity.atendidoPorUid,
                atendidoPorNombre = remoteEntity.atendidoPorNombre,
                materialesTexto = remoteEntity.materialesTexto,
                materialesDetalleJson = remoteEntity.materialesDetalleJson,
                lastUpdated = remoteEntity.lastUpdated,
                isSynced = true
              )
            }
          }
          if (toUpsert.isNotEmpty()) {
            dao.upsertAll(toUpsert)
          }
        }
      }

      override fun onCancelled(error: DatabaseError) {
        Log.e("AveriasRepo", "Realtime listener cancelled", error.toException())
      }
    }
    firebaseRef.addValueEventListener(realtimeListener!!)
  }

  fun stopRealtimeListener() {
    realtimeListener?.let { firebaseRef.removeEventListener(it) }
    realtimeListener = null
    scope.coroutineContext.cancelChildren()
  }
}
