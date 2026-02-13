package com.Arasoftsolutions.tecniapp_ice.fcm

import android.content.Context
import android.util.Log
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AveriaEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriaNotificationDispatcher
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriaNotificationPreferences
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriasRepository
import com.Arasoftsolutions.tecniapp_ice.ui.averias.shouldNotifyForAgency
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
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
            cacheToken(this, token)
            Log.w(TAG, "Token recibido sin usuario autenticado; se cachea para subir luego")
            return
        }

        saveTokenToRtdb(uid, token)
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
        }

        if (!AveriaNotificationPreferences.areNotificationsEnabled(this)) {
            Log.d(TAG, "Notificaciones desactivadas por el usuario; se omite alerta local")
            return
        }

        val agencyFilters = AveriaNotificationPreferences.normalizedAgencies(this)
        if (!shouldNotifyForAgency(averia, agencyFilters)) {
            Log.d(TAG, "Mensaje FCM filtrado por agencia (${averia.agencia}/${averia.agenciaTag})")
            return
        }

        AveriaNotificationDispatcher.notifyNewCases(this, listOf(averia))
    }

    private fun cacheToken(context: Context, token: String) {
        context.getSharedPreferences(FCM_PREFS, MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_TOKEN, token)
            .apply()
    }

    private fun saveTokenToRtdb(uid: String, token: String) {
        val safeTokenKey = token.toFirebaseKey()
        val updates = hashMapOf<String, Any>(
            "fcmToken" to token,
            "fcm/currentToken" to token,
            "fcm/lastUpdated" to ServerValue.TIMESTAMP,
            "fcm/tokens/$safeTokenKey" to token
        )

        FirebaseDatabase.getInstance("https://tecniapp-ice-user.firebaseio.com")
            .getReference("usuarios")
            .child(uid)
            .updateChildren(updates)
            .addOnFailureListener { error ->
                cacheToken(this, token)
                Log.e(TAG, "No se pudo guardar el token FCM", error)
            }
            .addOnSuccessListener {
                getSharedPreferences(FCM_PREFS, MODE_PRIVATE)
                    .edit()
                    .remove(KEY_PENDING_TOKEN)
                    .apply()
            }
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
            estado = data["estado"] ?: "PENDIENTE",
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
        private const val FCM_PREFS = "fcm_prefs"
        private const val KEY_PENDING_TOKEN = "pending_token"

        fun flushPendingToken(context: Context, uid: String?) {
            if (uid.isNullOrBlank()) return
            val prefs = context.getSharedPreferences(FCM_PREFS, MODE_PRIVATE)
            val pending = prefs.getString(KEY_PENDING_TOKEN, null) ?: return
            FirebaseDatabase.getInstance("https://tecniapp-ice-user.firebaseio.com")
                .getReference("usuarios")
                .child(uid)
                .updateChildren(
                    hashMapOf<String, Any>(
                        "fcmToken" to pending,
                        "fcm/currentToken" to pending,
                        "fcm/lastUpdated" to ServerValue.TIMESTAMP,
                        "fcm/tokens/${pending.toFirebaseKey()}" to pending
                    )
                )
                .addOnSuccessListener {
                    prefs.edit().remove(KEY_PENDING_TOKEN).apply()
                }
                .addOnFailureListener { error ->
                    Log.e(TAG, "No se pudo subir pending_token cacheado", error)
                }
        }

        private fun String.toFirebaseKey(): String =
            replace('.', '_')
                .replace('#', '_')
                .replace('$', '_')
                .replace('[', '_')
                .replace(']', '_')
                .replace('/', '_')
    }
}
