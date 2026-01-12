package com.Arasoftsolutions.tecniapp_ice

import android.app.Application
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriaNotifications
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriasSyncWorker

class TecniApp : Application() {
    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("TecniApp", "Application onCreate() ejecutado ✅")
        AveriaNotifications.ensureChannel(this)
        AveriasSyncWorker.schedule(this)

    }
}
