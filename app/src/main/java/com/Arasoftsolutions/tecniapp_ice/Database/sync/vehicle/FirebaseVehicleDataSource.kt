package com.Arasoftsolutions.tecniapp_ice.Database.sync.vehicle

import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculoLogEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculosEntity
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.tasks.await

private const val VEHICULOS_URL = "https://tecniapp-ice-datosgenerales.firebaseio.com/"
private const val VEHICULOS_PATH = "datosGenerales/vehiculos"

class FirebaseVehicleDataSource(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(VEHICULOS_URL),
) {
    private val vehiculosRoot = database.reference.child(VEHICULOS_PATH)

    suspend fun pushVehiculoBase(vehiculo: VehiculosEntity) {
        if (vehiculo.vehiculoId.isBlank()) return
        val payload = mapOf(
            "base" to mapOf(
                "placa" to vehiculo.placaRaw,
                "placaNumerica" to vehiculo.placa,
                "subregion" to vehiculo.subregion,
                "tipo" to vehiculo.tipo,
                "agencia" to vehiculo.agencia,
            ),
            "kmActual" to (vehiculo.kilometrajeActual ?: 0.0),
            "updatedAt" to ServerValue.TIMESTAMP,
        )
        vehiculosRoot.child(vehiculo.vehiculoId).updateChildren(payload).await()
    }

    suspend fun pushLogs(vehiculoId: String, logs: List<VehiculoLogEntity>) {
        if (vehiculoId.isBlank() || logs.isEmpty()) return
        val updates = HashMap<String, Any?>()
        logs.forEach { log ->
            updates["logs/${log.logId}"] = mapOf(
                "tipo" to log.tipo,
                "timestamp" to log.timestamp,
                "km" to log.km,
                "payloadJson" to log.payloadJson,
                "updatedAt" to ServerValue.TIMESTAMP,
            )
        }
        vehiculosRoot.child(vehiculoId).updateChildren(updates).await()
    }

    suspend fun pullVehiculoBase(vehiculoId: String): VehiculosEntity? {
        if (vehiculoId.isBlank()) return null
        val snap = vehiculosRoot.child(vehiculoId).get().await()
        if (!snap.exists()) return null
        val base = snap.child("base")
        return VehiculosEntity(
            vehiculoId = vehiculoId,
            placaRaw = base.child("placa").getValue(String::class.java).orEmpty(),
            placa = base.child("placaNumerica").getValue(Long::class.java) ?: 0L,
            subregion = base.child("subregion").getValue(String::class.java),
            tipo = base.child("tipo").getValue(String::class.java).orEmpty(),
            agencia = base.child("agencia").getValue(String::class.java).orEmpty(),
            kilometrajeActual = snap.child("kmActual").getValue(Double::class.java)
                ?: snap.child("kmActual").getValue(Long::class.java)?.toDouble(),
            updatedAt = snap.child("updatedAt").getValue(Long::class.java) ?: System.currentTimeMillis(),
        )
    }

    suspend fun pullKmActual(vehiculoId: String): Double? {
        if (vehiculoId.isBlank()) return null
        val snap = vehiculosRoot.child(vehiculoId).child("kmActual").get().await()
        return snap.getValue(Double::class.java) ?: snap.getValue(Long::class.java)?.toDouble()
    }
}
