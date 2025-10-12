package com.Arasoftsolutions.tecniapp_ice.ui.settings

import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.WorkManager
import com.Arasoftsolutions.tecniapp_ice.ActivityMain
import com.Arasoftsolutions.tecniapp_ice.LoginActivity
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.BuildConfig
import com.Arasoftsolutions.tecniapp_ice.Database.entities.apellidosCompletos
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.databinding.DialogNotificationFiltersBinding
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentSettingsBinding
import com.Arasoftsolutions.tecniapp_ice.preferences.DataStoreManager
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriaNotificationPreferences
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriasSyncWorker
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private var notificationDialog: BottomSheetDialog? = null
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val dataStore by lazy { DataStoreManager.getInstance(requireContext()) }
    private val roomRepository by lazy { RoomRepository.getInstance(requireContext()) }
    private var availableNotificationAgencies: List<String> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSettingsBinding.bind(view)

        binding.textVersion.text = getString(R.string.settings_version_placeholder, BuildConfig.VERSION_NAME)

        setupNotificationPreferences()
        setupSyncPreferences()
        setupLocationPreferences()
        setupAppearancePreferences()
        setupAccountSection()
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
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                roomRepository.observarAgenciasCatalogo().collect { agencias ->
                    availableNotificationAgencies = agencias.mapNotNull { it.nombre?.takeIf { nombre ->
                        nombre.isNotBlank()
                    }?.trim() }
                        .distinctBy { it.lowercase(Locale.getDefault()) }
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
                }
            }
        }
    }

    private fun setupSyncPreferences() {
        binding.switchAutoSync.setOnCheckedChangeListener { _, isChecked ->
            viewLifecycleOwner.lifecycleScope.launch {
                dataStore.setAutoSyncEnabled(isChecked)
            }
            if (isChecked) {
                AveriasSyncWorker.schedule(requireContext())
            } else {
                WorkManager.getInstance(requireContext()).cancelUniqueWork("averias_sync")
            }
        }

        binding.btnSincronizarAhora.setOnClickListener {
            AveriasSyncWorker.triggerNow(requireContext())
            viewLifecycleOwner.lifecycleScope.launch {
                dataStore.markManualSyncNow()
            }
            Toast.makeText(requireContext(), R.string.settings_sync_triggered, Toast.LENGTH_SHORT).show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    dataStore.autoSyncEnabled.collect { enabled ->
                        if (binding.switchAutoSync.isChecked != enabled) {
                            binding.switchAutoSync.isChecked = enabled
                        }
                    }
                }

                launch {
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
        }
    }

    private fun setupLocationPreferences() {
        binding.switchGps.setOnCheckedChangeListener { _, isChecked ->
            viewLifecycleOwner.lifecycleScope.launch {
                dataStore.setGpsEnabled(isChecked)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                dataStore.gpsEnabled.collect { enabled ->
                    if (binding.switchGps.isChecked != enabled) {
                        binding.switchGps.isChecked = enabled
                    }
                }
            }
        }
    }

    private fun setupAppearancePreferences() {
        binding.switchDarkTheme.setOnCheckedChangeListener { _, isChecked ->
            viewLifecycleOwner.lifecycleScope.launch {
                dataStore.setDarkThemeEnabled(isChecked)
            }
            val mode = if (isChecked) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
            AppCompatDelegate.setDefaultNightMode(mode)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                dataStore.darkThemeEnabled.collect { enabled ->
                    if (binding.switchDarkTheme.isChecked != enabled) {
                        binding.switchDarkTheme.isChecked = enabled
                    }
                }
            }
        }
    }

    private fun setupAccountSection() {
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

        fun updateNotificationList() {
            val agencies = AveriaNotificationPreferences.getSelectedAgencies(requireContext())
            dialogBinding.tvNotificationFiltersEmpty.isVisible = agencies.isEmpty()
            dialogBinding.chipGroupNotificationAgencies.removeAllViews()
            agencies.forEach { agency ->
                val chip = Chip(requireContext()).apply {
                    text = agency
                    isCloseIconVisible = true
                    setOnCloseIconClickListener {
                        AveriaNotificationPreferences.removeAgency(requireContext(), agency)
                        updateNotificationSummary()
                        updateNotificationList()
                    }
                }
                dialogBinding.chipGroupNotificationAgencies.addView(chip)
            }
            val suggestions = (availableNotificationAgencies + agencies)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase(Locale.getDefault()) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, suggestions)
            dialogBinding.actvNotificationAgency.setAdapter(adapter)
        }

        dialogBinding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            binding.switchNotificaciones.isChecked = isChecked
            AveriaNotificationPreferences.setNotificationsEnabled(requireContext(), isChecked)
            viewLifecycleOwner.lifecycleScope.launch {
                dataStore.setNotificationsEnabled(isChecked)
            }
            updateNotificationSummary()
        }

        fun addAgency(value: String) {
            if (value.isBlank()) return
            AveriaNotificationPreferences.addAgency(requireContext(), value)
            dialogBinding.actvNotificationAgency.setText("", false)
            updateNotificationSummary()
            updateNotificationList()
        }

        dialogBinding.actvNotificationAgency.setOnItemClickListener { parent, _, position, _ ->
            val value = parent.getItemAtPosition(position)?.toString()?.trim().orEmpty()
            addAgency(value)
        }

        dialogBinding.actvNotificationAgency.setOnEditorActionListener { textView, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val value = textView.text?.toString()?.trim().orEmpty()
                addAgency(value)
                true
            } else {
                false
            }
        }

        dialog.setOnDismissListener {
            dialogBinding.actvNotificationAgency.setOnItemClickListener(null)
            dialogBinding.actvNotificationAgency.setOnEditorActionListener(null)
        }

        notificationDialog = dialog
        updateNotificationList()
        dialog.show()
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
        _binding = null
    }
}

