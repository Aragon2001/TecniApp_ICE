package com.Arasoftsolutions.tecniapp_ice

import android.app.Application
import androidx.work.WorkManager
import com.Arasoftsolutions.tecniapp_ice.preferences.DataStoreManager
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriaNotifications
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriasSyncWorker
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

    }
}
