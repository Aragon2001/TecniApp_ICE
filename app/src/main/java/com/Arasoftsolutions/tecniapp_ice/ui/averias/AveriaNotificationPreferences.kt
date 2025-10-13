package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Gestor centralizado de preferencias relacionadas con las notificaciones de averías.
 */
object AveriaNotificationPreferences {

    private const val PREFS_NAME = "averia_notification_prefs"
    private const val KEY_ENABLED = "notifications_enabled"
    private const val KEY_AGENCIES = "notifications_agencies"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun areNotificationsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, enabled) }
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
}
