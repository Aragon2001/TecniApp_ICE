package com.Arasoftsolutions.tecniapp_ice

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.WorkManager
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.preferences.DataStoreManager
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriaNotifications
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriasSyncWorker
import com.Arasoftsolutions.tecniapp_ice.ui.common.NetworkAlertManager
import com.Arasoftsolutions.tecniapp_ice.update.UpdateWorker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TecniApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val networkAlertManager by lazy { NetworkAlertManager(this) }
    private val roomRepository by lazy { RoomRepository.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("TecniApp", "Application onCreate() ejecutado ✅")
        AveriaNotifications.ensureChannel(this)
        networkAlertManager.start()
        enableFirebasePersistence()
        registerRealtimeSyncObserver()
        val dataStore = DataStoreManager.getInstance(this)
        applicationScope.launch {
            val darkThemeEnabled = dataStore.darkThemeEnabled.first()
            val mode = if (darkThemeEnabled) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
            AppCompatDelegate.setDefaultNightMode(mode)
        }
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
                   android.util.Log.e("TecniApp", "Error aplicando actualización de schema", error)
                }
            }
        }
    }

    private fun enableFirebasePersistence() {
        val urls = listOf(
            "https://tecniapp-ice-user.firebaseio.com",
            "https://tecniapp-ice-datosgenerales.firebaseio.com",
            "https://tecniapp-ice.firebaseio.com",
            "https://tecniapp-ice-default-rtdb.firebaseio.com",
            "https://tecniapp-ice-personal.firebaseio.com/",
            "https://tecniapp-ice-materiales.firebaseio.com/"
        )

        // ⚠️ Evitamos persistencia local en nodos de alto volumen (inventario/averías/luminarias)
        // para prevenir OOM al rehidratar caché SQLite de Firebase.

        runCatching {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        }.onFailure { error ->
            android.util.Log.w("TecniApp", "No se pudo habilitar persistencia default", error)
        }

        urls.forEach { url ->
            runCatching {
                FirebaseDatabase.getInstance(url).setPersistenceEnabled(true)
            }.onFailure { error ->
                android.util.Log.w("TecniApp", "No se pudo habilitar persistencia en $url", error)
            }
        }
    }

    private fun registerRealtimeSyncObserver() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (FirebaseAuth.getInstance().currentUser != null) {
                    roomRepository.startRealtimeSync()
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                roomRepository.stopRealtimeSync()
            }
        })
    }
}
