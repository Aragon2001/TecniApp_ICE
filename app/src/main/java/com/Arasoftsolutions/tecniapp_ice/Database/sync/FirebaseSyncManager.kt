package com.Arasoftsolutions.tecniapp_ice.Database.sync

import android.content.Context
import android.util.Log
import com.Arasoftsolutions.tecniapp_ice.Database.entities.*
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import com.google.firebase.database.DataSnapshot
import java.text.Normalizer
import java.util.Locale

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
        database("https://tecniapp-ice-datosgenerales.firebaseio.com")
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
        database("https://tecniapp-ice-inventario.firebaseio.com/").child("inventario")
    }

    private val subregionNombreCache = mutableMapOf<String, String>()

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
        return snap.getValue(UserEntity::class.java)
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
        return first.getValue(UserEntity::class.java)
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
                val matchesSubregion = agency.subregion?.equals(filtroSubregion, ignoreCase = true) == true
                val regionOfSub = regionPorSubregion[filtroSubregion.lowercase(Locale.getDefault())]
                val matchesRegion = regionOfSub != null && agency.regionId?.equals(regionOfSub, ignoreCase = true) == true
                matchesSubregion || matchesRegion
            }
        }
    }

    suspend fun obtenerVehiculos(subregionId: String? = null): List<VehiculosEntity> {
        val nodeName = resolveDatosGeneralesNode("vehiculos", "Vehiculos")
        val snap = dbDatosGenerales.child(nodeName).get().await()
        val filtroSubregion = subregionId?.trim()?.takeIf { it.isNotEmpty() }
        return snap.children.mapNotNull { child ->
            val idValue = child.stringChild("id") ?: child.key
            val agencia = child.stringChild("agencia") ?: return@mapNotNull null
            val tipo = child.stringChild("tipo") ?: ""
            val subregion = child.stringChild("subregion")
                ?: child.stringChild("subregion_id")
                ?: child.stringChild("subregionId")
            val placaRaw = child.child("placa").value
            val placa = when (placaRaw) {
                is Long -> placaRaw
                is Int -> placaRaw.toLong()
                is Double -> placaRaw.toLong()
                is String -> placaRaw.trim().toLongOrNull()
                else -> null
            } ?: return@mapNotNull null
            val entityId = idValue?.toIntOrNull()
                ?: idValue?.hashCode()
                ?: "${agencia.trim()}_${placa}".hashCode()
            VehiculosEntity(
                id = entityId,
                agencia = agencia.trim(),
                placa = placa,
                tipo = tipo.trim(),
                subregion = subregion?.trim()
            )
        }.filter { vehiculo ->
            filtroSubregion == null || vehiculo.subregion?.equals(filtroSubregion, ignoreCase = true) == true
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
            parseLocalizacionNode(child)
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

    private fun parseLocalizacionNode(
        node: DataSnapshot
    ): List<LocalizacionesEntity> {
        if (!node.exists()) return emptyList()

        val calle = node.intValueAny("calle", "Calle", "CALLE")
        val pueblo = node.intValueAny("pueblo", "Pueblo", "PUEBLO")
        val direccion = node.stringValueAny("direccion", "Dirección", "Direccion", "DIRECCION", "DIRECCIÓN")
        val hasLeafData = calle != null || pueblo != null || !direccion.isNullOrBlank()

        if (!hasLeafData && node.childrenCount > 0) {
            return node.children.flatMap { child ->
                parseLocalizacionNode(child)
            }
        }

        val latitud = node.doubleValueAny("latitud", "Latitud", "LATITUD") ?: 0.0
        val longitud = node.doubleValueAny("longitud", "Longitud", "LONGITUD") ?: 0.0
        val delPoste = node.intValueAny("del poste", "del poste ", "Del poste", "DelPoste", "del_poste", "delposte") ?: 0
        val alPoste = node.intValueAny("al poste", "Al poste", "al_poste", "alposte") ?: 0

        val calleValue = calle ?: 0
        val puebloValue = pueblo ?: 0
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

    // --- MEDIDORES (Sync completa) ---
    suspend fun obtenerMedidores(subregionId: String, subregionNombre: String? = null): List<MedidorEntity> {
        val storageKey = subregionId.takeIf { it.isNotBlank() }?.trim()
            ?: subregionNombre?.takeIf { it.isNotBlank() }?.trim()
            ?: return emptyList()

        val lookupNombre = subregionNombre?.takeIf { it.isNotBlank() }
            ?: nombreSubregionDesdeCatalogo(subregionId)

        val referencia = obtenerReferenciaSubregion(storageKey, lookupNombre, createIfMissing = false)
            ?: return emptyList()

        val snapshot = referencia.get().await()
        if (!snapshot.exists()) return emptyList()

        val result = mutableListOf<MedidorEntity>()
        snapshot.children.forEach { child ->
            result += extraerMedidores(child, storageKey)
        }
        return result.distinctBy { it.medidorNumber }
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

    suspend fun guardarVehiculo(vehiculo: VehiculosEntity) {
        val root = dbDatosGenerales.child("vehiculos")
        val key = vehiculo.id.takeIf { it != 0 }?.toString()
            ?: vehiculo.placa.takeIf { it != 0L }?.toString()
            ?: throw IllegalArgumentException("Vehículo inválido, requiere id o placa")

        val payload = mapOf(
            "id" to vehiculo.id,
            "agencia" to vehiculo.agencia,
            "placa" to vehiculo.placa,
            "tipo" to vehiculo.tipo,
            "subregion" to vehiculo.subregion
        )

        root.child(key).updateChildren(payload).await()
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
        val snap = dbInventario.get().await()
        if (!snap.exists()) return emptyList()
        return snap.children.flatMap { vehiculoNode ->
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
        dbInventario.child(vehiculoKey).setValue(payload).await()
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
        dbInventario.child(vehiculoKey).child(codigo).setValue(payload).await()
    }

    suspend fun eliminarInventarioItem(vehiculoKey: String, codigoMaterial: String) {
        val codigo = codigoMaterial.trim()
        if (codigo.isEmpty()) return
        dbInventario.child(vehiculoKey).child(codigo).removeValue().await()
    }

    suspend fun eliminarInventarioVehiculo(vehiculoKey: String) {
        dbInventario.child(vehiculoKey).removeValue().await()
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
        val root = dbLuminarias.child(agenciaKey)
        val exists = runCatching { root.get().await().exists() }.getOrDefault(false)
        return if (exists) root else root
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

    private fun DataSnapshot.stringValueAny(vararg names: String): String? {
        val value = valueAny(*names) ?: return null
        return value.toString()
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
