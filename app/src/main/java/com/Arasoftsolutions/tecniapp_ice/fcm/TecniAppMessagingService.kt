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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicLong

class TecniAppMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repo by lazy { AveriasRepository(AppDatabase.getInstance(applicationContext)) }

    // Debounce para pullFromFirebaseOnce()
    private var refreshJob: Job? = null

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Nuevo token FCM: $token")

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            Log.w(TAG, "Token recibido sin usuario autenticado; se omite el guardado")
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

        // 1) Guardar SIEMPRE en Room (offline-first)
        scope.launch {
            repo.upsertFromPush(averia)

            // 2) Refrescar desde Firebase, pero con debounce para no saturar
            scheduleFirebaseRefreshDebounced()
        }

        // 3) Respetar apagado global del usuario
        if (!AveriaNotificationPreferences.areNotificationsEnabled(this)) {
            Log.d(TAG, "Notificaciones desactivadas por el usuario; se omite alerta local")
            return
        }

        // 4) Aplicar filtros locales por agencias (preferencias)
        val agencyFilters = AveriaNotificationPreferences.normalizedAgencies(this)
        if (!shouldNotifyForAgency(averia, agencyFilters)) {
            Log.d(TAG, "Mensaje FCM filtrado por agencia (${averia.agencia}/${averia.agenciaTag})")
            return
        }

        AveriaNotificationDispatcher.notifyNewCases(this, listOf(averia))
    }

    private fun scheduleFirebaseRefreshDebounced() {
        // Si llegan muchos pushes seguidos, cancelamos y reprogramamos
        refreshJob?.cancel()
        refreshJob = scope.launch {
            delay(15_000) // 15s (ajustable 10–20s)
            val now = System.currentTimeMillis()
            val last = LAST_REFRESH_AT.get()
            // Gate extra: no permitir más de 1 refresh cada 12s aunque algo raro pase
            if (now - last < 12_000) return@launch

            LAST_REFRESH_AT.set(now)
            runCatching { repo.pullFromFirebaseOnce() }
                .onFailure { Log.w(TAG, "No se pudo refrescar Firebase (debounced)", it) }
        }
    }

    private fun saveTokenToRtdb(uid: String, token: String) {
        FirebaseDatabase.getInstance()
            .getReference("usuarios")
            .child(uid)
            .child("fcmToken")
            .setValue(token)
            .addOnFailureListener { error ->
                Log.e(TAG, "No se pudo guardar el token FCM", error)
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
            // OJO: Idealmente server manda PENDIENTE/RESUELTA; si no, esto igual no crashea.
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
        private val LAST_REFRESH_AT = AtomicLong(0L)
    }
}
