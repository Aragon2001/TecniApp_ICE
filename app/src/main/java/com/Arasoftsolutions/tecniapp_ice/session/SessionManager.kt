package com.Arasoftsolutions.tecniapp_ice.session

import android.content.Context
import android.content.Context.MODE_PRIVATE
import com.google.firebase.auth.FirebaseAuth

object SessionManager {
    private const val LEGACY_PREFS = "TecniAppPrefs"
    private const val SYNC_PREFS = "app_preferences"

    fun signOutAndClear(context: Context) {
        FirebaseAuth.getInstance().signOut()
        context.getSharedPreferences(LEGACY_PREFS, MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences(SYNC_PREFS, MODE_PRIVATE).edit().clear().apply()
    }
}
