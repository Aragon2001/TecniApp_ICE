package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.util.Log
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AveriaEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.InventarioMovimientoAveriaEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.MedidorEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase
import com.Arasoftsolutions.tecniapp_ice.ui.vehiculo.VehiculoPlacaUtils
import com.google.firebase.database.DataSnapshot
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale

class AveriasRepository(private val db: AppDatabase) {


    private val dao get() = db.averiaDao()
    private val vehiculoDao get() = db.vehiculoDao()
    private val medidorDao get() = db.medidorDao()
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

    private val vehiculosDatosRef = FirebaseDatabase
        .getInstance("https://tecniapp-ice-datosgenerales.firebaseio.com/")
        .reference
        .child("vehiculos")

    private val medidoresRef = FirebaseDatabase
        .getInstance("https://tecniapp-ice-default-rtdb.firebaseio.com/")
        .reference

    private data class SyncScope(
        val uid: String?,
        val rol: String?,
        val region: String?,
        val agenciaTag: String?,
        val agencia: String?
    )

    private var cachedSyncScope: SyncScope? = null
    private var cachedSyncScopeAt: Long = 0L

    private enum class ScopedRole {
        TECNICO,
        SUPERVISOR_AGENCIA,
        ADMIN_REGIONAL,
        DESCONOCIDO
    }

    private data class QueryDescriptor(
        val field: String,
        val value: String
    )

    private data class ScopedQueryPlan(
        val role: ScopedRole,
        val primary: QueryDescriptor?,
        val fallback: QueryDescriptor?
    )

    private enum class ScopedSource {
        PRIMARY,
        FALLBACK
    }

    private data class MergeCandidate(
        val entity: AveriaEntity,
        val source: ScopedSource
    )

    private val operativeStates = setOf("Pendiente", "Asignada", "En atención")
    private val useScopedAverias = true

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
                evidenciasJson = mergeRemoteString(canonical.evidenciasJson, existing.evidenciasJson),
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

    private fun normTag(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return Normalizer.normalize(value.trim().uppercase(Locale.getDefault()), Normalizer.Form.NFD)
            .replace(Regex("[\u0300-\u036f]"), "")
            .replace(Regex("[^A-Z0-9]+"), "_")
            .replace(Regex("^_+|_+$"), "")
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


    // Regiones canónicas
    private val REGION_CANON = mapOf(
        "huetar atlantica" to "Huetar Atlántica",
        "huetar atlantico" to "Huetar Atlántica",
        "atlantica" to "Huetar Atlántica",
        "atlantico" to "Huetar Atlántica",
        "caribe" to "Huetar Atlántica",
        // ✅ "HUETAR" solo puede venir del perfil de usuario como alias truncado
        // Lo mapeamos a Atlántica porque es la región más común con ese prefijo en ICE
        // Si el usuario es de Norte, su perfil tendrá "HUETAR NORTE" o "NORTE"
        "huetar" to "Huetar Atlántica",
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

        val tag = canonNombre?.let { normTag(it) }
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



    // ---------------------------------------------------------------------------------------------
    // Fechas, filtros
    // ---------------------------------------------------------------------------------------------

    private val datePatterns = listOf(
        "yyyy-MM-dd'T'HH:mm",
        "yyyy-MM-dd HH:mm:ss"
    )




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
            evidenciasJson = map["evidenciasJson"].asStringOrNull(),
            isSynced = true,
            lastUpdated = map["lastUpdated"].asLongOrNull() ?: 0L
        )

    }

    private suspend fun resolveSyncScope(forceRefresh: Boolean = false): SyncScope {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedSyncScope != null && (now - cachedSyncScopeAt) < SYNC_SCOPE_CACHE_MS) {
            return cachedSyncScope ?: SyncScope(
                uid = null,
                rol = null,
                region = null,
                agenciaTag = null,
                agencia = null
            )
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            val fallback = SyncScope(
                uid = null,
                rol = null,
                region = null,
                agenciaTag = null,
                agencia = null
            )
            cachedSyncScope = fallback
            cachedSyncScopeAt = now
            return fallback
        }

        val localUser = runCatching { db.usuarioDao().getByUid(uid) }.getOrNull()
        val localRole = localUser?.rol?.trim()?.takeIf { it.isNotEmpty() }
        val localRegion = canonicalRegion(localUser?.region ?: localUser?.regionNombre)
        val localAgency = localUser?.agencia ?: localUser?.agenciaId
        val localCanon = canonicalizeAgency(localRegion, localAgency, localUser?.agencia)
        if (!localCanon.tag.isNullOrBlank() || !localRegion.isNullOrBlank() || !localRole.isNullOrBlank()) {
            val scope = SyncScope(
                uid = uid,
                rol = localRole,
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
        val remoteRole = remoteSnap?.child("rol")?.getValue(String::class.java)?.trim()
            ?.takeIf { it.isNotEmpty() }
        val remoteRegion = canonicalRegion(remoteRegionRaw)
        val remoteCanon = canonicalizeAgency(remoteRegion, remoteAgency, remoteAgencyName)

        val scope = SyncScope(
            uid = uid,
            rol = remoteRole,
            region = remoteRegion,
            agenciaTag = remoteCanon.tag,
            agencia = remoteCanon.agencia
        )
        cachedSyncScope = scope
        cachedSyncScopeAt = now
        return scope
    }

    private fun buildScopedQueryPlan(scope: SyncScope): ScopedQueryPlan {
        val role = normalizeScopedRole(scope.rol)
        val agenciaTag = scope.agenciaTag?.trim().orEmpty()
        val region = scope.region?.trim().orEmpty()

        val primary = region.takeIf { it.isNotEmpty() }?.let {
            QueryDescriptor(field = "region", value = it)
        }
        // Importante: la descarga/sincronización debe quedar acotada por región,
        // no por una sola agencia del usuario.
        val fallback: QueryDescriptor? = null

        val plan = ScopedQueryPlan(
            role = role,
            primary = primary,
            fallback = fallback
        )

        Log.i(
            TAG,
            "[SCOPED_PLAN] role=${plan.role} primary=${plan.primary?.field}:${plan.primary?.value ?: "null"} fallback=${plan.fallback?.field}:${plan.fallback?.value ?: "null"}"
        )
        return plan
    }

    private fun normalizeScopedRole(raw: String?): ScopedRole {
        val value = raw?.trim()?.lowercase(Locale.getDefault()).orEmpty()
        if (value.isBlank()) return ScopedRole.DESCONOCIDO

        return when {
            value.contains("tecnico") || value.contains("técnico") -> ScopedRole.TECNICO
            value.contains("supervisor") || value.contains("agencia") -> ScopedRole.SUPERVISOR_AGENCIA
            value.contains("admin") || value.contains("regional") -> ScopedRole.ADMIN_REGIONAL
            else -> ScopedRole.DESCONOCIDO
        }
    }

    private fun shouldRunFallback(primaryCount: Int): Boolean = primaryCount == 0

    private fun buildRegionVariants(canonical: String): List<String> {
        if (canonical.isBlank()) return emptyList()
        val variants = mutableSetOf<String>()

        // ✅ Expandir alias cortos/truncados que pueden venir del perfil del usuario
        // El campo 'region' en Room puede estar guardado como "HUETAR", "HUETAR ATLANTICA",
        // "Huetar Norte", etc. Expandimos a todos los valores posibles que Firebase puede tener.
        val expansions = when {
            canonical.contains("HUETAR", ignoreCase = true) && canonical.contains("ATL", ignoreCase = true) ->
                listOf("Huetar Atlántica", "Huetar Atlantica", "HUETAR ATLANTICA", "huetar atlantica")
            canonical.equals("HUETAR", ignoreCase = true) ->
                // Ambiguo — podría ser Atlántica o Norte; intentamos ambas
                listOf(
                    "Huetar Atlántica", "Huetar Atlantica", "HUETAR ATLANTICA", "huetar atlantica",
                    "Huetar Norte", "HUETAR NORTE", "huetar norte"
                )
            canonical.contains("HUETAR", ignoreCase = true) && canonical.contains("NORTE", ignoreCase = true) ->
                listOf("Huetar Norte", "HUETAR NORTE", "huetar norte")
            canonical.contains("CENTRAL", ignoreCase = true) ->
                listOf("Central", "CENTRAL", "central")
            canonical.contains("CHOROTEGA", ignoreCase = true) ->
                listOf("Chorotega", "CHOROTEGA", "chorotega")
            canonical.contains("BRUNCA", ignoreCase = true) ->
                listOf("Brunca", "BRUNCA", "brunca")
            canonical.contains("PACIF", ignoreCase = true) ->
                listOf("Pacífico Central", "Pacifico Central", "PACIFICO CENTRAL", "pacifico central")
            else -> emptyList()
        }
        variants += expansions

        // Agregar el valor original y sus variantes normalizadas
        variants += canonical
        variants += canonical.trim()
        val noAccents = Normalizer.normalize(canonical, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        variants += noAccents
        variants += noAccents.uppercase(Locale.getDefault())
        variants += noAccents.lowercase(Locale.getDefault())
        variants += canonical.uppercase(Locale.getDefault())
        variants += canonical.lowercase(Locale.getDefault())

        return variants.filter { it.isNotBlank() }.distinct()
    }

    private fun buildFallbackQuery(plan: ScopedQueryPlan): Query? {
        val fallback = plan.fallback ?: return null
        return firebaseRef.orderByChild(fallback.field).equalTo(fallback.value)
    }

    private fun isOperativeEstado(estado: String?): Boolean {
        val normalized = normalizeEstadoLabel(estado)
        return normalized in operativeStates
    }

    /**
     * Deduplica averías por caseId.
     *
     * Regla de conflicto:
     * 1) Gana mayor lastUpdated.
     * 2) Si lastUpdated empata, gana PRIMARY sobre FALLBACK.
     */
    private fun mergeByCaseId(
        primary: List<AveriaEntity>,
        fallback: List<AveriaEntity>
    ): List<AveriaEntity> {
        val merged = LinkedHashMap<String, MergeCandidate>()

        primary.forEach { entity ->
            val key = entity.caseId.trim()
            if (key.isNotEmpty()) {
                merged[key] = MergeCandidate(entity = entity, source = ScopedSource.PRIMARY)
            }
        }

        fallback.forEach { entity ->
            val key = entity.caseId.trim()
            if (key.isEmpty()) return@forEach
            val existing = merged[key]
            if (existing == null) {
                merged[key] = MergeCandidate(entity = entity, source = ScopedSource.FALLBACK)
                return@forEach
            }

            val existingUpdated = existing.entity.lastUpdated
            val incomingUpdated = entity.lastUpdated
            val shouldReplace = when {
                incomingUpdated > existingUpdated -> true
                incomingUpdated < existingUpdated -> false
                else -> existing.source != ScopedSource.PRIMARY
            }

            if (shouldReplace) {
                merged[key] = MergeCandidate(entity = entity, source = ScopedSource.FALLBACK)
            }
        }

        return merged.values.map { it.entity }
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


    private fun mergeTecnicosAtendieron(
        existentes: List<TecnicoAtencion>,
        nuevos: List<TecnicoAtencion>
    ): List<TecnicoAtencion> {
        fun normalizedName(value: String): String = value.trim().lowercase(Locale.getDefault())

        fun identityKey(tecnico: TecnicoAtencion): String {
            val uid = tecnico.uid?.trim().orEmpty()
            if (uid.isNotEmpty()) return "uid:$uid"
            val cedula = tecnico.cedula?.trim().orEmpty()
            if (cedula.isNotEmpty()) return "cedula:$cedula"
            val nombre = normalizedName(tecnico.nombre)
            return "nombre:$nombre"
        }

        fun mergeOne(existing: TecnicoAtencion?, incoming: TecnicoAtencion): TecnicoAtencion {
            if (existing == null) {
                val normalizedName = incoming.nombre.trim().ifBlank { incoming.cedula?.trim().orEmpty() }
                return incoming.copy(nombre = normalizedName)
            }
            val mergedName = incoming.nombre.trim().ifBlank { existing.nombre.trim() }
            val mergedTimestamp = maxOf(existing.timestamp ?: Long.MIN_VALUE, incoming.timestamp ?: Long.MIN_VALUE)
                .takeIf { it != Long.MIN_VALUE }
            return TecnicoAtencion(
                uid = incoming.uid?.trim().takeIf { !it.isNullOrBlank() }
                    ?: existing.uid?.trim().takeIf { !it.isNullOrBlank() },
                cedula = incoming.cedula?.trim().takeIf { !it.isNullOrBlank() }
                    ?: existing.cedula?.trim().takeIf { !it.isNullOrBlank() },
                nombre = mergedName.ifBlank {
                    incoming.cedula?.trim().orEmpty().ifBlank { existing.cedula?.trim().orEmpty() }
                },
                rol = incoming.rol?.trim().takeIf { !it.isNullOrBlank() }
                    ?: existing.rol?.trim().takeIf { !it.isNullOrBlank() },
                timestamp = mergedTimestamp,
                fuente = incoming.fuente?.trim().takeIf { !it.isNullOrBlank() }
                    ?: existing.fuente?.trim().takeIf { !it.isNullOrBlank() }
            )
        }

        val merged = LinkedHashMap<String, TecnicoAtencion>()
        existentes.forEach { tecnico ->
            val key = identityKey(tecnico)
            merged[key] = mergeOne(merged[key], tecnico)
        }
        nuevos.forEach { tecnico ->
            val key = identityKey(tecnico)
            merged[key] = mergeOne(merged[key], tecnico)
        }
        return merged.values
            .filter { tecnico ->
                tecnico.nombre.trim().isNotEmpty() || !tecnico.cedula.isNullOrBlank() || !tecnico.uid.isNullOrBlank()
            }
    }


    private fun ensurePrincipalInTecnicos(
        tecnicos: List<TecnicoAtencion>,
        atendidoPorUid: String?,
        atendidoPorNombre: String?
    ): List<TecnicoAtencion> {
        val uid = atendidoPorUid?.trim().takeIf { !it.isNullOrBlank() }
        val nombre = atendidoPorNombre?.trim().takeIf { !it.isNullOrBlank() } ?: uid
        if (uid == null && nombre.isNullOrBlank()) return tecnicos

        val principal = TecnicoAtencion(
            uid = uid,
            cedula = null,
            nombre = nombre.toString(),
            rol = null,
            timestamp = System.currentTimeMillis(),
            fuente = "principal"
        )
        return mergeTecnicosAtendieron(tecnicos, listOf(principal))
    }

    suspend fun enAtencion(caseId: String, data: AveriaActionData) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val base = dao.getByCaseId(caseId)
        actualizarCambioMedidorSiAplica(caseId, base?.numeroMedidor, data)
        val horaInicio = data.horaInicioMillis
            ?: base?.atencionHoraInicioMillis
            ?: base?.horaInicioMillis
        val horaFinal = data.horaFinalMillis
            ?: base?.atencionHoraFinalMillis
            ?: base?.horaFinalMillis
        val horaLlegada = data.horaLlegadaMillis ?: base?.horaLlegadaMillis
        val resumen = MaterialesSerializer.toSummary(data.materiales).ifBlank { null }
        val detalle = MaterialesSerializer.toJson(data.materiales)
        val tecnicosEntrada = ensurePrincipalInTecnicos(
            data.tecnicos,
            data.atendidoPorUid,
            data.atendidoPorNombre
        )
        val principalPresent = data.atendidoPorUid?.isNotBlank() == true || data.atendidoPorNombre?.isNotBlank() == true
        Log.i(TAG, "[AVERIA_TECNICOS][PRINCIPAL_ENSURE] input=${data.tecnicos.size} output=${tecnicosEntrada.size} principal_present=$principalPresent")
        val tecnicosExistentes = TecnicosSerializer.fromJson(base?.tecnicosAtendieronJson)
        val tecnicosMergeados = mergeTecnicosAtendieron(tecnicosExistentes, tecnicosEntrada)
        Log.i(TAG, "[AVERIA_TECNICOS][MERGE] existing=${tecnicosExistentes.size} incoming=${tecnicosEntrada.size} result=${tecnicosMergeados.size}")
        val tecnicosJson = TecnicosSerializer.toJson(tecnicosMergeados)
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
            evidenciasJson = EvidenciasSerializer.toJson(data.evidencias),
            lastUpdated = now,
            nuevoEstado = "En atención"
        )
        syncSingle(caseId)
        registrarMaterialesUsados(caseId, data)
        registrarKilometrajeFinal(data.vehiculo, data.kilometrajeFinal)
    }

    suspend fun cerrar(caseId: String, data: AveriaActionData) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val base = dao.getByCaseId(caseId)
        actualizarCambioMedidorSiAplica(caseId, base?.numeroMedidor, data)
        val horaInicio = data.horaInicioMillis
            ?: base?.atencionHoraInicioMillis
            ?: base?.horaInicioMillis
        val horaFinal = data.horaFinalMillis
            ?: base?.atencionHoraFinalMillis
            ?: base?.horaFinalMillis
        val horaLlegada = data.horaLlegadaMillis ?: base?.horaLlegadaMillis
        val resumen = MaterialesSerializer.toSummary(data.materiales).ifBlank { null }
        val detalle = MaterialesSerializer.toJson(data.materiales)
        val tecnicosEntrada = ensurePrincipalInTecnicos(
            data.tecnicos,
            data.atendidoPorUid,
            data.atendidoPorNombre
        )
        val principalPresent = data.atendidoPorUid?.isNotBlank() == true || data.atendidoPorNombre?.isNotBlank() == true
        Log.i(TAG, "[AVERIA_TECNICOS][PRINCIPAL_ENSURE] input=${data.tecnicos.size} output=${tecnicosEntrada.size} principal_present=$principalPresent")
        val tecnicosExistentes = TecnicosSerializer.fromJson(base?.tecnicosAtendieronJson)
        val tecnicosMergeados = mergeTecnicosAtendieron(tecnicosExistentes, tecnicosEntrada)
        Log.i(TAG, "[AVERIA_TECNICOS][MERGE] existing=${tecnicosExistentes.size} incoming=${tecnicosEntrada.size} result=${tecnicosMergeados.size}")
        val tecnicosJson = TecnicosSerializer.toJson(tecnicosMergeados)
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
            evidenciasJson = EvidenciasSerializer.toJson(data.evidencias),
            lastUpdated = now,
            nuevoEstado = "Resuelta"
        )
        syncSingle(caseId)
        registrarMaterialesUsados(caseId, data)
        registrarKilometrajeFinal(data.vehiculo, data.kilometrajeFinal)
    }

    private suspend fun registrarKilometrajeFinal(vehiculo: String?, kilometraje: Double?) {
        if (kilometraje == null || vehiculo.isNullOrBlank()) return
        val normalizada = VehiculoPlacaUtils.parsePlacaLong(vehiculo)
            ?: VehiculoPlacaUtils.parsePlacaLong(vehiculo.replace("ICE", "", ignoreCase = true))
            ?: return
        vehiculoDao.actualizarKilometrajeActual(normalizada, kilometraje)

        val placaNormalizada = normalizada.toString()
        val updates = mapOf<String, Any>(
            "kmActual" to kilometraje,
            "kilometrajeActual" to kilometraje,
            "meta/kmActual" to kilometraje,
            "meta/kilometrajeActual" to kilometraje,
            "meta/updatedAt" to System.currentTimeMillis()
        )

        val targetKey = resolveVehiculoNodeKey(placaNormalizada)
        if (targetKey == null) {
            Log.w(TAG, "No se encontró nodo remoto para placa=$placaNormalizada al actualizar km de avería")
            return
        }

        runCatching { vehiculosDatosRef.child(targetKey).updateChildren(updates).await() }
            .onFailure {
                Log.w(
                    TAG,
                    "No se pudo actualizar kilometraje remoto para placa=$placaNormalizada (key=$targetKey)",
                    it
                )
            }
    }

    private suspend fun resolveVehiculoNodeKey(placaNormalizada: String): String? {
        val direct = runCatching { vehiculosDatosRef.child(placaNormalizada).get().await() }.getOrNull()
        if (direct?.exists() == true) return placaNormalizada

        val byMetaString = runCatching {
            vehiculosDatosRef.orderByChild("meta/placa").equalTo(placaNormalizada).get().await()
        }.getOrNull()?.children?.firstOrNull()?.key
        if (!byMetaString.isNullOrBlank()) return byMetaString

        val byRootString = runCatching {
            vehiculosDatosRef.orderByChild("placa").equalTo(placaNormalizada).get().await()
        }.getOrNull()?.children?.firstOrNull()?.key
        if (!byRootString.isNullOrBlank()) return byRootString

        val byMetaNumber = runCatching {
            vehiculosDatosRef.orderByChild("meta/placa").equalTo(placaNormalizada.toDouble()).get().await()
        }.getOrNull()?.children?.firstOrNull()?.key
        if (!byMetaNumber.isNullOrBlank()) return byMetaNumber

        val byRootNumber = runCatching {
            vehiculosDatosRef.orderByChild("placa").equalTo(placaNormalizada.toDouble()).get().await()
        }.getOrNull()?.children?.firstOrNull()?.key
        if (!byRootNumber.isNullOrBlank()) return byRootNumber

        val scanFallback = runCatching { vehiculosDatosRef.get().await() }.getOrNull()
        return scanFallback?.children?.firstOrNull { node ->
            val metaPlaca = node.child("meta").child("placa").value?.toString()?.trim()
            val rootPlaca = node.child("placa").value?.toString()?.trim()
            metaPlaca == placaNormalizada || rootPlaca == placaNormalizada
        }?.key
    }

    suspend fun medidorExisteEnRoom(numero: String): Boolean =
        medidorDao.buscarPorNumero(numero.trim()) != null

    private suspend fun actualizarCambioMedidorSiAplica(
        caseId: String,
        medidorAnterior: String?,
        data: AveriaActionData
    ) {
        if (data.tipoAfectacion != TipoAfectacion.CLIENTE) return

        val anterior = medidorAnterior?.trim().orEmpty()
        val nuevo = data.numeroMedidor?.trim().orEmpty()
        if (anterior.isBlank() || nuevo.isBlank() || anterior.equals(nuevo, ignoreCase = true)) return

        // Find the meter material to extract readings and label
        val materialMedidor = data.materiales.firstOrNull { uso ->
            uso.medidorInstalado != null &&
                uso.medidorInstalado.numero?.trim()?.equals(nuevo, ignoreCase = true) == true
        } ?: data.materiales.firstOrNull { uso ->
            "${uso.codigo} ${uso.descripcion}".lowercase().contains("medidor")
        }
        val meta = materialMedidor?.medidorInstalado
        val (lecturaNueva, lecturaAnterior) = MaterialesSerializer.decodeLectura(meta?.lectura)
        val lecturaAnteriorVisible = lecturaAnterior != null

        val cambioData = CambioMedidorData(
            numeroCaso = caseId,
            medidorAnterior = anterior.ifBlank { null },
            medidorInstalado = nuevo,
            lecturaAnteriorVisible = lecturaAnteriorVisible,
            lecturaAnterior = lecturaAnterior,
            lecturaNueva = lecturaNueva,
            materialUsado = materialMedidor?.descripcion?.ifBlank { materialMedidor.codigo },
            tecnicoUid = data.atendidoPorUid,
            tecnicoNombre = data.atendidoPorNombre,
            fechaCambio = CambioMedidorSerializer.now()
        )
        val cambioJson = CambioMedidorSerializer.toJson(cambioData)

        // Save audit trail to Room
        dao.actualizarCambioMedidorJson(caseId, cambioJson, System.currentTimeMillis())

        // Swap meter in medidores catalog (Room)
        val medidorActual = medidorDao.buscarPorNumero(anterior) ?: run {
            // Meter not in local catalog — record the change and notify supervisor, skip the swap
            runCatching {
                firebaseRef.child(caseId).child("cambioMedidor")
                    .setValue(CambioMedidorSerializer.toFirebaseMap(cambioData)).await()
            }.onFailure { Log.w(TAG, "No se pudo escribir cambioMedidor en Firebase para avería=$caseId", it) }
            runCatching { notificarSupervisorCambioMedidor(cambioData) }
                .onFailure { Log.w(TAG, "No se pudo invocar notifyCambioMedidorSupervisor para avería=$caseId", it) }
            return
        }
        val medidorActualizado = medidorActual.copy(medidorNumber = nuevo)
        medidorDao.insertAll(listOf(medidorActualizado))
        medidorDao.eliminarPorNumero(anterior)

        runCatching {
            actualizarMedidorEnFirebase(medidorActual, medidorActualizado)
        }.onFailure {
            Log.w(TAG, "No se pudo actualizar el medidor en Firebase para avería=$caseId", it)
        }

        // Write structured cambioMedidor node to Firebase
        runCatching {
            firebaseRef.child(caseId).child("cambioMedidor")
                .setValue(CambioMedidorSerializer.toFirebaseMap(cambioData)).await()
        }.onFailure {
            Log.w(TAG, "No se pudo escribir cambioMedidor en Firebase para avería=$caseId", it)
        }

        // Notify supervisor via Cloud Function — failure must NOT revert the change
        runCatching {
            notificarSupervisorCambioMedidor(cambioData)
        }.onFailure {
            Log.w(TAG, "No se pudo invocar notifyCambioMedidorSupervisor para avería=$caseId", it)
        }
    }

    private suspend fun notificarSupervisorCambioMedidor(cambio: CambioMedidorData) {
        val averia = dao.getByCaseId(cambio.numeroCaso)
        val agencia = averia?.agencia?.takeIf { it.isNotBlank() } ?: ""
        val nombreAgencia = averia?.nombreAgencia?.takeIf { it.isNotBlank() } ?: agencia
        val payload = mapOf(
            "caseId" to cambio.numeroCaso,
            "agencia" to agencia,
            "nombreAgencia" to nombreAgencia,
            "tecnicoNombre" to (cambio.tecnicoNombre ?: ""),
            "tecnicoUid" to (cambio.tecnicoUid ?: ""),
            "medidorAnterior" to (cambio.medidorAnterior ?: ""),
            "medidorInstalado" to cambio.medidorInstalado,
            "lecturaAnteriorVisible" to cambio.lecturaAnteriorVisible,
            "lecturaAnterior" to (cambio.lecturaAnterior ?: ""),
            "lecturaNueva" to (cambio.lecturaNueva ?: ""),
            "materialUsado" to (cambio.materialUsado ?: ""),
            "fechaCambio" to cambio.fechaCambio
        )
        FirebaseFunctions.getInstance()
            .getHttpsCallable("notifyCambioMedidorSupervisor")
            .call(payload)
            .await()
    }

    private suspend fun actualizarMedidorEnFirebase(anterior: MedidorEntity, nuevo: MedidorEntity) {
        val subregion = anterior.subregion?.trim().orEmpty()
        if (subregion.isBlank()) return

        val nodoSubregion = resolveSubregionRef(subregion) ?: return
        val payload = mutableMapOf<String, Any?>()
        payload["medidorNumber"] = nuevo.medidorNumber
        nuevo.cliente?.takeIf { it.isNotBlank() }?.let { payload["cliente"] = it }
        nuevo.calle?.takeIf { it.isNotBlank() }?.let { payload["calle"] = it }
        nuevo.poste?.takeIf { it.isNotBlank() }?.let { payload["poste"] = it }
        nuevo.metros?.takeIf { it.isNotBlank() }?.let { payload["metros"] = it }
        nuevo.pueblo?.takeIf { it.isNotBlank() }?.let { payload["pueblo"] = it }
        nuevo.localizacion?.let { payload["localizacion"] = it }
        payload["subregion"] = subregion

        nodoSubregion.child(nuevo.medidorNumber).setValue(payload).await()
        nodoSubregion.child(anterior.medidorNumber).removeValue().await()
    }

    private suspend fun resolveSubregionRef(subregion: String): DatabaseReference? {
        val roots = listOf("Medidores", "medidores")
        for (root in roots) {
            val rootRef = medidoresRef.child(root)
            val snapshot = runCatching { rootRef.get().await() }.getOrNull() ?: continue
            if (!snapshot.exists()) continue
            val match = snapshot.children.firstOrNull { child ->
                child.key?.trim()?.equals(subregion, ignoreCase = true) == true
            }
            if (match != null) return match.ref
        }
        return null
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
        "evidenciasJson" to evidenciasJson,
        "cambioMedidorJson" to cambioMedidorJson,

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

    /**
     * Descarga INCREMENTAL de averías (fix de consumo de bandwidth — ver log.md §D1).
     * En vez de re-bajar toda la región en cada sync (cada 15 min + tras cada escritura),
     * pide solo los registros con lastUpdated >= (watermark - ventana), usando el índice
     * 'lastUpdated' que ya existe en las reglas. RTDB no deja filtrar por 'region' y
     * 'lastUpdated' a la vez, pero el delta entre syncs es minúsculo: se traen los cambios
     * recientes de todas las regiones y se filtran por región en el cliente.
     * Reduce cada pull de ~toda la región (MB) a ~KB.
     */
    private suspend fun fetchDeltaChildren(
        regionVariants: List<String>,
        watermark: Long
    ): List<DataSnapshot> {
        val variantSet = regionVariants.toHashSet()
        // Ventana de solape: re-trae los últimos minutos para tolerar relojes desincronizados
        // y updates fuera de orden sin perder cambios (costo despreciable, idempotente).
        val desde = (watermark - DELTA_OVERLAP_MS).coerceAtLeast(0L)
        val snap = firebaseRef
            .orderByChild("lastUpdated")
            .startAt(desde.toDouble())
            .get()
            .await()
        val delta = snap.children.filter { child ->
            val childRegion = child.child("region").getValue(String::class.java)
            childRegion != null && variantSet.contains(childRegion)
        }
        Log.i(
            TAG,
            "[SCOPED_DELTA] desde=$desde watermark=$watermark recibidos=${snap.childrenCount} regionMatch=${delta.size}"
        )
        return delta
    }

    suspend fun pullFromFirebaseOnce(): PullResult = withContext(Dispatchers.IO) {
        val current = dao.all().associateBy { it.caseId }
        val hadLocalData = current.isNotEmpty()
        try {
            Log.i(TAG, "[SCOPED_FLAG] useScopedAverias=$useScopedAverias")
            val finalChildren = if (!useScopedAverias) {
                Log.w(TAG, "[SCOPED_GUARD] global_blocked=true reason=scoped_flag_disabled")
                emptyList()
            } else {
                val scope = resolveSyncScope()
                val plan = buildScopedQueryPlan(scope)
                val primary = plan.primary
                val uidPresent = !scope.uid.isNullOrBlank()
                val agenciaTagPresent = !scope.agenciaTag.isNullOrBlank()
                val regionPresent = !scope.region.isNullOrBlank()
                if (primary == null) {
                    Log.w(
                        TAG,
                        "[SCOPED_GUARD] global_blocked=true reason=missing_region role=${plan.role} uid_present=$uidPresent agenciaTag_present=$agenciaTagPresent region_present=$regionPresent"
                    )
                    return@withContext PullResult(emptyList(), hadLocalData)
                }

                // ✅ FIX 1: Intentar múltiples variantes del valor de región para tolerar
                // diferencias de tildes/mayúsculas entre lo que Firebase guarda y lo canónico.
                val regionVariants = buildRegionVariants(primary.value)
                Log.i(
                    TAG,
                    "[SCOPED_EXECUTION] stage=primary role=${plan.role} field=${primary.field} variants=$regionVariants"
                )

                // 🚀 Sync incremental (log.md §D1): si ya hay averías locales, solo se descargan
                // los cambios desde la última sync (índice 'lastUpdated') en vez de re-bajar toda
                // la región cada 15 min. La rama 'else' (seed) mantiene intacta la carga completa
                // acotada por región para la primera vez (Room vacío).
                val deltaWatermark = current.values.maxOfOrNull { it.lastUpdated } ?: 0L
                if (deltaWatermark > 0L) {
                    fetchDeltaChildren(regionVariants, deltaWatermark)
                } else {

                val allPrimaryChildren = mutableListOf<DataSnapshot>()
                val seenKeys = mutableSetOf<String>()
                for (variant in regionVariants) {
                    val snap = firebaseRef.orderByChild(primary.field).equalTo(variant).get().await()
                    val children = snap.children.toList()
                    Log.i(TAG, "[SCOPED_EXECUTION] stage=primary_variant variant=$variant count=${children.size}")
                    for (child in children) {
                        val key = child.key ?: continue
                        if (seenKeys.add(key)) allPrimaryChildren += child
                    }
                }
                val primaryChildren = allPrimaryChildren

                Log.i(
                    TAG,
                    "[SCOPED_EXECUTION] stage=primary_result role=${plan.role} field=${primary.field} total_count=${primaryChildren.size}"
                )

                if (shouldRunFallback(primaryChildren.size)) {
                    val fallback = plan.fallback
                    val fallbackReason = if (fallback == null) "region_variants_zero" else "primary_zero"
                    Log.w(
                        TAG,
                        "[SCOPED_FALLBACK] enabled=true reason=$fallbackReason role=${plan.role} " +
                                "region=${scope.region} agenciaTag=${scope.agenciaTag} " +
                                "uid_present=$uidPresent primary_count=${primaryChildren.size} " +
                                "HINT: verifica que el campo 'region' en Firebase coincida con alguna variante de '${scope.region}'"
                    )
                    // ✅ No usamos fallback por agenciaTag: eso limitaría la descarga a una sola
                    // agencia en vez de toda la región. Si todas las variantes de región
                    // devuelven 0, significa que el valor de 'region' en Firebase no coincide
                    // con ninguna variante conocida — revisar los logs para diagnosticar.
                    val fallbackQuery = buildFallbackQuery(plan)
                    if (fallbackQuery == null) {
                        Log.w(
                            TAG,
                            "[SCOPED_GUARD] global_blocked=true reason=fallback_not_buildable role=${plan.role} " +
                                    "Acción: agrega el valor exacto del campo 'region' en Firebase al mapa REGION_CANON"
                        )
                        emptyList()
                    } else {
                        Log.i(
                            TAG,
                            "[SCOPED_EXECUTION] stage=fallback role=${plan.role} field=${fallback?.field} value=${fallback?.value} primary_count=${primaryChildren.size}"
                        )
                        val fallbackSnapshot = fallbackQuery.get().await()
                        val fallbackChildrenRaw = fallbackSnapshot.children.toList()
                        val fallbackChildrenFiltered = if (plan.role == ScopedRole.TECNICO || plan.role == ScopedRole.SUPERVISOR_AGENCIA) {
                            fallbackChildrenRaw.filter { child ->
                                val estado = child.child("estado").getValue(String::class.java)
                                isOperativeEstado(estado)
                            }
                        } else {
                            fallbackChildrenRaw
                        }
                        Log.i(
                            TAG,
                            "[SCOPED_EXECUTION] stage=fallback_result role=${plan.role} field=${fallback?.field} value=${fallback?.value} rawCount=${fallbackChildrenRaw.size} filteredCount=${fallbackChildrenFiltered.size}"
                        )
                        fallbackChildrenFiltered
                    }
                } else {
                    Log.i(
                        TAG,
                        "[SCOPED_FALLBACK] enabled=false reason=primary_has_data role=${plan.role} primary_count=${primaryChildren.size}"
                    )
                    primaryChildren
                }
                } // cierre de la rama 'else' (seed) del sync incremental
            }

            val updated = mutableListOf<AveriaEntity>()
            val newlyCreated = mutableListOf<AveriaEntity>()
            finalChildren.forEach { child ->
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
                        val bloquearPromocionAClorResuelta =
                            normalizeEstadoLabel(existing.estado) == "Resuelta" &&
                                    !isClorResuelta(existing.estadoClor) &&
                                    isClorResuelta(remote.estadoClor)

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
                            idEstadoAranda = if (bloquearPromocionAClorResuelta) existing.idEstadoAranda else remote.idEstadoAranda,
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
                            evidenciasJson = mergeRemoteString(remote.evidenciasJson, existing.evidenciasJson),
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
                            estadoClor = if (bloquearPromocionAClorResuelta) {
                                existing.estadoClor
                            } else {
                                preferMeaningfulClor(remote.estadoClor, existing.estadoClor)
                            },
                            causaClor = if (bloquearPromocionAClorResuelta) {
                                existing.causaClor
                            } else {
                                preferMeaningfulClor(remote.causaClor, existing.causaClor)
                            },
                            observacionesClor = if (bloquearPromocionAClorResuelta) {
                                existing.observacionesClor
                            } else {
                                preferMeaningfulClor(remote.observacionesClor, existing.observacionesClor)
                            },

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

    private companion object {
        private const val TAG = "AveriasRepo"
        private const val FALLBACK_GLOBAL_LIMIT = 300
        private const val DELTA_OVERLAP_MS = 5 * 60_000L  // 5 min de solape para robustez (log.md §D1)
        private const val SYNC_SCOPE_CACHE_MS = 60_000L
        private val DIACRITICS_REGEX = "\\p{InCombiningDiacriticalMarks}+".toRegex()
        private val NON_ALNUM_SPACE_REGEX = "[^a-z0-9 ]".toRegex()
        private val MULTI_SPACE_REGEX = "\\s+".toRegex()
    }
}