package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Gestor centralizado de preferencias de notificaciones de averías.
 *
 * Las preferencias son POR USUARIO (keyed con UID) para que el cierre de sesión
 * no borre las configuraciones del usuario. Solo se sincronizan a Firebase
 * cuando el usuario ha configurado explícitamente sus preferencias.
 *
 * Firebase path: /usuarios/{uid}/notificationAgencies : ["GUACIMO","GUAPILES",...]
 *                /usuarios/{uid}/notificationEnabled  : true/false
 */
object AveriaNotificationPreferences {

    private const val PREFS_BASE = "averia_notif_v2"
    private const val LEGACY_PREFS = "averia_notification_prefs"
    private const val KEY_ENABLED = "notifications_enabled"
    private const val KEY_AGENCIES = "notifications_agencies"
    private const val KEY_HAS_EXPLICIT_DATA = "has_explicit_data"

    private const val TAG = "AveriaNotifPrefs"

    private fun currentUid(): String? = FirebaseAuth.getInstance().currentUser?.uid

    private fun prefsForUid(context: Context, uid: String): SharedPreferences =
        context.applicationContext.getSharedPreferences("${PREFS_BASE}_$uid", Context.MODE_PRIVATE)

    private fun prefs(context: Context): SharedPreferences {
        val uid = currentUid()
        return if (uid != null) {
            val userPrefs = prefsForUid(context, uid)
            // Migrar datos del prefs global al prefs de usuario si aún no se migró
            migrateLegacyIfNeeded(context, uid, userPrefs)
            userPrefs
        } else {
            context.applicationContext.getSharedPreferences(PREFS_BASE, Context.MODE_PRIVATE)
        }
    }

    private fun migrateLegacyIfNeeded(context: Context, uid: String, userPrefs: SharedPreferences) {
        if (userPrefs.contains(KEY_ENABLED) || userPrefs.contains(KEY_AGENCIES)) return
        val legacy = context.applicationContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        if (!legacy.contains(KEY_AGENCIES) && !legacy.contains(KEY_ENABLED)) return
        val legacyAgencies = legacy.getStringSet(KEY_AGENCIES, emptySet()) ?: emptySet()
        val legacyEnabled = legacy.getBoolean(KEY_ENABLED, true)
        if (legacyAgencies.isNotEmpty() || !legacyEnabled) {
            userPrefs.edit {
                putBoolean(KEY_ENABLED, legacyEnabled)
                putStringSet(KEY_AGENCIES, legacyAgencies)
                putBoolean(KEY_HAS_EXPLICIT_DATA, true)
            }
            Log.d(TAG, "Migradas ${legacyAgencies.size} agencias del prefs global al usuario $uid")
        }
    }

    fun areNotificationsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit {
            putBoolean(KEY_ENABLED, enabled)
            putBoolean(KEY_HAS_EXPLICIT_DATA, true)
        }
        pushFiltersToFirebase(context)
    }

    fun getSelectedAgencies(context: Context): List<String> {
        val stored = prefs(context).getStringSet(KEY_AGENCIES, emptySet()) ?: emptySet()
        return stored.map { it.trim() }
            .filter { it.isNotEmpty() }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
    }

    fun setSelectedAgencies(context: Context, agencies: Collection<String>) {
        val cleaned = agencies.map { it.trim() }
            .filter { it.isNotEmpty() }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
        prefs(context).edit {
            putStringSet(KEY_AGENCIES, cleaned.toSet())
            putBoolean(KEY_HAS_EXPLICIT_DATA, true)
        }
        pushFiltersToFirebase(context)
    }

    fun selectedAgenciesFlow(context: Context): Flow<List<String>> = callbackFlow {
        val preferences = prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_AGENCIES) {
                trySend(getSelectedAgencies(context)).isSuccess
            }
        }
        trySend(getSelectedAgencies(context)).isSuccess
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun addAgency(context: Context, agency: String) {
        val current = getSelectedAgencies(context).toMutableSet()
        current += agency.trim()
        setSelectedAgencies(context, current)
    }

    fun removeAgency(context: Context, agency: String) {
        val current = getSelectedAgencies(context)
            .filterNot { it.equals(agency, ignoreCase = true) }
        setSelectedAgencies(context, current)
    }

    fun normalizedAgencies(context: Context): Set<String> =
        getSelectedAgencies(context).map { normalizeAveriaText(it) }
            .filter { it.isNotBlank() }
            .toSet()

    fun hasExplicitData(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HAS_EXPLICIT_DATA, false)

    fun pushFiltersToFirebase(context: Context) {
        val uid = currentUid()
        if (uid.isNullOrBlank()) {
            Log.w(TAG, "pushFiltersToFirebase: sin usuario autenticado; se omite")
            return
        }

        // No sobreescribir Firebase con datos vacíos/default si el usuario nunca configuró
        if (!hasExplicitData(context)) {
            Log.d(TAG, "pushFiltersToFirebase: sin datos explícitos del usuario, se omite push")
            return
        }

        val enabled = areNotificationsEnabled(context)
        val agencies = normalizedAgencies(context).toList().sorted()

        val updates = hashMapOf<String, Any>(
            "notificationEnabled" to enabled,
            "notificationAgencies" to agencies
        )

        FirebaseDatabase.getInstance("https://tecniapp-ice-user.firebaseio.com/")
            .getReference("usuarios")
            .child(uid)
            .updateChildren(updates)
            .addOnFailureListener { e ->
                Log.e(TAG, "No se pudo sincronizar notificationAgencies", e)
            }
    }
}
