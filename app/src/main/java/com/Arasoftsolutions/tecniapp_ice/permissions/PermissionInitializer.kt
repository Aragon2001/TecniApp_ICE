package com.Arasoftsolutions.tecniapp_ice.permissions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.preferences.DataStoreManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PermissionInitializer(
    private val activity: AppCompatActivity,
    private val dataStoreManager: DataStoreManager
) {

    private val runtimePermissionsLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            handlePermissionResult()
        }

    private val backgroundLocationLauncher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            handlePermissionResult()
        }

    private val settingsLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            handlePermissionResult()
        }

    fun initializeIfNeeded() {
        activity.lifecycleScope.launch {
            val alreadyValidated = dataStoreManager.permissionsValidated.first()
            if (alreadyValidated) return@launch

            if (areAllRequiredPermissionsGranted()) {
                dataStoreManager.setPermissionsValidated(true)
                return@launch
            }

            requestNextMissingPermission()
        }
    }

    fun areAllRequiredPermissionsGranted(): Boolean {
        val notificationsGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                hasPermission(Manifest.permission.POST_NOTIFICATIONS)

        val locationGranted = hasAnyLocationPermission()

        val backgroundLocationGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || hasBackgroundLocationPermission()

        val manageStorageGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

        val installPackagesGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                activity.packageManager.canRequestPackageInstalls()

        return notificationsGranted &&
            locationGranted &&
            backgroundLocationGranted &&
            manageStorageGranted &&
            installPackagesGranted
    }

    private fun requestNextMissingPermission() {
        when (firstMissingRequirement()) {
            MissingRequirement.NOTIFICATIONS -> {
                runtimePermissionsLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
            MissingRequirement.LOCATION -> {
                runtimePermissionsLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            MissingRequirement.BACKGROUND_LOCATION -> {
                showPermissionRequiredDialog(
                    message = activity.getString(R.string.permission_background_location_required)
                ) {
                    backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
            MissingRequirement.MANAGE_EXTERNAL_STORAGE -> {
                showPermissionRequiredDialog(
                    message = activity.getString(R.string.permission_all_files_required)
                ) {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.fromParts("package", activity.packageName, null)
                    }
                    settingsLauncher.launch(intent)
                }
            }
            MissingRequirement.INSTALL_PACKAGES -> {
                showPermissionRequiredDialog(
                    message = activity.getString(R.string.permission_unknown_sources_required)
                ) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.fromParts("package", activity.packageName, null)
                    }
                    settingsLauncher.launch(intent)
                }
            }
            null -> {
                activity.lifecycleScope.launch {
                    dataStoreManager.setPermissionsValidated(true)
                }
            }
        }
    }

    private fun handlePermissionResult() {
        if (areAllRequiredPermissionsGranted()) {
            activity.lifecycleScope.launch {
                dataStoreManager.setPermissionsValidated(true)
            }
            return
        }

        val message = when (firstMissingRequirement()) {
            MissingRequirement.NOTIFICATIONS -> activity.getString(R.string.permission_notification_required)
            MissingRequirement.LOCATION -> activity.getString(R.string.permission_location_required)
            MissingRequirement.BACKGROUND_LOCATION -> activity.getString(R.string.permission_background_location_required)
            MissingRequirement.MANAGE_EXTERNAL_STORAGE -> activity.getString(R.string.permission_all_files_required)
            MissingRequirement.INSTALL_PACKAGES -> activity.getString(R.string.permission_unknown_sources_required)
            null -> return
        }

        showPermissionRequiredDialog(message) {
            requestNextMissingPermission()
        }
    }

    private fun firstMissingRequirement(): MissingRequirement? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            return MissingRequirement.NOTIFICATIONS
        }

        if (!hasAnyLocationPermission()) {
            return MissingRequirement.LOCATION
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackgroundLocationPermission()) {
            return MissingRequirement.BACKGROUND_LOCATION
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            return MissingRequirement.MANAGE_EXTERNAL_STORAGE
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            return MissingRequirement.INSTALL_PACKAGES
        }

        return null
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

    private fun hasAnyLocationPermission(): Boolean =
        hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun hasBackgroundLocationPermission(): Boolean =
        hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    private fun showPermissionRequiredDialog(message: String, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.permission_required_title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton(R.string.permission_required_action) { dialog, _ ->
                dialog.dismiss()
                onConfirm()
            }
            .setNegativeButton(R.string.permission_required_exit) { _, _ ->
                activity.finishAffinity()
            }
            .show()
    }

    private enum class MissingRequirement {
        NOTIFICATIONS,
        LOCATION,
        BACKGROUND_LOCATION,
        MANAGE_EXTERNAL_STORAGE,
        INSTALL_PACKAGES
    }
}
