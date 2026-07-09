package com.Arasoftsolutions.tecniapp_ice.ui.settings

import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.WorkManager
import androidx.navigation.fragment.findNavController
import com.Arasoftsolutions.tecniapp_ice.ActivityMain
import com.Arasoftsolutions.tecniapp_ice.LoginActivity
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.BuildConfig
import com.Arasoftsolutions.tecniapp_ice.Database.entities.apellidosCompletos
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.Database.sync.AppSyncCoordinator
import com.Arasoftsolutions.tecniapp_ice.Database.sync.Synchronizer
import com.Arasoftsolutions.tecniapp_ice.databinding.DialogNotificationFiltersBinding
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentSettingsBinding
import com.Arasoftsolutions.tecniapp_ice.notifications.SyncStatusNotifications
import com.Arasoftsolutions.tecniapp_ice.preferences.DataStoreManager
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriaNotificationPreferences
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriasSyncWorker
import com.Arasoftsolutions.tecniapp_ice.ui.modal.SyncDialogFragment
import com.Arasoftsolutions.tecniapp_ice.update.GithubUpdateChecker
import com.Arasoftsolutions.tecniapp_ice.update.UpdateCheckResult
import com.Arasoftsolutions.tecniapp_ice.update.UpdateDialog
import com.Arasoftsolutions.tecniapp_ice.update.UpdateDownloadManager
import com.Arasoftsolutions.tecniapp_ice.update.UpdateInfo
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private var notificationDialog: BottomSheetDialog? = null
    private var syncDialog: SyncDialogFragment? = null
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val dataStore by lazy { DataStoreManager.getInstance(requireContext()) }
    private val roomRepository by lazy { RoomRepository.getInstance(requireContext()) }
    private val synchronizer by lazy { Synchronizer(roomRepository) }
    private var availableNotificationAgencies: List<String> = emptyList()
    private var userRegionNameForNotifications: String? = null
    private val updateDownloadManager by lazy { UpdateDownloadManager(requireContext()) }
    private var manualSyncInProgress = false
    private var cacheClearInProgress = false
    private var updatingDarkTheme = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSettingsBinding.bind(view)

        binding.textVersion.text = getString(R.string.settings_version_placeholder, BuildConfig.VERSION_NAME)

        setupNotificationPreferences()
        setupSyncPreferences()
        setupAppearancePreferences()
        setupAdminPreferences()
        setupAccountSection()
        setupUpdateSection()
        setupLegalSection()
    }

    private fun setupNotificationPreferences() {
        val enabled = AveriaNotificationPreferences.areNotificationsEnabled(requireContext())
        binding.switchNotificaciones.isChecked = enabled
        updateNotificationSummary()

        viewLifecycleOwner.lifecycleScope.launch {
            dataStore.setNotificationsEnabled(enabled)
        }

        binding.switchNotificaciones.setOnCheckedChangeListener { _, isChecked ->
            AveriaNotificationPreferences.setNotificationsEnabled(requireContext(), isChecked)
            viewLifecycleOwner.lifecycleScope.launch {
                dataStore.setNotificationsEnabled(isChecked)
            }
            updateNotificationSummary()
        }

        binding.btnNotificationAgencies.setOnClickListener { showNotificationDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                dataStore.notificationsEnabled.collect { value ->
                    if (binding.switchNotificaciones.isChecked != value) {
                        binding.switchNotificaciones.isChecked = value
                    }
                    updateNotificationSummary()
                }
            }
        }

        // Cargar SOLO agencias de la región del usuario
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        fun loadRegionAgencies() {
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    val uid = auth.currentUser?.uid
                    val agenciasFlow = if (uid != null) {
                        roomRepository.observarUsuario(uid)
                            .flatMapLatest { user ->
                                userRegionNameForNotifications = user?.regionNombre?.takeIf { it.isNotBlank() }
                                val regionId = user?.region?.takeIf { it.isNotBlank() }
                                if (regionId != null) roomRepository.observarAgencias(regionId)
                                else roomRepository.observarAgenciasCatalogo()
                            }
                    } else {
                        roomRepository.observarAgenciasCatalogo()
                    }
                    agenciasFlow.collect { agencias ->
                        availableNotificationAgencies = agencias
                            .map { it.nombre.trim() }
                            .filter { it.isNotBlank() }
                            .distinctBy { it.lowercase(Locale.getDefault()) }
                            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
                    }
                }
            }
        }
        loadRegionAgencies()
    }

    private fun setupSyncPreferences() {
        setManualSyncInProgress(false)

        binding.btnSincronizarAhora.setOnClickListener { launchManualSync() }
        binding.btnClearCache.setOnClickListener { confirmClearCache() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                dataStore.lastManualSyncMillis.collect { timestamp ->
                    binding.textLastSync.text = if (timestamp == null) {
                        getString(R.string.settings_last_sync_never)
                    } else {
                        val formatted = DateFormat.getDateTimeInstance().format(Date(timestamp))
                        getString(R.string.settings_last_sync_format, formatted)
                    }
                }
            }
        }

        loadStorageInfo()
    }

    private fun loadStorageInfo() {
        binding.textStorageTotal.text = "Calculando…"
        binding.textStorageData.text = "—"
        binding.textStorageCache.text = "—"
        binding.progressStorage.isIndeterminate = true

        viewLifecycleOwner.lifecycleScope.launch {
            val (totalBytes, cacheBytes) = withContext(Dispatchers.IO) {
                val total = requireContext().dataDir.dirSize()
                val cache = requireContext().cacheDir.dirSize()
                Pair(total, cache)
            }
            val dataBytes = (totalBytes - cacheBytes).coerceAtLeast(0L)
            binding.textStorageTotal.text = totalBytes.toStorageMb()
            binding.textStorageData.text  = dataBytes.toStorageMb()
            binding.textStorageCache.text = cacheBytes.toStorageMb()
            binding.progressStorage.isIndeterminate = false
            val cachePercent = if (totalBytes > 0L) (cacheBytes * 100L / totalBytes).toInt() else 0
            binding.progressStorage.setProgressCompat(cachePercent.coerceIn(0, 100), true)
        }
    }

    private fun File.dirSize(): Long =
        if (!exists()) 0L
        else if (isFile) length()
        else walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun Long.toStorageMb(): String {
        val mb = toDouble() / (1024.0 * 1024.0)
        return when {
            mb < 0.01 -> "< 0.01 MB"
            mb < 1.0  -> String.format(Locale.getDefault(), "%.2f MB", mb)
            else      -> String.format(Locale.getDefault(), "%.1f MB", mb)
        }
    }

    private fun setupUpdateSection() {
        binding.btnCheckUpdates.setOnClickListener {
            binding.btnCheckUpdates.isEnabled = false
            Toast.makeText(requireContext(), R.string.update_checking, Toast.LENGTH_SHORT).show()
            viewLifecycleOwner.lifecycleScope.launch {
                val checker = GithubUpdateChecker(BuildConfig.UPDATE_JSON_URL)
                val result = checker.checkForUpdate(BuildConfig.VERSION_CODE)
                binding.btnCheckUpdates.isEnabled = true
                when (result) {
                    is UpdateCheckResult.UpdateAvailable -> {
                        dataStore.setPendingUpdateInfo(result.info)
                        showUpdateDialog(result.info)
                    }
                    UpdateCheckResult.UpToDate -> {
                        dataStore.setPendingUpdateInfo(null)
                        Toast.makeText(requireContext(), R.string.update_no_update, Toast.LENGTH_LONG).show()
                    }
                    is UpdateCheckResult.Error -> {
                        Toast.makeText(requireContext(), R.string.update_check_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun showUpdateDialog(info: UpdateInfo) {
        val manager = parentFragmentManager
        if (manager.findFragmentByTag(UPDATE_DIALOG_TAG) != null) return
        val dialog = UpdateDialog.newInstance(info).apply {
            onConfirmUpdate = { updateDownloadManager.startDownload(requireActivity(), it) }
        }
        dialog.show(manager, UPDATE_DIALOG_TAG)
    }

    private fun setupLegalSection() {
        binding.btnAbout.setOnClickListener {
            findNavController().navigate(R.id.nav_help)
        }
        binding.btnPrivacy.setOnClickListener {
            findNavController().navigate(R.id.nav_privacy)
        }
    }

    companion object {
        private const val UPDATE_DIALOG_TAG = "update_dialog"
    }


    private fun setupAppearancePreferences() {
        // Usa lifecycleScope (no viewLifecycleOwner) para que el save en DataStore complete
        // antes de que setDefaultNightMode cause la recreación de la Activity.
        binding.switchDarkTheme.setOnCheckedChangeListener { _, isChecked ->
            if (updatingDarkTheme) return@setOnCheckedChangeListener
            lifecycleScope.launch {
                dataStore.setDarkThemeEnabled(isChecked)
                val mode = if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                AppCompatDelegate.setDefaultNightMode(mode)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                dataStore.darkThemeEnabled.collect { enabled ->
                    updatingDarkTheme = true
                    if (binding.switchDarkTheme.isChecked != enabled) {
                        binding.switchDarkTheme.isChecked = enabled
                    }
                    updatingDarkTheme = false
                }
            }
        }
    }

    private fun setupAdminPreferences() {
        binding.groupAdminPrivileges.isVisible = false
        viewLifecycleOwner.lifecycleScope.launch {
            val user = withContext(Dispatchers.IO) {
                auth.currentUser?.uid?.let { uid ->
                    RoomRepository.getInstance(requireContext()).obtenerUsuario(uid)
                }
            }
            val rol = user?.rol?.trim()?.lowercase(Locale.getDefault())
            val isAdmin = rol == "administrador"
            binding.groupAdminPrivileges.isVisible = isAdmin
            if (!isAdmin) return@launch

            binding.switchAdminPrivileges.setOnCheckedChangeListener { _, isChecked ->
                viewLifecycleOwner.lifecycleScope.launch {
                    dataStore.setAdminPrivilegesEnabled(isChecked)
                }
                updateAdminPrivilegesLabel(isChecked)
            }

            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    dataStore.adminPrivilegesEnabled.collect { enabled ->
                        if (binding.switchAdminPrivileges.isChecked != enabled) {
                            binding.switchAdminPrivileges.isChecked = enabled
                        }
                        updateAdminPrivilegesLabel(enabled)
                    }
                }
            }
        }
    }

    private fun setupAccountSection() {
        binding.cardAccount.setOnClickListener {
            findNavController().navigate(R.id.nav_account)
        }
        binding.btnOpenProfile.setOnClickListener {
            findNavController().navigate(R.id.nav_account)
        }

        binding.btnCerrarSesion.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_sign_out_title)
                .setMessage(R.string.settings_sign_out_message)
                .setPositiveButton(R.string.settings_sign_out_confirm) { _, _ -> signOut() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val user = withContext(Dispatchers.IO) {
                auth.currentUser?.uid?.let { uid ->
                    RoomRepository.getInstance(requireContext()).obtenerUsuario(uid)
                }
            }

            val name = buildList {
                user?.nombre?.takeIf { it.isNotBlank() }?.let { add(it) }
                user?.apellidosCompletos?.takeIf { it.isNotBlank() }?.let { add(it) }
            }.joinToString(" ").ifBlank {
                user?.email ?: getString(R.string.profile_default_name)
            }

            binding.textAccountName.text = name
            binding.textAccountEmail.text = user?.email ?: auth.currentUser?.email
                ?: getString(R.string.settings_account_email_placeholder)
            binding.textAccountAgency.text = user?.agencia?.takeIf { it.isNotBlank() }
                ?: getString(R.string.settings_account_agency_placeholder)
        }
    }

    private fun updateNotificationSummary() {
        val agencies = AveriaNotificationPreferences.getSelectedAgencies(requireContext())
        binding.textNotificationSummary.text = if (agencies.isEmpty()) {
            getString(R.string.settings_notifications_summary_all)
        } else {
            getString(R.string.settings_notifications_summary_filtered, agencies.joinToString(", "))
        }
    }

    private fun showNotificationDialog() {
        val dialogBinding = DialogNotificationFiltersBinding.inflate(layoutInflater)
        dialogBinding.switchNotifications.isChecked = binding.switchNotificaciones.isChecked

        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(dialogBinding.root)

        // Etiqueta de región
        val regionLabel = userRegionNameForNotifications
            ?: getString(R.string.averia_notificacion_todas_regiones)
        dialogBinding.tvUserRegionLabel.text =
            getString(R.string.averia_notificacion_region_label, regionLabel)

        // Construir chips toggle con las agencias de la región del usuario
        fun buildToggleChips() {
            val selected = AveriaNotificationPreferences.getSelectedAgencies(requireContext())
                .map { it.lowercase(Locale.getDefault()) }.toSet()
            val group = dialogBinding.chipGroupRegionAgencias
            group.removeAllViews()
            val agencies = availableNotificationAgencies
            if (agencies.isEmpty()) {
                dialogBinding.tvNotificationFiltersEmpty.isVisible = true
                return
            }
            dialogBinding.tvNotificationFiltersEmpty.isVisible = false
            agencies.forEach { agency ->
                val chip = Chip(requireContext()).apply {
                    text = agency
                    isCheckable = true
                    isChecked = selected.contains(agency.lowercase(Locale.getDefault()))
                }
                group.addView(chip)
            }
        }

        dialogBinding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            binding.switchNotificaciones.isChecked = isChecked
            AveriaNotificationPreferences.setNotificationsEnabled(requireContext(), isChecked)
            viewLifecycleOwner.lifecycleScope.launch {
                dataStore.setNotificationsEnabled(isChecked)
            }
            val alpha = if (isChecked) 1f else 0.5f
            dialogBinding.chipGroupRegionAgencias.alpha = alpha
            val group = dialogBinding.chipGroupRegionAgencias
            for (i in 0 until group.childCount) {
                (group.getChildAt(i) as? Chip)?.isEnabled = isChecked
            }
            updateNotificationSummary()
        }

        dialogBinding.btnGuardarNotificaciones.setOnClickListener {
            val selected = mutableSetOf<String>()
            val group = dialogBinding.chipGroupRegionAgencias
            for (i in 0 until group.childCount) {
                val chip = group.getChildAt(i) as? Chip ?: continue
                if (chip.isChecked) selected.add(chip.text.toString())
            }
            AveriaNotificationPreferences.setSelectedAgencies(requireContext(), selected)
            updateNotificationSummary()
            Toast.makeText(requireContext(), R.string.settings_notifications_saved, Toast.LENGTH_SHORT)
                .show()
            dialog.dismiss()
        }

        dialog.setOnDismissListener { notificationDialog = null }

        notificationDialog = dialog
        buildToggleChips()
        dialog.show()
    }

    private fun updateAdminPrivilegesLabel(enabled: Boolean) {
        binding.switchAdminPrivileges.text = if (enabled) {
            getString(R.string.settings_admin_privileges_disable)
        } else {
            getString(R.string.settings_admin_privileges_enable)
        }
        binding.textAdminPrivilegesSummary.text = getString(R.string.settings_admin_privileges_summary)
    }

    private fun launchManualSync() {
        if (manualSyncInProgress || cacheClearInProgress) return
        setManualSyncInProgress(true)
        val dialog = showSyncDialog(
            getString(R.string.settings_sync_in_progress_title),
            getString(R.string.settings_sync_in_progress_message)
        )
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val uid = requireActiveUser()
                val subregion = withContext(Dispatchers.IO) {
                    roomRepository.obtenerUsuario(uid)?.subregion?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: roomRepository.upsertUserFromFirebase(uid).subregion?.trim()
                            ?.takeIf { it.isNotEmpty() }
                } ?: throw IllegalStateException("Subregión no disponible")

                val executed = AppSyncCoordinator.runExclusiveDebounced(
                    key = "manual_sync_subregion",
                    minIntervalMs = 20_000L
                ) {
                    synchronizer.syncSubregion(
                        subregion,
                        onSyncStart = { message ->
                            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                                if (isAdded) dialog.setHeader(message)
                            }
                        },
                        onSyncProgress = { done, total, msg, downloadedBytes ->
                            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                                val safeContext = context ?: return@launch
                                if (isAdded) {
                                    dialog.update(done, total, msg ?: "", downloadedBytes)
                                    SyncStatusNotifications.notifyProgress(
                                        safeContext,
                                        done,
                                        total,
                                        msg
                                    )
                                }
                            }
                        },
                        onSyncSuccess = {
                            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                                val safeContext = context ?: return@launch
                                SyncStatusNotifications.dismissProgress(safeContext)
                                AveriasSyncWorker.triggerNow(safeContext)
                                viewLifecycleOwner.lifecycleScope.launch {
                                    dataStore.markManualSyncNow()
                                }
                                viewLifecycleOwner.lifecycleScope.launch {
                                    withContext(Dispatchers.IO) {
                                        roomRepository.refreshUsuarioActual()
                                    }
                                }
                                SyncStatusNotifications.notifySynced(safeContext)
                                dismissSyncDialog()
                                Toast.makeText(
                                    safeContext,
                                    R.string.settings_sync_triggered,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onSyncError = { error ->
                            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                                val safeContext = context ?: return@launch
                                SyncStatusNotifications.dismissProgress(safeContext)
                                dismissSyncDialog()
                                Toast.makeText(
                                    safeContext,
                                    error.message ?: getString(R.string.settings_clear_cache_failure),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    )
                }

                if (executed == null) {
                    dismissSyncDialog()
                    context?.let {
                        Toast.makeText(
                            it,
                            "Ya hay una sincronización manual en curso o fue ejecutada recientemente",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (_: Exception) {
                dismissSyncDialog()
                context?.let {
                    Toast.makeText(it, R.string.settings_clear_cache_failure, Toast.LENGTH_LONG).show()
                }
            } finally {
                setManualSyncInProgress(false)
            }
        }
    }

    private fun confirmClearCache() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_clear_cache_title)
            .setMessage(R.string.settings_clear_cache_message)
            .setPositiveButton(R.string.settings_clear_cache_confirm) { _, _ -> clearCacheAndResync() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun clearCacheAndResync() {
        setCacheClearInProgress(true)
        val dialog = showSyncDialog(
            getString(R.string.settings_clear_cache_title),
            getString(R.string.settings_clear_cache_message_in_progress)
        )
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val uid = requireActiveUser()
                // Pasos extra propios de este flujo "limpiar caché y resincronizar":
                // 1) limpiar base local · 2) catálogos generales · 3) perfil de usuario.
                val cacheResyncExtraSteps = 3
                val totalSteps = RoomRepository.SUBREGION_SYNC_STEPS + cacheResyncExtraSteps
                val total = totalSteps * 100
                var done = 0
                var downloadedBytes = 0L
                if (isAdded) {
                    dialog.update(done, total, getString(R.string.settings_clear_cache_step_clear), downloadedBytes)
                }
                withContext(Dispatchers.IO) {
                    val workManager = WorkManager.getInstance(requireContext())
                    workManager.cancelUniqueWork(AveriasSyncWorker.UNIQUE_MANUAL_WORK)
                    workManager.cancelUniqueWork(AveriasSyncWorker.UNIQUE_PERIODIC_WORK)
                    workManager.cancelUniqueWork("vehiculo_sync_now")
                    roomRepository.stopRealtimeSync()
                    roomRepository.limpiarBaseLocal()
                }
                if (isAdded) {
                    done += 100
                    dialog.update(done, total, getString(R.string.settings_clear_cache_step_catalogs), downloadedBytes)
                }
                withContext(Dispatchers.IO) {
                    downloadedBytes += roomRepository.syncCatalogosGenerales()
                }
                if (isAdded) {
                    done += 100
                    dialog.update(done, total, getString(R.string.settings_clear_cache_step_profile), downloadedBytes)
                }
                val subregion = withContext(Dispatchers.IO) {
                    roomRepository.upsertUserFromFirebase(uid).subregion?.trim()
                        ?.takeIf { it.isNotEmpty() }
                }
                if (subregion != null) {
                    val baseBytes = downloadedBytes
                    withContext(Dispatchers.IO) {
                        roomRepository.syncSubregion(subregion) { subDone, subTotal, msg, bytes ->
                            downloadedBytes = baseBytes + bytes
                            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                                if (isAdded) {
                                    val adjustedDone = done + subDone
                                    val adjustedTotal = (done + subTotal).coerceAtLeast(total)
                                    dialog.update(adjustedDone, adjustedTotal, msg ?: "", downloadedBytes)
                                    SyncStatusNotifications.notifyProgress(
                                        requireContext(),
                                        adjustedDone,
                                        adjustedTotal,
                                        msg
                                    )
                                }
                            }
                        }
                    }
                }
                AveriasSyncWorker.triggerNow(requireContext())
                dataStore.markManualSyncNow()
                SyncStatusNotifications.dismissProgress(requireContext())
                SyncStatusNotifications.notifySynced(requireContext())
                Toast.makeText(requireContext(), R.string.settings_clear_cache_success, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                SyncStatusNotifications.dismissProgress(requireContext())
                Toast.makeText(requireContext(), R.string.settings_clear_cache_failure, Toast.LENGTH_LONG).show()
            } finally {
                withContext(Dispatchers.IO) {
                    val uid = auth.currentUser?.uid
                    if (uid != null) {
                        val scope = roomRepository.buildUserScope(uid)
                        roomRepository.startRealtimeSyncForScope(scope)
                    }
                }
                dismissSyncDialog()
                setCacheClearInProgress(false)
            }
        }
    }

    private suspend fun requireActiveUser(): String {
        val currentUser = auth.currentUser ?: throw IllegalStateException("Sesión no disponible")
        val reloadResult = runCatching { currentUser.reload().await() }
        if (reloadResult.exceptionOrNull() is FirebaseAuthInvalidUserException) {
            if (isAdded) {
                Toast.makeText(requireContext(), R.string.settings_session_expired, Toast.LENGTH_LONG).show()
                signOut()
            }
            throw IllegalStateException("Usuario deshabilitado")
        }
        return currentUser.uid
    }

    private fun signOut() {
        auth.signOut()

        requireContext().getSharedPreferences("TecniAppPrefs", MODE_PRIVATE).edit().apply {
            clear()
            apply()
        }
        requireContext().getSharedPreferences("app_preferences", MODE_PRIVATE).edit().apply {
            clear()
            apply()
        }

        val activity = activity
        if (activity is ActivityMain) {
            activity.finish()
        }

        startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        notificationDialog?.dismiss()
        notificationDialog = null
        dismissSyncDialog()
        _binding = null
    }

    private fun dismissSyncDialog() {
        syncDialog?.dismissAllowingStateLoss()
        syncDialog = null
        setManualSyncInProgress(false)
        setCacheClearInProgress(false)
    }

    private fun setManualSyncInProgress(inProgress: Boolean) {
        if (_binding == null) return
        manualSyncInProgress = inProgress
        refreshSyncButtons()
    }

    private fun setCacheClearInProgress(inProgress: Boolean) {
        if (_binding == null) return
        cacheClearInProgress = inProgress
        refreshSyncButtons()
    }

    private fun refreshSyncButtons() {
        if (_binding == null) return
        val manualSyncEnabled = !manualSyncInProgress && !cacheClearInProgress
        binding.btnSincronizarAhora.isEnabled = manualSyncEnabled
        binding.btnSincronizarAhora.alpha = if (manualSyncEnabled) 1f else 0.6f
        binding.btnSincronizarAhora.text = if (manualSyncInProgress) {
            getString(R.string.settings_sync_in_progress_button)
        } else {
            getString(R.string.settings_sync_now)
        }
        val clearEnabled = !cacheClearInProgress
        binding.btnClearCache.isEnabled = clearEnabled
        binding.btnClearCache.alpha = if (clearEnabled) 1f else 0.6f
        binding.btnClearCache.text = if (cacheClearInProgress) {
            getString(R.string.settings_clear_cache_in_progress)
        } else {
            getString(R.string.settings_clear_cache)
        }
    }

    private fun showSyncDialog(header: String, status: String): SyncDialogFragment {
        syncDialog?.let { return it }
        val dialog = SyncDialogFragment.newInstance(header, status)
        dialog.isCancelable = false
        dialog.show(parentFragmentManager, "sync_dialog_settings")
        syncDialog = dialog
        return dialog
    }
}
