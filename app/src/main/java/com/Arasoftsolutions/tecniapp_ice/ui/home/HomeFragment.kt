package com.Arasoftsolutions.tecniapp_ice.ui.home

import android.app.AlertDialog
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.Database.entities.UserEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.apellidosCompletos
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriasFragment
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriasSyncWorker
import com.Arasoftsolutions.tecniapp_ice.ui.averias.Estado
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
    private var statusIndicator: View? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallbackRegistered = false
    private var syncDialog: SyncDialogFragment? = null
    private var offlineDialog: AlertDialog? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val greetingText: TextView = view.findViewById(R.id.text_greeting)
        val statusText: TextView = view.findViewById(R.id.text_status)
        val assignmentText: TextView = view.findViewById(R.id.text_assignment)
        val dateText: TextView = view.findViewById(R.id.text_date)
        val lastSyncSummary: TextView = view.findViewById(R.id.text_last_sync_value)
        val syncStatus: TextView = view.findViewById(R.id.text_sync_status)
        val syncProgress: LinearProgressIndicator = view.findViewById(R.id.progress_sync)
        val syncProgressText: TextView = view.findViewById(R.id.text_sync_progress)
        val syncActionButton: MaterialButton = view.findViewById(R.id.button_sync_now)
        val pendingCount: TextView = view.findViewById(R.id.text_pending_count)
        val attendedCount: TextView = view.findViewById(R.id.text_attended_count)
        val kilometrajeValue: TextView = view.findViewById(R.id.text_kilometraje)
        val statsPendingAverias: TextView = view.findViewById(R.id.text_stats_pending_averias)
        val statsDamagedLights: TextView = view.findViewById(R.id.text_stats_damaged_lights)
        val statsScheduledVisits: TextView = view.findViewById(R.id.text_stats_scheduled_visits)
        val cardPending: View = view.findViewById(R.id.card_pending)
        val cardAttended: View = view.findViewById(R.id.card_attended)
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
        statusText.text = getString(R.string.home_status_offline)
        dateText.text = formatCurrentDate()
        statsDamagedLights.text = getString(R.string.home_cards_placeholder)
        statsScheduledVisits.text = getString(R.string.home_cards_placeholder)

        syncActionButton.isEnabled = false
        syncActionButton.setOnClickListener { sincronizarConModal() }
        cardPending.setOnClickListener {
            navigateTo(
                R.id.nav_averias,
                bundleOf(AveriasFragment.ARG_INITIAL_ESTADO to Estado.ASIGNADA.name)
            )
        }
        cardAttended.setOnClickListener {
            navigateTo(
                R.id.nav_averias,
                bundleOf(AveriasFragment.ARG_INITIAL_ESTADO to Estado.RESUELTA.name)
            )
        }
        actionAverias.setOnClickListener { navigateTo(R.id.nav_averias) }
        actionMedidor.setOnClickListener { navigateTo(R.id.nav_medidor) }
        actionReportes.setOnClickListener { navigateTo(R.id.nav_reportes) }
        actionSettings.setOnClickListener { navigateTo(R.id.nav_settings) }

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
                        statsPendingAverias.text = count.toString()
                    }
                }
                launch {
                    vm.averiasResueltasHoyCount.collect { count ->
                        attendedCount.text = count.toString()
                    }
                }
                launch {
                    vm.kilometrajeFinalReciente.collect { kms ->
                        kilometrajeValue.text = kms?.takeIf { it > 0.0 }?.let {
                            getString(R.string.home_card_kilometraje_value, it)
                        } ?: getString(R.string.home_cards_placeholder)
                    }
                }
                launch {
                    vm.lastManualSync.collect { timestamp ->
                        val relative = formatRelativeSync(timestamp)
                        lastSyncSummary.text = relative
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
        statusIndicator = null
        syncDialog = null
        offlineDialog?.dismiss()
        offlineDialog = null
        connectivityManager = null
    }

    // HomeFragment
    private fun sincronizarConModal() {
        val usuarioActual = vm.usuario.value
        if (usuarioActual?.subregion.isNullOrBlank()) {
            syncStatusText?.text = getString(
                R.string.home_sync_status_error,
                getString(R.string.home_header_assignment_placeholder)
            )
            return
        }

        val dialog = SyncDialogFragment.newInstance(
            header = getString(R.string.home_sync_dialog_title),
            status = getString(R.string.home_sync_status_running)
        )
        dialog.show(childFragmentManager, "sync_dialog")
        syncDialog = dialog

        // Actualizas UI local
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
        renderNetworkStatus(statusText, isConnected())
    }

    private fun renderNetworkStatus(statusText: TextView, connected: Boolean) {
        if (connected) {
            statusText.text = getString(R.string.home_status_online)
            statusIndicator?.setBackgroundResource(R.drawable.bg_home_status_online)
            dismissOfflineDialog()
        } else {
            statusText.text = getString(R.string.home_status_offline)
            statusIndicator?.setBackgroundResource(R.drawable.bg_home_status_offline)
            showOfflineDialog()
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
                statusText.post { renderNetworkStatus(statusText, true) }
            }

            override fun onLost(network: android.net.Network) {
                if (!isAdded) return
                statusText.post { renderNetworkStatus(statusText, false) }
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

    private fun navigateTo(destinationId: Int, args: Bundle? = null) {
        val navController = findNavController()
        val options = navOptions {
            launchSingleTop = true
            restoreState = true
            popUpTo(navController.graph.startDestinationId) {
                inclusive = false
                saveState = true
            }
        }
        navController.navigate(destinationId, args, options)
    }

    private var currentAlertView: View? = null

    private fun showOfflineDialog() {
        // Evita mostrar más de una alerta
        if (currentAlertView != null || !isAdded) return

        val parent = requireActivity().findViewById<ViewGroup>(android.R.id.content)
        val alertView = layoutInflater.inflate(R.layout.layout_top_alert, parent, false)
        val textView = alertView.findViewById<TextView>(R.id.tvMessage)
        val iconView = alertView.findViewById<ImageView>(R.id.iconAlert)

        // Personaliza el mensaje y color (puedes usar tus colores corporativos)
        textView.text = "Sin conexión a Internet"
        alertView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red))
        iconView.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.white))

        // Añadir la vista al contenedor raíz
        parent.addView(alertView)
        currentAlertView = alertView

        // Animación de entrada
        alertView.translationY = -200f
        alertView.alpha = 0f
        alertView.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(350)
            .start()

        // Quitar automáticamente después de 3 segundos
        alertView.postDelayed({
            hideOfflineAlert()
        }, 3000)
    }

    private fun hideOfflineAlert() {
        currentAlertView?.let { alert ->
            alert.animate()
                .translationY(-200f)
                .alpha(0f)
                .setDuration(350)
                .withEndAction {
                    (alert.parent as? ViewGroup)?.removeView(alert)
                    currentAlertView = null
                }
                .start()
        }
    }


    private fun dismissOfflineDialog() {
        offlineDialog?.let { dialog ->
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        }
    }
}
