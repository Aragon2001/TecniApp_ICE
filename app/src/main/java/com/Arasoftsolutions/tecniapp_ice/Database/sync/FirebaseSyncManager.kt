package com.Arasoftsolutions.tecniapp_ice.Database.sync

import android.content.Context
import android.util.Log
import com.Arasoftsolutions.tecniapp_ice.Database.entities.*
import com.Arasoftsolutions.tecniapp_ice.ui.vehiculo.VehiculoPlacaUtils
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ChildEventListener
import kotlinx.coroutines.tasks.await
import com.google.firebase.database.DataSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Acceso centralizado a Realtime Database en *varios* proyectos.
 *
 * Endpoints:
 * - Usuarios / verificationCodes: https://tecniapp-ice-user.firebaseio.com/
 * - Agencias / Subregiones / Vehículos: https://tecniapp-ice-datosgenerales.firebaseio.com/
 * - Localizaciones / pueblos: https://tecniapp-ice.firebaseio.com/
 * - Medidores: https://tecniapp-ice-default-rtdb.firebaseio.com/
 */
class FirebaseSyncManager(@Suppress("UNUSED_PARAMETER") context: Context) {

    private val dbUsers: DatabaseReference by lazy {
        database("https://tecniapp-ice-user.firebaseio.com")
    }

    private val dbDatosGenerales: DatabaseReference by lazy {
       database( "https://tecniapp-ice-datosgenerales.firebaseio.com")
    }

    private val dbLocal: DatabaseReference by lazy {
        database("https://tecniapp-ice.firebaseio.com")
    }

    private val dbMedidores: DatabaseReference by lazy {
        database("https://tecniapp-ice-default-rtdb.firebaseio.com")
    }

    private val dbTecnicos: DatabaseReference by lazy {
        database("https://tecniapp-ice-personal.firebaseio.com/")
    }

    private val dbMaterialesIce: DatabaseReference by lazy {
        database("https://tecniapp-ice-materiales.firebaseio.com/")
    }

    private val dbInventario: DatabaseReference by lazy {
        database("https://tecniapp-ice-inventario.firebaseio.com/")
    }

    private val dbLuminarias: DatabaseReference by lazy {
        database("https://tecniapp-ice-luminarias.firebaseio.com/")
    }

    private val dbVehiculoOps: DatabaseReference by lazy {
        database( "https://tecniapp-ice-datosgenerales.firebaseio.com/vehiculos")
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val subregionNombreCache = mutableMapOf<String, String>()
    private val inventarioChildListeners = mutableMapOf<ValueEventListener, ChildEventListener>()
    private val luminariasChildListeners = mutableMapOf<ValueEventListener, ChildEventListener>()
    private val inventarioChildRefs = mutableMapOf<ValueEventListener, Query>()
    private val luminariasChildRefs = mutableMapOf<ValueEventListener, Query>()
    private val localizacionesFallbackWarnThreshold = 10_000

    private fun database(url: String): DatabaseReference {
        return runCatching { FirebaseDatabase.getInstance(url).reference }
            .getOrElse { throwable ->
                Log.e(TAG, "Error inicializando FirebaseDatabase", throwable)
                throw IllegalStateException("No se pudo inicializar la base de datos en $url", throwable)
            }

    }

    private suspend fun resolveDatosGeneralesNode(vararg candidates: String): String {
        for (name in candidates) {
            if (dbDatosGenerales.child(name).get().await().exists()) {
                return name
            }
        }
        return candidates.first()
    }

    // --- USUARIOS ---
    suspend fun obtenerUsuario(uid: String): UserEntity? {
        val snap = dbUsers.child("usuarios").child(uid).get().await()
        return snap.toUserEntity()
    }

    suspend fun upsertUsuarioConEmail(uid: String, email: String, otrosCampos: Map<String, Any?> = emptyMap()) {
        val emailLower = email.trim().lowercase()
        val base = mapOf(
            "uid" to uid,
            "email" to email,
            "email_lower" to emailLower
        )
        val payload = base + otrosCampos
        dbUsers.child("usuarios").child(uid).updateChildren(payload).await()
    }

    suspend fun buscarUsuarioPorEmail(email: String): UserEntity? {
        val emailLower = email.trim().lowercase()
        val snap = dbUsers.child("usuarios")
            .orderByChild("email_lower")
            .equalTo(emailLower)
            .limitToFirst(1)
            .get()
            .await()

        val first = snap.children.firstOrNull() ?: return null
        return first.toUserEntity()
    }

    suspend fun buscarUsuarioPorCedula(cedula: String): UserEntity? {
        val cedulaTrim = cedula.trim()
        if (cedulaTrim.isEmpty()) return null
        val snap = dbUsers.child("usuarios")
            .orderByChild("cedula")
            .equalTo(cedulaTrim)
            .limitToFirst(1)
            .get()
            .await()

        val first = snap.children.firstOrNull() ?: return null
        return first.toUserEntity()
    }

    suspend fun actualizarUsuarioAdmin(user: UserEntity) {
        val uid = user.uid.trim()
        require(uid.isNotEmpty()) { "UID vacío" }
        val email = user.email?.trim()?.takeIf { it.isNotEmpty() }
        val payload = buildMap<String, Any?> {
            put("uid", uid)
            if (email != null) {
                put("email", email)
                put("email_lower", email.lowercase())
            }
            user.nombre?.let { put("nombre", it) }
            user.apellidos?.let { put("apellidos", it) }
            user.primerApellido?.let { put("primer_apellido", it) }
            user.segundoApellido?.let { put("segundo_apellido", it) }
            user.cedula?.let { put("cedula", it) }
            user.region?.let { put("region", it) }
            user.regionNombre?.let { put("region_nombre", it) }
            user.subregion?.let { put("subregion", it) }
            user.subregionNombre?.let { put("subregion_nombre", it) }
            user.agenciaId?.let { put("agencia_id", it) }
            user.agencia?.let { put("agencia", it) }
            user.placaVehiculo?.let { put("placaVehiculo", it) }
            user.telefono?.let { put("telefono", it) }
            user.rol?.let { put("rol", it) }
        }
        dbUsers.child("usuarios").child(uid).updateChildren(payload).await()
    }

    // --- DATOS GENERALES (Regiones / Agencias / Subregiones / Vehículos) ---
    suspend fun obtenerRegiones(): List<RegionEntity> {
        val snap = dbDatosGenerales.child("regiones").get().await()
        return snap.children.mapNotNull { child ->
            val id = child.stringChild("id") ?: child.key ?: return@mapNotNull null
            val nombre = child.stringChild("nombre") ?: return@mapNotNull null
            val trimmedId = id.trim()
            if (trimmedId.isEmpty()) return@mapNotNull null
            RegionEntity(id = trimmedId, nombre = nombre.trim())
        }
    }

    suspend fun obtenerAgencias(subregionId: String? = null): List<AgenciaEntity> {
        val nodeName = resolveDatosGeneralesNode("agencias", "Agencias")
        val snap = dbDatosGenerales.child(nodeName).get().await()
        val filtroSubregion = subregionId?.trim()?.takeIf { it.isNotEmpty() }
        val regionPorSubregion = if (filtroSubregion != null) {
            runCatching {
                obtenerSubregiones().associate { it.id.lowercase(Locale.getDefault()) to it.regionId }
            }.getOrDefault(emptyMap())
        } else emptyMap()
        return snap.children.mapNotNull { child ->
            val id = child.stringChild("id") ?: child.key
            val nombre = child.stringChild("nombre") ?: return@mapNotNull null
            val regionId = child.stringChild("region_id")
                ?: child.stringChild("regionId")
                ?: child.stringChild("region")
            val subregion = child.stringChild("subregion")
                ?: child.stringChild("subregion_id")
                ?: child.stringChild("subregionId")
            val entityId = (id ?: nombre).trim()
            if (entityId.isEmpty()) return@mapNotNull null
            AgenciaEntity(
                id = entityId,
                nombre = nombre.trim(),
                regionId = regionId?.trim(),
                subregion = subregion?.trim()
            )
        }.filter { agency ->
            if (filtroSubregion == null) {
                true
            } else {
                val regionOfSub = regionPorSubregion[filtroSubregion.lowercase(Locale.getDefault())]
                regionOfSub != null && agency.regionId?.equals(regionOfSub, ignoreCase = true) == true
            }
        }
    }

    suspend fun obtenerAgenciasPorSubregion(subregionId: String): List<AgenciaEntity> {
        val filtro = subregionId.trim().takeIf { it.isNotEmpty() } ?: return emptyList()
        val nodeName = resolveDatosGeneralesNode("agencias", "Agencias")
        val startedAt = System.currentTimeMillis()
        return try {
            Log.i(TAG, "[SYNC_FETCH][datos_generales/$nodeName] query=orderByChild(subregion).equalTo($filtro) start")
            val snap = dbDatosGenerales.child(nodeName)
                .orderByChild("subregion")
                .equalTo(filtro)
                .get()
                .await()
            val result = if (!snap.exists()) {
                emptyList()
            } else {
                snap.children.mapNotNull { child ->
                    val id = child.stringChild("id") ?: child.key
                    val nombre = child.stringChild("nombre") ?: return@mapNotNull null
                    val regionId = child.stringChild("region_id")
                        ?: child.stringChild("regionId")
                        ?: child.stringChild("region")
                    val subregion = child.stringChild("subregion")
                        ?: child.stringChild("subregion_id")
                        ?: child.stringChild("subregionId")
                    val entityId = (id ?: nombre).trim()
                    if (entityId.isEmpty()) return@mapNotNull null
                    AgenciaEntity(
                        id = entityId,
                        nombre = nombre.trim(),
                        regionId = regionId?.trim(),
                        subregion = subregion?.trim()
                    )
                }
            }
            Log.i(TAG, "[SYNC_FETCH][datos_generales/$nodeName] query_indexed=true fallback=false count=${result.size} bytes=${estimatePayloadBytes(result)} tookMs=${System.currentTimeMillis()-startedAt}")
            result
        } catch (e: Exception) {
            val isIndexError = e.message?.contains("Index not defined", ignoreCase = true) == true
            Log.w(
                TAG,
                "[SYNC_FETCH][datos_generales/$nodeName] indexed_query_failed fallback_skipped=true indexError=$isIndexError detail=${e.message}"
            )
            emptyList()
        }
    }

    suspend fun obtenerVehiculos(subregionId: String? = null): List<VehiculosEntity> {
        val nodeName = resolveDatosGeneralesNode("vehiculos", "Vehiculos")
        val snap = dbDatosGenerales.child(nodeName).get().await()
        val filtroSubregion = subregionId?.trim()?.takeIf { it.isNotEmpty() }

        return snap.children.mapNotNull { child ->
            val placaNodo = child.key?.trim().orEmpty().takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val placaTexto = child.stringChild("placa")?.trim().takeIf { !it.isNullOrEmpty() }
                ?: placaNodo
            val placaLong = placaTexto.toLongOrNull() ?: return@mapNotNull null
            val agencia = child.stringChild("agencia")?.trim().orEmpty()
            if (agencia.isEmpty()) return@mapNotNull null

            val tipo = child.stringChild("tipo")?.trim().orEmpty()
            val subregion = child.stringChild("subregion")?.trim()
            val kmActual = child.doubleValueAny("kmActual") ?: 0.0
            val registroCerrado = child.booleanValueAny("registroCerrado", "registro_cerrado") ?: false

            VehiculosEntity(
                vehiculoId = placaNodo,
                placaRaw = placaTexto,
                placa = placaLong,
                id = placaTexto.hashCode(),
                tipo = tipo,
                subregion = subregion,
                agencia = agencia,
                kmActual = kmActual,
                registroCerrado = registroCerrado
            )
        }.filter { vehiculo ->
            filtroSubregion == null || vehiculo.subregion?.equals(filtroSubregion, ignoreCase = true) == true
        }
    }

    suspend fun obtenerVehiculosPorSubregion(subregionId: String): List<VehiculosEntity> {
        val filtro = subregionId.trim().takeIf { it.isNotEmpty() } ?: return emptyList()
        val nodeName = resolveDatosGeneralesNode("vehiculos", "Vehiculos")
        val startedAt = System.currentTimeMillis()
        return try {
            Log.i(TAG, "[SYNC_FETCH][datos_generales/$nodeName] query=orderByChild(subregion).equalTo($filtro) start")
            val snap = dbDatosGenerales.child(nodeName)
                .orderByChild("subregion")
                .equalTo(filtro)
                .get()
                .await()
            val result = if (!snap.exists()) {
                emptyList()
            } else {
                snap.children.mapNotNull { child ->
                    val placaNodo = child.key?.trim().orEmpty().takeIf { it.isNotEmpty() }
                        ?: return@mapNotNull null
                    val placaTexto = child.stringChild("placa")?.trim().takeIf { !it.isNullOrEmpty() }
                        ?: placaNodo
                    val placaLong = placaTexto.toLongOrNull() ?: return@mapNotNull null
                    val agencia = child.stringChild("agencia")?.trim().orEmpty()
                    if (agencia.isEmpty()) return@mapNotNull null

                    val tipo = child.stringChild("tipo")?.trim().orEmpty()
                    val subregion = child.stringChild("subregion")?.trim()
                    val kmActual = child.doubleValueAny("kmActual") ?: 0.0
                    val registroCerrado = child.booleanValueAny("registroCerrado", "registro_cerrado") ?: false

                    VehiculosEntity(
                        vehiculoId = placaNodo,
                        placaRaw = placaTexto,
                        placa = placaLong,
                        id = placaTexto.hashCode(),
                        tipo = tipo,
                        subregion = subregion,
                        agencia = agencia,
                        kmActual = kmActual,
                        registroCerrado = registroCerrado
                    )
                }
            }
            Log.i(TAG, "[SYNC_FETCH][datos_generales/$nodeName] query_indexed=true fallback=false count=${result.size} bytes=${estimatePayloadBytes(result)} tookMs=${System.currentTimeMillis()-startedAt}")
            result
        } catch (e: Exception) {
            val isIndexError = e.message?.contains("Index not defined", ignoreCase = true) == true
            Log.w(
                TAG,
                "[SYNC_FETCH][datos_generales/$nodeName] indexed_query_failed fallback_skipped=true indexError=$isIndexError detail=${e.message}"
            )
            emptyList()
        }
    }


    suspend fun obtenerSubregiones(): List<SubregionesEntity> {
        val snap = dbDatosGenerales.child("subregiones").get().await()
        return snap.children.mapNotNull { child ->
            val id = child.stringChild("id") ?: child.key ?: return@mapNotNull null
            val nombre = child.stringChild("nombre") ?: return@mapNotNull null
            val regionId = child.stringChild("region_id")
                ?: child.stringChild("regionId")
                ?: child.stringChild("region")
                ?: ""
            val trimmedId = id.trim()
            if (trimmedId.isEmpty()) return@mapNotNull null
            SubregionesEntity(
                id = trimmedId,
                nombre = nombre.trim(),
                regionId = regionId.trim()
            )
        }
    }

    // --- LOCALIZACIONES / PUEBLOS ---
    suspend fun obtenerLocalizaciones(): List<LocalizacionesEntity> {
        val nodeName = if (dbLocal.child("Localizaciones").get().await().exists()) {
            "Localizaciones"
        } else {
            "localizaciones"
        }

        val snap = dbLocal.child(nodeName).get().await()
        if (!snap.exists()) return emptyList()

        return snap.children.flatMap { child ->
            val inherited = child.key?.trim()?.toIntOrNull()
            parseLocalizacionNode(child, inheritedPueblo = inherited)
        }.map { entity ->
            val direccionLimpia = entity.direccion.trim()
            entity.copy(
                direccion = direccionLimpia,
                subregion = null
            )
        }.distinctBy { it.id }
    }

    suspend fun obtenerPueblos(): List<PueblosEntity> {
        val node = if (dbLocal.child("pueblos").get().await().exists()) "pueblos" else "Pueblos"
        val snap = dbLocal.child(node).get().await()
        if (!snap.exists()) return emptyList()

        return snap.children.mapNotNull { child ->
            val id = child.key?.trim()?.toIntOrNull()
                ?: child.intValueAny("id", "Id", "ID")
                ?: return@mapNotNull null
            val nombre = child.stringValueAny("nombre", "Nombre", "NOMBRE")?.trim()
                ?: return@mapNotNull null
            val remoteSubregion = child.stringValueAny("subregion", "Subregion", "SubRegión", "Subregión")?.trim()
            val canonical = SubregionNormalizer.canonicalIdOrSelf(remoteSubregion) ?: ""

            PueblosEntity(
                id = id,
                nombre = nombre,
                subregion = remoteSubregion.orEmpty(),
                subregion_id_normalizado = canonical
            )
        }.distinctBy { it.id }
    }

    suspend fun obtenerPueblosPorSubregion(subregionId: String): List<PueblosEntity> {
        val node = if (dbLocal.child("pueblos").get().await().exists()) "pueblos" else "Pueblos"
        val filtro = subregionId.trim().takeIf { it.isNotEmpty() } ?: return emptyList()
        val canonicalFiltro = SubregionNormalizer.canonicalIdOrSelf(filtro) ?: filtro
        val filtroNormalizado = normalizarClave(filtro)
        val canonicalFiltroNormalizado = normalizarClave(canonicalFiltro)

        val resultadoIndexado = runCatching {
            dbLocal.child(node)
                .orderByChild("subregion")
                .equalTo(filtro)
                .get()
                .await()
        }.getOrNull()?.children.orEmpty().mapNotNull { child ->
            child.toPuebloEntity()
        }.distinctBy { it.id }
        Log.i(
            TAG,
            "[SYNC_FETCH][local/$node] query=orderByChild(subregion).equalTo($filtro) primaryCount=${resultadoIndexado.size}"
        )

        if (resultadoIndexado.isNotEmpty()) {
            Log.i(
                TAG,
                "[SYNC_FETCH][local/$node] fallback=false finalCount=${resultadoIndexado.size}"
            )
            return resultadoIndexado
        }

        Log.w(
            TAG,
            "[SYNC_FETCH][local/$node] fallback=true reason=primary_empty query=orderByChild(subregion).equalTo($filtro)"
        )
        val datasetCompleto = obtenerPueblos()
        val resultadoFallback = datasetCompleto.filter { pueblo ->
            val subregionNormalizada = normalizarClave(pueblo.subregion)
            val canonicalPueblo = pueblo.subregion_id_normalizado
                .ifBlank { SubregionNormalizer.canonicalIdOrSelf(pueblo.subregion).orEmpty() }
            val canonicalPuebloNormalizada = normalizarClave(canonicalPueblo)
            (filtroNormalizado != null && subregionNormalizada == filtroNormalizado) ||
                (canonicalFiltroNormalizado != null && canonicalPuebloNormalizada == canonicalFiltroNormalizado)
        }.distinctBy { it.id }
        Log.i(
            TAG,
            "[SYNC_FETCH][local/$node] fallback=true datasetCount=${datasetCompleto.size} finalCount=${resultadoFallback.size}"
        )
        return resultadoFallback
    }

    private fun DataSnapshot.toPuebloEntity(): PueblosEntity? {
        val id = key?.trim()?.toIntOrNull()
            ?: intValueAny("id", "Id", "ID")
            ?: return null
        val nombre = stringValueAny("nombre", "Nombre", "NOMBRE")?.trim()
            ?: return null
        val remoteSubregion = stringValueAny("subregion", "Subregion", "SubRegión", "Subregión")?.trim()
        val canonical = SubregionNormalizer.canonicalIdOrSelf(remoteSubregion) ?: ""
        return PueblosEntity(
            id = id,
            nombre = nombre,
            subregion = remoteSubregion.orEmpty(),
            subregion_id_normalizado = canonical
        )
    }

    suspend fun obtenerLocalizacionesPorPueblos(puebloIds: List<Int>): List<LocalizacionesEntity> {
        if (puebloIds.isEmpty()) return emptyList()
        val nodeName = if (dbLocal.child("Localizaciones").get().await().exists()) {
            "Localizaciones"
        } else {
            "localizaciones"
        }
        val pueblosSet = puebloIds.toSet()
        val result = mutableListOf<LocalizacionesEntity>()
        var huboNodoPorPueblo = false
        puebloIds.forEach { puebloId ->
            val snapshot = dbLocal.child(nodeName).child(puebloId.toString()).get().await()
            if (!snapshot.exists()) {
                Log.i(
                    TAG,
                    "[SYNC_FETCH][local/$nodeName] pueblo=$puebloId exists=false count=0"
                )
                return@forEach
            }
            huboNodoPorPueblo = true
            val porPueblo = parseLocalizacionNode(snapshot, inheritedPueblo = puebloId)
                .filter { it.pueblo != 0 && it.calle != 0 }
            Log.i(
                TAG,
                "[SYNC_FETCH][local/$nodeName] pueblo=$puebloId exists=true count=${porPueblo.size}"
            )
            result += porPueblo
        }

        if (result.isNotEmpty()) {
            val final = result.distinctBy { it.id }
            Log.i(
                TAG,
                "[SYNC_FETCH][local/$nodeName] fallback=false source=por_pueblo totalFinal=${final.size}"
            )
            return final
        }

        Log.w(
            TAG,
            "[SYNC_FETCH][local/$nodeName] fallback=true reason=${if (huboNodoPorPueblo) "por_pueblo_vacio" else "nodos_por_pueblo_inexistentes"}"
        )
        if (pueblosSet.isEmpty()) {
            Log.w(
                TAG,
                "[SYNC_FETCH][local/$nodeName] fallback_skipped reason=pueblos_empty"
            )
            return emptyList()
        }
        val snap = dbLocal.child(nodeName).get().await()
        if (!snap.exists()) return emptyList()
        val fallbackDatasetSize = snap.childrenCount.toInt()
        Log.i(
            TAG,
            "[SYNC_FETCH][local/$nodeName] fallback_dataset_size=$fallbackDatasetSize"
        )
        if (fallbackDatasetSize > localizacionesFallbackWarnThreshold) {
            Log.w(
                TAG,
                "[SYNC_FETCH][local/$nodeName] fallback_dataset_size_warning size=$fallbackDatasetSize threshold=$localizacionesFallbackWarnThreshold"
            )
        }

        val final = snap.children
            .flatMap { child ->
                val inherited = child.key?.trim()?.toIntOrNull()
                parseLocalizacionNode(child, inheritedPueblo = inherited)
            }
            .filter { it.pueblo != 0 && it.calle != 0 }
            .filter { it.pueblo in pueblosSet }
            .distinctBy { it.id }
        Log.i(
            TAG,
            "[SYNC_FETCH][local/$nodeName] fallback=true source=dataset_completo totalFinal=${final.size}"
        )
        return final
    }

    private fun parseLocalizacionNode(
        node: DataSnapshot,
        inheritedPueblo: Int? = null
    ): List<LocalizacionesEntity> {
        if (!node.exists()) return emptyList()

        val calleRaw = node.stringValueAny("calle", "Calle", "CALLE")
        val puebloRaw = node.stringValueAny("pueblo", "Pueblo", "PUEBLO")
        val calle = node.intValueAny("calle", "Calle", "CALLE")
            ?: calleRaw.toIntLooseOrNull()
        val pueblo = node.intValueAny("pueblo", "Pueblo", "PUEBLO")
            ?: puebloRaw.toIntLooseOrNull()
        val direccion = node.stringValueAny("direccion", "Dirección", "Direccion", "DIRECCION", "DIRECCIÓN")
        val hasLeafData = calle != null ||
            pueblo != null ||
            !calleRaw.isNullOrBlank() ||
            !puebloRaw.isNullOrBlank() ||
            !direccion.isNullOrBlank()

        if (!hasLeafData && node.childrenCount > 0) {
            return node.children.flatMap { child ->
                parseLocalizacionNode(child, inheritedPueblo = inheritedPueblo)
            }
        }

        val latitud = node.doubleValueAny("latitud", "Latitud", "LATITUD") ?: 0.0
        val longitud = node.doubleValueAny("longitud", "Longitud", "LONGITUD") ?: 0.0
        val delPoste = node.intValueAny("del poste", "del poste ", "Del poste", "DelPoste", "del_poste", "delposte") ?: 0
        val alPoste = node.intValueAny("al poste", "Al poste", "al_poste", "alposte") ?: 0

        val calleValue = calle ?: 0
        val puebloValue = pueblo ?: inheritedPueblo ?: 0
        val direccionValue = direccion?.trim().orEmpty()

        val id = node.intValueAny("id", "Id", "ID")
            ?: node.key?.trim()?.toIntOrNull()
            ?: generarIdLocalizacion(puebloValue, calleValue, delPoste, direccionValue, node.key)

        if (puebloValue == 0 && calleValue == 0 && direccionValue.isBlank()) {
            return emptyList()
        }

        val entity = LocalizacionesEntity(
            id = id,
            calle = calleValue,
            direccion = direccionValue,
            latitud = latitud,
            longitud = longitud,
            pueblo = puebloValue,
            alPoste = alPoste,
            delPoste = delPoste,
            subregion = null
        )

        return listOf(entity)
    }

    // --- MEDIDORES (Sync por lotes) ---
    suspend fun obtenerMedidoresPorLotes(
        subregionId: String,
        subregionNombre: String? = null,
        batchSize: Int = 500,
        onBatch: suspend (medidores: List<MedidorEntity>) -> Unit
    ) {
        require(batchSize in 1..1000) { "batchSize debe estar entre 1 y 1000" }

        val storageKey = subregionId.takeIf { it.isNotBlank() }?.trim()
            ?: subregionNombre?.takeIf { it.isNotBlank() }?.trim()
            ?: return

        val lookupNombre = subregionNombre?.takeIf { it.isNotBlank() }
            ?: nombreSubregionDesdeCatalogo(subregionId)

        val referencia = obtenerReferenciaSubregion(storageKey, lookupNombre, createIfMissing = false)
            ?: return

        var lastKey: String? = null
        var keepPaging = true
        while (keepPaging) {
            val snapshot = if (lastKey == null) {
                referencia.orderByKey().limitToFirst(batchSize).get().await()
            } else {
                referencia.orderByKey().startAt(lastKey).limitToFirst(batchSize + 1).get().await()
            }

            if (!snapshot.exists() || snapshot.childrenCount == 0L) break

            val children = snapshot.children
                .filterNot { child -> lastKey != null && child.key == lastKey }
            if (children.isEmpty()) break

            val medidoresBatch = buildList {
                children.forEach { child ->
                    addAll(extraerMedidores(child, storageKey))
                }
            }.distinctBy { it.medidorNumber }

            if (medidoresBatch.isNotEmpty()) {
                onBatch(medidoresBatch)
            }

            lastKey = children.lastOrNull()?.key
            keepPaging = children.size >= batchSize && !lastKey.isNullOrBlank()
        }
    }

    suspend fun contarMedidoresSubregion(
        subregionId: String,
        subregionNombre: String? = null
    ): Int {
        val storageKey = subregionId.takeIf { it.isNotBlank() }?.trim()
            ?: subregionNombre?.takeIf { it.isNotBlank() }?.trim()
            ?: return 0

        val lookupNombre = subregionNombre?.takeIf { it.isNotBlank() }
            ?: nombreSubregionDesdeCatalogo(subregionId)

        val referencia = obtenerReferenciaSubregion(storageKey, lookupNombre, createIfMissing = false)
            ?: return 0
        val path = referencia.path.toString().trimStart('/')
        if (path.isBlank()) return 0

        val request = Request.Builder()
            .url("https://tecniapp-ice-default-rtdb.firebaseio.com/$path.json?shallow=true")
            .get()
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use 0
                val body = response.body?.string()?.trim().orEmpty()
                if (body.isBlank() || body == "null" || body == "{}") return@use 0
                JSONObject(body).length()
            }
        }.getOrElse {
            Log.w(TAG, "No se pudo contar medidores por shallow endpoint: ${it.message}")
            0
        }
    }

    // --- MEDIDOR (Búsqueda puntual de uno solo) ---
    suspend fun buscarMedidorEnFirebase(
        subregionId: String,
        subregionNombre: String?,
        medidorNumber: String
    ): MedidorEntity? {
        val storageKey = subregionId.takeIf { it.isNotBlank() }?.trim()
            ?: subregionNombre?.takeIf { it.isNotBlank() }?.trim()
            ?: return null
        val numeroBuscado = medidorNumber.trim()
        if (numeroBuscado.isEmpty()) return null

        val lookupNombre = subregionNombre?.takeIf { it.isNotBlank() }
            ?: nombreSubregionDesdeCatalogo(subregionId)

        val referencia = obtenerReferenciaSubregion(storageKey, lookupNombre, createIfMissing = false)
            ?: return null

        val directo = referencia.child(numeroBuscado).get().await()
        if (directo.exists()) {
            parseMedidorSnapshot(directo, storageKey, numeroBuscado)?.let { return it }
        }

        val snapshot = referencia.get().await()
        return buscarMedidorEnNodo(snapshot, storageKey, numeroBuscado)
    }

    suspend fun buscarMedidorEnFirebaseLigero(
        subregionId: String,
        subregionNombre: String?,
        medidorNumber: String
    ): MedidorEntity? {
        val storageKey = subregionId.takeIf { it.isNotBlank() }?.trim()
            ?: subregionNombre?.takeIf { it.isNotBlank() }?.trim()
            ?: return null
        val numeroBuscado = medidorNumber.trim()
        if (numeroBuscado.isEmpty()) return null

        val lookupNombre = subregionNombre?.takeIf { it.isNotBlank() }
            ?: nombreSubregionDesdeCatalogo(subregionId)

        val referencia = obtenerReferenciaSubregion(storageKey, lookupNombre, createIfMissing = false)
            ?: return null

        val directo = referencia.child(numeroBuscado).get().await()
        if (directo.exists()) {
            return parseMedidorSnapshot(directo, storageKey, numeroBuscado)
        }
        return null
    }

    suspend fun registrarMedidorManual(
        subregionId: String,
        subregionNombre: String?,
        medidor: MedidorEntity
    ) {
        val storageKey = subregionId.takeIf { it.isNotBlank() }?.trim()
            ?: subregionNombre?.takeIf { it.isNotBlank() }?.trim()
            ?: throw IllegalArgumentException("Subregión inválida para registrar medidor")
        val numero = medidor.medidorNumber.trim()
        require(numero.isNotEmpty()) { "Número de medidor vacío" }

        val lookupNombre = subregionNombre?.takeIf { it.isNotBlank() }
            ?: nombreSubregionDesdeCatalogo(subregionId)

        val referencia = obtenerReferenciaSubregion(storageKey, lookupNombre, createIfMissing = true)
            ?: throw IllegalStateException("No se pudo resolver el nodo de la subregión para medidores")

        val payload = mutableMapOf<String, Any?>()
        payload["medidorNumber"] = numero
        medidor.cliente?.takeIf { it.isNotBlank() }?.let { payload["cliente"] = it }
        medidor.calle?.takeIf { it.isNotBlank() }?.let { payload["calle"] = it }
        medidor.poste?.takeIf { it.isNotBlank() }?.let { payload["poste"] = it }
        medidor.metros?.takeIf { it.isNotBlank() }?.let { payload["metros"] = it }
        medidor.pueblo?.takeIf { it.isNotBlank() }?.let { payload["pueblo"] = it }
        medidor.localizacion?.let { payload["localizacion"] = it }
        payload["subregion"] = storageKey

        referencia.child(numero).setValue(payload).await()
    }

    suspend fun eliminarMedidor(
        subregionId: String,
        subregionNombre: String?,
        medidorNumber: String,
    ) {
        val storageKey = subregionId.takeIf { it.isNotBlank() }?.trim()
            ?: subregionNombre?.takeIf { it.isNotBlank() }?.trim()
            ?: throw IllegalArgumentException("Subregión inválida para eliminar medidor")
        val numero = medidorNumber.trim()
        require(numero.isNotEmpty()) { "Número de medidor vacío" }

        val lookupNombre = subregionNombre?.takeIf { it.isNotBlank() }
            ?: nombreSubregionDesdeCatalogo(subregionId)

        val referencia = obtenerReferenciaSubregion(storageKey, lookupNombre, createIfMissing = false)
            ?: return

        referencia.child(numero).removeValue().await()
    }


    suspend fun actualizarVehiculoCampos(vehiculoKey: String, campos: Map<String, Any?>) {
        if (vehiculoKey.isBlank() || campos.isEmpty()) return
        val payload = campos.toMutableMap()
        payload["updatedAt"] = ServerValue.TIMESTAMP
        dbDatosGenerales.child("vehiculos").child(vehiculoKey).updateChildren(payload).await()
    }

    suspend fun actualizarVehiculoKm(vehiculoKey: String, kmActual: Double) {
        actualizarVehiculoCampos(vehiculoKey, mapOf("kmActual" to kmActual))
    }
    suspend fun guardarVehiculoMeta(vehiculo: VehiculosEntity) {
        val root = dbDatosGenerales.child("vehiculos")
        val idKey = vehiculo.id.takeIf { it != 0 }?.toString()
        val placaKey = vehiculo.placa.takeIf { it != 0L }?.toString()
        val primaryKey = placaKey ?: idKey
            ?: throw IllegalArgumentException("Vehículo inválido, requiere id o placa")

        val payload = mapOf(
            "placa" to (vehiculo.placaRaw.ifBlank { placaKey ?: primaryKey }),
            "tipo" to vehiculo.tipo,
            "subregion" to vehiculo.subregion,
            "agencia" to vehiculo.agencia,
            "kmActual" to vehiculo.kmActual,
            "registroCerrado" to vehiculo.registroCerrado,
            "updatedAt" to ServerValue.TIMESTAMP
        )

        root.child(primaryKey).updateChildren(payload).await()
    }

    suspend fun eliminarVehiculo(id: Int) {
        if (id == 0) return
        val root = dbDatosGenerales.child("vehiculos")
        val key = id.toString()
        val direct = runCatching { root.child(key).get().await() }.getOrNull()
        if (direct != null && direct.exists()) {
            direct.ref.removeValue().await()
            return
        }

        val numericMatch = runCatching {
            root.orderByChild("id").equalTo(id.toDouble()).get().await()
        }.getOrNull()
        val fallback = numericMatch?.children?.firstOrNull()
            ?: runCatching {
                root.orderByChild("id").equalTo(id.toString()).get().await().children.firstOrNull()
            }.getOrNull()

        fallback?.ref?.removeValue()?.await()
    }

    suspend fun guardarLocalizacion(localizacion: LocalizacionesEntity) {
        val root = localizacionesRoot()
        val key = localizacion.id.takeIf { it != 0 }?.toString()
            ?: throw IllegalArgumentException("La localización requiere un id válido")

        val payload = mapOf(
            "id" to localizacion.id,
            "pueblo" to localizacion.pueblo,
            "calle" to localizacion.calle,
            "direccion" to localizacion.direccion,
            "latitud" to localizacion.latitud,
            "longitud" to localizacion.longitud,
            "del poste" to localizacion.delPoste,
            "al poste" to localizacion.alPoste,
            "subregion" to localizacion.subregion
        )

        root.child(key).setValue(payload).await()
    }

    suspend fun guardarReparacionLuminaria(
        reparacion: LuminariaReparacionEntity,
        agencia: String?
    ) {
        val root = luminariasRoot(agencia)
        val payload = mapOf(
            "id" to reparacion.id,
            "vehiculoId" to reparacion.vehiculoId,
            "localizacion" to reparacion.localizacion,
            "cliente" to reparacion.cliente,
            "contacto" to reparacion.contacto,
            "observaciones" to reparacion.observaciones,
            "materialesJson" to reparacion.materialesJson,
            "estado" to reparacion.estado,
            "ejecutorNombre" to reparacion.ejecutorNombre,
            "ejecutorCedula" to reparacion.ejecutorCedula,
            "fechaRegistro" to reparacion.fechaRegistro,
            "fechaCarga" to reparacion.fechaCarga,
            "fechaReparacion" to reparacion.fechaReparacion
        )
        val estado = LuminariaEstado.fromRaw(reparacion.estado)
        val destino = if (estado == LuminariaEstado.PENDIENTE) "pendientes" else "reparadas"
        val limpiar = if (estado == LuminariaEstado.PENDIENTE) "reparadas" else "pendientes"
        root.child(destino).child(reparacion.id.toString()).setValue(payload).await()
        root.child(limpiar).child(reparacion.id.toString()).removeValue().await()
    }

    suspend fun eliminarReparacionLuminaria(id: Long, agencia: String?) {
        val root = luminariasRoot(agencia)
        root.child("pendientes").child(id.toString()).removeValue().await()
        root.child("reparadas").child(id.toString()).removeValue().await()
    }

    // --- INVENTARIO ---
    suspend fun obtenerInventario(): List<InventarioItemEntity> {
        val root = inventarioRoot()
        val snap = root.get().await()
        if (!snap.exists()) return emptyList()
        return parseInventarioSnapshot(snap)
    }

    suspend fun obtenerInventarioDeVehiculo(vehiculoKey: String): List<InventarioItemEntity> {
        val key = vehiculoKey.trim()
        if (key.isEmpty()) {
            Log.w(TAG, "[INV_DIAG][FIREBASE_INVENTARIO_SKIP] reason=vehiculoKey_blank")
            return emptyList()
        }
        val base = inventarioRoot()
        val root = base.child(key)
        Log.i(TAG, "[INV_DIAG][FIREBASE_INVENTARIO_FETCH] basePath=${base.path} candidatePath=${root.path} key=$key")
        val snap = root.get().await()
        if (!snap.exists()) {
            Log.i(TAG, "[INV_DIAG][FIREBASE_INVENTARIO_FETCH] path_not_found candidatePath=${root.path} key=$key")
            return emptyList()
        }
        val parsed = parseInventarioVehiculoNode(snap)
        Log.i(TAG, "[INV_DIAG][FIREBASE_INVENTARIO_FETCH] path_found candidatePath=${root.path} key=$key items=${parsed.size}")
        return parsed
    }

    suspend fun guardarInventarioVehiculo(vehiculoKey: String, vehiculoId: Int, items: List<InventarioItemEntity>) {
        val payload = items.associate { item ->
            val key = item.codigoMaterial.trim()
            key to mapOf(
                "id" to item.id,
                "vehiculoId" to vehiculoId,
                "codigoMaterial" to item.codigoMaterial,
                "descripcionMaterial" to item.descripcionMaterial,
                "cantidadDisponible" to item.cantidadDisponible
            )
        }
        val root = inventarioRoot()
        root.child(vehiculoKey).setValue(payload).await()
    }

    suspend fun guardarInventarioItem(vehiculoKey: String, item: InventarioItemEntity) {
        val codigo = item.codigoMaterial.trim()
        if (codigo.isEmpty()) return
        val payload = mapOf(
            "id" to item.id,
            "vehiculoId" to item.vehiculoId,
            "codigoMaterial" to item.codigoMaterial,
            "descripcionMaterial" to item.descripcionMaterial,
            "cantidadDisponible" to item.cantidadDisponible
        )
        val root = inventarioRoot()
        root.child(vehiculoKey).child(codigo).setValue(payload).await()
    }

    suspend fun eliminarInventarioItem(vehiculoKey: String, codigoMaterial: String) {
        val codigo = codigoMaterial.trim()
        if (codigo.isEmpty()) return
        val root = inventarioRoot()
        root.child(vehiculoKey).child(codigo).removeValue().await()
    }

    suspend fun eliminarInventarioVehiculo(vehiculoKey: String) {
        val root = inventarioRoot()
        root.child(vehiculoKey).removeValue().await()
    }

    suspend fun obtenerLuminarias(agencia: String?): List<LuminariaReparacionEntity> {
        val base = luminariasBase()
        val agencias = if (agencia.isNullOrBlank()) {
            base.get().await().children.mapNotNull { it.key }
        } else {
            listOf(normalizarClave(agencia) ?: agencia)
        }

        val reparaciones = mutableListOf<LuminariaReparacionEntity>()
        agencias.forEach { agenciaKey ->
            val root = base.child(agenciaKey)
            val snap = root.get().await()
            if (!snap.exists()) return@forEach
            reparaciones += parseLuminariasSnapshot(snap)
        }
        return reparaciones
    }

    suspend fun obtenerLuminariasPorAgencia(agencia: String): List<LuminariaReparacionEntity> {
        val agenciaRaw = agencia.trim()
        if (agenciaRaw.isBlank()) {
            Log.w(TAG, "[LUM_FIREBASE][PATH_NOT_FOUND] reason=agencia_blank")
            return emptyList()
        }
        val base = luminariasBase()
        Log.i(TAG, "[LUM_FIREBASE][ROOT] path=${base.path}")
        val candidates = buildAgencyKeyCandidates(agenciaRaw)
        candidates.forEach { agencyKey ->
            val node = base.child(agencyKey)
            Log.i(TAG, "[LUM_FIREBASE][AGENCY_PATH] trying=${node.path}")
            val snap = node.get().await()
            if (!snap.exists()) {
                Log.w(TAG, "[LUM_FIREBASE][PATH_NOT_FOUND] path=${node.path}")
                return@forEach
            }
            val pendientesCount = snap.child("pendientes").childrenCount.toInt()
            val reparadasCount = snap.child("reparadas").childrenCount.toInt()
            val parsed = parseLuminariasSnapshot(snap)
            Log.i(TAG, "[LUM_FIREBASE][PENDIENTES_COUNT] agencyKey=$agencyKey count=$pendientesCount")
            Log.i(TAG, "[LUM_FIREBASE][REPARADAS_COUNT] agencyKey=$agencyKey count=$reparadasCount")
            Log.i(TAG, "[LUM_FIREBASE][TOTAL_COUNT] agencyKey=$agencyKey count=${parsed.size}")
            return parsed
        }
        return emptyList()
    }

    private fun buildAgencyKeyCandidates(agencia: String): List<String> {
        val raw = agencia.trim()
        val normalizedStrong = normalizeAgencyKeyForFirebase(raw)
        val mojibakeFixed = fixCommonMojibake(raw)
        val lower = raw.lowercase(Locale.getDefault())
        val noSpaces = raw.replace("\\s+".toRegex(), "")
        val underscore = raw.replace("\\s+".toRegex(), "_")
        val noDiacriticsRaw = removeDiacritics(raw).lowercase(Locale.getDefault())
        val noDiacriticsNoSpaces = noDiacriticsRaw.replace("\\s+".toRegex(), "")
        val legacyNormalized = normalizarClave(raw).orEmpty()

        Log.i(
            TAG,
            "[LUM_FIREBASE][AGENCY_NORMALIZE] rawLength=${raw.length} normalized=$normalizedStrong"
        )
        val candidates = linkedSetOf<String>()
        listOf(
            normalizedStrong,
            raw,
            lower,
            noSpaces,
            noDiacriticsRaw,
            noDiacriticsNoSpaces,
            underscore,
            legacyNormalized
        ).forEach { candidate ->
            val key = candidate.trim()
            if (key.isNotEmpty()) candidates.add(key)
        }
        return candidates.toList()
    }

    private fun normalizeAgencyKeyForFirebase(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val mojibakeFixed = fixCommonMojibake(trimmed)
        val withoutDiacritics = removeDiacritics(mojibakeFixed).lowercase(Locale.getDefault())
        val noSpacesOrPunctuation = withoutDiacritics
            .replace("[\\s_\\-]+".toRegex(), "")
            .replace("[^a-z0-9]".toRegex(), "")
        return noSpacesOrPunctuation
    }

    private fun removeDiacritics(value: String): String {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
        return DIACRITIC_REGEX.replace(normalized, "")
    }

    private fun fixCommonMojibake(value: String): String {
        var fixed = value
        val replacements = listOf(
            "Ã¡" to "á",
            "Ã©" to "é",
            "Ã­" to "í",
            "Ã³" to "ó",
            "Ãº" to "ú",
            "Ã±" to "ñ",
            "Ã" to "Á",
            "Ã‰" to "É",
            "Ã" to "Í",
            "Ã“" to "Ó",
            "Ãš" to "Ú",
            "Ã‘" to "Ñ"
        )
        replacements.forEach { (broken, corrected) ->
            fixed = fixed.replace(broken, corrected)
        }
        return fixed
    }

    suspend fun guardarKilometrajeVehicular(
        placa: String,
        kilometrajeFinal: Double,
        registradoEn: Long
    ) {
        val placaNormalizada = VehiculoPlacaUtils.parsePlacaLong(placa)?.toString() ?: return
        val payload = mapOf(
            "placa" to placa.trim(),
            "placaNormalizada" to placaNormalizada,
            "kilometrajeFinal" to kilometrajeFinal,
            "registradoEn" to registradoEn
        )
        vehiculoKilometrajesRoot()
            .child(placaNormalizada)
            .child(registradoEn.toString())
            .updateChildren(payload)
            .await()
    }

    suspend fun guardarMantenimientoVehicular(
        placa: String,
        vehiculoId: Int?,
        tipo: String,
        fecha: Long?,
        valorAlMomento: Double?,
        observaciones: String?,
        proximoKm: Double?,
        proximoHoras: Double?,
        proximoFecha: Long?,
        registradoEn: Long
    ) {
        val placaNormalizada = VehiculoPlacaUtils.parsePlacaLong(placa)?.toString() ?: return
        val payload = mapOf(
            "placa" to placa.trim(),
            "placaNormalizada" to placaNormalizada,
            "vehiculoId" to vehiculoId,
            "tipo" to tipo,
            "fecha" to fecha,
            "valorAlMomento" to valorAlMomento,
            "observaciones" to observaciones,
            "proximoKm" to proximoKm,
            "proximoHoras" to proximoHoras,
            "proximoFecha" to proximoFecha,
            "registradoEn" to registradoEn
        )
        vehiculoMantenimientosRoot()
            .child(placaNormalizada)
            .child(registradoEn.toString())
            .updateChildren(payload)
            .await()
    }

    fun startInventarioRealtimeForVehiculo(
        vehiculoKey: String,
        vehiculoIdLocal: Int,
        scope: CoroutineScope,
        onItemUpsert: suspend (InventarioItemEntity) -> Unit,
        onItemRemove: suspend (vehiculoId: Int, codigo: String) -> Unit,
        onError: (DatabaseError) -> Unit
    ): ValueEventListener {

        val token = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) = Unit
            override fun onCancelled(error: DatabaseError) = Unit
        }

        val target = inventarioRoot().child(vehiculoKey.trim())
        Log.i(TAG, "[INV_DIAG][REALTIME_INVENTARIO] listening key=${vehiculoKey.trim()} vehiculoIdLocal=$vehiculoIdLocal path=${target.path}")

        val childListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                scope.launch {
                    val codigo = snapshot.key?.trim().orEmpty()
                    if (codigo.isBlank()) return@launch

                    val item = InventarioItemEntity(
                        id = snapshot.child("id").getValue(Long::class.java) ?: 0L,
                        vehiculoId = vehiculoIdLocal,
                        codigoMaterial = codigo,
                        descripcionMaterial = snapshot.child("descripcionMaterial").getValue(String::class.java).orEmpty(),
                        cantidadDisponible = snapshot.child("cantidadDisponible").getValue(Double::class.java) ?: 0.0
                    )
                    onItemUpsert(item)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                onChildAdded(snapshot, previousChildName)
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                scope.launch {
                    val codigo = snapshot.key?.trim().orEmpty()
                    if (codigo.isBlank()) return@launch
                    onItemRemove(vehiculoIdLocal, codigo)
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onCancelled(error: DatabaseError) = onError(error)
        }

        inventarioChildListeners[token] = childListener
        inventarioChildRefs[token] = target
        target.addChildEventListener(childListener)

        return token
    }

    fun stopInventarioRealtime(listener: ValueEventListener) {
        val childListener = inventarioChildListeners.remove(listener)
        val target = inventarioChildRefs.remove(listener)
        if (childListener != null && target != null) {
            target.removeEventListener(childListener)
        }
    }

    fun startLuminariasRealtimeForAgencia(
        agenciaKey: String,
        scope: CoroutineScope,
        onUpsert: suspend (LuminariaReparacionEntity) -> Unit,
        onRemove: suspend (id: Long) -> Unit,
        onError: (DatabaseError) -> Unit
    ): ValueEventListener {

        val token = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) = Unit
            override fun onCancelled(error: DatabaseError) = Unit
        }

        val target = runBlocking {
            luminariasRoot(agenciaKey.trim())
        }

        val childListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                scope.launch {
                    parseLuminariaSnapshot(snapshot, null)?.let { onUpsert(it) }
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                onChildAdded(snapshot, previousChildName)
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                scope.launch {
                    val nodeId = parseLuminariaSnapshot(snapshot, null)?.id
                    if (nodeId != null) onRemove(nodeId)
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onCancelled(error: DatabaseError) = onError(error)
        }

        luminariasChildListeners[token] = childListener
        luminariasChildRefs[token] = target
        target.addChildEventListener(childListener)

        return token
    }

    fun stopLuminariasRealtime(listener: ValueEventListener) {
        val childListener = luminariasChildListeners.remove(listener)
        val target = luminariasChildRefs.remove(listener)
        if (childListener != null && target != null) {
            target.removeEventListener(childListener)
        }
    }

    suspend fun eliminarLocalizacion(id: Int) {
        if (id == 0) return
        val root = localizacionesRoot()
        val key = id.toString()
        val direct = runCatching { root.child(key).get().await() }.getOrNull()
        if (direct != null && direct.exists()) {
            direct.ref.removeValue().await()
            return
        }

        val match = runCatching {
            root.orderByChild("id").equalTo(id.toDouble()).get().await().children.firstOrNull()
        }.getOrNull()
        match?.ref?.removeValue()?.await()
    }

    private suspend fun nombreSubregionDesdeCatalogo(subregionId: String): String? {
        val id = subregionId.trim()
        if (id.isEmpty()) return null
        subregionNombreCache[id]?.let { return it }
        val catalogo = runCatching { obtenerSubregiones() }.getOrElse { emptyList() }
        catalogo.forEach { subregion ->
            if (subregion.id.isNotBlank() && subregion.nombre.isNotBlank()) {
                subregionNombreCache[subregion.id] = subregion.nombre
            }
        }
        return subregionNombreCache[id]
    }

    private suspend fun obtenerReferenciaSubregion(
        storageKey: String,
        lookupNombre: String?,
        createIfMissing: Boolean
    ): DatabaseReference? {
        val candidatos = buildList {
            add(storageKey)
            lookupNombre?.takeIf { it.isNotBlank() }?.let { add(it) }
            nombreSubregionDesdeCatalogo(storageKey)?.let { add(it) }
        }.mapNotNull { it?.trim()?.takeIf { trimmed -> trimmed.isNotEmpty() } }
            .distinct()

        if (candidatos.isEmpty()) return null

        val normalizedTargets = candidatos.mapNotNull { normalizarClave(it) }.distinct()
        val rootCandidates = listOf("Medidores", "medidores")
        var fallbackRoot: DatabaseReference? = null

        for (rootKey in rootCandidates) {
            val rootRef = dbMedidores.child(rootKey)
            val rootSnap = runCatching { rootRef.get().await() }.getOrNull()
            fallbackRoot = rootRef
            if (rootSnap == null || !rootSnap.exists()) continue
            val match = encontrarSubregion(rootSnap, normalizedTargets)
            if (match != null) return match.ref
        }

        if (!createIfMissing) return null
        val rootRef = fallbackRoot ?: dbMedidores.child(rootCandidates.first())
        val preferred = lookupNombre?.takeIf { it.isNotBlank() }?.trim()
        val newKey = preferred ?: candidatos.first()
        return rootRef.child(newKey)
    }

    private fun encontrarSubregion(
        snapshot: DataSnapshot,
        normalizedTargets: List<String>
    ): DataSnapshot? {
        if (normalizedTargets.isEmpty()) return null
        snapshot.children.forEach { child ->
            val keyNormalized = normalizarClave(child.key)
            if (keyNormalized != null && normalizedTargets.any { it == keyNormalized }) {
                return child
            }
        }

        snapshot.children.forEach { child ->
            val key = child.key ?: return@forEach
            val shouldDive = key.any { it.isLetter() } && child.childrenCount > 0
            if (!shouldDive) return@forEach
            val nested = encontrarSubregion(child, normalizedTargets)
            if (nested != null) return nested
        }
        return null
    }

    private fun extraerMedidores(node: DataSnapshot, storageKey: String): List<MedidorEntity> {
        val result = mutableListOf<MedidorEntity>()
        if (esNodoMedidor(node)) {
            parseMedidorSnapshot(node, storageKey)?.let { result.add(it) }
        } else {
            node.children.forEach { child ->
                result += extraerMedidores(child, storageKey)
            }
        }
        return result
    }


    private suspend fun selectInventarioRealtimeRef(): DatabaseReference {
        val lower = dbInventario.child("inventario")
        val upper = dbInventario.child("Inventario")
        val lowerExists = runCatching { lower.get().await().exists() }.getOrDefault(false)
        val upperExists = runCatching { upper.get().await().exists() }.getOrDefault(false)
        return when {
            lowerExists -> lower
            upperExists -> upper
            else -> lower
        }
    }

    private suspend fun selectLuminariasRealtimeRef(): DatabaseReference {
        val lower = dbLuminarias.child("luminarias")
        val upper = dbLuminarias.child("Luminarias")
        val lowerExists = runCatching { lower.get().await().exists() }.getOrDefault(false)
        val upperExists = runCatching { upper.get().await().exists() }.getOrDefault(false)
        return when {
            lowerExists -> lower
            upperExists -> upper
            else -> lower
        }
    }

    private suspend fun localizacionesRoot(): DatabaseReference {
        val upper = dbLocal.child("Localizaciones")
        val lower = dbLocal.child("localizaciones")
        val upperExists = runCatching { upper.get().await().exists() }.getOrDefault(false)
        val lowerExists = runCatching { lower.get().await().exists() }.getOrDefault(false)
        return when {
            upperExists -> upper
            lowerExists -> lower
            else -> upper
        }
    }

    private suspend fun luminariasRoot(agencia: String?): DatabaseReference {
        val agenciaKey = normalizarClave(agencia)?.takeIf { it.isNotBlank() } ?: "sin_agencia"
        val root = luminariasBase().child(agenciaKey)
        val exists = runCatching { root.get().await().exists() }.getOrDefault(false)
        return if (exists) root else root
    }

    private suspend fun luminariasBase(): DatabaseReference {
        val lower = dbLuminarias.child("luminarias")
        val upper = dbLuminarias.child("Luminarias")
        val lowerExists = runCatching { lower.get().await().exists() }.getOrDefault(false)
        val upperExists = runCatching { upper.get().await().exists() }.getOrDefault(false)
        return when {
            lowerExists -> lower
            upperExists -> upper
            else -> lower
        }
    }

    private suspend fun inventarioRoot(): DatabaseReference {
        val lower = dbInventario.child("inventario")
        val upper = dbInventario.child("Inventario")
        val lowerExists = runCatching { lower.get().await().exists() }.getOrDefault(false)
        val upperExists = runCatching { upper.get().await().exists() }.getOrDefault(false)
        return when {
            lowerExists -> lower
            upperExists -> upper
            else -> lower
        }
    }

    private fun vehiculoKilometrajesRoot(): DatabaseReference {
        return dbVehiculoOps.child("vehiculo_etm")
    }

    private fun vehiculoMantenimientosRoot(): DatabaseReference {
        return dbVehiculoOps.child("vehiculo_mantenimiento")
    }

    private fun parseLuminariaSnapshot(
        snapshot: DataSnapshot,
        estadoFallback: LuminariaEstado?
    ): LuminariaReparacionEntity? {
        if (!snapshot.exists()) return null
        val idFromPayload = snapshot.longChildAny("id")
        val idFromKey = snapshot.key?.toLongOrNull()
        val id = idFromPayload ?: idFromKey
        if (id == null) {
            Log.w(
                TAG,
                "[LUM_PARSE][INVALID_ID] key=${snapshot.key ?: "null"} hasIdField=${idFromPayload != null}"
            )
            return null
        }
        if (id == 0L) {
            Log.w(TAG, "[LUM_PARSE][ZERO_ID] key=${snapshot.key ?: "null"}")
        }
        val vehiculoId = snapshot.intValueAny("vehiculoId", "vehiculo_id") ?: 0
        val localizacion = snapshot.stringChildAny("localizacion", "Localizacion", "Localización").orEmpty()
        val cliente = snapshot.stringChildAny("cliente", "Cliente")
        val contacto = snapshot.stringChildAny("contacto", "Contacto")
        val observaciones = snapshot.stringChildAny("observaciones", "Observaciones")
        val materialesJson = snapshot.stringChildAny("materialesJson", "materiales")
        val estadoRaw = snapshot.stringChildAny("estado", "Estado")
        val estado = when {
            estadoRaw.isNullOrBlank() -> estadoFallback ?: LuminariaEstado.REPARADA
            else -> LuminariaEstado.fromRaw(estadoRaw)
        }
        val ejecutorNombre = snapshot.stringChildAny("ejecutorNombre", "ejecutor", "Ejecutor").orEmpty()
        val ejecutorCedula = snapshot.stringChildAny("ejecutorCedula", "ejecutor_cedula")
        val fechaRegistro = snapshot.longChildAny("fechaRegistro", "fecha_registro") ?: System.currentTimeMillis()
        val fechaCarga = snapshot.longChildAny("fechaCarga", "fecha_carga") ?: fechaRegistro
        val fechaReparacion = snapshot.longChildAny("fechaReparacion", "fecha_reparacion")

        return LuminariaReparacionEntity(
            id = id,
            vehiculoId = vehiculoId,
            localizacion = localizacion,
            cliente = cliente,
            contacto = contacto,
            observaciones = observaciones,
            materialesJson = materialesJson,
            estado = estado.name,
            ejecutorNombre = ejecutorNombre,
            ejecutorCedula = ejecutorCedula,
            fechaRegistro = fechaRegistro,
            fechaCarga = fechaCarga,
            fechaReparacion = fechaReparacion
        )
    }

    private fun resolveInventarioNode(snapshot: DataSnapshot): DataSnapshot {
        return when {
            snapshot.hasChild("inventario") -> snapshot.child("inventario")
            snapshot.hasChild("Inventario") -> snapshot.child("Inventario")
            else -> snapshot
        }
    }

    private fun parseInventarioSnapshot(snapshot: DataSnapshot): List<InventarioItemEntity> {
        return snapshot.children.flatMap { vehiculoNode ->
            vehiculoNode.children.mapNotNull { itemNode ->
                val vehiculoId = itemNode.intValueAny("vehiculoId", "vehiculo_id")
                    ?: vehiculoNode.intValueAny("vehiculoId", "vehiculo_id")
                    ?: vehiculoNode.key?.toIntOrNull()
                    ?: return@mapNotNull null
                val codigo = itemNode.stringChildAny("codigoMaterial", "codigo", "codigo_material")
                    ?: itemNode.key?.trim()
                if (codigo.isNullOrBlank()) return@mapNotNull null
                val descripcion = itemNode.stringChildAny(
                    "descripcionMaterial",
                    "descripcion",
                    "descripcion_material"
                ).orEmpty()
                val cantidad = itemNode.doubleValueAny(
                    "cantidadDisponible",
                    "cantidad",
                    "cantidad_disponible"
                ) ?: 0.0
                val id = itemNode.longChildAny("id") ?: 0L
                InventarioItemEntity(
                    id = id,
                    vehiculoId = vehiculoId,
                    codigoMaterial = codigo,
                    descripcionMaterial = descripcion,
                    cantidadDisponible = cantidad
                )
            }
        }
    }

    private fun resolveLuminariasNode(snapshot: DataSnapshot): DataSnapshot {
        return when {
            snapshot.hasChild("luminarias") -> snapshot.child("luminarias")
            snapshot.hasChild("Luminarias") -> snapshot.child("Luminarias")
            else -> snapshot
        }
    }

    private fun parseLuminariasSnapshot(snapshot: DataSnapshot): List<LuminariaReparacionEntity> {
        if (!snapshot.exists()) return emptyList()
        val parsed = if (snapshot.hasChild("pendientes") || snapshot.hasChild("reparadas")) {
            parseLuminariasFromAgencia(snapshot)
        } else {
            val reparaciones = mutableListOf<LuminariaReparacionEntity>()
            snapshot.children.forEach { agenciaNode ->
                if (!agenciaNode.exists()) return@forEach
                reparaciones += parseLuminariasFromAgencia(agenciaNode)
            }
            reparaciones
        }
        val duplicateIds = parsed
            .groupBy { it.id }
            .filterValues { it.size > 1 }
            .keys
        if (duplicateIds.isNotEmpty()) {
            duplicateIds.forEach { duplicateId ->
                Log.w(TAG, "[LUM_PARSE][DUPLICATE_ID] id=$duplicateId occurrences=${parsed.count { it.id == duplicateId }}")
            }
        }
        val zeroIdCount = parsed.count { it.id == 0L }
        Log.i(
            TAG,
            "[LUM_PARSE][SUMMARY] parsed=${parsed.size} duplicateIds=${duplicateIds.size} zeroIds=$zeroIdCount"
        )
        return parsed
    }

    private fun parseLuminariasFromAgencia(agenciaNode: DataSnapshot): List<LuminariaReparacionEntity> {
        val pendientesNode = agenciaNode.child("pendientes")
        val reparadasNode = agenciaNode.child("reparadas")
        return if (pendientesNode.exists() || reparadasNode.exists()) {
            pendientesNode.children.mapNotNull { parseLuminariaSnapshot(it, LuminariaEstado.PENDIENTE) } +
                reparadasNode.children.mapNotNull { parseLuminariaSnapshot(it, LuminariaEstado.REPARADA) }
        } else {
            agenciaNode.children.mapNotNull { parseLuminariaSnapshot(it, null) }
        }
    }

    private fun processInventarioNodeUpdate(
        snapshot: DataSnapshot,
        byNode: MutableMap<String, List<InventarioItemEntity>>,
        scope: CoroutineScope,
        onUpdate: suspend (List<InventarioItemEntity>) -> Unit
    ) {
        val nodeKey = snapshot.key.orEmpty()
        val parsed = parseInventarioVehiculoNode(snapshot)
        byNode[nodeKey] = parsed
        emitInventarioSnapshot(scope, byNode, onUpdate)
    }

    private fun emitInventarioSnapshot(
        scope: CoroutineScope,
        byNode: Map<String, List<InventarioItemEntity>>,
        onUpdate: suspend (List<InventarioItemEntity>) -> Unit
    ) {
        scope.launch {
            onUpdate(byNode.values.flatten())
        }
    }

    private fun parseInventarioVehiculoNode(vehiculoNode: DataSnapshot): List<InventarioItemEntity> {
        if (!vehiculoNode.exists()) return emptyList()
        val vehiculoId = vehiculoNode.intValueAny("vehiculoId", "vehiculo_id")
            ?: vehiculoNode.key?.toIntOrNull()
        if (vehiculoId == null) return emptyList()
        return vehiculoNode.children.mapNotNull { itemNode ->
            val codigo = itemNode.stringChildAny("codigoMaterial", "codigo", "codigo_material")
                ?: itemNode.key?.trim()
            if (codigo.isNullOrBlank()) return@mapNotNull null
            val descripcion = itemNode.stringChildAny(
                "descripcionMaterial",
                "descripcion",
                "descripcion_material"
            ).orEmpty()
            val cantidad = itemNode.doubleValueAny(
                "cantidadDisponible",
                "cantidad",
                "cantidad_disponible"
            ) ?: 0.0
            val id = itemNode.longChildAny("id") ?: 0L
            InventarioItemEntity(
                id = id,
                vehiculoId = vehiculoId,
                codigoMaterial = codigo,
                descripcionMaterial = descripcion,
                cantidadDisponible = cantidad
            )
        }
    }

    private fun processLuminariasNodeUpdate(
        snapshot: DataSnapshot,
        byNode: MutableMap<String, List<LuminariaReparacionEntity>>,
        scope: CoroutineScope,
        onUpdate: suspend (List<LuminariaReparacionEntity>) -> Unit
    ) {
        val nodeKey = snapshot.key.orEmpty()
        val parsed = parseLuminariasNode(snapshot)
        byNode[nodeKey] = parsed
        emitLuminariasSnapshot(scope, byNode, onUpdate)
    }

    private fun parseLuminariasNode(snapshot: DataSnapshot): List<LuminariaReparacionEntity> {
        if (!snapshot.exists()) return emptyList()
        return parseLuminariasFromAgencia(snapshot)
    }

    private fun emitLuminariasSnapshot(
        scope: CoroutineScope,
        byNode: Map<String, List<LuminariaReparacionEntity>>,
        onUpdate: suspend (List<LuminariaReparacionEntity>) -> Unit
    ) {
        scope.launch {
            onUpdate(byNode.values.flatten())
        }
    }

    private fun esNodoMedidor(node: DataSnapshot): Boolean {
        if (!node.hasChildren()) return false
        val keys = node.children.mapNotNull { it.key?.lowercase(Locale.getDefault()) }
        if (keys.isEmpty()) return false
        val expected = setOf(
            "cliente",
            "calle",
            "metros",
            "poste",
            "pueblo",
            "localizacion",
            "localización",
            "medidornumber"
        )
        return keys.any { it in expected }
    }

    private fun parseMedidorSnapshot(
        snapshot: DataSnapshot,
        storageKey: String,
        fallbackNumero: String? = null
    ): MedidorEntity? {
        if (!snapshot.hasChildren()) return null
        val entity = snapshot.getValue(MedidorEntity::class.java)
        val numero = (entity?.medidorNumber?.takeIf { it.isNotBlank() }
            ?: snapshot.key?.takeIf { it.isNotBlank() }
            ?: fallbackNumero).orEmpty().trim()
        if (numero.isEmpty()) return null

        val cliente = entity?.cliente?.trim()
            ?: snapshot.stringChildAny("cliente", "Cliente")
        val calle = entity?.calle?.trim()
            ?: snapshot.stringChildAny("calle", "Calle")
        val metros = entity?.metros?.trim()
            ?: snapshot.stringChildAny("metros", "Metros")
        val poste = entity?.poste?.trim()
            ?: snapshot.stringChildAny("poste", "Poste")
        val pueblo = entity?.pueblo?.trim()
            ?: snapshot.stringChildAny("pueblo", "Pueblo")
        val localizacion = entity?.localizacion
            ?: snapshot.longChildAny("localizacion", "Localizacion", "Localización")
            ?: snapshot.stringChildAny("localizacion", "Localizacion", "Localización")?.toLongOrNull()

        val base = entity ?: MedidorEntity()
        val cleaned = base.copy(
            medidorNumber = numero,
            cliente = cliente,
            calle = calle,
            metros = metros,
            poste = poste,
            pueblo = pueblo,
            localizacion = localizacion,
            subregion = storageKey
        )
        return cleaned.takeIf { it.medidorNumber.isNotBlank() }
    }

    private fun buscarMedidorEnNodo(
        node: DataSnapshot,
        storageKey: String,
        numero: String
    ): MedidorEntity? {
        if (!node.hasChildren()) return null
        if (esNodoMedidor(node)) {
            val candidato = parseMedidorSnapshot(node, storageKey, numero)
            if (candidato != null && coincideNumero(numero, candidato.medidorNumber)) {
                return candidato
            }
        }
        node.children.forEach { child ->
            val encontrado = buscarMedidorEnNodo(child, storageKey, numero)
            if (encontrado != null) return encontrado
        }
        return null
    }

    private fun coincideNumero(target: String, candidate: String?): Boolean {
        if (candidate.isNullOrBlank()) return false
        val esperado = target.trim()
        val comparado = candidate.trim()
        if (esperado.equals(comparado, ignoreCase = true)) return true
        return esperado.trimStart('0') == comparado.trimStart('0')
    }

    private fun normalizarClave(valor: String?): String? {
        if (valor.isNullOrBlank()) return null
        val normalized = Normalizer.normalize(valor, Normalizer.Form.NFD)
        val sinTildes = DIACRITIC_REGEX.replace(normalized, "")
        return NON_ALNUM_REGEX.replace(sinTildes.lowercase(Locale.getDefault()), "")
    }

    // --- TÉCNICOS ---
    suspend fun obtenerTecnicos(): List<TecnicoEntity> {
        val root = dbTecnicos.get().await()
        val nodo = when {
            root.hasChild("personal") -> root.child("personal")
            else -> root
        }
        return nodo.children.mapNotNull { child ->
            val cedula = child.key?.trim().orEmpty()
            val nombre = child.child("nombre").value?.toString()?.trim().orEmpty()
            if (cedula.isBlank() || nombre.isBlank()) return@mapNotNull null
            TecnicoEntity(cedula = cedula, nombre = nombre)
        }
    }

    // --- MATERIALES ---
    suspend fun obtenerMaterialesCatalogo(): List<MaterialEntity> {
        val snap = dbMaterialesIce.get().await()
        return snap.children.mapNotNull { child ->
            val codigo = child.key?.trim().orEmpty()
            val nombre = child.child("Nombre").value?.toString()?.trim().orEmpty()
            if (codigo.isBlank() || nombre.isBlank()) return@mapNotNull null
            val unidad = child.child("Unidad").value?.toString()?.trim().orEmpty()
            MaterialEntity(
                id = codigo.hashCode().toLong(),
                codigo = codigo,
                descripcion = nombre,
                unidad = unidad
            )
        }
    }

    private fun DataSnapshot.stringChildAny(vararg names: String): String? {
        names.forEach { nombre ->
            val valor = child(nombre).value?.toString()?.trim()
            if (!valor.isNullOrEmpty()) return valor
        }
        return null
    }

    private fun DataSnapshot.longChildAny(vararg names: String): Long? {
        names.forEach { nombre ->
            val valor = child(nombre).value ?: return@forEach
            when (valor) {
                is Long -> return valor
                is Int -> return valor.toLong()
                is Double -> return valor.toLong()
                is Float -> return valor.toLong()
                else -> valor.toString().toLongOrNull()?.let { return it }
            }
        }
        return null
    }


    private fun estimatePayloadBytes(value: Any?): Long =
        value?.toString()?.toByteArray(Charsets.UTF_8)?.size?.toLong() ?: 0L

    private fun DataSnapshot.valueAny(vararg names: String): Any? {
        names.forEach { nombre ->
            val direct = child(nombre)
            if (direct.exists()) return direct.value
        }
        val normalizedTargets = names.mapNotNull { normalizarClave(it) }.toSet()
        if (normalizedTargets.isEmpty()) return null
        children.forEach { child ->
            val keyNormalized = normalizarClave(child.key)
            if (keyNormalized != null && keyNormalized in normalizedTargets) {
                return child.value
            }
        }
        return null
    }

    private fun DataSnapshot.intValueAny(vararg names: String): Int? {
        val value = valueAny(*names) ?: return null
        return when (value) {
            is Int -> value
            is Long -> value.toInt()
            is Double -> value.toInt()
            is Float -> value.toInt()
            is String -> value.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.toInt()
            else -> value.toString().toDoubleOrNull()?.toInt()
        }
    }

    private fun DataSnapshot.doubleValueAny(vararg names: String): Double? {
        val value = valueAny(*names) ?: return null
        return when (value) {
            is Double -> value
            is Float -> value.toDouble()
            is Int -> value.toDouble()
            is Long -> value.toDouble()
            is String -> value.trim().replace(",", ".").toDoubleOrNull()
            else -> value.toString().toDoubleOrNull()
        }
    }

    private fun DataSnapshot.booleanValueAny(vararg names: String): Boolean? {
        val value = valueAny(*names) ?: return null
        return when (value) {
            is Boolean -> value
            is Int -> value != 0
            is Long -> value != 0L
            is Double -> value != 0.0
            is Float -> value != 0f
            is String -> {
                val normalized = value.trim().lowercase(Locale.getDefault())
                normalized in setOf("true", "1", "si", "sí", "yes")
            }
            else -> value.toString().trim().lowercase(Locale.getDefault()) in setOf("true", "1", "si", "sí", "yes")
        }
    }

    private fun DataSnapshot.stringValueAny(vararg names: String): String? {
        val value = valueAny(*names) ?: return null
        return value.toString()
    }

    private fun DataSnapshot.stringValueAnyTrim(vararg names: String): String? {
        return stringValueAny(*names)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun DataSnapshot.toUserEntity(): UserEntity? {
        val uid = stringValueAnyTrim("uid") ?: key?.trim().orEmpty()
        if (uid.isBlank()) return null
        return UserEntity(
            uid = uid,
            email = stringValueAnyTrim("email"),
            email_lower = stringValueAnyTrim("email_lower", "emailLower"),
            nombre = stringValueAnyTrim("nombre"),
            apellidos = stringValueAnyTrim("apellidos"),
            primerApellido = stringValueAnyTrim("primer_apellido", "primerApellido"),
            segundoApellido = stringValueAnyTrim("segundo_apellido", "segundoApellido"),
            cedula = stringValueAnyTrim("cedula"),
            region = stringValueAnyTrim("region"),
            regionNombre = stringValueAnyTrim("region_nombre", "regionNombre"),
            subregion = stringValueAnyTrim("subregion"),
            subregionNombre = stringValueAnyTrim("subregion_nombre", "subregionNombre"),
            agenciaId = stringValueAnyTrim("agencia_id", "agenciaId"),
            agencia = stringValueAnyTrim("agencia"),
            placaVehiculo = stringValueAnyTrim("placaVehiculo", "placa_vehiculo", "placa"),
            telefono = stringValueAnyTrim("telefono", "tel", "phone"),
            password = stringValueAnyTrim("password"),
            fotoUrl = stringValueAnyTrim("fotoUrl", "foto_url", "foto"),
            rol = stringValueAnyTrim("rol")
        )
    }

    private fun String?.toIntLooseOrNull(): Int? {
        val cleaned = this?.trim().orEmpty()
        if (cleaned.isBlank()) return null
        cleaned.toDoubleOrNull()?.toInt()?.let { return it }
        val digits = cleaned.filter { it.isDigit() }
        return digits.toIntOrNull()
    }

    private fun generarIdLocalizacion(
        pueblo: Int,
        calle: Int,
        delPoste: Int,
        direccion: String?,
        rawKey: String?
    ): Int {
        val composite = pueblo * 100_000 + calle * 1_000 + delPoste
        if (composite != 0) return composite
        val source = buildString {
            append(pueblo)
            append('_')
            append(calle)
            append('_')
            append(delPoste)
            append('_')
            append(rawKey.orEmpty())
            append('_')
            append(direccion.orEmpty())
        }
        return source.hashCode()
    }

    private fun DataSnapshot.stringChild(name: String): String? =
        child(name).value?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}

private val DIACRITIC_REGEX = Regex("\\p{Mn}+")
private val NON_ALNUM_REGEX = Regex("[^a-z0-9]+")

private const val TAG = "FirebaseSyncManager"
