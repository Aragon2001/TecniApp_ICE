package com.Arasoftsolutions.tecniapp_ice.ui.home

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.Database.entities.UserEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.apellidosCompletos
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriasSyncWorker
import com.Arasoftsolutions.tecniapp_ice.ui.modal.SyncDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class HomeFragment : Fragment(R.layout.fragment_home) {

    private val vm: HomeViewModel by viewModels()

    private val locale = Locale("es", "CR")
    private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", locale)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", locale)

    private var syncButton: MaterialButton? = null
    private var syncStatusText: TextView? = null
    private var syncProgressIndicator: LinearProgressIndicator? = null
    private var syncProgressLabel: TextView? = null
    private var lastSyncValue: TextView? = null
    private var mapStatusText: TextView? = null
    private var statusIndicator: View? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallbackRegistered = false
    private var syncDialog: SyncDialogFragment? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val greetingText: TextView = view.findViewById(R.id.text_greeting)
        val statusText: TextView = view.findViewById(R.id.text_status)
        val assignmentText: TextView = view.findViewById(R.id.text_assignment)
        val dateText: TextView = view.findViewById(R.id.text_date)
        val lastSyncSummary: TextView = view.findViewById(R.id.text_last_sync_value)
        val mapStatus: TextView = view.findViewById(R.id.text_map_status)
        val syncStatus: TextView = view.findViewById(R.id.text_sync_status)
        val syncProgress: LinearProgressIndicator = view.findViewById(R.id.progress_sync)
        val syncProgressText: TextView = view.findViewById(R.id.text_sync_progress)
        val syncActionButton: MaterialButton = view.findViewById(R.id.button_sync_now)
        val pendingCount: TextView = view.findViewById(R.id.text_pending_count)
        val attendedCount: TextView = view.findViewById(R.id.text_attended_count)
        val kilometrajeValue: TextView = view.findViewById(R.id.text_kilometraje)
        val mapCard: View = view.findViewById(R.id.card_map)
        val actionAverias: View = view.findViewById(R.id.action_averias)
        val actionMedidor: View = view.findViewById(R.id.action_medidor)
        val actionReportes: View = view.findViewById(R.id.action_reportes)
        val actionSettings: View = view.findViewById(R.id.action_settings)
        statusIndicator = view.findViewById(R.id.view_status_indicator)

        syncButton = syncActionButton
        syncStatusText = syncStatus
        syncProgressIndicator = syncProgress
        syncProgressLabel = syncProgressText
        lastSyncValue = lastSyncSummary
        mapStatusText = mapStatus
        statusText.text = getString(R.string.home_status_offline)
        dateText.text = formatCurrentDate()

        syncActionButton.isEnabled = false
        syncActionButton.setOnClickListener { sincronizarConModal() }
        mapCard.setOnClickListener { findNavController().navigate(R.id.nav_localizacion) }
        actionAverias.setOnClickListener { findNavController().navigate(R.id.nav_averias) }
        actionMedidor.setOnClickListener { findNavController().navigate(R.id.nav_medidor) }
        actionReportes.setOnClickListener { findNavController().navigate(R.id.nav_reportes) }
        actionSettings.setOnClickListener { findNavController().navigate(R.id.nav_settings) }

        connectivityManager = requireContext()
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        registerNetworkCallback(statusText)
        updateNetworkStatus(statusText)

        vm.loadUsuarioActual()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    while (true) {
                        dateText.text = formatCurrentDate()
                        delay(1_000)
                    }
                }
                launch {
                    vm.usuario.collect { usuario ->
                        greetingText.text = getString(
                            R.string.home_greeting_format,
                            formatDisplayName(usuario)
                        )
                        assignmentText.text = formatAssignment(usuario)
                        syncActionButton.isEnabled =
                            usuario?.subregion?.isNullOrBlank() == false
                    }
                }
                launch {
                    vm.averiasAsignadasCount.collect { count ->
                        pendingCount.text = count.toString()
                    }
                }
                launch {
                    vm.averiasResueltasHoyCount.collect { count ->
                        attendedCount.text = count.toString()
                    }
                }
                launch {
                    vm.kilometrosInicialesHoy.collect { kms ->
                        kilometrajeValue.text = if (kms <= 0.0) {
                            getString(R.string.home_cards_placeholder)
                        } else {
                            getString(R.string.home_card_kilometraje_value, kms)
                        }
                    }
                }
                launch {
                    vm.lastManualSync.collect { timestamp ->
                        val relative = formatRelativeSync(timestamp)
                        lastSyncSummary.text = relative
                        mapStatus.text = buildString {
                            appendLine(getString(R.string.home_map_description))
                            append(getString(R.string.home_map_last_sync, relative))
                        }
                    }
                }
            }
        }


        observeManualSync()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        unregisterNetworkCallback()
        syncButton = null
        syncStatusText = null
        syncProgressIndicator = null
        syncProgressLabel = null
        lastSyncValue = null
        mapStatusText = null
        statusIndicator = null
        syncDialog = null
        connectivityManager = null
    }

    private fun sincronizarConModal() {
        val usuarioActual = vm.usuario.value
        if (usuarioActual?.subregion.isNullOrBlank()) {
            syncStatusText?.text = getString(
                R.string.home_sync_status_error,
                getString(R.string.home_header_assignment_placeholder)
            )
            return
        }

        syncDialog = SyncDialogFragment.show(childFragmentManager).apply {
            setHeader(getString(R.string.home_sync_dialog_title))
            update(0, 0, getString(R.string.home_sync_status_running))
        }

        syncButton?.isEnabled = false
        syncStatusText?.text = getString(R.string.home_sync_status_running)
        syncProgressIndicator?.apply {
            visibility = View.VISIBLE
            isIndeterminate = true
            setProgressCompat(0, false)
        }
        syncProgressLabel?.apply {
            visibility = View.VISIBLE
            text = getString(R.string.home_sync_status_running)
        }

        vm.triggerManualSync()
    }

    private fun formatDisplayName(user: UserEntity?): String {
        if (user == null) return getString(R.string.home_header_default_user)
        val full = listOfNotNull(user.nombre, user.apellidosCompletos)
            .joinToString(" ")
            .trim()
        if (full.isNotBlank()) return full
        val email = user.email?.takeIf { it.isNotBlank() }
        return email ?: user.uid.ifBlank { getString(R.string.home_header_default_user) }
    }

    private fun formatAssignment(user: UserEntity?): String {
        if (user == null) return getString(R.string.home_header_assignment_placeholder)
        val parts = buildList {
            val subregion = user.subregionNombre?.takeIf { it.isNotBlank() }
                ?: user.subregion?.takeIf { it.isNotBlank() }
            val agency = user.agencia?.takeIf { it.isNotBlank() }
            val vehicle = user.placaVehiculo?.takeIf { it.isNotBlank() }
            if (subregion != null) add(subregion)
            if (agency != null) add(agency)
            if (vehicle != null) add("ICE $vehicle")
        }
        return if (parts.isEmpty()) {
            getString(R.string.home_header_assignment_placeholder)
        } else {
            parts.joinToString(" • ")
        }
    }

    private fun formatCurrentDate(): String {
        val now = ZonedDateTime.now()
        val date = dateFormatter.format(now).replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(locale) else char.toString()
        }
        val time = timeFormatter.format(now)
        return getString(R.string.home_header_date_format, date, time)
    }

    private fun updateNetworkStatus(statusText: TextView) {
        val connected = isConnected()
        if (connected) {
            statusText.text = getString(R.string.home_status_online)
            statusIndicator?.setBackgroundResource(R.drawable.bg_home_status_online)
        } else {
            statusText.text = getString(R.string.home_status_offline)
            statusIndicator?.setBackgroundResource(R.drawable.bg_home_status_offline)
        }
    }

    private fun registerNetworkCallback(statusText: TextView) {
        if (networkCallbackRegistered || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            updateNetworkStatus(statusText)
            return
        }
        val cm = connectivityManager ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                if (!isAdded) return
                statusText.post { updateNetworkStatus(statusText) }
            }

            override fun onLost(network: android.net.Network) {
                if (!isAdded) return
                statusText.post { updateNetworkStatus(statusText) }
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }
            .onSuccess { networkCallbackRegistered = true }
        if (networkCallbackRegistered) {
            networkCallback = callback
        }
    }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private fun unregisterNetworkCallback() {
        val cm = connectivityManager
        val callback = networkCallback
        if (cm != null && callback != null && networkCallbackRegistered) {
            runCatching { cm.unregisterNetworkCallback(callback) }
        }
        networkCallbackRegistered = false
        networkCallback = null
    }

    private fun isConnected(): Boolean {
        val cm = connectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun observeManualSync() {
        val manager = WorkManager.getInstance(requireContext())
        manager.getWorkInfosForUniqueWorkLiveData(AveriasSyncWorker.UNIQUE_MANUAL_WORK)
            .observe(viewLifecycleOwner) { infos ->
                val info = infos.firstOrNull()
                when {
                    info == null -> {
                        syncButton?.isEnabled = vm.usuario.value?.subregion?.isNullOrBlank() == false
                        syncProgressIndicator?.visibility = View.GONE
                        syncProgressLabel?.visibility = View.GONE
                        val current = syncStatusText?.text?.toString().orEmpty()
                        if (current.isBlank() ||
                            current == getString(R.string.home_sync_status_running) ||
                            current == getString(R.string.home_sync_status_idle)
                        ) {
                            syncStatusText?.text = getString(R.string.home_sync_status_idle)
                        }
                        syncDialog?.let { dialog ->
                            runCatching { dialog.dismissAllowingStateLoss() }
                        }
                        syncDialog = null
                    }

                    info.state == WorkInfo.State.RUNNING ||
                        info.state == WorkInfo.State.ENQUEUED ||
                        info.state == WorkInfo.State.BLOCKED -> {
                        syncButton?.isEnabled = false
                        syncStatusText?.text = getString(R.string.home_sync_status_running)
                        syncProgressIndicator?.apply {
                            visibility = View.VISIBLE
                            isIndeterminate = true
                        }
                        syncProgressLabel?.apply {
                            visibility = View.VISIBLE
                            text = getString(R.string.home_sync_status_running)
                        }
                    }

                    info.state.isFinished -> {
                        syncButton?.isEnabled = vm.usuario.value?.subregion?.isNullOrBlank() == false
                        syncProgressIndicator?.visibility = View.GONE
                        syncProgressLabel?.visibility = View.GONE
                        if (info.state == WorkInfo.State.SUCCEEDED) {
                            syncStatusText?.text = getString(R.string.home_sync_status_success)
                            val relative = formatRelativeSync(vm.lastManualSync.value)
                            lastSyncValue?.text = relative
                            mapStatusText?.text = buildString {
                                appendLine(getString(R.string.home_map_description))
                                append(getString(R.string.home_map_last_sync, relative))
                            }
                            syncDialog?.let { dialog ->
                                runCatching { dialog.dismissAllowingStateLoss() }
                            }
                        } else {
                            val message = info.outputData.getString("error")
                                ?: getString(R.string.home_sync_unknown_error)
                            syncStatusText?.text = getString(
                                R.string.home_sync_status_error,
                                message
                            )
                            syncDialog?.dismissWithError(message) {
                                if (isAdded) sincronizarConModal()
                            }
                        }
                        syncDialog = null
                    }
                }
            }
    }

    private fun formatRelativeSync(timestamp: Long?): String {
        if (timestamp == null || timestamp <= 0) {
            return getString(R.string.home_last_sync_never)
        }
        val duration = Duration.between(Instant.ofEpochMilli(timestamp), Instant.now())
        val minutes = duration.toMinutes()
        return when {
            minutes < 1 -> getString(R.string.home_last_sync_just_now)
            minutes < 60 -> getString(R.string.home_last_sync_minutes, minutes.toInt())
            minutes < 60 * 24 -> getString(
                R.string.home_last_sync_hours,
                duration.toHours().toInt()
            )
            else -> getString(R.string.home_last_sync_days, duration.toDays().toInt())
        }
    }

    private fun updateSyncProgress(done: Int, total: Int, message: String?) {
        val indicator = syncProgressIndicator ?: return
        val label = syncProgressLabel ?: return
        if (total <= 0) {
            indicator.isIndeterminate = true
            indicator.setProgressCompat(0, false)
            label.text = message ?: getString(R.string.home_sync_status_running)
            return
        }
        indicator.isIndeterminate = false
        val clampedDone = done.coerceIn(0, total)
        val progress = (clampedDone * 100f / total).toInt()
        indicator.setProgressCompat(progress, true)
        label.text = getString(
            R.string.home_sync_progress_format,
            clampedDone,
            total,
            message ?: getString(R.string.home_sync_status_running)
        )
    }

    private fun resolveErrorMessage(throwable: Throwable?): String {
        val detail = throwable?.localizedMessage?.takeIf { !it.isNullOrBlank() }
            ?: throwable?.javaClass?.simpleName
            ?: getString(R.string.home_sync_unknown_error)
        return detail
    }


    }

