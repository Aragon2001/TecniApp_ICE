package com.Arasoftsolutions.tecniapp_ice.ui.vehiculo

import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculoEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculoLogEntity
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

private const val VEHICULOS_URL = "https://tecniapp-ice-datosgenerales.firebaseio.com/"
private const val VEHICULOS_PATH = "vehiculos"
private const val VEHICULO_ETM_PATH = "vehiculo_etm"
private const val VEHICULO_MANTENIMIENTO_PATH = "vehiculo_mantenimiento"

// ─────────────────────────────────────────────────────────────────────────────
// CORRECCIONES aplicadas en este archivo:
//
// FIX-1  pushLogs / DIARIO: la clave Firebase ahora es por evento (apertura /
//        cierre) en lugar de updateChildren() sobre un nodo único de fecha.
//        Antes: etmRef/{id}/{fecha} se sobreescribía en cada registro.
//        Ahora:  etmRef/{id}/{fecha}/apertura y etmRef/{id}/{fecha}/cierre son
//        nodos independientes → no se pierde ningún registro del día.
//
// FIX-2  pullVehiculoBase ahora también lee el nodo vehiculo_etm/{id} y
//        reconstruye registrosDiariosJson, de manera que al sincronizar desde
//        otro teléfono se recupera el historial ETM completo.
//
// FIX-3  pullKmActual: si Firebase devuelve un km menor al parámetro
//        localMin, se devuelve localMin (km nunca puede bajar).
// ─────────────────────────────────────────────────────────────────────────────

class FirebaseVehicleDataSource {

    private val vehiculosRef =
        FirebaseDatabase.getInstance(VEHICULOS_URL).reference.child(VEHICULOS_PATH)
    private val etmRef =
        FirebaseDatabase.getInstance(VEHICULOS_URL).reference.child(VEHICULO_ETM_PATH)
    private val mantenimientoRef =
        FirebaseDatabase.getInstance(VEHICULOS_URL).reference.child(VEHICULO_MANTENIMIENTO_PATH)

    // ─── PUSH BASE ───────────────────────────────────────────────────────────
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

    suspend fun updateKmActual(vehiculoId: String, nuevoKm: Double) {
        updateVehiculoFields(vehiculoId, mapOf("kmActual" to nuevoKm))
    }

    // ─── PUSH LOGS ───────────────────────────────────────────────────────────
    /**
     * FIX-1: cada registro DIARIO ahora se guarda en un subnodo "apertura" o
     * "cierre" dentro de la fecha, en lugar de sobreescribir el nodo padre.
     *
     * Estructura resultante en Firebase:
     *   vehiculo_etm/{vehiculoId}/{fechaIso}/apertura  → {km, tecnico, …}
     *   vehiculo_etm/{vehiculoId}/{fechaIso}/cierre    → {kmFinal, actividad, …}
     *
     * Así un registro de cierre no destruye la apertura.
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
                "DIARIO" -> {
                    // FIX-1: separar apertura y cierre como subnodos distintos
                    if (cerrado && kmFinal != null) {
                        // Nodo cierre
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

                        // Actualizar resumen en nodo padre para listas rápidas
                        val resumenCierre = mutableMapOf<String, Any>(
                            "updatedAt"       to log.timestamp,
                            "registroCerrado" to true
                        )
                        log.km?.let { resumenCierre["kmActual"] = it }
                        vehiculosRef.child(vehiculoId).updateChildren(resumenCierre).await()

                    } else {
                        // Nodo apertura
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

                "MANTENIMIENTO" -> {
                    val mantenimientoPayload = mutableMapOf<String, Any>(
                        "creadoEn" to log.timestamp,
                        "km"       to (log.km ?: 0.0)
                    )
                    tipoMant?.let   { mantenimientoPayload["tipo"]        = it }
                    proximoKm?.let  { mantenimientoPayload["proximoKm"]   = it }
                    observacion?.let { mantenimientoPayload["descripcion"] = it }
                    mantenimientoRef.child(vehiculoId).child(fechaIso)
                        .updateChildren(mantenimientoPayload).await()

                    val resumenPayload = mutableMapOf<String, Any>(
                        "updatedAt"            to log.timestamp,
                        "mantenimientoUltimo"  to ((tipoMant ?: "Mantenimiento") + " • " +
                                String.format(java.util.Locale.US, "%.0f", (log.km ?: 0.0))),
                        "tipoMantenimiento"    to (tipoMant ?: "General")
                    )
                    proximoKm?.let {
                        resumenPayload["mantenimientoProximo"] =
                            String.format(java.util.Locale.US, "%.0f", it)
                    }
                    vehiculosRef.child(vehiculoId).updateChildren(resumenPayload).await()
                }
            }
        }
    }

    // ─── PULL BASE ───────────────────────────────────────────────────────────
    /**
     * FIX-2: además de los campos base, ahora lee vehiculo_etm/{vehiculoId}
     * y reconstruye el campo registrosDiariosJson para que al sincronizar
     * desde otro teléfono se recupere el historial ETM completo.
     */
    suspend fun pullVehiculoBase(vehiculoId: String): VehiculoEntity? {
        val snap = vehiculosRef.child(vehiculoId).get().await()
        if (!snap.exists()) return null

        val kmActual = snap.child("kmActual").getValue(Double::class.java)
            ?: snap.child("kilometrajeActual").getValue(Double::class.java)
            ?: 0.0

        // Leer historial ETM del nodo vehiculo_etm
        val registrosDiariosJson = pullRegistrosDiariosJson(vehiculoId)

        return VehiculoEntity(
            vehiculoId        = vehiculoId,
            placaRaw          = snap.child("placa").getValue(String::class.java)
                ?: snap.child("placaRaw").getValue(String::class.java).orEmpty(),
            subregion         = snap.child("subregion").getValue(String::class.java),
            tipo              = snap.child("tipo").getValue(String::class.java).orEmpty(),
            agencia           = snap.child("agencia").getValue(String::class.java).orEmpty(),
            kmActual          = kmActual,
            registroCerrado   = snap.child("registroCerrado").getValue(Boolean::class.java) ?: false,
            updatedAt         = snap.child("updatedAt").getValue(Long::class.java)
                ?: System.currentTimeMillis(),
            mantenimientoUltimo  = snap.child("mantenimientoUltimo").getValue(String::class.java),
            mantenimientoProximo = snap.child("mantenimientoProximo").getValue(String::class.java),
            registrosDiariosJson = registrosDiariosJson
            // los logs de mantenimiento se insertan por separado vía pullMantenimientosLogs()
        )
    }

    /**
     * FIX-2 (auxiliar): lee vehiculo_etm/{vehiculoId} y construye la lista
     * de RegistroDiarioVehiculo serializada como JSON, en el mismo formato
     * que usa serializeRegistrosDiarios().
     */
    suspend fun pullRegistrosDiariosJson(vehiculoId: String): String? {
        val etmSnap = etmRef.child(vehiculoId).get().await()
        if (!etmSnap.exists()) return null

        val registros = mutableListOf<org.json.JSONObject>()

        for (fechaSnap in etmSnap.children) {
            val fecha = fechaSnap.key ?: continue

            // Leer apertura
            val aperturaSnap = fechaSnap.child("apertura")
            val cierreSnap   = fechaSnap.child("cierre")

            // Compatibilidad hacia atrás: si no tiene subnodos, el nodo fecha
            // es directamente el payload (formato antiguo flat)
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
                // Formato antiguo: los campos están directamente bajo la fecha
                kmInicio      = fechaSnap.child("kmInicio").getValue(Double::class.java) ?: 0.0
                kmFinal       = fechaSnap.child("kmFin").getValue(Double::class.java)
                cerrado       = fechaSnap.child("cerrado").getValue(Boolean::class.java) ?: false
                creadoEn      = fechaSnap.child("createdAt").getValue(Long::class.java) ?: 0L
                tecnico       = fechaSnap.child("tecnico").getValue(String::class.java)
                observacion   = fechaSnap.child("observacion").getValue(String::class.java)
                combustible   = null
                actividad     = null
                cuenta        = null
                numeroCaso    = null
                lugar         = null
                horasLaboradas = null
            } else {
                // Formato nuevo: apertura + cierre como subnodos
                kmInicio      = aperturaSnap.child("kmInicio").getValue(Double::class.java) ?: 0.0
                kmFinal       = cierreSnap.child("kmFinal").getValue(Double::class.java)
                cerrado       = cierreSnap.child("cerrado").getValue(Boolean::class.java) ?: false
                creadoEn      = aperturaSnap.child("creadoEn").getValue(Long::class.java) ?: 0L
                tecnico       = aperturaSnap.child("tecnico").getValue(String::class.java)
                observacion   = (cierreSnap.child("observacion").getValue(String::class.java)
                    ?: aperturaSnap.child("observacion").getValue(String::class.java))
                combustible   = aperturaSnap.child("combustible").getValue(String::class.java)
                actividad     = cierreSnap.child("actividad").getValue(String::class.java)
                cuenta        = cierreSnap.child("cuenta").getValue(String::class.java)
                numeroCaso    = cierreSnap.child("numeroCaso").getValue(String::class.java)
                lugar         = cierreSnap.child("lugar").getValue(String::class.java)
                horasLaboradas = cierreSnap.child("horasLaboradas").getValue(Int::class.java)
            }

            val obj = org.json.JSONObject()
            obj.put("fecha",          fecha)
            obj.put("valorInicial",   kmInicio)
            obj.put("cerrado",        cerrado)
            obj.put("registradoEn",   creadoEn)
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

        // Ordenar por fecha descendente (más reciente primero)
        registros.sortByDescending { it.optString("fecha") }

        val array = org.json.JSONArray()
        registros.forEach { array.put(it) }
        return array.toString()
    }

    // ─── PULL KM ─────────────────────────────────────────────────────────────
    /**
     * FIX-3: si Firebase devuelve un km menor al mínimo local conocido,
     * se respeta el valor local (el km nunca puede bajar).
     *
     * @param localMin  km mínimo conocido localmente; si Firebase devuelve
     *                  algo menor, este método retorna localMin.
     */
    suspend fun pullKmActual(vehiculoId: String, localMin: Double = 0.0): Double? {
        val snap = vehiculosRef.child(vehiculoId).get().await()
        val remoto = snap.child("kmActual").getValue(Double::class.java)
            ?: snap.child("kilometrajeActual").getValue(Double::class.java)
            ?: return null
        // El km nunca puede bajar: devolvemos el mayor entre remoto y local
        return maxOf(remoto, localMin)
    }

    /**
     * Lee vehiculo_mantenimiento/{vehiculoId} y devuelve una lista de
     * VehiculoLogEntity con tipo MANTENIMIENTO, lista para insertar en Room.
     * Compatibilidad: soporta tanto el formato antiguo (campos planos)
     * como el nuevo (creadoEn, km, tipo, proximoKm, descripcion).
     */
    suspend fun pullMantenimientosLogs(vehiculoId: String): List<VehiculoLogEntity> {
        val snap = mantenimientoRef.child(vehiculoId).get().await()
        if (!snap.exists()) return emptyList()

        val logs = mutableListOf<VehiculoLogEntity>()
        for (fechaSnap in snap.children) {
            val fecha   = fechaSnap.key ?: continue
            val km      = fechaSnap.child("km").getValue(Double::class.java) ?: 0.0
            val tipo    = fechaSnap.child("tipo").getValue(String::class.java) ?: "General"
            val proximo = fechaSnap.child("proximoKm").getValue(Double::class.java)
            val desc    = fechaSnap.child("descripcion").getValue(String::class.java)
            val ts      = fechaSnap.child("creadoEn").getValue(Long::class.java)
                ?: fechaSnap.child("createdAt").getValue(Long::class.java)
                ?: System.currentTimeMillis()

            val payload = org.json.JSONObject()
                .put("tipoMantenimiento",    tipo)
                .put("unidad",               "km")
                .put("observaciones",        desc)
                .put("proximoMantenimiento", proximo ?: 0.0)
                .toString()

            logs.add(
                VehiculoLogEntity(
                    logId       = "mant_${vehiculoId}_${ts}_pull",
                    vehiculoId  = vehiculoId,
                    tipo        = "MANTENIMIENTO",
                    timestamp   = ts,
                    km          = km,
                    payloadJson = payload,
                    syncState   = "SYNCED"
                )
            )
        }
        return logs
    }

    // ─── HELPERS JSON ────────────────────────────────────────────────────────
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