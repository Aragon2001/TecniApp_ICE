package com.Arasoftsolutions.tecniapp_ice.permissions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.Arasoftsolutions.tecniapp_ice.preferences.DataStoreManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Verifica si todos los permisos requeridos están concedidos.
 * Si faltan permisos, lanza [PermissionsActivity] que guía al usuario
 * de forma clara y paso a paso en lugar de mostrar diálogos repetitivos.
 */
class PermissionInitializer(
    private val activity: AppCompatActivity,
    private val dataStoreManager: DataStoreManager
) {

    fun initializeIfNeeded() {
        activity.lifecycleScope.launch {
            val alreadyValidated = dataStoreManager.permissionsValidated.first()
            if (alreadyValidated) return@launch

            if (areAllRequiredPermissionsGranted()) {
                dataStoreManager.setPermissionsValidated(true)
                return@launch
            }

            activity.startActivity(Intent(activity, PermissionsActivity::class.java))
        }
    }

    fun areAllRequiredPermissionsGranted(): Boolean {
        val notificationsOk =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                hasPermission(Manifest.permission.POST_NOTIFICATIONS)

        val locationOk = hasAnyLocationPermission()

        val backgroundLocationOk =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                hasBackgroundLocationPermission()

        val manageStorageOk =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                Environment.isExternalStorageManager()

        val installPackagesOk =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                activity.packageManager.canRequestPackageInstalls()

        return notificationsOk &&
            locationOk &&
            backgroundLocationOk &&
            manageStorageOk &&
            installPackagesOk
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

    private fun hasAnyLocationPermission(): Boolean =
        hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun hasBackgroundLocationPermission(): Boolean =
        hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
}
