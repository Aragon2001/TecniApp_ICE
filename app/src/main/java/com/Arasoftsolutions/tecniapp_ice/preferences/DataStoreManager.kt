package com.Arasoftsolutions.tecniapp_ice.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Gestor centralizado de preferencias persistentes usando DataStore.
 *
 * Permite guardar toggles simples de la pantalla de ajustes sin depender
 * de múltiples [SharedPreferences] dispersas en la app.
 */
class DataStoreManager private constructor(private val appContext: Context) {

    private val dataStore get() = appContext.settingsDataStore

    val notificationsEnabled: Flow<Boolean> = booleanFlow(Keys.NOTIFICATIONS_ENABLED, default = true)

    val autoSyncEnabled: Flow<Boolean> = booleanFlow(Keys.AUTO_SYNC_ENABLED, default = true)

    val gpsEnabled: Flow<Boolean> = booleanFlow(Keys.GPS_ENABLED, default = false)

    val darkThemeEnabled: Flow<Boolean> = booleanFlow(Keys.DARK_THEME_ENABLED, default = false)

    val onboardingCompleted: Flow<Boolean> = booleanFlow(Keys.ONBOARDING_COMPLETED, default = false)

    val lastManualSyncMillis: Flow<Long?> = dataStore.data.map { preferences ->
        preferences[Keys.LAST_MANUAL_SYNC]
    }

    suspend fun setNotificationsEnabled(value: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.NOTIFICATIONS_ENABLED] = value }
    }

    suspend fun setAutoSyncEnabled(value: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.AUTO_SYNC_ENABLED] = value }
    }

    suspend fun setGpsEnabled(value: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.GPS_ENABLED] = value }
    }

    suspend fun setDarkThemeEnabled(value: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.DARK_THEME_ENABLED] = value }
    }

    suspend fun setOnboardingCompleted(value: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.ONBOARDING_COMPLETED] = value }
    }

    suspend fun markManualSyncNow(timestampMillis: Long = System.currentTimeMillis()) {
        dataStore.edit { prefs -> prefs[Keys.LAST_MANUAL_SYNC] = timestampMillis }
    }

    private fun booleanFlow(key: Preferences.Key<Boolean>, default: Boolean): Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[key] ?: default }

    private object Keys {
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val AUTO_SYNC_ENABLED = booleanPreferencesKey("auto_sync_enabled")
        val GPS_ENABLED = booleanPreferencesKey("gps_enabled")
        val DARK_THEME_ENABLED = booleanPreferencesKey("dark_theme_enabled")
        val LAST_MANUAL_SYNC = longPreferencesKey("last_manual_sync")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    companion object {
        private val Context.settingsDataStore by preferencesDataStore(name = "tecniapp_settings")

        @Volatile
        private var INSTANCE: DataStoreManager? = null

        fun getInstance(context: Context): DataStoreManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DataStoreManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

