package com.Arasoftsolutions.tecniapp_ice

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.preferences.DataStoreManager
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriaNotifications
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriasSyncWorker
import com.Arasoftsolutions.tecniapp_ice.network.NetworkHealthMonitor
import com.Arasoftsolutions.tecniapp_ice.ui.common.NetworkAlertManager
import com.Arasoftsolutions.tecniapp_ice.update.UpdateWorker
import com.Arasoftsolutions.tecniapp_ice.ui.vehiculo.worker.VehiculoReminderWorker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TecniApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val networkAlertManager by lazy { NetworkAlertManager(this) }
    private val roomRepository by lazy { RoomRepository.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("TecniApp", "Application onCreate() ejecutado ✅")
        AveriaNotifications.ensureChannel(this)
        networkAlertManager.start()
        NetworkHealthMonitor.getInstance(this)
        enableFirebasePersistence()
        val dataStore = DataStoreManager.getInstance(this)
        applicationScope.launch {
            val darkThemeEnabled = dataStore.darkThemeEnabled.first()
            val mode = if (darkThemeEnabled) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
            withContext(Dispatchers.Main.immediate) {
                AppCompatDelegate.setDefaultNightMode(mode)
            }
        }
        applicationScope.launch {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (!uid.isNullOrBlank()) {
                AveriasSyncWorker.schedule(this@TecniApp)
                VehiculoReminderWorker.scheduleDaily(this@TecniApp)
            }
        }

        UpdateWorker.schedule(this)
        registerRealtimeSyncObserver()
    }

    private fun enableFirebasePersistence() {
        val urls = listOf(
            "https://tecniapp-ice-user.firebaseio.com",
            "https://tecniapp-ice-datosgenerales.firebaseio.com",
            "https://tecniapp-ice-personal.firebaseio.com/",
            "https://tecniapp-ice-materiales.firebaseio.com/"
        )

        // ⚠️ Evitamos persistencia local en nodos de alto volumen (medidores/localizaciones,
        // inventario/averías/luminarias) para prevenir OOM al rehidratar caché SQLite de Firebase.

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
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
                applicationScope.launch(Dispatchers.IO) {
                    val scope = roomRepository.buildUserScope(uid)
                    roomRepository.startRealtimeSyncForScope(scope)
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                roomRepository.stopRealtimeSync()
            }
        })
    }
}
