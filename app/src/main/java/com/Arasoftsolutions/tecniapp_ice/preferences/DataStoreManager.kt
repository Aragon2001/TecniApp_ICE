package com.Arasoftsolutions.tecniapp_ice.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.Arasoftsolutions.tecniapp_ice.update.UpdateInfo
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

    val lastSchemaVersionApplied: Flow<Int?> = dataStore.data.map { preferences ->
        preferences[Keys.LAST_SCHEMA_VERSION_APPLIED]
    }

    val pendingUpdateInfo: Flow<UpdateInfo?> = dataStore.data.map { preferences ->
        val versionCode = preferences[Keys.PENDING_UPDATE_VERSION_CODE] ?: return@map null
        val apkUrl = preferences[Keys.PENDING_UPDATE_APK_URL] ?: return@map null
        val versionName = preferences[Keys.PENDING_UPDATE_VERSION_NAME] ?: ""
        val notes = preferences[Keys.PENDING_UPDATE_NOTES] ?: ""
        val sha256 = preferences[Keys.PENDING_UPDATE_SHA256]
        UpdateInfo(
            versionCode = versionCode,
            versionName = versionName,
            apkUrl = apkUrl,
            sha256 = sha256,
            notes = notes
        )
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

    suspend fun setLastSchemaVersionApplied(schemaVersion: Int) {
        dataStore.edit { prefs -> prefs[Keys.LAST_SCHEMA_VERSION_APPLIED] = schemaVersion }
    }

    suspend fun setPendingUpdateInfo(info: UpdateInfo?) {
        dataStore.edit { prefs ->
            if (info == null) {
                prefs.remove(Keys.PENDING_UPDATE_VERSION_CODE)
                prefs.remove(Keys.PENDING_UPDATE_VERSION_NAME)
                prefs.remove(Keys.PENDING_UPDATE_APK_URL)
                prefs.remove(Keys.PENDING_UPDATE_SHA256)
                prefs.remove(Keys.PENDING_UPDATE_NOTES)
            } else {
                prefs[Keys.PENDING_UPDATE_VERSION_CODE] = info.versionCode
                prefs[Keys.PENDING_UPDATE_VERSION_NAME] = info.versionName
                prefs[Keys.PENDING_UPDATE_APK_URL] = info.apkUrl
                info.sha256?.let { prefs[Keys.PENDING_UPDATE_SHA256] = it }
                prefs[Keys.PENDING_UPDATE_NOTES] = info.notes
            }
        }
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
        val LAST_SCHEMA_VERSION_APPLIED = intPreferencesKey("last_schema_version_applied")
        val PENDING_UPDATE_VERSION_CODE = intPreferencesKey("pending_update_version_code")
        val PENDING_UPDATE_VERSION_NAME = androidx.datastore.preferences.core.stringPreferencesKey(
            "pending_update_version_name"
        )
        val PENDING_UPDATE_APK_URL = androidx.datastore.preferences.core.stringPreferencesKey(
            "pending_update_apk_url"
        )
        val PENDING_UPDATE_SHA256 = androidx.datastore.preferences.core.stringPreferencesKey(
            "pending_update_sha256"
        )
        val PENDING_UPDATE_NOTES = androidx.datastore.preferences.core.stringPreferencesKey(
            "pending_update_notes"
        )
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
