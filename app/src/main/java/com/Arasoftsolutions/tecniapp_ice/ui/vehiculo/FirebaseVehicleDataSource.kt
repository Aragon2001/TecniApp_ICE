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
            val payload = mapOf(
                "km" to log.km,
                "tecnico" to extractString(log.payloadJson, "registradoPor"),
                "observacion" to extractString(log.payloadJson, "observaciones"),
                "createdAt" to log.timestamp,
                "tipo" to extractString(log.payloadJson, "tipoMantenimiento"),
                "proximoKm" to extractDouble(log.payloadJson, "proximoMantenimiento"),
                "descripcion" to extractString(log.payloadJson, "observaciones")
            )
            when (log.tipo.uppercase()) {
                "DIARIO" -> etmRef.child(vehiculoId).child(fechaIso).updateChildren(
                    payload.filterKeys { it in setOf("km", "tecnico", "observacion", "createdAt") }
                        .toMutableMap()
                        .apply {
                            this["kmInicio"] = log.km ?: 0.0
                            this["kmFin"] = log.km ?: 0.0
                        }
                ).await()

                "MANTENIMIENTO" -> mantenimientoRef.child(vehiculoId).child(fechaIso).updateChildren(
                    payload.filterKeys { it in setOf("tipo", "km", "proximoKm", "descripcion", "createdAt") }
                ).await()
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
