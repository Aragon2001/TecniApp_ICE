package com.Arasoftsolutions.tecniapp_ice.Database.sync.vehicle

import com.Arasoftsolutions.tecniapp_ice.Database.sync.RtdbInstances
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val VEHICULOS_PATH = "vehiculos"

data class VehicleOdometerSnapshot(
    val vehiculoId: String,
    val kmActual: Double,
    val version: Long,
    val updatedBy: String? = null,
    val origen: String? = null,
)

data class KmEventRequest(
    val vehiculoId: String,
    val km: Double,
    val tipo: String,
    val refPath: String,
    val nota: String? = null,
    val allowDecrease: Boolean = false,
    val motivo: String? = null,
    val isAdmin: Boolean = false,
)

data class MaintenanceEventRequest(
    val vehiculoId: String,
    val mantenimientoId: String,
    val payload: Map<String, Any?>,
    val km: Double?,
)

data class EtmEventRequest(
    val vehiculoId: String,
    val etmId: String,
    val kmInicio: Double?,
    val kmFin: Double?,
    val refPath: String,
)

data class AveriaEventRequest(
    val vehiculoId: String,
    val averiaId: String,
    val km: Double?,
    val refPath: String,
)

class FirebaseVehicleDataSource(
    private val vehiculosRoot: DatabaseReference =
        RtdbInstances.datosGenerales().reference.child(VEHICULOS_PATH),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {

    fun observeVehicle(vehiculoId: String): Flow<VehicleOdometerSnapshot?> = callbackFlow {
        val ref = vehiculosRoot.child(vehiculoId).child("odometro")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val km = snapshot.child("km_actual").getValue(Double::class.java)
                    ?: snapshot.child("km_actual").getValue(Long::class.java)?.toDouble()
                    ?: 0.0
                val version = snapshot.child("version").getValue(Long::class.java) ?: 0L
                val updatedBy = snapshot.child("updatedBy").getValue(String::class.java)
                val origen = snapshot.child("origen").getValue(String::class.java)
                trySend(
                    VehicleOdometerSnapshot(
                        vehiculoId = vehiculoId,
                        kmActual = km,
                        version = version,
                        updatedBy = updatedBy,
                        origen = origen,
                    )
                )
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun registerKmEvent(request: KmEventRequest) {
        require(request.vehiculoId.isNotBlank()) { "vehiculoId requerido" }
        require(request.km >= 0) { "km debe ser >= 0" }

        val vehiculoRef = vehiculosRoot.child(request.vehiculoId)
        val eventoRef = vehiculoRef.child("historial").child("lecturasKm").push()
        val uid = auth.currentUser?.uid ?: "system"
        val eventId = eventoRef.key ?: throw IllegalStateException("No se pudo crear eventoId")

        val txResult = runKmTransaction(vehiculoRef, request, uid)

        val eventPayload = hashMapOf<String, Any?>(
            "eventoId" to eventId,
            "tipo" to request.tipo,
            "km" to request.km,
            "refPath" to request.refPath,
            "nota" to request.nota,
            "createdAt" to ServerValue.TIMESTAMP,
            "createdBy" to uid,
            "fuera_de_rango" to txResult.wasOutOfRange,
            "odometroVersion" to txResult.version,
            "km_actual_snapshot" to txResult.kmActual,
        )
        if (request.allowDecrease && request.isAdmin) {
            eventPayload["motivo"] = request.motivo
            eventPayload["decremento_admin"] = true
        }

        eventoRef.updateChildren(eventPayload).await()
    }

    suspend fun registerMaintenance(request: MaintenanceEventRequest) {
        val vehiculoRef = vehiculosRoot.child(request.vehiculoId)
        val mantenimientoRef = vehiculoRef.child("historial").child("mantenimientos").child(request.mantenimientoId)
        val payload = HashMap(request.payload)
        payload["updatedAt"] = ServerValue.TIMESTAMP
        mantenimientoRef.updateChildren(payload).await()

        request.km?.let {
            registerKmEvent(
                KmEventRequest(
                    vehiculoId = request.vehiculoId,
                    km = it,
                    tipo = "mantenimiento",
                    refPath = "$VEHICULOS_PATH/${request.vehiculoId}/historial/mantenimientos/${request.mantenimientoId}",
                )
            )
        }
    }

    suspend fun registerEtmEvent(request: EtmEventRequest) {
        val vehiculoRef = vehiculosRoot.child(request.vehiculoId)
        val eventoRef = vehiculoRef.child("historial").child("eventosETM").child(request.etmId)
        val payload = hashMapOf<String, Any?>(
            "etmId" to request.etmId,
            "refPath" to request.refPath,
            "km_inicio" to request.kmInicio,
            "km_fin" to request.kmFin,
            "updatedAt" to ServerValue.TIMESTAMP,
            "updatedBy" to (auth.currentUser?.uid ?: "system"),
        )
        eventoRef.updateChildren(payload).await()

        val maxKm = listOfNotNull(request.kmInicio, request.kmFin).maxOrNull()
        if (maxKm != null) {
            registerKmEvent(
                KmEventRequest(
                    vehiculoId = request.vehiculoId,
                    km = maxKm,
                    tipo = "etm",
                    refPath = request.refPath,
                )
            )
        }
    }

    suspend fun registerAveriaEvent(request: AveriaEventRequest) {
        val vehiculoRef = vehiculosRoot.child(request.vehiculoId)
        val eventoRef = vehiculoRef.child("historial").child("eventosAverias").child(request.averiaId)
        val payload = hashMapOf<String, Any?>(
            "averiaId" to request.averiaId,
            "refPath" to request.refPath,
            "km" to request.km,
            "updatedAt" to ServerValue.TIMESTAMP,
            "updatedBy" to (auth.currentUser?.uid ?: "system"),
        )
        eventoRef.updateChildren(payload).await()

        request.km?.let {
            registerKmEvent(
                KmEventRequest(
                    vehiculoId = request.vehiculoId,
                    km = it,
                    tipo = "averia",
                    refPath = request.refPath,
                )
            )
        }
    }

    private suspend fun runKmTransaction(
        vehiculoRef: DatabaseReference,
        request: KmEventRequest,
        uid: String,
    ): KmTransactionResult = suspendCancellableCoroutine { cont ->
        val odometroRef = vehiculoRef.child("odometro")
        odometroRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val currentKm = (currentData.child("km_actual").value as? Number)?.toDouble() ?: 0.0
                val currentVersion = (currentData.child("version").value as? Number)?.toLong() ?: 0L
                val canDecrease = request.allowDecrease && request.isAdmin && !request.motivo.isNullOrBlank()

                val isOutOfRange = request.km < currentKm && !canDecrease
                val nextKm = when {
                    canDecrease -> request.km
                    request.km > currentKm -> request.km
                    else -> currentKm
                }

                currentData.child("km_actual").value = nextKm
                currentData.child("version").value = currentVersion + 1L
                currentData.child("updatedAt").value = ServerValue.TIMESTAMP
                currentData.child("updatedBy").value = uid
                currentData.child("origen").value = request.tipo
                if (canDecrease) {
                    currentData.child("motivo_ajuste").value = request.motivo
                }
                currentData.child("fuera_de_rango_ultimo").value = isOutOfRange
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (error != null) {
                    if (cont.isActive) cont.resumeWithException(error.toException())
                    return
                }
                if (!committed || currentData == null) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException("Transacción no aplicada"))
                    return
                }

                val kmActual = currentData.child("km_actual").getValue(Double::class.java)
                    ?: currentData.child("km_actual").getValue(Long::class.java)?.toDouble()
                    ?: 0.0
                val version = currentData.child("version").getValue(Long::class.java) ?: 0L
                val outOfRange = currentData.child("fuera_de_rango_ultimo").getValue(Boolean::class.java) ?: false
                if (cont.isActive) {
                    cont.resume(KmTransactionResult(kmActual = kmActual, version = version, wasOutOfRange = outOfRange))
                }
            }
        })
    }
}

private data class KmTransactionResult(
    val kmActual: Double,
    val version: Long,
    val wasOutOfRange: Boolean,
)
