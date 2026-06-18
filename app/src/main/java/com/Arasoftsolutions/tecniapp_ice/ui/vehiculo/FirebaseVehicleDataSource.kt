package com.Arasoftsolutions.tecniapp_ice.ui.vehiculo

import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculoEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculoLogEntity
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

private const val VEHICULOS_URL = "https://tecniapp-ice-datosgenerales.firebaseio.com/"
private const val VEHICULOS_PATH = "vehiculos"
private const val VEHICULO_ETM_PATH = "vehiculo_etm"
private const val VEHICULO_MANTENIMIENTO_PATH = "vehiculo_mantenimiento"

class FirebaseVehicleDataSource {

    private val vehiculosRef =
        FirebaseDatabase.getInstance(VEHICULOS_URL).reference.child(VEHICULOS_PATH)
    private val etmRef =
        FirebaseDatabase.getInstance(VEHICULOS_URL).reference.child(VEHICULO_ETM_PATH)
    private val mantenimientoRef =
        FirebaseDatabase.getInstance(VEHICULOS_URL).reference.child(VEHICULO_MANTENIMIENTO_PATH)

    // ─── PUSH BASE ────────────────────────────────────────────────────────────
    // Solo campos de identidad y operación del vehículo.
    // NO se incluyen mantenimientoUltimo ni mantenimientoProximo — esos datos
    // viven en vehiculo_mantenimiento y se calculan localmente desde Room.
    suspend fun pushVehiculoBase(vehiculo: VehiculoEntity) {
        val payload = mapOf(
            "placa"           to vehiculo.placaRaw,
            "subregion"       to vehiculo.subregion,
            "tipo"            to vehiculo.tipo,
            "agencia"         to vehiculo.agencia,
            "kmActual"        to vehiculo.kmActual,
            "registroCerrado" to vehiculo.registroCerrado,
            "updatedAt"       to vehiculo.updatedAt
        )
        vehiculosRef.child(vehiculo.vehiculoId).updateChildren(payload).await()
    }

    suspend fun updateVehiculoFields(vehiculoId: String, fields: Map<String, Any?>) {
        if (fields.isEmpty()) return
        val payload = fields.toMutableMap()
        payload["updatedAt"] = System.currentTimeMillis()
        vehiculosRef.child(vehiculoId).updateChildren(payload).await()
    }

    // Sube el km garantizando que nunca baje — el llamador ya debería pasar
    // maxOf(local, remoto), pero se hace la comprobación aquí también por seguridad.
    suspend fun updateKmActual(vehiculoId: String, nuevoKm: Double) {
        updateVehiculoFields(vehiculoId, mapOf("kmActual" to nuevoKm))
    }

    // ─── PUSH LOGS ────────────────────────────────────────────────────────────
    /**
     * Empuja los logs pendientes de Room a Firebase.
     *
     * Estructura ETM (DIARIO):
     *   vehiculo_etm/{vehiculoId}/{fecha}/apertura → {km, tecnico, combustible, …}
     *   vehiculo_etm/{vehiculoId}/{fecha}/cierre   → {kmFinal, actividad, lugar, …}
     *   → apertura y cierre son subnodos separados; nunca se sobreescriben entre sí.
     *
     * Estructura mantenimiento (MANTENIMIENTO):
     *   vehiculo_mantenimiento/{vehiculoId}/{fecha}/{Tipo} → {km, proximoKm, …}
     *   → el tipo es la clave (Aceite, Frenos, etc.).
     *   → mismo tipo mismo día = sobreescribe (solo importa el último del día).
     *   → distintos tipos mismo día = subnodos independientes.
     *   → vehiculos/{id} NO recibe resumen de mantenimiento.
     */
    suspend fun pushLogs(vehiculoId: String, logs: List<VehiculoLogEntity>) {
        if (logs.isEmpty()) return
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)

        logs.forEach { log ->
            val fechaIso       = fmt.format(java.util.Date(log.timestamp))
            val tecnico        = extractString(log.payloadJson, "registradoPor")
            val observacion    = extractString(log.payloadJson, "observaciones")
            val tipoMant       = extractString(log.payloadJson, "tipoMantenimiento")
            val proximoKm      = extractDouble(log.payloadJson, "proximoMantenimiento")
            val cerrado        = extractBoolean(log.payloadJson, "cerrado") ?: false
            val kmFinal        = extractDouble(log.payloadJson, "kmFinal")
            val actividad      = extractString(log.payloadJson, "actividad")
            val cuenta         = extractString(log.payloadJson, "cuenta")
            val numeroCaso     = extractString(log.payloadJson, "numeroCaso")
            val lugar          = extractString(log.payloadJson, "lugar")
            val horasLaboradas = extractInt(log.payloadJson, "horasLaboradas")
            val combustible    = extractString(log.payloadJson, "combustible")

            when (log.tipo.uppercase()) {

                // ── ETM: apertura y cierre como subnodos distintos ───────────
                "DIARIO" -> {
                    if (cerrado && kmFinal != null) {
                        val cierrePayload = mutableMapOf<String, Any>(
                            "creadoEn" to log.timestamp,
                            "kmFinal"  to kmFinal,
                            "cerrado"  to true
                        )
                        actividad?.let      { cierrePayload["actividad"]      = it }
                        cuenta?.let         { cierrePayload["cuenta"]         = it }
                        numeroCaso?.let     { cierrePayload["numeroCaso"]     = it }
                        lugar?.let          { cierrePayload["lugar"]          = it }
                        horasLaboradas?.let { cierrePayload["horasLaboradas"] = it }
                        observacion?.let    { cierrePayload["observacion"]    = it }
                        tecnico?.let        { cierrePayload["tecnico"]        = it }
                        etmRef.child(vehiculoId).child(fechaIso).child("cierre")
                            .updateChildren(cierrePayload).await()

                        // Actualizar registroCerrado en nodo padre para consultas rápidas
                        vehiculosRef.child(vehiculoId).updateChildren(
                            mapOf("registroCerrado" to true, "updatedAt" to log.timestamp)
                        ).await()

                    } else {
                        val aperturaPayload = mutableMapOf<String, Any>(
                            "creadoEn"  to log.timestamp,
                            "kmInicio"  to (log.km ?: 0.0),
                            "cerrado"   to false
                        )
                        tecnico?.let     { aperturaPayload["tecnico"]     = it }
                        observacion?.let { aperturaPayload["observacion"] = it }
                        combustible?.let { aperturaPayload["combustible"] = it }
                        log.km?.let      { aperturaPayload["km"]          = it }
                        etmRef.child(vehiculoId).child(fechaIso).child("apertura")
                            .updateChildren(aperturaPayload).await()
                    }
                }

                // ── Mantenimiento: clave por tipo dentro de la fecha ─────────
                "MANTENIMIENTO" -> {
                    // Sanitizar el tipo para que sea clave válida en Firebase
                    val tipoKey = (tipoMant ?: "General")
                        .trim()
                        .replace("/", "-")
                        .replace(".", "-")
                        .replace("#", "")
                        .replace("$", "")
                        .replace("[", "")
                        .replace("]", "")

                    val mantPayload = mutableMapOf<String, Any>(
                        "creadoEn" to log.timestamp,
                        "km"       to (log.km ?: 0.0)
                    )
                    proximoKm?.let   { mantPayload["proximoKm"]    = it }
                    observacion?.let { mantPayload["observaciones"] = it }

                    // vehiculo_mantenimiento/{id}/{fecha}/{Tipo}
                    mantenimientoRef
                        .child(vehiculoId)
                        .child(fechaIso)
                        .child(tipoKey)
                        .updateChildren(mantPayload).await()

                    // Solo actualizamos updatedAt en vehiculos/ para que otros
                    // dispositivos sepan que hay cambios y sincronicen
                    vehiculosRef.child(vehiculoId)
                        .updateChildren(mapOf("updatedAt" to log.timestamp)).await()
                }
            }
        }
    }

    // ─── PULL BASE ────────────────────────────────────────────────────────────
    /**
     * Lee el nodo vehiculos/{vehiculoId} y el historial ETM de vehiculo_etm.
     * No lee mantenimientoUltimo ni mantenimientoProximo — esos campos
     * ya no existen en Firebase; el próximo mantenimiento se calcula desde Room.
     */
    suspend fun pullVehiculoBase(vehiculoId: String): VehiculoEntity? {
        val snap = vehiculosRef.child(vehiculoId).get().await()
        if (!snap.exists()) return null

        val kmActual = snap.child("kmActual").getValue(Double::class.java)
            ?: snap.child("kilometrajeActual").getValue(Double::class.java)
            ?: 0.0

        val registrosDiariosJson = pullRegistrosDiariosJson(vehiculoId)

        return VehiculoEntity(
            vehiculoId           = vehiculoId,
            placaRaw             = snap.child("placa").getValue(String::class.java)
                ?: snap.child("placaRaw").getValue(String::class.java).orEmpty(),
            subregion            = snap.child("subregion").getValue(String::class.java),
            tipo                 = snap.child("tipo").getValue(String::class.java).orEmpty(),
            agencia              = snap.child("agencia").getValue(String::class.java).orEmpty(),
            kmActual             = kmActual,
            registroCerrado      = snap.child("registroCerrado").getValue(Boolean::class.java) ?: false,
            updatedAt            = snap.child("updatedAt").getValue(Long::class.java)
                ?: System.currentTimeMillis(),
            // mantenimientoUltimo y mantenimientoProximo no se leen aquí:
            // se reconstruyen desde vehiculo_log en Room vía pullMantenimientosLogs()
            registrosDiariosJson = registrosDiariosJson
        )
    }

    /**
     * Lee vehiculo_etm/{vehiculoId} y reconstruye el JSON de registros diarios
     * en el formato de RegistroDiarioVehiculo.
     *
     * Soporta:
     *   - Formato nuevo: {fecha}/apertura + {fecha}/cierre como subnodos
     *   - Formato legacy plano: {fecha}/{km, kmInicio, …} directamente
     */
    suspend fun pullRegistrosDiariosJson(vehiculoId: String): String? {
        val etmSnap = etmRef.child(vehiculoId).get().await()
        if (!etmSnap.exists()) return null

        val registros = mutableListOf<org.json.JSONObject>()

        for (fechaSnap in etmSnap.children) {
            val fecha = fechaSnap.key ?: continue

            val aperturaSnap = fechaSnap.child("apertura")
            val cierreSnap   = fechaSnap.child("cierre")
            val esFlatLegacy = !aperturaSnap.exists() && !cierreSnap.exists()

            val kmInicio: Double
            val kmFinal: Double?
            val cerrado: Boolean
            val creadoEn: Long
            val tecnico: String?
            val observacion: String?
            val combustible: String?
            val actividad: String?
            val cuenta: String?
            val numeroCaso: String?
            val lugar: String?
            val horasLaboradas: Int?

            if (esFlatLegacy) {
                kmInicio       = fechaSnap.child("kmInicio").getValue(Double::class.java) ?: 0.0
                kmFinal        = fechaSnap.child("kmFin").getValue(Double::class.java)
                cerrado        = fechaSnap.child("cerrado").getValue(Boolean::class.java) ?: false
                creadoEn       = fechaSnap.child("createdAt").getValue(Long::class.java) ?: 0L
                tecnico        = fechaSnap.child("tecnico").getValue(String::class.java)
                observacion    = fechaSnap.child("observacion").getValue(String::class.java)
                combustible    = null
                actividad      = null
                cuenta         = null
                numeroCaso     = null
                lugar          = null
                horasLaboradas = null
            } else {
                kmInicio       = aperturaSnap.child("kmInicio").getValue(Double::class.java)
                    ?: aperturaSnap.child("km").getValue(Double::class.java) ?: 0.0
                kmFinal        = cierreSnap.child("kmFinal").getValue(Double::class.java)
                cerrado        = cierreSnap.child("cerrado").getValue(Boolean::class.java) ?: false
                creadoEn       = aperturaSnap.child("creadoEn").getValue(Long::class.java) ?: 0L
                tecnico        = aperturaSnap.child("tecnico").getValue(String::class.java)
                observacion    = cierreSnap.child("observacion").getValue(String::class.java)
                    ?: aperturaSnap.child("observacion").getValue(String::class.java)
                combustible    = aperturaSnap.child("combustible").getValue(String::class.java)
                actividad      = cierreSnap.child("actividad").getValue(String::class.java)
                cuenta         = cierreSnap.child("cuenta").getValue(String::class.java)
                numeroCaso     = cierreSnap.child("numeroCaso").getValue(String::class.java)
                lugar          = cierreSnap.child("lugar").getValue(String::class.java)
                horasLaboradas = cierreSnap.child("horasLaboradas").getValue(Int::class.java)
            }

            val obj = org.json.JSONObject()
            obj.put("fecha",        fecha)
            obj.put("valorInicial", kmInicio)
            obj.put("cerrado",      cerrado)
            obj.put("registradoEn", creadoEn)
            kmFinal?.let        { obj.put("valorFinal",      it) }
            tecnico?.let        { obj.put("registradoPor",   it) }
            observacion?.let    { obj.put("observaciones",   it) }
            combustible?.let    { obj.put("combustible",     it) }
            actividad?.let      { obj.put("actividad",       it) }
            cuenta?.let         { obj.put("cuenta",          it) }
            numeroCaso?.let     { obj.put("numeroCaso",      it) }
            lugar?.let          { obj.put("lugar",           it) }
            horasLaboradas?.let { obj.put("horasLaboradas",  it) }

            registros.add(obj)
        }

        if (registros.isEmpty()) return null
        registros.sortByDescending { it.optString("fecha") }
        val array = org.json.JSONArray()
        registros.forEach { array.put(it) }
        return array.toString()
    }

    /**
     * Lee vehiculo_mantenimiento/{vehiculoId} con la nueva estructura
     * {fecha}/{Tipo} → payload y devuelve una lista de VehiculoLogEntity
     * lista para upsert en Room.
     *
     * El logId termina en "_pull" para diferenciarse de los logs registrados
     * localmente (que terminan en "_UUID"). No hay colisión posible.
     */
    suspend fun pullMantenimientosLogs(vehiculoId: String): List<VehiculoLogEntity> {
        val snap = mantenimientoRef.child(vehiculoId).get().await()
        if (!snap.exists()) return emptyList()

        val logs = mutableListOf<VehiculoLogEntity>()

        for (fechaSnap in snap.children) {
            val fecha = fechaSnap.key ?: continue

            // Detectar si es estructura antigua (campos planos directamente bajo la fecha)
            // o nueva (subnodos por tipo)
            val esLegacy = fechaSnap.child("km").exists()

            if (esLegacy) {
                // Formato antiguo: vehiculo_mantenimiento/{id}/{fecha} → {km, tipo, …}
                val km      = fechaSnap.child("km").getValue(Double::class.java) ?: 0.0
                val tipo    = fechaSnap.child("tipo").getValue(String::class.java) ?: "General"
                val proximo = fechaSnap.child("proximoKm").getValue(Double::class.java)
                val desc    = fechaSnap.child("descripcion").getValue(String::class.java)
                val ts      = fechaSnap.child("creadoEn").getValue(Long::class.java)
                    ?: fechaSnap.child("createdAt").getValue(Long::class.java)
                    ?: System.currentTimeMillis()
                logs.add(buildMantenimientoLog(vehiculoId, tipo, km, proximo, desc, ts))

            } else {
                // Formato nuevo: vehiculo_mantenimiento/{id}/{fecha}/{Tipo} → {km, …}
                for (tipoSnap in fechaSnap.children) {
                    val tipo    = tipoSnap.key ?: continue
                    val km      = tipoSnap.child("km").getValue(Double::class.java) ?: 0.0
                    val proximo = tipoSnap.child("proximoKm").getValue(Double::class.java)
                    val desc    = tipoSnap.child("observaciones").getValue(String::class.java)
                    val ts      = tipoSnap.child("creadoEn").getValue(Long::class.java)
                        ?: System.currentTimeMillis()
                    logs.add(buildMantenimientoLog(vehiculoId, tipo, km, proximo, desc, ts))
                }
            }
        }

        // Ordenar ASC para que findLastByTipo() devuelva siempre el más reciente
        return logs.sortedBy { it.timestamp }
    }

    // ─── PULL KM ──────────────────────────────────────────────────────────────
    /**
     * Devuelve el km más alto entre Firebase y el mínimo local conocido.
     * Garantiza que el km nunca baje al reconciliar entre dispositivos.
     */
    suspend fun pullKmActual(vehiculoId: String, localMin: Double = 0.0): Double? {
        val snap = vehiculosRef.child(vehiculoId).get().await()
        val remoto = snap.child("kmActual").getValue(Double::class.java)
            ?: snap.child("kilometrajeActual").getValue(Double::class.java)
            ?: return null
        return maxOf(remoto, localMin)
    }

    // ─── HELPERS PRIVADOS ────────────────────────────────────────────────────
    private fun buildMantenimientoLog(
        vehiculoId: String,
        tipo: String,
        km: Double,
        proximoKm: Double?,
        observaciones: String?,
        timestamp: Long
    ): VehiculoLogEntity {
        val payload = org.json.JSONObject()
            .put("tipoMantenimiento",    tipo)
            .put("unidad",               "km")
            .put("observaciones",        observaciones)
            .put("proximoMantenimiento", proximoKm ?: 0.0)
            .toString()

        return VehiculoLogEntity(
            logId       = "mant_${vehiculoId}_${timestamp}_${tipo.take(6)}_pull",
            vehiculoId  = vehiculoId,
            tipo        = "MANTENIMIENTO",
            timestamp   = timestamp,
            km          = km,
            payloadJson = payload,
            syncState   = "SYNCED"
        )
    }

    private fun extractString(json: String, key: String): String? = runCatching {
        org.json.JSONObject(json).optString(key).takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun extractDouble(json: String, key: String): Double? = runCatching {
        org.json.JSONObject(json).optDouble(key).takeIf { !it.isNaN() }
    }.getOrNull()

    private fun extractBoolean(json: String, key: String): Boolean? = runCatching {
        if (org.json.JSONObject(json).has(key))
            org.json.JSONObject(json).getBoolean(key)
        else null
    }.getOrNull()

    private fun extractInt(json: String, key: String): Int? = runCatching {
        val v = org.json.JSONObject(json).optInt(key, -1)
        if (v > 0) v else null
    }.getOrNull()
}