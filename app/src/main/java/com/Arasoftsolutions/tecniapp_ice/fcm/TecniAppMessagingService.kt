package com.Arasoftsolutions.tecniapp_ice.fcm

import android.util.Log
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AveriaEntity
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriaNotificationDispatcher
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriaNotificationPreferences
import com.Arasoftsolutions.tecniapp_ice.ui.averias.shouldNotifyForAgency
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class TecniAppMessagingService : FirebaseMessagingService() {

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

        if (!AveriaNotificationPreferences.areNotificationsEnabled(this)) {
            Log.d(TAG, "Notificaciones desactivadas por el usuario; se descarta el push")
            return
        }

        val averia = buildAveriaFromMessage(caseId, data)
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
            lng = data["lng"]?.toDoubleOrNull()
        )
    }

    companion object {
        private const val TAG = "TecniAppFCM"
    }
}
