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
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class AveriasRepository(private val db: AppDatabase) {

    private val dao get() = db.averiaDao()
    private val firebaseRef = FirebaseDatabase
        .getInstance("https://averias.firebaseio.com")
        .reference
        .child("averias")

    // Base de materiales usada por ICE
    private val materialesRef = FirebaseDatabase
        .getInstance("https://tecniapp-ice-materiales.firebaseio.com/")
        .reference

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var realtimeListener: ValueEventListener? = null

    fun observe(agencias: List<String>, estado: String, q: String): Flow<List<AveriaEntity>> =
        dao.observe(agencias, agencias.size, estado, q)

    // ---------------------------------------------------------------------------------------------
    // Normalizadores (región / agencia) y utilitarios
    // ---------------------------------------------------------------------------------------------

    private fun stripAccentsLower(s: String): String {
        val n = Normalizer.normalize(s, Normalizer.Form.NFD)
        return n.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase(Locale.getDefault())
    }

    private fun normalizeKey(s: String?): String {
        if (s.isNullOrBlank()) return ""
        var k = stripAccentsLower(s)
        k = k.replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
        // Remueve prefijos ruidosos frecuentes (S., SUB, AGENCIA, etc.)
        k = k.removePrefix("s ").removePrefix("sub ").removePrefix("agencia ")
        return k
    }

    private fun titleCase(s: String): String =
        s.split(" ").filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                if (part.length == 1) part.uppercase()
                else part.substring(0, 1).uppercase() + part.substring(1).lowercase()
            }

    private fun slugTag(s: String): String {
        val n = stripAccentsLower(s).replace("\\s+".toRegex(), " ").trim()
        return n.split(" ").joinToString("") { titleCase(it) } // "Río Frío" -> "RioFrio"
    }

    private fun mergeRemoteString(remote: String?, local: String?): String? {
        val raw = remote ?: return local
        val trimmed = raw.trim()
        return if (trimmed.isEmpty()) null else trimmed
    }

    // Regiones canónicas
    private val REGION_CANON = mapOf(
        "huetar atlantica" to "Huetar Atlántica",
        "atlantica" to "Huetar Atlántica",
        "caribe" to "Huetar Atlántica",
        "huetar norte" to "Huetar Norte",
        "norte" to "Huetar Norte",
        "pacifico central" to "Pacífico Central",
        "central pacifico" to "Pacífico Central",
        "central" to "Central",
        "chorotega" to "Chorotega",
        "brunca" to "Brunca"
    )

    private fun canonicalRegion(raw: String?): String? {
        val k = normalizeKey(raw)
        return REGION_CANON[k] ?: raw?.trim()
    }

    // Alias de agencias -> nombre canónico (enfocado en Huetar Atlántica + frecuentes)
    private val AGENCIA_CANON = mapOf(
        // Huetar Atlántica
        "guapiles" to "Guápiles",
        "s guapiles" to "Guápiles",
        "sub guapiles" to "Guápiles",

        "guacimo" to "Guácimo",

        "siquirres" to "Siquirres",
        "s siquirres" to "Siquirres",

        "limon" to "Limón",
        "puerto limon" to "Limón",

        "talamanca" to "Talamanca",
        "bri bri" to "Talamanca",
        "bribri" to "Talamanca",
        "s limon talamanca" to "Talamanca",

        "cariari" to "Cariari",

        "batan" to "Batán",
        "bataan" to "Batán",

        "tortuguero" to "Tortuguero",

        "rio frio" to "Río Frío",
        "riofrio" to "Río Frío",

        "turrialba" to "Turrialba",
        "juan vinas" to "Juan Viñas",

        // Otras frecuentes (por si llegan)
        "heredia" to "Heredia",
        "cartago" to "Cartago",
        "san jose" to "San José",
        "alajuela" to "Alajuela",
        "liberia" to "Liberia",
        "perez zeledon" to "Pérez Zeledón",
        "jaco" to "Jacó"
    )

    private data class AgenciaCanon(val agencia: String?, val nombre: String?, val tag: String?)

    private fun canonicalizeAgency(region: String?, agencia: String?, nombreAgencia: String?): AgenciaCanon {
        // Preferimos el nombre visible si existe
        val source = nombreAgencia?.takeIf { it.isNotBlank() } ?: agencia ?: ""
        val k = normalizeKey(source)
        val canonNombre = AGENCIA_CANON[k] ?: run {
            // Si no tenemos alias directo, intentar heurística simple (title case)
            if (source.isBlank()) null else titleCase(stripAccentsLower(source))
        }

        val tag = canonNombre?.let { slugTag(it) }
        // En "agencia" guardamos el mismo canónico, y en nombre también
        return AgenciaCanon(
            agencia = canonNombre,
            nombre = canonNombre,
            tag = tag
        )
    }

    private fun canonicalizeAgenciaFields(entity: AveriaEntity): AveriaEntity {
        val regionCanon = canonicalRegion(entity.region)
        val canon = canonicalizeAgency(regionCanon, entity.agencia, entity.nombreAgencia)
        return entity.copy(
            region = regionCanon,
            agencia = canon.agencia ?: entity.agencia,
            nombreAgencia = canon.nombre ?: entity.nombreAgencia,
            agenciaTag = canon.tag ?: entity.agenciaTag
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Estado (no “bajar” estado) + utilitarios
    // ---------------------------------------------------------------------------------------------

    private enum class EstadoRank { PENDIENTE, ASIGNADA, EN_ATENCION, RESUELTA }

    private fun normalizeEstadoLabel(raw: String?): String {
        if (raw.isNullOrBlank()) return "Pendiente"
        val v = raw.trim().lowercase(Locale.getDefault())
        return when {
            "resuel" in v -> "Resuelta"
            "en at" in v || "atenci" in v -> "En atención"
            "asign" in v -> "Asignada"
            "nuevo" in v -> "Pendiente"
            else -> if (v.isBlank()) "Pendiente" else raw.trim()
        }
    }

    private fun rankOfEstado(label: String?): EstadoRank = when (normalizeEstadoLabel(label)) {
        "Resuelta" -> EstadoRank.RESUELTA
        "En atención" -> EstadoRank.EN_ATENCION
        "Asignada" -> EstadoRank.ASIGNADA
        else -> EstadoRank.PENDIENTE
    }

    private fun pickEstadoPreferAdvanced(local: String?, remote: String?): String {
        val l = normalizeEstadoLabel(local)
        val r = normalizeEstadoLabel(remote)
        return if (rankOfEstado(l) >= rankOfEstado(r)) l else r
    }

    private fun idEstadoFromLabel(label: String?): Int = when (normalizeEstadoLabel(label)) {
        "Pendiente" -> 1
        "Asignada" -> 2
        "En atención" -> 3
        "Resuelta" -> 4
        else -> 1
    }

    // ---------------------------------------------------------------------------------------------
    // Fechas, filtros de inclusión y tag de agencia (compat)
    // ---------------------------------------------------------------------------------------------

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

    // Mantengo este método (usado por vistas antiguas) redirigiendo al canónico.
    private fun agenciaTag(region: String?, nombreAgencia: String?): String {
        val canon = canonicalizeAgency(canonicalRegion(region), null, nombreAgencia)
        return canon.tag ?: "Otra"
    }

    private fun estadoFromIce(idEstadoAve: Int?, estadoTexto: String?): String =
        normalizeEstadoLabel(
            when (idEstadoAve) {
                1 -> "Pendiente"
                2 -> "Asignada"
                3 -> "En atención"
                4 -> "Resuelta"
                else -> estadoTexto?.ifBlank { "Pendiente" } ?: "Pendiente"
            }
        )

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

    // ---------------------------------------------------------------------------------------------
    // Map desde ICE (con normalización de región/agencia)
    // ---------------------------------------------------------------------------------------------

    private fun map(remote: IceAveria): AveriaEntity? {
        val id = remote.noCaso?.trim().orEmpty()
        if (id.isBlank()) return null

        val lat = remote.latitud?.replace(",", ".")?.toDoubleOrNull()
        val lng = remote.longitud?.replace(",", ".")?.toDoubleOrNull()
        val estado = estadoFromIce(remote.idEstadoAve, remote.estado)

        val regionCanon = canonicalRegion(remote.region)
        val agenciaCanon = canonicalizeAgency(regionCanon, remote.agencia, remote.nombreAgencia)

        return AveriaEntity(
            caseId = id,
            region = regionCanon,
            provincia = remote.provincia,
            agencia = agenciaCanon.agencia ?: remote.agencia,
            nombreAgencia = agenciaCanon.nombre ?: remote.nombreAgencia,
            nise = remote.nise,
            causa = remote.causa,
            observaciones = remote.observaciones,
            estado = estado,
            idEstadoAve = idEstadoAveFromLabelOrRemote(estado, remote.idEstadoAve),
            idEstadoAranda = remote.idEstadoAranda,
            lat = lat?.takeIf { it in -90.0..90.0 },
            lng = lng?.takeIf { it in -180.0..180.0 },
            clientesAfectados = remote.clientesAfectados,
            fechaInicioMillis = parseMillis(remote.fechaInicio) ?: System.currentTimeMillis(),
            horaInicioMillis = parseMillis(remote.manualSalidaVehiculo),
            horaFinalMillis = parseMillis(remote.horaCierreInterrupcion),
            agenciaTag = agenciaCanon.tag ?: agenciaTag(regionCanon, agenciaCanon.nombre),
            vehiculoAsignado = null,
            tecnicoAsignadoUid = null,
            tecnicoAsignadoNombre = null,
            atendidoPorUid = null,
            atendidoPorNombre = null,
            materialesTexto = null,
            materialesDetalleJson = null,
            tecnicosAtendieronJson = null,
            cliente = null,
            localizacion = null,
            isSynced = true,
            lastUpdated = System.currentTimeMillis()
        )
    }

    private fun idEstadoAveFromLabelOrRemote(label: String, remoteId: Int?): Int =
        remoteId ?: idEstadoFromLabel(label)

    // ---------------------------------------------------------------------------------------------
    // Sync desde ICE (merge sin bajar estado + conservar lastUpdated más nuevo)
    // ---------------------------------------------------------------------------------------------

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
                else -> {
                    // No bajar estado y normalizar agencias
                    val estadoElegido = pickEstadoPreferAdvanced(existing.estado, remote.estado)
                    val idEstadoElegido = idEstadoFromLabel(estadoElegido)

                    val normalRemote = canonicalizeAgenciaFields(remote)
                    existing.copy(
                        region = normalRemote.region,
                        provincia = normalRemote.provincia,
                        agencia = normalRemote.agencia,
                        nombreAgencia = normalRemote.nombreAgencia,
                        nise = normalRemote.nise,
                        causa = normalRemote.causa ?: existing.causa,
                        observaciones = normalRemote.observaciones ?: existing.observaciones,

                        estado = estadoElegido,
                        idEstadoAve = idEstadoElegido,
                        idEstadoAranda = normalRemote.idEstadoAranda,

                        lat = normalRemote.lat,
                        lng = normalRemote.lng,
                        clientesAfectados = normalRemote.clientesAfectados,
                        fechaInicioMillis = normalRemote.fechaInicioMillis,
                        horaInicioMillis = existing.horaInicioMillis ?: normalRemote.horaInicioMillis,
                        horaFinalMillis = normalRemote.horaFinalMillis ?: existing.horaFinalMillis,
                        atencionHoraInicioMillis = existing.atencionHoraInicioMillis,
                        atencionHoraFinalMillis = existing.atencionHoraFinalMillis,
                        kilometrajeInicio = existing.kilometrajeInicio,
                        kilometrajeFinal = existing.kilometrajeFinal,

                        materialesTexto = existing.materialesTexto,
                        materialesDetalleJson = existing.materialesDetalleJson,

                        agenciaTag = normalRemote.agenciaTag,
                        lastUpdated = maxOf(existing.lastUpdated, normalRemote.lastUpdated),
                        isSynced = true
                    )
                }
            }
        }

        dao.upsertAll(merged)

        val newOnes = merged.filter { !current.containsKey(it.caseId) }
        registerNewOnFirebase(newOnes)

        incoming.map { it.caseId }.filter { !current.containsKey(it) }
    }

    // ---------------------------------------------------------------------------------------------
    // Acciones y sync con Firebase
    // ---------------------------------------------------------------------------------------------

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
        val tecnicosJson = TecnicosSerializer.toJson(data.tecnicos)
        val localizacion = data.localizacion?.trim()?.takeIf { it.isNotBlank() }
        val cliente = data.cliente?.trim()?.takeIf { it.isNotBlank() }
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
            tecnicosAtendieron = tecnicosJson,
            cliente = cliente,
            localizacion = localizacion,
            lastUpdated = now,
            nuevoEstado = "En atención"
        )
        syncSingle(caseId)
        registrarMaterialesUsados(data.materiales)
    }

    suspend fun cerrar(caseId: String, data: AveriaActionData) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val horaInicio = data.horaInicioMillis ?: now
        val horaFinal = data.horaFinalMillis ?: now
        val resumen = MaterialesSerializer.toSummary(data.materiales).ifBlank { null }
        val detalle = MaterialesSerializer.toJson(data.materiales)
        val tecnicosJson = TecnicosSerializer.toJson(data.tecnicos)
        val localizacion = data.localizacion?.trim()?.takeIf { it.isNotBlank() }
        val cliente = data.cliente?.trim()?.takeIf { it.isNotBlank() }
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
            tecnicosAtendieron = tecnicosJson,
            cliente = cliente,
            localizacion = localizacion,
            lastUpdated = now,
            nuevoEstado = "Resuelta"
        )
        syncSingle(caseId)
        registrarMaterialesUsados(data.materiales)
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
        // 👇 En lugar de updateChildren usamos setValue para reemplazar todo el objeto completo
        firebaseRef.child(entity.caseId).setValue(payload).await()
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

        "tecnicosAtendieronJson" to tecnicosAtendieronJson,

        "cliente" to cliente,
        "localizacion" to localizacion,
        "lastUpdated" to lastUpdated
    )

    private suspend fun registrarMaterialesUsados(lista: List<MaterialUso>) {
        lista.forEach { uso ->
            val ref = materialesRef.child(uso.codigo)
            val map = mapOf(
                "Nombre" to uso.descripcion,
                "Cantidad" to uso.cantidad
            )
            ref.setValue(map).await()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Firebase: pull (una vez) y realtime (con normalización y sin bajar estado)
    // ---------------------------------------------------------------------------------------------

    suspend fun pullFromFirebaseOnce() = withContext(Dispatchers.IO) {
        try {
            val snapshot = firebaseRef.get().await()
            val current = dao.all().associateBy { it.caseId }
            val updated = mutableListOf<AveriaEntity>()
            snapshot.children.forEach { child ->
                val remote0 = child.getValue(AveriaEntity::class.java) ?: return@forEach
                // Normalizar estado "nuevo" -> "Pendiente"
                val normalizedEstado = normalizeEstadoLabel(remote0.estado)
                val remoteBase = remote0.copy(estado = normalizedEstado, isSynced = true)
                val remote = canonicalizeAgenciaFields(remoteBase)

                val existing = current[remote.caseId]
                when {
                    existing == null -> updated += remote
                    existing.isSynced && remote.lastUpdated >= existing.lastUpdated -> {
                        // Merge sin bajar estado
                        val estadoElegido = pickEstadoPreferAdvanced(existing.estado, remote.estado)
                        val idEstadoElegido = idEstadoFromLabel(estadoElegido)
                        updated += existing.copy(
                            region = remote.region,
                            provincia = remote.provincia,
                            agencia = remote.agencia,
                            nombreAgencia = remote.nombreAgencia,
                            nise = remote.nise,
                            causa = remote.causa ?: existing.causa,
                            observaciones = remote.observaciones ?: existing.observaciones,
                            estado = estadoElegido,
                            idEstadoAve = idEstadoElegido,
                            idEstadoAranda = remote.idEstadoAranda,
                            lat = remote.lat,
                            lng = remote.lng,
                            clientesAfectados = remote.clientesAfectados,
                            fechaInicioMillis = remote.fechaInicioMillis,
                            horaInicioMillis = remote.horaInicioMillis,
                            horaFinalMillis = remote.horaFinalMillis,
                            atencionHoraInicioMillis = remote.atencionHoraInicioMillis,
                            atencionHoraFinalMillis = remote.atencionHoraFinalMillis,
                            kilometrajeInicio = remote.kilometrajeInicio,
                            kilometrajeFinal = remote.kilometrajeFinal,
                            vehiculoAsignado = remote.vehiculoAsignado,
                            tecnicoAsignadoUid = remote.tecnicoAsignadoUid,
                            tecnicoAsignadoNombre = remote.tecnicoAsignadoNombre,
                            atendidoPorUid = remote.atendidoPorUid,
                            atendidoPorNombre = remote.atendidoPorNombre,
                            materialesTexto = remote.materialesTexto,
                            materialesDetalleJson = remote.materialesDetalleJson,
                            tecnicosAtendieronJson = mergeRemoteString(
                                remote.tecnicosAtendieronJson,
                                existing.tecnicosAtendieronJson
                            ),
                            cliente = mergeRemoteString(remote.cliente, existing.cliente),
                            localizacion = mergeRemoteString(remote.localizacion, existing.localizacion),
                            agenciaTag = remote.agenciaTag,
                            lastUpdated = maxOf(existing.lastUpdated, remote.lastUpdated),
                            isSynced = true
                        )
                    }
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
                        val remote0 = child.getValue(AveriaEntity::class.java) ?: return@forEach
                        val normalizedEstado = normalizeEstadoLabel(remote0.estado)
                        val remoteBase = remote0.copy(estado = normalizedEstado, isSynced = true)
                        val remote = canonicalizeAgenciaFields(remoteBase)

                        val existing = current[remote.caseId]
                        when {
                            existing == null -> toUpsert += remote
                            !existing.isSynced -> if (remote.lastUpdated > existing.lastUpdated) {
                                toUpsert += remote
                            }
                            remote.lastUpdated >= existing.lastUpdated -> {
                                val estadoElegido = pickEstadoPreferAdvanced(existing.estado, remote.estado)
                                val idEstadoElegido = idEstadoFromLabel(estadoElegido)
                                toUpsert += existing.copy(
                                    region = remote.region,
                                    provincia = remote.provincia,
                                    agencia = remote.agencia,
                                    nombreAgencia = remote.nombreAgencia,
                                    nise = remote.nise,
                                    causa = remote.causa ?: existing.causa,
                                    observaciones = remote.observaciones ?: existing.observaciones,
                                    estado = estadoElegido,
                                    idEstadoAve = idEstadoElegido,
                                    idEstadoAranda = remote.idEstadoAranda,
                                    lat = remote.lat,
                                    lng = remote.lng,
                                    clientesAfectados = remote.clientesAfectados,
                                    fechaInicioMillis = remote.fechaInicioMillis,
                                    horaInicioMillis = remote.horaInicioMillis,
                                    horaFinalMillis = remote.horaFinalMillis,
                                    atencionHoraInicioMillis = remote.atencionHoraInicioMillis,
                                    atencionHoraFinalMillis = remote.atencionHoraFinalMillis,
                                    kilometrajeInicio = remote.kilometrajeInicio,
                                    kilometrajeFinal = remote.kilometrajeFinal,
                                    vehiculoAsignado = remote.vehiculoAsignado,
                                    tecnicoAsignadoUid = remote.tecnicoAsignadoUid,
                                    tecnicoAsignadoNombre = remote.tecnicoAsignadoNombre,
                                    atendidoPorUid = remote.atendidoPorUid,
                                    atendidoPorNombre = remote.atendidoPorNombre,
                                    materialesTexto = remote.materialesTexto,
                                    materialesDetalleJson = remote.materialesDetalleJson,
                                    tecnicosAtendieronJson = mergeRemoteString(
                                        remote.tecnicosAtendieronJson,
                                        existing.tecnicosAtendieronJson
                                    ),
                                    cliente = mergeRemoteString(remote.cliente, existing.cliente),
                                    localizacion = mergeRemoteString(remote.localizacion, existing.localizacion),
                                    agenciaTag = remote.agenciaTag,
                                    lastUpdated = maxOf(existing.lastUpdated, remote.lastUpdated),
                                    isSynced = true
                                )
                            }
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
