package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.util.Log
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AveriaEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class AveriasRepository(private val db: AppDatabase) {

    private val dao get() = db.averiaDao()
    private val firebaseRef = FirebaseDatabase
        .getInstance("https://averias.firebaseio.com")
        .reference
        .child("averias")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var realtimeListener: ValueEventListener? = null

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

    private fun agenciaTag(region: String?, nombreAgencia: String?): String {
        val normalized = (nombreAgencia ?: "").lowercase(Locale.ROOT)
        val isHuetarAtlantica =
            (region ?: "").contains("huetar atl", ignoreCase = true) ||
            (region ?: "").contains("atlánt", ignoreCase = true)
        return when {
            normalized.contains("guápiles") || normalized.contains("guapiles") -> "Guapiles"
            normalized.contains("guácimo") || normalized.contains("guacimo") -> "Guacimo"
            normalized.contains("cariari") -> "Cariari"
            normalized.contains("tortuguero") -> "Tortuguero"
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

    private fun shouldInclude(remote: IceAveria): Boolean {
        val estadoTexto = remote.estado?.lowercase(Locale.getDefault()) ?: ""
        val byId = when (remote.idEstadoAve) {
            1 -> true // Pendiente
            2 -> false
            3 -> false
            4 -> false
            else -> false
        }
        if (byId) return true
        if (estadoTexto.isBlank()) return false
        return estadoTexto.contains("nuevo") || estadoTexto.contains("pend")
    }

    private fun map(remote: IceAveria): AveriaEntity? {
        val id = remote.noCaso?.trim().orEmpty()
        if (id.isBlank()) return null

        val lat = remote.latitud?.replace(",", ".")?.toDoubleOrNull()
        val lng = remote.longitud?.replace(",", ".")?.toDoubleOrNull()
        val estado = estadoFromIce(remote.idEstadoAve, remote.estado)

        return AveriaEntity(
            caseId = id,
            region = remote.region,
            provincia = remote.provincia,
            agencia = remote.agencia,
            nombreAgencia = remote.nombreAgencia,
            nise = remote.nise,
            causa = remote.causa,
            observaciones = remote.observaciones,
            estado = estado,
            idEstadoAve = remote.idEstadoAve,
            idEstadoAranda = remote.idEstadoAranda,
            lat = lat?.takeIf { it in -90.0..90.0 },
            lng = lng?.takeIf { it in -180.0..180.0 },
            clientesAfectados = remote.clientesAfectados,
            fechaInicioMillis = parseMillis(remote.fechaInicio) ?: System.currentTimeMillis(),
            horaInicioMillis = parseMillis(remote.manualSalidaVehiculo),
            horaFinalMillis = parseMillis(remote.horaCierreInterrupcion),
            agenciaTag = agenciaTag(remote.region, remote.nombreAgencia),
            vehiculoAsignado = null,
            tecnicoAsignadoUid = null,
            tecnicoAsignadoNombre = null,
            atendidoPorUid = null,
            atendidoPorNombre = null,
            materialesTexto = null,
            materialesDetalleJson = null,
            isSynced = true,
            lastUpdated = System.currentTimeMillis()
        )
    }

    suspend fun syncFromIce(bearer: String?): List<String> = withContext(Dispatchers.IO) {
        val authHeader = bearer?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
        val envelope = IceApi.service.getAverias(authHeader)
        val incoming = envelope.payload()
            .filter { shouldInclude(it) }
            .mapNotNull { map(it) }

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

        val newOnes = merged.filter { !current.containsKey(it.caseId) }
        registerNewOnFirebase(newOnes)

        incoming.map { it.caseId }.filter { !current.containsKey(it) }
    }

    suspend fun syncPendientesConFirebase() = withContext(Dispatchers.IO) {
        dao.pendingSync().forEach { entity ->
            try {
                pushToFirebase(entity)
                dao.marcarSincronizado(entity.caseId)
            } catch (t: Throwable) {
                Log.e(TAG, "Firebase sync failed for ${entity.caseId}", t)
            }
        }
    }

    suspend fun asignar(caseId: String, uid: String, nombre: String?, vehiculo: String?) {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            dao.marcarAsignada(caseId, uid, nombre, vehiculo, now, now)
            syncSingle(caseId)
        }
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
            Log.e(TAG, "Firebase push failed for $caseId", t)
        }
    }

    private suspend fun pushToFirebase(entity: AveriaEntity) {
        val payload = entity.toFirebasePayload()
        firebaseRef.child(entity.caseId).updateChildren(payload).await()
    }

    private suspend fun registerNewOnFirebase(entities: List<AveriaEntity>) {
        entities.forEach { entity ->
            try {
                val ref = firebaseRef.child(entity.caseId)
                val snapshot = ref.get().await()
                if (!snapshot.exists()) {
                    ref.setValue(entity.toFirebasePayload()).await()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "No se pudo registrar avería nueva ${entity.caseId} en Firebase", t)
            }
        }
    }

    private fun AveriaEntity.toFirebasePayload(): Map<String, Any?> = hashMapOf(
        "caseId" to caseId,
        "region" to region,
        "provincia" to provincia,
        "agencia" to agencia,
        "nombreAgencia" to nombreAgencia,
        "nise" to nise,
        "causa" to causa,
        "observaciones" to observaciones,
        "estado" to estado,
        "idEstadoAve" to idEstadoAve,
        "idEstadoAranda" to idEstadoAranda,
        "lat" to lat,
        "lng" to lng,
        "clientesAfectados" to clientesAfectados,
        "fechaInicioMillis" to fechaInicioMillis,
        "horaInicioMillis" to horaInicioMillis,
        "horaFinalMillis" to horaFinalMillis,
        "atencionHoraInicioMillis" to atencionHoraInicioMillis,
        "atencionHoraFinalMillis" to atencionHoraFinalMillis,
        "kilometrajeInicio" to kilometrajeInicio,
        "kilometrajeFinal" to kilometrajeFinal,
        "agenciaTag" to agenciaTag,
        "vehiculoAsignado" to vehiculoAsignado,
        "tecnicoAsignadoUid" to tecnicoAsignadoUid,
        "tecnicoAsignadoNombre" to tecnicoAsignadoNombre,
        "atendidoPorUid" to atendidoPorUid,
        "atendidoPorNombre" to atendidoPorNombre,
        "materialesTexto" to materialesTexto,
        "materialesDetalleJson" to materialesDetalleJson,
        "lastUpdated" to lastUpdated
    )

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
                } else if (existing.isSynced && remoteEntity.lastUpdated >= existing.lastUpdated) {
                    updated += remoteEntity
                }
            }
            if (updated.isNotEmpty()) {
                dao.upsertAll(updated)
            } else {

            }
        } catch (t: Throwable) {
            Log.e(TAG, "Firebase pull failed", t)
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
                        when {
                            existing == null -> toUpsert += remoteEntity
                            !existing.isSynced -> if (remoteEntity.lastUpdated > existing.lastUpdated) {
                                toUpsert += remoteEntity
                            }
                            remoteEntity.lastUpdated >= existing.lastUpdated -> toUpsert += existing.copy(
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
                Log.e(TAG, "Realtime listener cancelled", error.toException())
            }
        }
        firebaseRef.addValueEventListener(realtimeListener!!)
    }

    fun stopRealtimeListener() {
        realtimeListener?.let { firebaseRef.removeEventListener(it) }
        realtimeListener = null
        scope.coroutineContext.cancelChildren()
    }

    companion object {
        private const val TAG = "AveriasRepo"
    }
}
