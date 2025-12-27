package com.Arasoftsolutions.tecniapp_ice.fcm

import android.util.Log
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AveriaEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriaNotificationDispatcher
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriaNotificationPreferences
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriasRepository
import com.Arasoftsolutions.tecniapp_ice.ui.averias.shouldNotifyForAgency
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TecniAppMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repo by lazy { AveriasRepository(AppDatabase.getInstance(applicationContext)) }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Nuevo token FCM: $token")

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            Log.w(TAG, "Token recibido sin usuario autenticado; se omite el guardado")
            return
        }

        val ref = FirebaseDatabase.getInstance()
            .getReference("usuarios")
            .child(uid)
            .child("fcmToken")

        ref.setValue(token)
            .addOnFailureListener { error ->
                Log.e(TAG, "No se pudo guardar el token FCM", error)
            }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Mensaje FCM recibido: ${remoteMessage.data}")

        val data = remoteMessage.data
        val caseId = data["caseId"]
        if (caseId.isNullOrBlank()) {
            Log.w(TAG, "Mensaje FCM ignorado: caseId faltante")
            return
        }

        val averia = buildAveriaFromMessage(caseId, data)

        scope.launch {
            repo.upsertFromPush(averia)
            runCatching { repo.pullFromFirebaseOnce() } // Obtener detalles completos del caso cuando el servidor ya los tenga
                .onFailure { Log.w(TAG, "No se pudo refrescar Firebase tras push", it) }
        }

        if (!AveriaNotificationPreferences.areNotificationsEnabled(this)) {
            Log.d(TAG, "Notificaciones desactivadas por el usuario; se omite la alerta local")
            return
        }

        val agencyFilters = AveriaNotificationPreferences.normalizedAgencies(this)
        if (!shouldNotifyForAgency(averia, agencyFilters)) {
            Log.d(TAG, "Mensaje FCM filtrado por agencia (${averia.agencia}/${averia.agenciaTag})")
            return
        }

        AveriaNotificationDispatcher.notifyNewCases(this, listOf(averia))
    }

    private fun buildAveriaFromMessage(
        caseId: String,
        data: Map<String, String>
    ): AveriaEntity {
        val agencia = data["agencia"]
        val nombreAgencia = data["nombreAgencia"] ?: agencia
        val localizacion = data["localizacion"] ?: data["descripcion"] ?: nombreAgencia
        val fechaInicio = data["fechaInicioMillis"]?.toLongOrNull()
            ?: data["fechaInicio"]?.toLongOrNull()
        val lastUpdated = data["lastUpdated"]?.toLongOrNull() ?: System.currentTimeMillis()

        return AveriaEntity(
            caseId = caseId,
            estado = data["estado"] ?: "NUEVO",
            agencia = agencia,
            nombreAgencia = nombreAgencia,
            agenciaTag = data["agenciaTag"] ?: agencia ?: "",
            region = data["region"],
            localizacion = localizacion,
            observaciones = data["descripcion"],
            nise = data["nise"],
            causa = data["causa"],
            clientesAfectados = data["clientesAfectados"],
            lat = data["lat"]?.toDoubleOrNull(),
            lng = data["lng"]?.toDoubleOrNull(),
            fechaInicioMillis = fechaInicio ?: lastUpdated,
            tipoAfectacion = data["tipoAfectacion"],
            numeroMedidor = data["numeroMedidor"],
            medidorCalle = data["medidorCalle"],
            medidorPueblo = data["medidorPueblo"],
            medidorMetros = data["medidorMetros"],
            medidorPoste = data["medidorPoste"],
            cliente = data["cliente"],
            direccion = data["direccion"],
            lastUpdated = lastUpdated,
            isSynced = true
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val TAG = "TecniAppFCM"
    }
}
