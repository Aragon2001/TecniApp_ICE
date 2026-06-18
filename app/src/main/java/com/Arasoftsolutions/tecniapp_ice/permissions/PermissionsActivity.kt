package com.Arasoftsolutions.tecniapp_ice.permissions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.ActivityPermissionsBinding
import com.Arasoftsolutions.tecniapp_ice.databinding.ItemPermissionCardBinding
import com.Arasoftsolutions.tecniapp_ice.preferences.DataStoreManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class PermissionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionsBinding
    private val dataStore by lazy { DataStoreManager.getInstance(applicationContext) }
    private lateinit var adapter: PermissionAdapter

    data class PermissionItem(
        val id: String,
        val iconRes: Int,
        val iconTintColorRes: Int,
        val title: String,
        val reason: String,
        var status: Status = Status.PENDING
    ) {
        enum class Status { PENDING, GRANTED, DENIED }
    }

    private val items = mutableListOf<PermissionItem>()
    private val requestedIds = mutableSetOf<String>()

    private val runtimeLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshStatuses(); advanceOrNotify() }

    private val backgroundLocLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshStatuses(); advanceOrNotify() }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshStatuses(); advanceOrNotify() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this) { confirmExit() }

        buildItems()
        refreshStatuses()

        adapter = PermissionAdapter(items)
        binding.permissionsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.permissionsRecyclerView.adapter = adapter

        binding.btnRequestPermissions.setOnClickListener { requestNextPending() }
        binding.tvExitApp.setOnClickListener { confirmExit() }

        updateButtonLabel()

        // Si ya todo está concedido (ej. segunda apertura), continúa directamente
        if (items.all { it.status == PermissionItem.Status.GRANTED }) {
            markAndFinish()
        }
    }

    // ── Construcción de la lista ──────────────────────────────────────────────

    private fun buildItems() {
        items.clear()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            items += PermissionItem(
                id = "notifications",
                iconRes = R.drawable.ic_notification,
                iconTintColorRes = R.color.chip_en_atencion,
                title = getString(R.string.perm_title_notifications),
                reason = getString(R.string.perm_reason_notifications)
            )
        }
        items += PermissionItem(
            id = "location",
            iconRes = R.drawable.ic_location,
            iconTintColorRes = R.color.success_green,
            title = getString(R.string.perm_title_location),
            reason = getString(R.string.perm_reason_location)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            items += PermissionItem(
                id = "background_location",
                iconRes = R.drawable.ic_menu_localizacion,
                iconTintColorRes = R.color.warning_yellow,
                title = getString(R.string.perm_title_background_location),
                reason = getString(R.string.perm_reason_background_location)
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            items += PermissionItem(
                id = "storage",
                iconRes = R.drawable.ic_save,
                iconTintColorRes = R.color.chip_asignada,
                title = getString(R.string.perm_title_storage),
                reason = getString(R.string.perm_reason_storage)
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            items += PermissionItem(
                id = "install_packages",
                iconRes = R.drawable.ic_cloud_download,
                iconTintColorRes = R.color.ice_blue,
                title = getString(R.string.perm_title_install_packages),
                reason = getString(R.string.perm_reason_install_packages)
            )
        }
    }

    // ── Comprobación del estado real del sistema ──────────────────────────────

    private fun isSystemGranted(id: String): Boolean = when (id) {
        "notifications" ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                hasPerm(Manifest.permission.POST_NOTIFICATIONS)
        "location" ->
            hasPerm(Manifest.permission.ACCESS_FINE_LOCATION) ||
                hasPerm(Manifest.permission.ACCESS_COARSE_LOCATION)
        "background_location" ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                hasPerm(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        "storage" ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                Environment.isExternalStorageManager()
        "install_packages" ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                packageManager.canRequestPackageInstalls()
        else -> true
    }

    private fun refreshStatuses() {
        for (item in items) {
            item.status = when {
                isSystemGranted(item.id) -> PermissionItem.Status.GRANTED
                item.id in requestedIds -> PermissionItem.Status.DENIED
                else -> PermissionItem.Status.PENDING
            }
        }
        if (::adapter.isInitialized) adapter.notifyDataSetChanged()
        updateButtonLabel()
    }

    // ── Flujo de solicitud de permisos ────────────────────────────────────────

    private fun requestNextPending() {
        if (items.all { it.status == PermissionItem.Status.GRANTED }) {
            markAndFinish()
            return
        }
        val next = items.firstOrNull { it.status != PermissionItem.Status.GRANTED } ?: return
        requestedIds += next.id
        when (next.id) {
            "notifications" ->
                runtimeLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            "location" ->
                runtimeLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            "background_location" ->
                backgroundLocLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            "storage" ->
                settingsLauncher.launch(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                )
            "install_packages" ->
                settingsLauncher.launch(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                )
        }
    }

    private fun advanceOrNotify() {
        if (items.all { it.status == PermissionItem.Status.GRANTED }) {
            markAndFinish()
        }
    }

    private fun markAndFinish() {
        lifecycleScope.launch {
            dataStore.setPermissionsValidated(true)
            finish()
        }
    }

    private fun updateButtonLabel() {
        val allGranted = items.all { it.status == PermissionItem.Status.GRANTED }
        binding.btnRequestPermissions.text =
            if (allGranted) getString(R.string.permissions_action_continue)
            else getString(R.string.permissions_action_grant_all)
    }

    // ── Confirmación de salida ────────────────────────────────────────────────

    private fun confirmExit() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permissions_exit_title)
            .setMessage(R.string.permissions_exit_message)
            .setCancelable(true)
            .setPositiveButton(R.string.permissions_exit_confirm) { _, _ -> finishAffinity() }
            .setNegativeButton(R.string.permissions_exit_cancel, null)
            .show()
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private fun hasPerm(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    // ── Adapter ───────────────────────────────────────────────────────────────

    inner class PermissionAdapter(
        private val data: List<PermissionItem>
    ) : RecyclerView.Adapter<PermissionAdapter.VH>() {

        inner class VH(val b: ItemPermissionCardBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemPermissionCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = data.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = data[position]
            val ctx = holder.b.root.context
            with(holder.b) {
                permissionTitle.text = item.title
                permissionReason.text = item.reason
                permissionIcon.setImageResource(item.iconRes)

                val accent = ContextCompat.getColor(ctx, item.iconTintColorRes)
                permissionIcon.imageTintList = ColorStateList.valueOf(accent)

                // Fondo circular con el color de acento al ~15% de opacidad
                val bgColor = Color.argb(
                    38,
                    Color.red(accent),
                    Color.green(accent),
                    Color.blue(accent)
                )
                iconContainer.backgroundTintList = ColorStateList.valueOf(bgColor)

                when (item.status) {
                    PermissionItem.Status.GRANTED -> {
                        statusIcon.setImageResource(R.drawable.ic_check)
                        statusIcon.imageTintList = ColorStateList.valueOf(
                            ContextCompat.getColor(ctx, R.color.success_green)
                        )
                        statusIcon.visibility = android.view.View.VISIBLE
                        permissionCard.strokeColor = ContextCompat.getColor(ctx, R.color.success_green)
                        permissionCard.strokeWidth = resources.getDimensionPixelSize(R.dimen.permission_card_stroke_granted)
                    }
                    PermissionItem.Status.DENIED -> {
                        statusIcon.setImageResource(R.drawable.ic_warning)
                        statusIcon.imageTintList = ColorStateList.valueOf(
                            ContextCompat.getColor(ctx, R.color.danger_red)
                        )
                        statusIcon.visibility = android.view.View.VISIBLE
                        permissionCard.strokeColor = ContextCompat.getColor(ctx, R.color.danger_red)
                        permissionCard.strokeWidth = resources.getDimensionPixelSize(R.dimen.permission_card_stroke_granted)
                    }
                    PermissionItem.Status.PENDING -> {
                        statusIcon.visibility = android.view.View.INVISIBLE
                        permissionCard.strokeColor = ContextCompat.getColor(ctx, R.color.outline_variant)
                        permissionCard.strokeWidth = resources.getDimensionPixelSize(R.dimen.permission_card_stroke_pending)
                    }
                }
            }
        }
    }
}
