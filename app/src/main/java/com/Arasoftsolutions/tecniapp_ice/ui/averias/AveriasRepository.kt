package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.util.Log
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AveriaEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.InventarioMovimientoAveriaEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase
import com.Arasoftsolutions.tecniapp_ice.ui.vehiculo.VehiculoPlacaUtils
import com.Arasoftsolutions.tecniapp_ice.ui.admin.MapCoordinatePickerBottomSheet.Companion.TAG
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
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

    data class SyncResult(
        val nuevas: List<AveriaEntity>,
        val resueltas: List<AveriaEntity>
    )

    private val dao get() = db.averiaDao()
    private val vehiculoDao get() = db.vehiculoDao()
    private val inventarioDao get() = db.inventarioDao()
    private val firebaseRef = FirebaseDatabase
        .getInstance("https://tecniapp-ice-averias.firebaseio.com/")
        .reference
        .child("averias")


    // Base de materiales usada por ICE
    private val materialesRef = FirebaseDatabase
        .getInstance("https://tecniapp-ice-materiales.firebaseio.com/")
        .reference

    private val usersRef = FirebaseDatabase
        .getInstance("https://tecniapp-ice-user.firebaseio.com/")
        .reference
        .child("usuarios")

    private data class SyncScope(
        val region: String?,
        val agenciaTag: String?,
        val agencia: String?
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var realtimeListener: ChildEventListener? = null
    private var realtimeQuery: Query? = null
    private var realtimeCallback: ((List<AveriaEntity>) -> Unit)? = null
    private var suppressInitialNotification = false
    private var realtimeEmittedOnce = false
    private var cachedSyncScope: SyncScope? = null
    private var cachedSyncScopeAt: Long = 0L

    fun observe(agencias: List<String>, estado: String, q: String, s: String): Flow<List<AveriaEntity>> =
        dao.observe(agencias, agencias.size, estado, q)

    suspend fun upsertFromPush(push: AveriaEntity) = withContext(Dispatchers.IO) {
        val normalizedEstado = normalizeEstadoLabel(push.estado)
        val canonical = canonicalizeAgenciaFields(push.copy(estado = normalizedEstado))
        val now = System.currentTimeMillis()
        val incomingFecha = canonical.fechaInicioMillis.takeIf { it > 0 } ?: now
        val existing = dao.getByCaseId(canonical.caseId)

        val merged = if (existing == null) {
            canonical.copy(
                agenciaTag = canonical.agenciaTag.ifBlank { canonical.agencia ?: canonical.nombreAgencia ?: "" },
                fechaInicioMillis = incomingFecha,
                lastUpdated = canonical.lastUpdated.takeIf { it > 0 } ?: now,
                isSynced = true
            )
        } else {
            val estadoElegido = pickEstadoPreferAdvanced(existing.estado, canonical.estado, canonical.estadoClor)
            val idEstadoElegido = idEstadoFromLabel(estadoElegido)

            existing.copy(
                region = canonical.region ?: existing.region,
                provincia = canonical.provincia ?: existing.provincia,
                agencia = canonical.agencia ?: existing.agencia,
                nombreAgencia = preferMeaningful(canonical.nombreAgencia, existing.nombreAgencia),
                nise = preferMeaningful(canonical.nise, existing.nise),
                causa = preferMeaningful(canonical.causa, existing.causa),
                observaciones = preferMeaningful(canonical.observaciones, existing.observaciones),
                estado = estadoElegido,
                idEstadoAve = idEstadoElegido,
                idEstadoAranda = canonical.idEstadoAranda ?: existing.idEstadoAranda,
                lat = canonical.lat ?: existing.lat,
                lng = canonical.lng ?: existing.lng,
                clientesAfectados = preferMeaningful(canonical.clientesAfectados, existing.clientesAfectados),
                fechaInicioMillis = when {
                    canonical.fechaInicioMillis > 0 -> canonical.fechaInicioMillis
                    existing.fechaInicioMillis > 0 -> existing.fechaInicioMillis
                    else -> incomingFecha
                },
                horaInicioMillis = canonical.horaInicioMillis ?: existing.horaInicioMillis,
                horaFinalMillis = canonical.horaFinalMillis ?: existing.horaFinalMillis,
                atencionHoraInicioMillis = canonical.atencionHoraInicioMillis ?: existing.atencionHoraInicioMillis,
                atencionHoraFinalMillis = canonical.atencionHoraFinalMillis ?: existing.atencionHoraFinalMillis,
                horaLlegadaMillis = canonical.horaLlegadaMillis ?: existing.horaLlegadaMillis,
                kilometrajeInicio = canonical.kilometrajeInicio ?: existing.kilometrajeInicio,
                kilometrajeLlegada = canonical.kilometrajeLlegada ?: existing.kilometrajeLlegada,
                kilometrajeFinal = canonical.kilometrajeFinal ?: existing.kilometrajeFinal,
                agenciaTag = if (canonical.agenciaTag.isNotBlank()) canonical.agenciaTag else existing.agenciaTag,
                vehiculoAsignado = canonical.vehiculoAsignado ?: existing.vehiculoAsignado,
                tecnicoAsignadoUid = canonical.tecnicoAsignadoUid ?: existing.tecnicoAsignadoUid,
                tecnicoAsignadoNombre = preferMeaningful(canonical.tecnicoAsignadoNombre, existing.tecnicoAsignadoNombre),
                atendidoPorUid = canonical.atendidoPorUid ?: existing.atendidoPorUid,
                atendidoPorNombre = preferMeaningful(canonical.atendidoPorNombre, existing.atendidoPorNombre),
                materialesTexto = preferMeaningful(canonical.materialesTexto, existing.materialesTexto),
                materialesDetalleJson = mergeRemoteString(canonical.materialesDetalleJson, existing.materialesDetalleJson),
                tecnicosAtendieronJson = mergeRemoteString(canonical.tecnicosAtendieronJson, existing.tecnicosAtendieronJson),
                cliente = preferMeaningful(canonical.cliente, existing.cliente),
                localizacion = preferMeaningful(canonical.localizacion, existing.localizacion),
                direccion = preferSavedAddress(existing.direccion, canonical.direccion),
                tipoAfectacion = preferMeaningful(canonical.tipoAfectacion, existing.tipoAfectacion),
                numeroMedidor = preferMeaningful(canonical.numeroMedidor, existing.numeroMedidor),
                medidorCalle = preferMeaningful(canonical.medidorCalle, existing.medidorCalle),
                medidorPueblo = preferMeaningful(canonical.medidorPueblo, existing.medidorPueblo),
                medidorMetros = preferMeaningful(canonical.medidorMetros, existing.medidorMetros),
                medidorPoste = preferMeaningful(canonical.medidorPoste, existing.medidorPoste),
                lastUpdated = listOf(existing.lastUpdated, canonical.lastUpdated, now).maxOrNull() ?: now,
                isSynced = true
            )
        }

        dao.upsertAll(listOf(merged))
    }

    // ---------------------------------------------------------------------------------------------
    // Normalizadores (región / agencia) y utilitarios
    // ---------------------------------------------------------------------------------------------

    private fun stripAccentsLower(s: String): String {
        val n = Normalizer.normalize(s, Normalizer.Form.NFD)
        return DIACRITICS_REGEX.replace(n, "").lowercase(Locale.getDefault())
    }

    private fun normalizeKey(s: String?): String {
        if (s.isNullOrBlank()) return ""
        var k = stripAccentsLower(s)
        k = NON_ALNUM_SPACE_REGEX.replace(k, " ")
            .let { MULTI_SPACE_REGEX.replace(it, " ") }
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
        val n = MULTI_SPACE_REGEX.replace(stripAccentsLower(s), " ").trim()
        return n.split(" ").joinToString("") { titleCase(it) } // "Río Frío" -> "RioFrio"
    }



    private fun mergeRemoteString(remote: String?, local: String?): String? {
        val trimmed = remote?.trim()
        return when {
            trimmed == null -> local
            trimmed.isEmpty() -> local
            else -> trimmed
        }
    }

    private fun preferMeaningful(remote: String?, local: String?): String? {
        val trimmed = remote?.trim()
        return if (trimmed.isNullOrEmpty()) local else trimmed
    }

    private fun preferSavedAddress(local: String?, remote: String?): String? {
        val trimmedLocal = local?.trim()
        if (!trimmedLocal.isNullOrEmpty()) return trimmedLocal
        return remote?.trim()
    }

    private fun looksLikePlusCode(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val hasPlus = value.contains("+")
        val alnum = value.any { it.isLetterOrDigit() }
        return hasPlus && alnum
    }

    private fun shouldReplaceAddress(current: String?, candidate: String): Boolean {
        if (candidate.isBlank()) return false
        if (current.isNullOrBlank()) return true
        val normalizedCurrent = current.trim()
        if (normalizedCurrent.startsWith("cerca de", ignoreCase = true)) return true
        if (looksLikePlusCode(normalizedCurrent)) return true
        return false
    }

    private fun mergeForApi(existing: AveriaEntity, remote: AveriaEntity): AveriaEntity {
        val estadoElegido = pickEstadoPreferAdvanced(existing.estado, remote.estado, remote.estadoClor)
        val idEstadoElegido = idEstadoFromLabel(estadoElegido)
        return existing.copy(
            region = remote.region,
            provincia = remote.provincia,
            agencia = remote.agencia,
            nombreAgencia = remote.nombreAgencia,
            nise = preferMeaningful(remote.nise, existing.nise),
            causa = preferMeaningful(remote.causa, existing.causa),
            observaciones = preferMeaningful(remote.observaciones, existing.observaciones),
            estado = estadoElegido,
            idEstadoAve = idEstadoElegido,
            idEstadoAranda = remote.idEstadoAranda ?: existing.idEstadoAranda,
            lat = remote.lat ?: existing.lat,
            lng = remote.lng ?: existing.lng,
            clientesAfectados = preferMeaningful(remote.clientesAfectados, existing.clientesAfectados),
            fechaInicioMillis = remote.fechaInicioMillis.takeIf { it != 0L } ?: existing.fechaInicioMillis,
            horaInicioMillis = remote.horaInicioMillis ?: existing.horaInicioMillis,
            horaFinalMillis = remote.horaFinalMillis ?: existing.horaFinalMillis,
            atencionHoraInicioMillis = remote.atencionHoraInicioMillis ?: existing.atencionHoraInicioMillis,
            atencionHoraFinalMillis = remote.atencionHoraFinalMillis ?: existing.atencionHoraFinalMillis,
            horaLlegadaMillis = remote.horaLlegadaMillis ?: existing.horaLlegadaMillis,
            kilometrajeInicio = remote.kilometrajeInicio ?: existing.kilometrajeInicio,
            kilometrajeLlegada = remote.kilometrajeLlegada ?: existing.kilometrajeLlegada,
            kilometrajeFinal = remote.kilometrajeFinal ?: existing.kilometrajeFinal,
            agenciaTag = remote.agenciaTag,
            vehiculoAsignado = existing.vehiculoAsignado,
            tecnicoAsignadoUid = existing.tecnicoAsignadoUid,
            tecnicoAsignadoNombre = existing.tecnicoAsignadoNombre,
            atendidoPorUid = existing.atendidoPorUid,
            atendidoPorNombre = existing.atendidoPorNombre,
            materialesTexto = existing.materialesTexto,
            materialesDetalleJson = existing.materialesDetalleJson,
            tecnicosAtendieronJson = existing.tecnicosAtendieronJson,
            cliente = preferMeaningful(remote.cliente, existing.cliente),
            localizacion = preferMeaningful(remote.localizacion, existing.localizacion),
            direccion = existing.direccion,
            tipoAfectacion = preferMeaningful(remote.tipoAfectacion, existing.tipoAfectacion),
            numeroMedidor = preferMeaningful(remote.numeroMedidor, existing.numeroMedidor),
            medidorCalle = preferMeaningful(remote.medidorCalle, existing.medidorCalle),
            medidorPueblo = preferMeaningful(remote.medidorPueblo, existing.medidorPueblo),
            medidorMetros = preferMeaningful(remote.medidorMetros, existing.medidorMetros),
            medidorPoste = preferMeaningful(remote.medidorPoste, existing.medidorPoste),
            lastUpdated = maxOf(existing.lastUpdated, remote.lastUpdated),
            isSynced = true
        )
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

    // Alias de agencias -> nombre canónico
    private val AGENCIA_CANON = mapOf(
        "guapiles" to "Guápiles",
        "s guapiles" to "Guápiles",
        "sub guapiles" to "Guápiles",
        "guacimo" to "Guácimo",
        "siquirres" to "Siquirres",
        "s siquirres" to "Siquirres",
        "limon" to "Limón",
        "puerto limon" to "Limón",
        "talamanca" to "Talamanca",
        "bri bri" to "bribri",
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
        val source = nombreAgencia?.takeIf { it.isNotBlank() } ?: agencia ?: ""
        val k = normalizeKey(source)
        val canonNombre = AGENCIA_CANON[k] ?: run {
            if (source.isBlank()) null else titleCase(stripAccentsLower(source))
        }

        val tag = canonNombre?.let { slugTag(it) }
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
    // Estado + utilitarios
    // ---------------------------------------------------------------------------------------------
/**
 * ✅ Devuelve true si este remote vale la pena procesarlo.
 * Regla: si CLOR ya cerró (RESUELTA), SIEMPRE procesamos para bloquear UI y reflejar verdad.
 * Si no, procesamos si el estado de app es Pendiente/Resuelta (tu regla base).
 */
private fun shouldProcessRemote(estadoApp: String?, estadoClor: String?): Boolean {
    val app = normalizeEstadoLabel(estadoApp)
    val clor = estadoClor?.trim()?.uppercase(Locale.getDefault())
    return app == "Pendiente" ||
        app == "Asignada" ||
        app == "En atención" ||
        app == "Resuelta" ||
        clor == "RESUELTA"
}

/**
 * ✅ Helpers para "preferir" campos CLOR: si viene remoto con valor, lo usamos;
 * si viene vacío/no registra, dejamos el existente.
 */
private fun preferMeaningfulClor(remote: String?, existing: String?): String? {
    val r = remote?.trim()
    if (r.isNullOrBlank()) return existing
    if (r.equals("No registra", true)) return existing
    if (r.equals("Pendiente de verificar", true)) return existing
    return r
}

 private fun normalizeEstadoLabel(raw: String?): String {
    if (raw.isNullOrBlank()) return "Pendiente"
    val v = raw.trim().lowercase(Locale.getDefault())
    return when {
        v.contains("anul") -> "Anulada"
        v.contains("resuel") -> "Resuelta"
        v.contains("en at") || v.contains("atenci") -> "En atención"
        v.contains("asign") -> "Asignada"
        v.contains("pendien") -> "Pendiente"   // ✅ FIX: acepta PENDIENTE / pendiente
        v.contains("nuevo") -> "Pendiente"
        else -> "Pendiente" // ✅ opcional: yo lo dejaría así para no inventar estados raros
    }
}


    private fun isClorResuelta(estadoClor: String?): Boolean =
        estadoClor?.trim()?.equals("RESUELTA", ignoreCase = true) == true

    private fun pickEstadoPreferAdvanced(local: String?, remote: String?, estadoClor: String?): String {
        if (isClorResuelta(estadoClor)) return "Resuelta"
        val localNormalized = normalizeEstadoLabel(local)
        val remoteNormalized = normalizeEstadoLabel(remote)
        if (localNormalized == "Anulada" || remoteNormalized == "Anulada") return "Anulada"
        if (local.isNullOrBlank()) return remoteNormalized
        val order = mapOf(
            "Pendiente" to 1,
            "Asignada" to 2,
            "En atención" to 3,
            "Resuelta" to 4
        )
        val localRank = order[localNormalized] ?: 1
        val remoteRank = order[remoteNormalized] ?: 1
        return if (remoteRank > localRank) remoteNormalized else localNormalized
    }

    private fun idEstadoFromLabel(label: String?): Int = when (normalizeEstadoLabel(label)) {
        "Pendiente" -> 1
        "Asignada" -> 2
        "En atención" -> 3
        "Resuelta" -> 4
        "Anulada" -> 5
        else -> 1
    }

    private fun shouldCreateNewCase(estado: String?): Boolean =
        normalizeEstadoLabel(estado) in setOf("Pendiente", "Asignada", "En atención", "Resuelta")

    private fun shouldProcessRemote(estado: String?): Boolean {
        val normalized = normalizeEstadoLabel(estado)
        return normalized in setOf("Pendiente", "Asignada", "En atención", "Resuelta")
    }

    // ---------------------------------------------------------------------------------------------
    // Fechas, filtros
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
                5 -> "Anulada"
                else -> estadoTexto?.ifBlank { "Pendiente" } ?: "Pendiente"
            }
        )

    private fun shouldInclude(remote: IceAveria): Boolean =
        shouldProcessRemote(estadoFromIce(remote.idEstadoAve, remote.estado))

    private fun applyUserFilters(
        averias: List<AveriaEntity>,
        normalizedRegion: String?,
        agencyFilters: Set<String>
    ): List<AveriaEntity> {
        val porRegion = filterAveriasByRegion(averias, normalizedRegion)
        return filterAveriasByAgencies(porRegion, agencyFilters)
    }

    // ---------------------------------------------------------------------------------------------
    // ✅ FIX: Safe parsing Firebase (String -> Double)
    // ---------------------------------------------------------------------------------------------

    private fun Any?.asDoubleOrNull(): Double? = when (this) {
        is Number -> this.toDouble()
        is String -> this.trim().replace(",", ".").toDoubleOrNull()
        else -> null
    }

    private fun Any?.asLongOrNull(): Long? = when (this) {
        is Number -> this.toLong()
        is String -> this.trim().toLongOrNull()
        else -> null
    }

    private fun Any?.asIntOrNull(): Int? = when (this) {
        is Number -> this.toInt()
        is String -> this.trim().toIntOrNull()
        else -> null
    }

    private fun Any?.asStringOrNull(): String? =
        (this as? String)?.trim()?.takeIf { it.isNotBlank() }

    private fun DataSnapshot.getAveriaEntitySafe(): AveriaEntity? {
        runCatching { getValue(AveriaEntity::class.java) }
            .getOrNull()
            ?.let { return it }

        val map = value as? Map<*, *> ?: return null

        val caseId = map["caseId"].asStringOrNull()
            ?: key?.trim()
            ?: return null

return AveriaEntity(
    caseId = caseId,
    region = map["region"].asStringOrNull(),
    provincia = map["provincia"].asIntOrNull(), // ✅ ERA String, debe ser Int?
    agencia = map["agencia"].asStringOrNull(),
    nombreAgencia = map["nombreAgencia"].asStringOrNull(),
    nise = map["nise"].asStringOrNull(),
    causa = map["causa"].asStringOrNull(),
    observaciones = map["observaciones"].asStringOrNull()
        ?: map["descripcion"].asStringOrNull(),
    estado = map["estado"].asStringOrNull() ?: "Pendiente",
    idEstadoAve = map["idEstadoAve"].asIntOrNull(),
    idEstadoAranda = map["idEstadoAranda"].asIntOrNull(),
    lat = map["lat"].asDoubleOrNull(),
    lng = map["lng"].asDoubleOrNull(),
    clientesAfectados = map["clientesAfectados"].asStringOrNull(),
    fechaInicioMillis = map["fechaInicioMillis"].asLongOrNull() ?: 0L,
    horaInicioMillis = map["horaInicioMillis"].asLongOrNull(),
    horaFinalMillis = map["horaFinalMillis"].asLongOrNull(),
    atencionHoraInicioMillis = map["atencionHoraInicioMillis"].asLongOrNull(),
    atencionHoraFinalMillis = map["atencionHoraFinalMillis"].asLongOrNull(),
    horaLlegadaMillis = map["horaLlegadaMillis"].asLongOrNull(),
    kilometrajeInicio = map["kilometrajeInicio"].asDoubleOrNull(),
    kilometrajeLlegada = map["kilometrajeLlegada"].asDoubleOrNull(),
    kilometrajeFinal = map["kilometrajeFinal"].asDoubleOrNull(),
    agenciaTag = map["agenciaTag"].asStringOrNull() ?: "", // ✅ ERA nullable, aquí no puede
    vehiculoAsignado = map["vehiculoAsignado"].asStringOrNull(),
    tecnicoAsignadoUid = map["tecnicoAsignadoUid"].asStringOrNull(),
    tecnicoAsignadoNombre = map["tecnicoAsignadoNombre"].asStringOrNull(),
    atendidoPorUid = map["atendidoPorUid"].asStringOrNull(),
    atendidoPorNombre = map["atendidoPorNombre"].asStringOrNull(),
    materialesTexto = map["materialesTexto"].asStringOrNull(),
    materialesDetalleJson = map["materialesDetalleJson"].asStringOrNull(),
    tecnicosAtendieronJson = map["tecnicosAtendieronJson"].asStringOrNull(),
    cliente = map["cliente"].asStringOrNull(),
    localizacion = map["localizacion"].asStringOrNull(),
    direccion = map["direccion"].asStringOrNull(),
    tipoAfectacion = map["tipoAfectacion"].asStringOrNull(),
    numeroMedidor = map["numeroMedidor"].asStringOrNull(),
    medidorCalle = map["medidorCalle"].asStringOrNull(),
    medidorPueblo = map["medidorPueblo"].asStringOrNull(),
    medidorMetros = map["medidorMetros"].asStringOrNull(),
    estadoClor = map["estadoClor"].asStringOrNull(),
    causaClor = map["causaClor"].asStringOrNull(),
    observacionesClor = map["observacionesClor"].asStringOrNull(),
    medidorPoste = map["medidorPoste"].asStringOrNull(),
    isSynced = true,
    lastUpdated = map["lastUpdated"].asLongOrNull() ?: 0L
)

    }

    private suspend fun resolveSyncScope(forceRefresh: Boolean = false): SyncScope {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedSyncScope != null && (now - cachedSyncScopeAt) < SYNC_SCOPE_CACHE_MS) {
            return cachedSyncScope ?: SyncScope(null, null, null)
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            val fallback = SyncScope(null, null, null)
            cachedSyncScope = fallback
            cachedSyncScopeAt = now
            return fallback
        }

        val localUser = runCatching { db.usuarioDao().getByUid(uid) }.getOrNull()
        val localRegion = canonicalRegion(localUser?.region ?: localUser?.regionNombre)
        val localAgency = localUser?.agencia ?: localUser?.agenciaId
        val localCanon = canonicalizeAgency(localRegion, localAgency, localUser?.agencia)
        if (!localCanon.tag.isNullOrBlank() || !localRegion.isNullOrBlank()) {
            val scope = SyncScope(
                region = localRegion,
                agenciaTag = localCanon.tag,
                agencia = localCanon.agencia
            )
            cachedSyncScope = scope
            cachedSyncScopeAt = now
            return scope
        }

        val remoteSnap = runCatching { usersRef.child(uid).get().await() }.getOrNull()
        val remoteRegionRaw = remoteSnap?.child("region")?.getValue(String::class.java)
            ?: remoteSnap?.child("region_nombre")?.getValue(String::class.java)
        val remoteAgency = remoteSnap?.child("agencia")?.getValue(String::class.java)
            ?: remoteSnap?.child("agencia_id")?.getValue(String::class.java)
        val remoteAgencyName = remoteSnap?.child("nombreAgencia")?.getValue(String::class.java)
            ?: remoteAgency
        val remoteRegion = canonicalRegion(remoteRegionRaw)
        val remoteCanon = canonicalizeAgency(remoteRegion, remoteAgency, remoteAgencyName)

        val scope = SyncScope(
            region = remoteRegion,
            agenciaTag = remoteCanon.tag,
            agencia = remoteCanon.agencia
        )
        cachedSyncScope = scope
        cachedSyncScopeAt = now
        return scope
    }

    private suspend fun scopedAveriasQuery(limitToLast: Int? = null): Query {
        val syncScope = resolveSyncScope()
        val base = when {
            !syncScope.agenciaTag.isNullOrBlank() -> firebaseRef.orderByChild("agenciaTag").equalTo(syncScope.agenciaTag)
            !syncScope.agencia.isNullOrBlank() -> firebaseRef.orderByChild("agencia").equalTo(syncScope.agencia)
            !syncScope.region.isNullOrBlank() -> firebaseRef.orderByChild("region").equalTo(syncScope.region)
            else -> firebaseRef.orderByKey().limitToLast(FALLBACK_GLOBAL_LIMIT)
        }
        return if (limitToLast != null) base.limitToLast(limitToLast) else base
    }

    private suspend fun loadFirebaseCases(): Map<String, AveriaEntity> =
        runCatching {
            val snapshot = scopedAveriasQuery().get().await()
            snapshot.children.mapNotNull { child ->
                val remote0 = child.getAveriaEntitySafe() ?: return@mapNotNull null
                val normalizedEstado = normalizeEstadoLabel(remote0.estado)
                val remoteBase = remote0.copy(estado = normalizedEstado, isSynced = true)
                canonicalizeAgenciaFields(remoteBase)
            }.associateBy { it.caseId }
        }.getOrElse { error ->
            Log.e(TAG, "No se pudieron cargar averías desde Firebase", error)
            emptyMap()
        }

    // ---------------------------------------------------------------------------------------------
    // Map desde ICE
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

    suspend fun persistDireccion(caseId: String, direccion: String) {
        val cleaned = direccion.trim()
        if (cleaned.isEmpty()) return

        val existing = dao.getByCaseId(caseId) ?: return
        if (!shouldReplaceAddress(existing.direccion, cleaned)) return

        val now = System.currentTimeMillis()
        dao.actualizarDireccion(caseId, cleaned, now)

        runCatching {
            firebaseRef.child(caseId)
                .updateChildren(mapOf("direccion" to cleaned, "lastUpdated" to now))
                .await()
        }.onFailure { error ->
            Log.w(TAG, "No se pudo actualizar la dirección en Firebase para $caseId", error)
        }
    }

    private fun idEstadoAveFromLabelOrRemote(label: String, remoteId: Int?): Int =
        remoteId ?: idEstadoFromLabel(label)

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

    suspend fun anular(caseId: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.marcarAnulada(caseId, lastUpdated = now)
        syncSingle(caseId)
    }

    suspend fun eliminarAveria(caseId: String) = withContext(Dispatchers.IO) {
        firebaseRef.child(caseId).removeValue().await()
        dao.eliminarPorCaseId(caseId)
    }

    suspend fun enAtencion(caseId: String, data: AveriaActionData) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val base = dao.getByCaseId(caseId)
        val horaInicio = data.horaInicioMillis
            ?: base?.atencionHoraInicioMillis
            ?: base?.horaInicioMillis
        val horaFinal = data.horaFinalMillis
            ?: base?.atencionHoraFinalMillis
            ?: base?.horaFinalMillis
        val horaLlegada = data.horaLlegadaMillis ?: base?.horaLlegadaMillis
        val resumen = MaterialesSerializer.toSummary(data.materiales).ifBlank { null }
        val detalle = MaterialesSerializer.toJson(data.materiales)
        val tecnicosJson = TecnicosSerializer.toJson(data.tecnicos)
        val localizacion = data.localizacion?.trim()
        val cliente = data.cliente?.trim()
        val tipo = data.tipoAfectacion.name
        dao.actualizarAtencion(
            caseId = caseId,
            causa = data.causa,
            obs = data.observaciones,
            horaInicio = horaInicio,
            horaFinal = horaFinal,
            horaLlegada = horaLlegada,
            kmInicio = data.kilometrajeInicio,
            kmLlegada = data.kilometrajeLlegada,
            kmFinal = data.kilometrajeFinal,
            atendidoPorUid = data.atendidoPorUid,
            atendidoPorNombre = data.atendidoPorNombre,
            vehiculo = data.vehiculo,
            materialesResumen = resumen,
            materialesDetalle = detalle,
            tecnicosAtendieron = tecnicosJson,
            cliente = cliente,
            localizacion = localizacion,
            tipoAfectacion = tipo,
            numeroMedidor = data.numeroMedidor,
            medidorCalle = data.medidorCalle,
            medidorPueblo = data.medidorPueblo,
            medidorMetros = data.medidorMetros,
            medidorPoste = data.medidorPoste,
            lastUpdated = now,
            nuevoEstado = "En atención"
        )
        syncSingle(caseId)
        registrarMaterialesUsados(caseId, data)
        registrarKilometrajeFinal(data.vehiculo, data.kilometrajeFinal, data.horaFinalMillis ?: now)
    }

    suspend fun cerrar(caseId: String, data: AveriaActionData) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val base = dao.getByCaseId(caseId)
        val horaInicio = data.horaInicioMillis
            ?: base?.atencionHoraInicioMillis
            ?: base?.horaInicioMillis
        val horaFinal = data.horaFinalMillis
            ?: base?.atencionHoraFinalMillis
            ?: base?.horaFinalMillis
        val horaLlegada = data.horaLlegadaMillis ?: base?.horaLlegadaMillis
        val resumen = MaterialesSerializer.toSummary(data.materiales).ifBlank { null }
        val detalle = MaterialesSerializer.toJson(data.materiales)
        val tecnicosJson = TecnicosSerializer.toJson(data.tecnicos)
        val localizacion = data.localizacion?.trim()
        val cliente = data.cliente?.trim()
        val tipo = data.tipoAfectacion.name
        dao.actualizarAtencion(
            caseId = caseId,
            causa = data.causa,
            obs = data.observaciones,
            horaInicio = horaInicio,
            horaFinal = horaFinal,
            horaLlegada = horaLlegada,
            kmInicio = data.kilometrajeInicio,
            kmLlegada = data.kilometrajeLlegada,
            kmFinal = data.kilometrajeFinal,
            atendidoPorUid = data.atendidoPorUid,
            atendidoPorNombre = data.atendidoPorNombre,
            vehiculo = data.vehiculo,
            materialesResumen = resumen,
            materialesDetalle = detalle,
            tecnicosAtendieron = tecnicosJson,
            cliente = cliente,
            localizacion = localizacion,
            tipoAfectacion = tipo,
            numeroMedidor = data.numeroMedidor,
            medidorCalle = data.medidorCalle,
            medidorPueblo = data.medidorPueblo,
            medidorMetros = data.medidorMetros,
            medidorPoste = data.medidorPoste,
            lastUpdated = now,
            nuevoEstado = "Resuelta"
        )
        syncSingle(caseId)
        registrarMaterialesUsados(caseId, data)
        registrarKilometrajeFinal(data.vehiculo, data.kilometrajeFinal, data.horaFinalMillis ?: now)
    }

    private suspend fun registrarKilometrajeFinal(vehiculo: String?, kilometraje: Double?, timestamp: Long) {
        if (kilometraje == null || vehiculo.isNullOrBlank()) return
        val normalizada = VehiculoPlacaUtils.parsePlacaLong(vehiculo)
            ?: VehiculoPlacaUtils.parsePlacaLong(vehiculo.replace("ICE", "", ignoreCase = true))
            ?: return
        vehiculoDao.actualizarKilometrajeActual(normalizada, kilometraje)
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

    /**
 * ✅ Subida App → Firebase sin borrar campos CLOR.
 * IMPORTANTE: usar updateChildren (NO setValue) para no reventar estadoClor/obsClor/causaClor.
 */
private suspend fun pushToFirebase(entity: AveriaEntity) {
    val payload = entity.toFirebaseAppPayload()
    firebaseRef.child(entity.caseId).updateChildren(payload).await()
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
        "horaLlegadaMillis" to horaLlegadaMillis,
        "kilometrajeInicio" to kilometrajeInicio,
        "kilometrajeLlegada" to kilometrajeLlegada,
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
        "direccion" to direccion,
        "tipoAfectacion" to tipoAfectacion,
        "numeroMedidor" to numeroMedidor,
        "medidorCalle" to medidorCalle,
        "medidorPueblo" to medidorPueblo,
        "medidorMetros" to medidorMetros,
        "medidorPoste" to medidorPoste,
        "lastUpdated" to lastUpdated
    )

    /**
 * ✅ Payload SOLO de la APP / TÉCNICO.
 * NO incluye campos CLOR.
 * Se usa SIEMPRE con updateChildren().
 */
private fun AveriaEntity.toFirebaseAppPayload(): Map<String, Any?> = hashMapOf(
    "caseId" to caseId,

    // ===== ESTADO Y NOTAS DEL TÉCNICO =====
    "estado" to estado,
    "causa" to causa,
    "observaciones" to observaciones,

    // ===== TIEMPOS DE ATENCIÓN =====
    "horaInicioMillis" to horaInicioMillis,
    "horaFinalMillis" to horaFinalMillis,
    "atencionHoraInicioMillis" to atencionHoraInicioMillis,
    "atencionHoraFinalMillis" to atencionHoraFinalMillis,
    "horaLlegadaMillis" to horaLlegadaMillis,

    // ===== VEHÍCULO / KILOMETRAJE =====
    "kilometrajeInicio" to kilometrajeInicio,
    "kilometrajeLlegada" to kilometrajeLlegada,
    "kilometrajeFinal" to kilometrajeFinal,
    "vehiculoAsignado" to vehiculoAsignado,

    // ===== ASIGNACIÓN =====
    "tecnicoAsignadoUid" to tecnicoAsignadoUid,
    "tecnicoAsignadoNombre" to tecnicoAsignadoNombre,
    "atendidoPorUid" to atendidoPorUid,
    "atendidoPorNombre" to atendidoPorNombre,

    // ===== MATERIALES / TÉCNICOS =====
    "materialesTexto" to materialesTexto,
    "materialesDetalleJson" to materialesDetalleJson,
    "tecnicosAtendieronJson" to tecnicosAtendieronJson,

    // ===== DATOS COMPLEMENTARIOS APP =====
    "cliente" to cliente,
    "localizacion" to localizacion,
    "direccion" to direccion,
    "tipoAfectacion" to tipoAfectacion,
    "numeroMedidor" to numeroMedidor,
    "medidorCalle" to medidorCalle,
    "medidorPueblo" to medidorPueblo,
    "medidorMetros" to medidorMetros,
    "medidorPoste" to medidorPoste,

    // ===== HOUSEKEEPING =====
    "lastUpdated" to lastUpdated,
    "isSynced" to true
)

    private suspend fun registrarMaterialesUsados(caseId: String, data: AveriaActionData) {
        val vehiculoId = resolveVehiculoId(data.vehiculo)
        val now = System.currentTimeMillis()
        data.materiales.filter { it.cantidad > 0 }.forEach { uso ->
            val ref = materialesRef.child(uso.codigo)
            val map = mapOf(
                "Nombre" to uso.descripcion,
                "Cantidad" to uso.cantidad
            )
            ref.setValue(map).await()

            if (vehiculoId != null) {
                val movimiento = InventarioMovimientoAveriaEntity(
                    averiaId = caseId,
                    vehiculoId = vehiculoId,
                    materialCodigo = uso.codigo,
                    cantidad = uso.cantidad.toDouble(),
                    fechaRegistro = now,
                    tecnicoUid = data.atendidoPorUid,
                    tecnicoNombre = data.atendidoPorNombre
                )
                inventarioDao.registrarMovimientoAveria(movimiento)
                ajustarInventarioLocal(vehiculoId, uso)
            }
        }
    }

    private suspend fun ajustarInventarioLocal(vehiculoId: Int, uso: MaterialUso) {
        val existente = inventarioDao.obtenerItem(vehiculoId, uso.codigo) ?: return
        val nuevaCantidad = (existente.cantidadDisponible - uso.cantidad).coerceAtLeast(0.0)
        if (nuevaCantidad == 0.0) {
            inventarioDao.eliminarPorId(existente.id)
        } else {
            inventarioDao.upsert(
                existente.copy(
                    cantidadDisponible = nuevaCantidad
                )
            )
        }
    }

    private suspend fun resolveVehiculoId(vehiculo: String?): Int? {
        if (vehiculo.isNullOrBlank()) return null
        val placaLong = VehiculoPlacaUtils.parsePlacaLong(vehiculo)
            ?: VehiculoPlacaUtils.parsePlacaLong(vehiculo.replace("ICE", "", ignoreCase = true))
            ?: return null
        return vehiculoDao.buscarPorPlaca(placaLong)?.id
    }

    // ---------------------------------------------------------------------------------------------
    // Firebase: pull (una vez) y realtime
    // ---------------------------------------------------------------------------------------------

    data class PullResult(
        val newCases: List<AveriaEntity>,
        val hadLocalData: Boolean
    )

    suspend fun pullFromFirebaseOnce(): PullResult = withContext(Dispatchers.IO) {
        val current = dao.all().associateBy { it.caseId }
        val hadLocalData = current.isNotEmpty()
        try {
            val snapshot = scopedAveriasQuery().get().await()
            val updated = mutableListOf<AveriaEntity>()
            val newlyCreated = mutableListOf<AveriaEntity>()
            snapshot.children.forEach { child ->
                val remote0 = child.getAveriaEntitySafe() ?: return@forEach
                val normalizedEstado = normalizeEstadoLabel(remote0.estado)
                val remoteBase = remote0.copy(estado = normalizedEstado, isSynced = true)
                val remote = canonicalizeAgenciaFields(remoteBase)

                val existing = current[remote.caseId]
                if (!shouldProcessRemote(remote.estado, remote.estadoClor)) return@forEach

                when {
                    existing == null -> if (shouldCreateNewCase(remote.estado)) {
                        updated += remote
                        newlyCreated += remote
                    }

                   existing.isSynced && remote.lastUpdated >= existing.lastUpdated -> {
    // ✅ Estado app se decide con tu lógica (pickEstadoPreferAdvanced)
        val estadoElegido = pickEstadoPreferAdvanced(existing.estado, remote.estado, remote.estadoClor)
    val idEstadoElegido = idEstadoFromLabel(estadoElegido)

    updated += existing.copy(
        // ==========================================================
        // NEUTROS / API (se pueden refrescar sin romper al técnico)
        // ==========================================================
        region = remote.region,
        provincia = remote.provincia,
        agencia = remote.agencia,
        nombreAgencia = remote.nombreAgencia,
        nise = remote.nise,
        clientesAfectados = remote.clientesAfectados,
        lat = remote.lat,
        lng = remote.lng,
        fechaInicioMillis = remote.fechaInicioMillis,
        horaInicioMillis = remote.horaInicioMillis,
        horaFinalMillis = remote.horaFinalMillis,
        horaLlegadaMillis = existing.horaLlegadaMillis,

        // ==========================================================
        // ✅ APP/TÉCNICO: usar remoto si está más nuevo (sin borrar datos útiles)
        // ==========================================================
        estado = estadoElegido,
        idEstadoAve = idEstadoElegido,
        idEstadoAranda = remote.idEstadoAranda, // este sí puede venir de CLOR
        causa = preferMeaningful(remote.causa, existing.causa),
        observaciones = preferMeaningful(remote.observaciones, existing.observaciones),

        atencionHoraInicioMillis = remote.atencionHoraInicioMillis,
        atencionHoraFinalMillis = remote.atencionHoraFinalMillis,
        kilometrajeInicio = remote.kilometrajeInicio,
        kilometrajeLlegada = remote.kilometrajeLlegada,
        kilometrajeFinal = remote.kilometrajeFinal,
        vehiculoAsignado = remote.vehiculoAsignado,
        tecnicoAsignadoUid = remote.tecnicoAsignadoUid,
        tecnicoAsignadoNombre = remote.tecnicoAsignadoNombre,
        atendidoPorUid = remote.atendidoPorUid,
        atendidoPorNombre = remote.atendidoPorNombre,
        materialesTexto = preferMeaningful(remote.materialesTexto, existing.materialesTexto),
        materialesDetalleJson = preferMeaningful(remote.materialesDetalleJson, existing.materialesDetalleJson),
        tecnicosAtendieronJson = mergeRemoteString(remote.tecnicosAtendieronJson, existing.tecnicosAtendieronJson),
        cliente = preferMeaningful(remote.cliente, existing.cliente),
        localizacion = preferMeaningful(remote.localizacion, existing.localizacion),
        direccion = preferMeaningful(remote.direccion, existing.direccion),
        tipoAfectacion = preferMeaningful(remote.tipoAfectacion, existing.tipoAfectacion),
        numeroMedidor = preferMeaningful(remote.numeroMedidor, existing.numeroMedidor),
        medidorCalle = preferMeaningful(remote.medidorCalle, existing.medidorCalle),
        medidorPueblo = preferMeaningful(remote.medidorPueblo, existing.medidorPueblo),
        medidorMetros = preferMeaningful(remote.medidorMetros, existing.medidorMetros),
        medidorPoste = preferMeaningful(remote.medidorPoste, existing.medidorPoste),
        agenciaTag = remote.agenciaTag,

        // ==========================================================
        // ✅ CLOR separado (estos sí deben actualizarse desde remote)
        // ==========================================================
        estadoClor = preferMeaningfulClor(remote.estadoClor, existing.estadoClor),
        causaClor = preferMeaningfulClor(remote.causaClor, existing.causaClor),
        observacionesClor = preferMeaningfulClor(remote.observacionesClor, existing.observacionesClor),

        // ==========================================================
        // Housekeeping
        // ==========================================================
        lastUpdated = maxOf(existing.lastUpdated, remote.lastUpdated),
        isSynced = true
    )
}

                }
            }
            if (updated.isNotEmpty()) dao.upsertAll(updated)

            // Nota: no eliminamos localmente si el caso no viene en Firebase.
            // La fuente ICE puede tener averías que aún no están replicadas en Firebase
            // y eliminarlas provoca que desaparezcan (especialmente Pendientes/Asignadas).
            PullResult(newlyCreated, hadLocalData)
        } catch (t: Throwable) {
            Log.e(TAG, "Firebase pull failed", t)
            PullResult(emptyList(), hadLocalData)
        }
    }

    suspend fun startRealtimeListener(
        onNewAverias: ((List<AveriaEntity>) -> Unit)? = null,
        suppressInitialNotification: Boolean = onNewAverias != null
    ) {
        if (realtimeListener != null) return
        realtimeCallback = onNewAverias
        this.suppressInitialNotification = suppressInitialNotification
        realtimeEmittedOnce = false

        val query = runCatching { scopedAveriasQuery(limitToLast = REALTIME_MAX_ITEMS) }
            .getOrElse {
                Log.w(TAG, "No se pudo resolver ámbito realtime, usando fallback acotado", it)
                firebaseRef.orderByKey().limitToLast(FALLBACK_GLOBAL_LIMIT)
            }
        realtimeQuery = query

        realtimeListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                scope.launch { processRealtimeChild(snapshot) }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                scope.launch { processRealtimeChild(snapshot) }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                // No eliminamos localmente cuando desaparece en Firebase para evitar pérdida de casos.
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Realtime listener cancelled", error.toException())
            }
        }

        query.addChildEventListener(realtimeListener!!)
        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                realtimeEmittedOnce = true
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "No se pudo confirmar carga inicial realtime", error.toException())
                realtimeEmittedOnce = true
            }
        })
    }

    private suspend fun processRealtimeChild(snapshot: DataSnapshot) {
        val remote0 = snapshot.getAveriaEntitySafe() ?: return
        val normalizedEstado = normalizeEstadoLabel(remote0.estado)
        val remoteBase = remote0.copy(estado = normalizedEstado, isSynced = true)
        val remote = canonicalizeAgenciaFields(remoteBase)
        if (!shouldProcessRemote(remote.estado, remote.estadoClor)) return

        val existing = dao.getByCaseId(remote.caseId)
        when {
            existing == null -> {
                if (!shouldCreateNewCase(remote.estado)) return
                dao.upsertAll(listOf(remote))
                val shouldNotify = realtimeEmittedOnce || !suppressInitialNotification
                if (shouldNotify) {
                    realtimeCallback?.invoke(listOf(remote))
                }
            }

            !existing.isSynced -> {
                if (remote.lastUpdated > existing.lastUpdated) {
                    dao.upsertAll(listOf(remote))
                }
            }

            remote.lastUpdated >= existing.lastUpdated -> {
                val estadoElegido = pickEstadoPreferAdvanced(existing.estado, remote.estado, remote.estadoClor)
                val idEstadoElegido = idEstadoFromLabel(estadoElegido)
                dao.upsertAll(
                    listOf(
                        existing.copy(
                            region = remote.region,
                            provincia = remote.provincia,
                            agencia = remote.agencia,
                            nombreAgencia = remote.nombreAgencia,
                            nise = remote.nise,
                            causa = preferMeaningful(remote.causa, existing.causa),
                            observaciones = preferMeaningful(remote.observaciones, existing.observaciones),
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
                            horaLlegadaMillis = remote.horaLlegadaMillis,
                            kilometrajeInicio = remote.kilometrajeInicio,
                            kilometrajeLlegada = remote.kilometrajeLlegada,
                            kilometrajeFinal = remote.kilometrajeFinal,
                            vehiculoAsignado = remote.vehiculoAsignado,
                            tecnicoAsignadoUid = remote.tecnicoAsignadoUid,
                            tecnicoAsignadoNombre = remote.tecnicoAsignadoNombre,
                            atendidoPorUid = remote.atendidoPorUid,
                            atendidoPorNombre = remote.atendidoPorNombre,
                            materialesTexto = preferMeaningful(remote.materialesTexto, existing.materialesTexto),
                            materialesDetalleJson = preferMeaningful(remote.materialesDetalleJson, existing.materialesDetalleJson),
                            tecnicosAtendieronJson = mergeRemoteString(remote.tecnicosAtendieronJson, existing.tecnicosAtendieronJson),
                            cliente = preferMeaningful(remote.cliente, existing.cliente),
                            localizacion = preferMeaningful(remote.localizacion, existing.localizacion),
                            direccion = preferMeaningful(remote.direccion, existing.direccion),
                            tipoAfectacion = preferMeaningful(remote.tipoAfectacion, existing.tipoAfectacion),
                            numeroMedidor = preferMeaningful(remote.numeroMedidor, existing.numeroMedidor),
                            medidorCalle = preferMeaningful(remote.medidorCalle, existing.medidorCalle),
                            medidorPueblo = preferMeaningful(remote.medidorPueblo, existing.medidorPueblo),
                            medidorMetros = preferMeaningful(remote.medidorMetros, existing.medidorMetros),
                            medidorPoste = preferMeaningful(remote.medidorPoste, existing.medidorPoste),
                            agenciaTag = remote.agenciaTag,
                            lastUpdated = maxOf(existing.lastUpdated, remote.lastUpdated),
                            isSynced = true
                        )
                    )
                )
            }
        }
    }

    fun stopRealtimeListener() {
        val listener = realtimeListener
        val query = realtimeQuery
        if (listener != null) {
            if (query != null) {
                query.removeEventListener(listener)
            } else {
                firebaseRef.removeEventListener(listener)
            }
        }
        realtimeListener = null
        realtimeQuery = null
        scope.coroutineContext.cancelChildren()
        realtimeCallback = null
        suppressInitialNotification = false
        realtimeEmittedOnce = false
    }

    companion object {
        private const val TAG = "AveriasRepo"
        private const val REALTIME_MAX_ITEMS = 400
        private const val FALLBACK_GLOBAL_LIMIT = 300
        private const val SYNC_SCOPE_CACHE_MS = 60_000L
        private val DIACRITICS_REGEX = "\\p{InCombiningDiacriticalMarks}+".toRegex()
        private val NON_ALNUM_SPACE_REGEX = "[^a-z0-9 ]".toRegex()
        private val MULTI_SPACE_REGEX = "\\s+".toRegex()
    }
}
