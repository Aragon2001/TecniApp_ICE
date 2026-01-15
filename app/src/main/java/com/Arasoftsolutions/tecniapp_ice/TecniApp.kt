package com.Arasoftsolutions.tecniapp_ice

import android.app.Application
import androidx.work.WorkManager
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.preferences.DataStoreManager
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriaNotifications
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriasSyncWorker
import com.Arasoftsolutions.tecniapp_ice.update.UpdateWorker
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TecniApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("TecniApp", "Application onCreate() ejecutado ✅")
        AveriaNotifications.ensureChannel(this)
        val dataStore = DataStoreManager.getInstance(this)
        applicationScope.launch {
            val autoSyncEnabled = dataStore.autoSyncEnabled.first()
            if (autoSyncEnabled) {
                AveriasSyncWorker.schedule(this@TecniApp)
            } else {
                WorkManager.getInstance(this@TecniApp)
                    .cancelUniqueWork(AveriasSyncWorker.UNIQUE_PERIODIC_WORK)
            }
        }

        UpdateWorker.schedule(this)

        applicationScope.launch {
            val currentSchemaVersion = AppDatabase.SCHEMA_VERSION
            val lastApplied = dataStore.lastSchemaVersionApplied.first()
            if (lastApplied != currentSchemaVersion) {
                val repository = RoomRepository.getInstance(this@TecniApp)
                runCatching {
                    repository.limpiarBaseLocal()
                    repository.syncCatalogosGenerales()
                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                    if (uid != null) {
                        val user = repository.upsertUserFromFirebase(uid)
                        val subregion = user.subregion?.trim()?.takeIf { it.isNotEmpty() }
                        if (subregion != null) {
                            repository.syncSubregion(subregion)
                        }
                    }
                    AveriasSyncWorker.triggerNow(this@TecniApp)
                    dataStore.setLastSchemaVersionApplied(currentSchemaVersion)
                }.onFailure { error ->
                    android.util.Log.e(\"TecniApp\", \"Error aplicando actualización de schema\", error)
                }
            }
        }
    }
}
