package com.Arasoftsolutions.tecniapp_ice.fcm

import android.content.Context
import android.util.Log
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AveriaEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriaNotificationDispatcher
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriaNotificationPreferences
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriasForegroundTracker
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriasRepository
import com.Arasoftsolutions.tecniapp_ice.ui.averias.shouldNotifyForAgency
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.*

class TecniAppMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val repo by lazy {
        AveriasRepository(AppDatabase.getInstance(applicationContext))
    }

    // =====================================================
    // TOKEN MANAGEMENT
    // =====================================================

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Nuevo token FCM recibido")

        val uid = FirebaseAuth.getInstance().currentUser?.uid

        if (uid.isNullOrBlank()) {
            cacheToken(this, token)
            Log.w(TAG, "Token recibido sin usuario autenticado. Se cachea.")
            return
        }

        saveTokenToRtdb(uid, token)
    }

    // =====================================================
    // MENSAJE RECIBIDO
    // =====================================================

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        if (data.isEmpty()) {
            Log.w(TAG, "Mensaje FCM vacío")
            return
        }

        val caseId = data["caseId"]
        if (caseId.isNullOrBlank()) {
            Log.w(TAG, "Mensaje ignorado: caseId faltante")
            return
        }

        val evento = data["evento"]?.trim()?.lowercase()
        // Solo procesar eventos de nueva avería o avería asignada
        if (evento != null && evento != "nueva_averia" && evento != "averia_asignada" && evento != "averia_resuelta") {
            Log.d(TAG, "Evento ignorado: evento=$evento caseId=$caseId")
            return
        }

        val estadoRaw = data["estado"] ?: data["estadoClor"] ?: "PENDIENTE"
        val estadoClor = data["estadoClor"]?.trim()?.uppercase()

        Log.d(TAG, "FCM caseId=$caseId evento=$evento estadoRaw=$estadoRaw estadoClor=$estadoClor")

        val averia = buildAveriaFromMessage(caseId, data, estadoRaw, estadoClor)

        // Insertar/Actualizar en Room
        scope.launch {
            try {
                repo.upsertFromPush(averia)
                Log.d(TAG, "Avería insertada/actualizada en Room caseId=$caseId")
            } catch (e: Exception) {
                Log.e(TAG, "Error insertando en Room caseId=$caseId", e)
            }
        }

        // FIX 1: Log explícito del estado del tracker ANTES de la guarda, para
        // detectar si isAveriasVisible quedó en true incorrectamente (flag zombie).
        // Si ves "isAveriasVisible=true" en logcat cuando la pantalla está cerrada,
        // revisa que el Fragment llame a AveriasForegroundTracker.isAveriasVisible = false
        // en onPause() y onDestroyView().
        Log.d(TAG, "isAveriasVisible=${AveriasForegroundTracker.isAveriasVisible} — caseId=$caseId")

        if (AveriasForegroundTracker.isAveriasVisible) {
            Log.d(TAG, "Pantalla de averías activa — notificación suprimida para caseId=$caseId")
            return
        }

        // Verificar preferencias del usuario
        if (!AveriaNotificationPreferences.areNotificationsEnabled(this)) {
            Log.d(TAG, "Notificaciones desactivadas por usuario")
            return
        }

        val agencyFilters = AveriaNotificationPreferences.normalizedAgencies(this)
        if (!shouldNotifyForAgency(averia, agencyFilters)) {
            Log.d(TAG, "Filtrado por agencia para caseId=$caseId")
            return
        }

        when {
            estadoClor == "RESUELTA" || evento == "averia_resuelta" ->
                AveriaNotificationDispatcher.notifyResolvedCases(this, listOf(averia))
            evento == "averia_asignada" ->
                AveriaNotificationDispatcher.notifyAssignedCase(this, averia)
            else ->
                AveriaNotificationDispatcher.notifyNewCases(this, listOf(averia))
        }
    }

    // =====================================================
    // BUILD ENTITY DESDE FCM
    // =====================================================

    private fun buildAveriaFromMessage(
        caseId: String,
        data: Map<String, String>,
        estadoRaw: String,
        estadoClor: String?
    ): AveriaEntity {

        val agencia = data["agencia"]
        val nombreAgencia = data["nombreAgencia"] ?: agencia
        val descripcion = data["descripcion"]

        val lastUpdated = data["lastUpdated"]?.toLongOrNull() ?: System.currentTimeMillis()
        val fechaInicio = data["fechaInicioMillis"]?.toLongOrNull() ?: lastUpdated

        // localizacion comes from the FCM payload (may be blank for CLOR-sourced events);
        // do NOT substitute descripcion here — that causes the notification to show the
        // observaciones text labeled as "Localización".
        val localizacion = data["localizacion"]?.takeIf { it.isNotBlank() }

        return AveriaEntity(
            caseId = caseId,
            estado = estadoRaw,
            estadoClor = estadoClor,
            agencia = agencia,
            nombreAgencia = nombreAgencia,
            agenciaTag = data["agenciaTag"] ?: agencia ?: "",
            region = data["region"],
            localizacion = localizacion,
            direccion = data["direccion"]?.takeIf { it.isNotBlank() },
            observaciones = descripcion,
            nise = data["nise"],
            causa = data["causa"],
            clientesAfectados = data["clientesAfectados"],
            lat = data["lat"]?.toDoubleOrNull(),
            lng = data["lng"]?.toDoubleOrNull(),
            fechaInicioMillis = fechaInicio,
            lastUpdated = lastUpdated,
            isSynced = true
        )
    }

    // =====================================================
    // TOKEN CACHE SI NO HAY LOGIN
    // =====================================================

    private fun cacheToken(context: Context, token: String) {
        context.getSharedPreferences(FCM_PREFS, MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_TOKEN, token)
            .apply()
    }

    private fun saveTokenToRtdb(uid: String, token: String) {

        val safeKey = token.replace("[.#$\\[\\]/]".toRegex(), "_")

        FirebaseDatabase.getInstance("https://tecniapp-ice-user.firebaseio.com")
            .getReference("usuarios")
            .child(uid)
            .updateChildren(
                hashMapOf(
                    "fcmToken" to token,
                    "fcm/currentToken" to token,
                    "fcm/lastUpdated" to ServerValue.TIMESTAMP,
                    "fcm/tokens/$safeKey" to token
                )
            )
            .addOnSuccessListener {
                clearPendingToken(this)
                Log.d(TAG, "Token FCM guardado correctamente")
            }
            .addOnFailureListener { e ->
                cacheToken(this, token)
                Log.e(TAG, "Error guardando token FCM", e)
            }
    }

    private fun clearPendingToken(context: Context) {
        context.getSharedPreferences(FCM_PREFS, MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING_TOKEN)
            .apply()
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

            val prefs = context.getSharedPreferences(FCM_PREFS, Context.MODE_PRIVATE)
            val pending = prefs.getString(KEY_PENDING_TOKEN, null) ?: return

            val safeKey = pending.replace("[.#$\\[\\]/]".toRegex(), "_")

            FirebaseDatabase.getInstance("https://tecniapp-ice-user.firebaseio.com")
                .getReference("usuarios")
                .child(uid)
                .updateChildren(
                    hashMapOf(
                        "fcmToken" to pending,
                        "fcm/currentToken" to pending,
                        "fcm/lastUpdated" to ServerValue.TIMESTAMP,
                        "fcm/tokens/$safeKey" to pending
                    )
                )
                .addOnSuccessListener {
                    prefs.edit().remove(KEY_PENDING_TOKEN).apply()
                    Log.d(TAG, "Pending token subido correctamente")
                }
                .addOnFailureListener {
                    Log.e(TAG, "Error subiendo pending token")
                }
        }
    }
}