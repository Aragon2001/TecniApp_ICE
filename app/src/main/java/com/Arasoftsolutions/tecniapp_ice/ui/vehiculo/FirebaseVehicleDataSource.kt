package com.Arasoftsolutions.tecniapp_ice.ui.vehiculo

import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculoEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculoLogEntity
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

private const val VEHICULOS_URL = "https://tecniapp-ice-datosgenerales.firebaseio.com/"
private const val VEHICULOS_PATH = "datosGenerales/vehiculos"

class FirebaseVehicleDataSource {

    private val vehiculosRef = FirebaseDatabase.getInstance(VEHICULOS_URL).reference.child(VEHICULOS_PATH)

    suspend fun pushVehiculoBase(vehiculo: VehiculoEntity) {
        val base = mapOf(
            "placaRaw" to vehiculo.placaRaw,
            "subregion" to vehiculo.subregion,
            "tipo" to vehiculo.tipo,
            "agencia" to vehiculo.agencia,
            "kmActual" to vehiculo.kmActual,
            "updatedAt" to vehiculo.updatedAt
        )
        vehiculosRef.child(vehiculo.vehiculoId).child("base").setValue(base).await()
        vehiculosRef.child(vehiculo.vehiculoId).child("kmActual").setValue(vehiculo.kmActual).await()
        vehiculosRef.child(vehiculo.vehiculoId).child("updatedAt").setValue(vehiculo.updatedAt).await()
    }

    suspend fun pushLogs(vehiculoId: String, logs: List<VehiculoLogEntity>) {
        if (logs.isEmpty()) return
        val updates = logs.associate { log ->
            "logs/${log.logId}" to mapOf(
                "logId" to log.logId,
                "vehiculoId" to log.vehiculoId,
                "tipo" to log.tipo,
                "timestamp" to log.timestamp,
                "km" to log.km,
                "payloadJson" to log.payloadJson,
                "syncState" to log.syncState,
                "updatedAt" to log.updatedAt
            )
        }
        vehiculosRef.child(vehiculoId).updateChildren(updates).await()
    }

    suspend fun pullVehiculoBase(vehiculoId: String): VehiculoEntity? {
        val snap = vehiculosRef.child(vehiculoId).child("base").get().await()
        if (!snap.exists()) return null
        val kmActual = snap.child("kmActual").getValue(Double::class.java) ?: 0.0
        return VehiculoEntity(
            vehiculoId = vehiculoId,
            placaRaw = snap.child("placaRaw").getValue(String::class.java).orEmpty(),
            subregion = snap.child("subregion").getValue(String::class.java),
            tipo = snap.child("tipo").getValue(String::class.java).orEmpty(),
            agencia = snap.child("agencia").getValue(String::class.java).orEmpty(),
            kmActual = kmActual,
            kilometrajeActual = kmActual,
            updatedAt = snap.child("updatedAt").getValue(Long::class.java) ?: System.currentTimeMillis()
        )
    }

    suspend fun pullKmActual(vehiculoId: String): Double? {
        return vehiculosRef.child(vehiculoId).child("kmActual").get().await().getValue(Double::class.java)
    }
}
