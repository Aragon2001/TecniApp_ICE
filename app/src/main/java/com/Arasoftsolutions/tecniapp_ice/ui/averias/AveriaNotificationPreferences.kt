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
 * Gestor centralizado de preferencias relacionadas con las notificaciones de averías.
 *
 * Sincroniza los filtros seleccionados hacia Firebase Realtime:
 * /usuarios/{uid}/notificationAgencies : ["GUACIMO","GUAPILES",...]
 * /usuarios/{uid}/notificationEnabled  : true/false
 */
object AveriaNotificationPreferences {

    private const val PREFS_NAME = "averia_notification_prefs"
    private const val KEY_ENABLED = "notifications_enabled"
    private const val KEY_AGENCIES = "notifications_agencies"

    private const val TAG = "AveriaNotifPrefs"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun areNotificationsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, enabled) }
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

    fun pushFiltersToFirebase(context: Context) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            Log.w(TAG, "pushFiltersToFirebase: sin usuario autenticado; se omite")
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