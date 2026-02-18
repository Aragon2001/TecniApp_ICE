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

    private val vehiculosRef = FirebaseDatabase.getInstance(VEHICULOS_URL).reference.child(VEHICULOS_PATH)
    private val etmRef = FirebaseDatabase.getInstance(VEHICULOS_URL).reference.child(VEHICULO_ETM_PATH)
    private val mantenimientoRef = FirebaseDatabase.getInstance(VEHICULOS_URL).reference.child(VEHICULO_MANTENIMIENTO_PATH)

    suspend fun pushVehiculoBase(vehiculo: VehiculoEntity) {
        val payload = mapOf(
            "placa" to vehiculo.placaRaw,
            "subregion" to vehiculo.subregion,
            "tipo" to vehiculo.tipo,
            "agencia" to vehiculo.agencia,
            "kmActual" to vehiculo.kmActual,
            "registroCerrado" to vehiculo.registroCerrado,
            "updatedAt" to vehiculo.updatedAt
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
        updateVehiculoFields(
            vehiculoId = vehiculoId,
            fields = mapOf("kmActual" to nuevoKm)
        )
    }

    suspend fun pushLogs(vehiculoId: String, logs: List<VehiculoLogEntity>) {
        if (logs.isEmpty()) return
        logs.forEach { log ->
            val fechaIso = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(log.timestamp))
            val tecnico = extractString(log.payloadJson, "registradoPor")
            val observacion = extractString(log.payloadJson, "observaciones")
            val tipoMantenimiento = extractString(log.payloadJson, "tipoMantenimiento")
            val proximoKm = extractDouble(log.payloadJson, "proximoMantenimiento")
            when (log.tipo.uppercase()) {
                "DIARIO" -> {
                    val dailyPayload = mutableMapOf<String, Any>(
                        "createdAt" to log.timestamp,
                        "kmInicio" to (log.km ?: 0.0),
                        "kmFin" to (log.km ?: 0.0)
                    )
                    tecnico?.let { dailyPayload["tecnico"] = it }
                    observacion?.let { dailyPayload["observacion"] = it }
                    log.km?.let { dailyPayload["km"] = it }
                    etmRef.child(vehiculoId).child(fechaIso).updateChildren(dailyPayload).await()
                }

                "MANTENIMIENTO" -> {
                    val mantenimientoPayload = mutableMapOf<String, Any>(
                        "createdAt" to log.timestamp,
                        "km" to (log.km ?: 0.0)
                    )
                    tipoMantenimiento?.let { mantenimientoPayload["tipo"] = it }
                    proximoKm?.let { mantenimientoPayload["proximoKm"] = it }
                    observacion?.let { mantenimientoPayload["descripcion"] = it }
                    mantenimientoRef.child(vehiculoId).child(fechaIso).updateChildren(mantenimientoPayload).await()

                    val resumenPayload = mutableMapOf<String, Any>(
                        "updatedAt" to log.timestamp,
                        "mantenimientoUltimo" to ((tipoMantenimiento ?: "Mantenimiento") + " • " + String.format(java.util.Locale.US, "%.0f", (log.km ?: 0.0))),
                        "tipoMantenimiento" to (tipoMantenimiento ?: "General")
                    )
                    proximoKm?.let { resumenPayload["mantenimientoProximo"] = String.format(java.util.Locale.US, "%.0f", it) }
                    vehiculosRef.child(vehiculoId).updateChildren(resumenPayload).await()
                }
            }
        }
    }

    suspend fun pullVehiculoBase(vehiculoId: String): VehiculoEntity? {
        val snap = vehiculosRef.child(vehiculoId).get().await()
        if (!snap.exists()) return null
        val kmActual = snap.child("kmActual").getValue(Double::class.java)
            ?: snap.child("kilometrajeActual").getValue(Double::class.java)
            ?: 0.0
        return VehiculoEntity(
            vehiculoId = vehiculoId,
            placaRaw = snap.child("placa").getValue(String::class.java)
                ?: snap.child("placaRaw").getValue(String::class.java).orEmpty(),
            subregion = snap.child("subregion").getValue(String::class.java),
            tipo = snap.child("tipo").getValue(String::class.java).orEmpty(),
            agencia = snap.child("agencia").getValue(String::class.java).orEmpty(),
            kmActual = kmActual,
            registroCerrado = snap.child("registroCerrado").getValue(Boolean::class.java) ?: false,
            updatedAt = snap.child("updatedAt").getValue(Long::class.java) ?: System.currentTimeMillis()
        )
    }

    suspend fun pullKmActual(vehiculoId: String): Double? {
        val snap = vehiculosRef.child(vehiculoId).get().await()
        return snap.child("kmActual").getValue(Double::class.java)
            ?: snap.child("kilometrajeActual").getValue(Double::class.java)
    }

    private fun extractString(json: String, key: String): String? = runCatching {
        org.json.JSONObject(json).optString(key).takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun extractDouble(json: String, key: String): Double? = runCatching {
        org.json.JSONObject(json).optDouble(key).takeIf { !it.isNaN() }
    }.getOrNull()
}
