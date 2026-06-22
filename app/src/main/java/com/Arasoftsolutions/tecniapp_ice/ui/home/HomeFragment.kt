package com.Arasoftsolutions.tecniapp_ice.ui.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
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
import com.Arasoftsolutions.tecniapp_ice.network.NetworkHealth
import com.Arasoftsolutions.tecniapp_ice.network.NetworkHealthMonitor
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriasFragment
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriasSyncWorker
import com.Arasoftsolutions.tecniapp_ice.ui.averias.Estado
import com.Arasoftsolutions.tecniapp_ice.ui.modal.SyncDialogFragment
import com.Arasoftsolutions.tecniapp_ice.ui.vehiculo.TipoVehiculo
import com.Arasoftsolutions.tecniapp_ice.ui.vehiculo.showRegistroVehiculoPendienteDialog
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.Database.sync.AppSyncCoordinator
import com.Arasoftsolutions.tecniapp_ice.Database.sync.Synchronizer
import com.Arasoftsolutions.tecniapp_ice.notifications.SyncStatusNotifications
import com.Arasoftsolutions.tecniapp_ice.preferences.DataStoreManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
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
    private val dataStore by lazy { DataStoreManager.getInstance(requireContext().applicationContext) }
    private val roomRepository by lazy { RoomRepository.getInstance(requireContext().applicationContext) }
    private val synchronizer by lazy { Synchronizer(roomRepository) }
    private var manualSyncInProgress = false

    // Vistas de sincronización (existentes)
    private var syncButton: MaterialButton? = null
    private var syncStatusText: TextView? = null
    private var syncProgressIndicator: LinearProgressIndicator? = null
    private var syncProgressLabel: TextView? = null
    private var lastSyncValue: TextView? = null
    private var statusIndicator: View? = null
    private var syncDialog: SyncDialogFragment? = null

    // Red (existente — para el callback del dot del header)
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var networkCallbackRegistered = false

    // Contadores animados — solo se animan una vez por sesión del fragment
    private val animatedCounters = mutableSetOf<Int>()

    // Nuevas vistas del dashboard
    private var cardNetworkBanner: MaterialCardView? = null
    private var icNetworkStatus: ImageView? = null
    private var textNetworkLabel: TextView? = null
    private var textNetworkDetail: TextView? = null
    private var layoutChartRows: LinearLayout? = null
    private var textStatsScheduledVisits: TextView? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ── Vistas existentes ──────────────────────────────────────────────
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
        val statsDamagedLights: TextView = view.findViewById(R.id.text_stats_damaged_lights)
        val statsPendingAverias: TextView = view.findViewById(R.id.text_stats_pending_averias)
        val cardPending: View = view.findViewById(R.id.card_pending)
        val cardKilometraje: View = view.findViewById(R.id.card_kilometraje)
        val cardAttended: View = view.findViewById(R.id.card_attended)
        val actionAverias: View = view.findViewById(R.id.action_averias)
        val actionMedidor: View = view.findViewById(R.id.action_medidor)
        val actionReportes: View = view.findViewById(R.id.action_reportes)
        val actionSettings: View = view.findViewById(R.id.action_settings)
        val kilometrajeLabel: TextView = view.findViewById(R.id.text_kilometraje_label)
        val etmAlertText: TextView = view.findViewById(R.id.text_etm_alert)
        val etmAlertCard: MaterialCardView = view.findViewById(R.id.card_etm_alert)
        statusIndicator = view.findViewById(R.id.view_status_indicator)

        // ── Nuevas vistas ──────────────────────────────────────────────────
        cardNetworkBanner = view.findViewById(R.id.card_network_banner)
        icNetworkStatus = view.findViewById(R.id.ic_network_status)
        textNetworkLabel = view.findViewById(R.id.text_network_label)
        textNetworkDetail = view.findViewById(R.id.text_network_detail)
        layoutChartRows = view.findViewById(R.id.layout_chart_rows)
        textStatsScheduledVisits = view.findViewById(R.id.text_stats_scheduled_visits)

        // Accesos rápidos nuevos
        val actionLuminarias: View? = view.findViewById(R.id.action_luminarias)
        val actionVehiculo: View? = view.findViewById(R.id.action_vehiculo)
        val actionInventario: View? = view.findViewById(R.id.action_inventario)
        val actionPlanillas: View? = view.findViewById(R.id.action_planillas)

        // ── Campos de instancia ────────────────────────────────────────────
        syncButton = syncActionButton
        syncStatusText = syncStatus
        syncProgressIndicator = syncProgress
        syncProgressLabel = syncProgressText
        lastSyncValue = lastSyncSummary

        // ── Estado inicial ─────────────────────────────────────────────────
        statusText.text = getString(R.string.home_status_offline)
        dateText.text = formatCurrentDate()
        statsDamagedLights.text = getString(R.string.home_cards_placeholder)
        syncActionButton.isEnabled = false

        // ── Click listeners ────────────────────────────────────────────────
        syncActionButton.setOnClickListener {
            if (vm.networkHealth.value.blocksFullSync) {
                syncStatus.text = getString(R.string.home_sync_blocked_offline)
                return@setOnClickListener
            }
            sincronizarConModal()
        }
        cardPending.setOnClickListener {
            ejecutarOperacionSiRegistroCompleto {
                navigateTo(R.id.nav_averias, bundleOf(AveriasFragment.ARG_INITIAL_ESTADO to Estado.ASIGNADA.name))
            }
        }
        cardAttended.setOnClickListener {
            ejecutarOperacionSiRegistroCompleto {
                navigateTo(R.id.nav_averias, bundleOf(AveriasFragment.ARG_INITIAL_ESTADO to Estado.RESUELTA.name))
            }
        }
        cardKilometraje.setOnClickListener {
            ejecutarOperacionSiRegistroCompleto { navigateTo(R.id.nav_mi_vehiculo) }
        }
        view.findViewById<View>(R.id.card_luminarias_stat).setOnClickListener {
            ejecutarOperacionSiRegistroCompleto { navigateTo(R.id.nav_luminarias) }
        }
        actionAverias.setOnClickListener {
            ejecutarOperacionSiRegistroCompleto { navigateTo(R.id.nav_averias) }
        }
        actionMedidor.setOnClickListener { navigateTo(R.id.nav_medidor) }
        actionReportes.setOnClickListener { navigateTo(R.id.nav_reportes) }
        actionSettings.setOnClickListener { navigateTo(R.id.nav_settings) }
        actionLuminarias?.setOnClickListener {
            ejecutarOperacionSiRegistroCompleto { navigateTo(R.id.nav_luminarias) }
        }
        actionVehiculo?.setOnClickListener {
            ejecutarOperacionSiRegistroCompleto { navigateTo(R.id.nav_mi_vehiculo) }
        }
        actionInventario?.setOnClickListener { navigateTo(R.id.nav_inventario) }
        actionPlanillas?.setOnClickListener { navigateTo(R.id.nav_planillas) }

        // ── Red (callback CM para el dot del header) ───────────────────────
        connectivityManager = requireContext()
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        registerNetworkCallback(statusText)
        updateNetworkStatus(statusText)

        vm.loadUsuarioActual()

        // ── Observadores de estado ─────────────────────────────────────────
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
                    vm.averiasAsignadasCount.collect {
                        animateToCount(pendingCount, it, R.id.text_pending_count)
                    }
                }

                launch {
                    vm.averiasResueltasHoyCount.collect {
                        animateToCount(attendedCount, it, R.id.text_attended_count)
                    }
                }

                launch {
                    vm.averiasPendientesPorAgencia.collect { items ->
                        statsPendingAverias.text = formatPendingAverias(items)
                        val count = items.count { it.pendientes > 0 }
                        textStatsScheduledVisits?.text = count.toString()
                        updateAgencyChart(items)
                    }
                }

                launch {
                    vm.valorEtmActual
                        .combine(vm.tipoVehiculo) { valor, tipo -> valor to tipo }
                        .collect { (valor, tipo) ->
                            val unidad = if (tipo.usaKilometraje) getString(R.string.home_unidad_km)
                                         else getString(R.string.home_unidad_horas)
                            if (valor != null && valor >= 0.0) {
                                animateToEtmValue(kilometrajeValue, valor, unidad, R.id.text_kilometraje)
                            } else {
                                kilometrajeValue.text = getString(R.string.home_cards_placeholder)
                            }
                            kilometrajeLabel.text = when (tipo) {
                                TipoVehiculo.MAQUINARIA_PESADA -> getString(R.string.home_card_orimetro_title)
                                else -> getString(R.string.home_card_kilometraje_title)
                            }
                        }
                }

                launch {
                    vm.luminariasPendientesCount.collect { count ->
                        animateToCount(statsDamagedLights, count, R.id.text_stats_damaged_lights)
                    }
                }

                launch {
                    vm.registroEtmPendiente.collect { pendiente ->
                        if (pendiente) {
                            etmAlertText.text = getString(R.string.home_etm_alert_pendiente)
                            etmAlertCard.isVisible = true
                        } else {
                            etmAlertCard.isVisible = false
                        }
                    }
                }

                launch {
                    vm.lastManualSync.collect { timestamp ->
                        lastSyncSummary.text = formatRelativeSync(timestamp)
                    }
                }

                // ── Banner de salud de red (nuevo) ─────────────────────────
                launch {
                    vm.networkHealth.collect { health ->
                        renderNetworkBanner(health)
                        renderNetworkStatus(statusText, health.isUsable)
                    }
                }
            }
        }

        observeManualSync()
        animateCardEntrance(view)
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
        connectivityManager = null
        cardNetworkBanner = null
        icNetworkStatus = null
        textNetworkLabel = null
        textNetworkDetail = null
        layoutChartRows = null
        textStatsScheduledVisits = null
    }

    // ═══════════════════════════════════════════════════════════════
    //  BANNER DE RED
    // ═══════════════════════════════════════════════════════════════

    private fun renderNetworkBanner(health: NetworkHealth) {
        val banner = cardNetworkBanner ?: return

        if (health == NetworkHealth.STABLE) {
            if (banner.isVisible) {
                banner.animate().alpha(0f).setDuration(250)
                    .withEndAction { banner.isVisible = false }.start()
            }
            return
        }

        if (!banner.isVisible) {
            banner.alpha = 0f
            banner.isVisible = true
            banner.animate().alpha(1f).setDuration(300).start()
        }

        val ctx = requireContext()
        val (bgColorRes, iconTintRes, labelRes, detailRes) = when (health) {
            NetworkHealth.OFFLINE -> BannerStyle(
                R.color.error_500, R.color.white,
                R.string.network_health_offline, R.string.network_health_offline_detail
            )
            NetworkHealth.CONNECTED_NO_INTERNET -> BannerStyle(
                R.color.averia_notification_pending, R.color.white,
                R.string.network_health_no_internet, R.string.network_health_no_internet_detail
            )
            NetworkHealth.SLOW -> BannerStyle(
                R.color.warning_yellow, R.color.on_light,
                R.string.network_health_slow, R.string.network_health_slow_detail
            )
            NetworkHealth.UNSTABLE -> BannerStyle(
                R.color.status_assigned, R.color.white,
                R.string.network_health_unstable, R.string.network_health_unstable_detail
            )
            NetworkHealth.STABLE -> return
        }

        banner.setCardBackgroundColor(ContextCompat.getColor(ctx, bgColorRes))
        icNetworkStatus?.setColorFilter(ContextCompat.getColor(ctx, iconTintRes))
        textNetworkLabel?.apply {
            text = getString(labelRes)
            setTextColor(ContextCompat.getColor(ctx, iconTintRes))
        }
        textNetworkDetail?.apply {
            text = getString(detailRes)
            setTextColor(ContextCompat.getColor(ctx, iconTintRes))
        }
    }

    private data class BannerStyle(val bg: Int, val tint: Int, val label: Int, val detail: Int)

    // ═══════════════════════════════════════════════════════════════
    //  MINI GRÁFICA DE AGENCIAS
    // ═══════════════════════════════════════════════════════════════

    private fun updateAgencyChart(items: List<HomeViewModel.AveriasPendientesPorAgencia>) {
        val container = layoutChartRows ?: return
        container.removeAllViews()
        val pending = items.filter { it.pendientes > 0 }
            .sortedByDescending { it.pendientes }
            .take(5)
        if (pending.isEmpty()) return

        val ctx = requireContext()
        val dm = ctx.resources.displayMetrics
        val dp4 = (4 * dm.density).toInt()
        val dp8 = (8 * dm.density).toInt()
        val dp10 = (10 * dm.density).toInt()
        val maxCount = pending.maxOf { it.pendientes }.coerceAtLeast(1)

        pending.forEachIndexed { index, item ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dp8 }
            }

            // Etiqueta de agencia
            row.addView(TextView(ctx).apply {
                text = item.agencia.split(" ").firstOrNull()?.take(10) ?: item.agencia.take(10)
                textSize = 11.5f
                maxLines = 1
                setTextColor(ContextCompat.getColor(ctx, R.color.dark_gray))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.30f)
            })

            // Barra proporcional
            val pct = item.pendientes.toFloat() / maxCount.toFloat()
            val barColorRes = when {
                pct >= 0.7f -> R.color.danger_red
                pct >= 0.4f -> R.color.status_assigned
                else -> R.color.success_green
            }
            val barHeight = dp8 + dp4
            val barContainer = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, barHeight, 0.60f)
                    .also { it.marginStart = dp4; it.marginEnd = dp4 }
            }
            // Segmento coloreado
            barContainer.addView(View(ctx).apply {
                background = GradientDrawable().apply {
                    setColor(ContextCompat.getColor(ctx, barColorRes))
                    cornerRadius = dp4.toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(0, barHeight, pct)
            })
            // Espacio vacío
            if (pct < 1f) {
                barContainer.addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(0, barHeight, 1f - pct)
                })
            }
            row.addView(barContainer)

            // Contador
            row.addView(TextView(ctx).apply {
                text = item.pendientes.toString()
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(ctx, R.color.on_light))
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.10f)
            })

            // Animación de entrada escalonada
            row.alpha = 0f
            row.translationX = -(18f * dm.density)
            container.addView(row)
            row.animate()
                .alpha(1f)
                .translationX(0f)
                .setStartDelay(index * 55L)
                .setDuration(220)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  ANIMACIÓN DE ENTRADA DE TARJETAS
    // ═══════════════════════════════════════════════════════════════

    private fun animateCardEntrance(root: View) {
        val density = root.resources.displayMetrics.density
        val cardIds = listOf(
            R.id.card_pending, R.id.card_attended,
            R.id.card_luminarias_stat, R.id.card_kilometraje,
            R.id.card_agency_chart, R.id.card_sync_status
        )
        cardIds.forEachIndexed { index, id ->
            root.findViewById<View>(id)?.let { card ->
                card.alpha = 0f
                card.translationY = 28f * density
                card.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(80L + index * 65L)
                    .setDuration(300)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  SINCRONIZACIÓN MANUAL (igual a la versión original)
    // ═══════════════════════════════════════════════════════════════

    private fun sincronizarConModal() {
        if (manualSyncInProgress) return
        val usuarioActual = vm.usuario.value
        if (usuarioActual?.subregion.isNullOrBlank()) {
            syncStatusText?.text = getString(
                R.string.home_sync_status_error,
                getString(R.string.home_header_assignment_placeholder)
            )
            return
        }
        if (!isConnected()) {
            syncStatusText?.text = getString(
                R.string.home_sync_status_error,
                getString(R.string.offline_alert_message)
            )
            return
        }

        manualSyncInProgress = true
        val dialog = SyncDialogFragment.newInstance(
            header = getString(R.string.home_sync_dialog_title),
            status = getString(R.string.home_sync_status_running)
        )
        dialog.show(childFragmentManager, "sync_dialog")
        syncDialog = dialog

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

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val subregion = usuarioActual?.subregion?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalStateException(getString(R.string.home_sync_unknown_error))
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
                                if (isAdded) {
                                    dialog.update(done, total, msg ?: "", downloadedBytes)
                                    updateSyncProgress(done, total, msg)
                                    SyncStatusNotifications.notifyProgress(requireContext(), done, total, msg)
                                }
                            }
                        },
                        onSyncSuccess = {
                            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                                if (!isAdded) return@launch
                                SyncStatusNotifications.dismissProgress(requireContext())
                                syncStatusText?.text = getString(R.string.home_sync_status_success)
                                viewLifecycleOwner.lifecycleScope.launch {
                                    dataStore.markManualSyncNow()
                                }
                                lastSyncValue?.text = formatRelativeSync(System.currentTimeMillis())
                                SyncStatusNotifications.notifySynced(requireContext())
                                dialog.dismissAllowingStateLoss()
                            }
                        },
                        onSyncError = { error ->
                            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                                if (!isAdded) return@launch
                                SyncStatusNotifications.dismissProgress(requireContext())
                                syncStatusText?.text = getString(
                                    R.string.home_sync_status_error,
                                    error.message ?: getString(R.string.home_sync_unknown_error)
                                )
                                dialog.dismissWithError(error) {
                                    if (isAdded) sincronizarConModal()
                                }
                            }
                        }
                    )
                }

                if (executed == null) {
                    context?.let {
                        android.widget.Toast.makeText(
                            it,
                            "Ya hay una sincronización en curso o fue ejecutada recientemente",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                    dialog.dismissAllowingStateLoss()
                }
            } catch (error: Throwable) {
                if (isAdded) {
                    SyncStatusNotifications.dismissProgress(requireContext())
                    syncStatusText?.text = getString(
                        R.string.home_sync_status_error,
                        error.message ?: getString(R.string.home_sync_unknown_error)
                    )
                    dialog.dismissWithError(error) {
                        if (isAdded) sincronizarConModal()
                    }
                }
            } finally {
                if (isAdded) SyncStatusNotifications.dismissProgress(requireContext())
                manualSyncInProgress = false
                syncButton?.isEnabled = vm.usuario.value?.subregion?.isNullOrBlank() == false
                syncProgressIndicator?.visibility = View.GONE
                syncProgressLabel?.visibility = View.GONE
                syncDialog = null
            }
        }
    }

    private fun ejecutarOperacionSiRegistroCompleto(onContinue: () -> Unit) {
        if (vm.registroEtmPendiente.value) {
            showRegistroVehiculoPendienteDialog(
                onRegistroGuardado = { onContinue() },
                onNoVehiculo = {}
            )
            return
        }
        onContinue()
    }

    // ═══════════════════════════════════════════════════════════════
    //  RED — dot del header
    // ═══════════════════════════════════════════════════════════════

    private fun updateNetworkStatus(statusText: TextView) {
        renderNetworkStatus(statusText, isConnected())
    }

    private fun renderNetworkStatus(statusText: TextView, connected: Boolean) {
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
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                if (!isAdded) return
                statusText.post { renderNetworkStatus(statusText, true) }
            }
            override fun onLost(network: android.net.Network) {
                if (!isAdded) return
                statusText.post { renderNetworkStatus(statusText, false) }
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }
            .onSuccess {
                networkCallbackRegistered = true
                networkCallback = cb
            }
    }

    private fun unregisterNetworkCallback() {
        val cm = connectivityManager
        val cb = networkCallback
        if (cm != null && cb != null && networkCallbackRegistered) {
            runCatching { cm.unregisterNetworkCallback(cb) }
        }
        networkCallbackRegistered = false
        networkCallback = null
    }

    /** Usa NetworkHealthMonitor para una verificación más precisa que NET_CAPABILITY_INTERNET. */
    private fun isConnected(): Boolean =
        NetworkHealthMonitor.getInstance(requireContext()).isUsable

    // ═══════════════════════════════════════════════════════════════
    //  OBSERVADOR DE TRABAJO EN SEGUNDO PLANO
    // ═══════════════════════════════════════════════════════════════

    private fun observeManualSync() {
        val manager = WorkManager.getInstance(requireContext())
        manager.getWorkInfosForUniqueWorkLiveData(AveriasSyncWorker.UNIQUE_MANUAL_WORK)
            .observe(viewLifecycleOwner) { infos ->
                if (manualSyncInProgress) return@observe
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
                        syncDialog?.let { runCatching { it.dismissAllowingStateLoss() } }
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
                            lastSyncValue?.text = formatRelativeSync(vm.lastManualSync.value)
                            syncDialog?.let { runCatching { it.dismissAllowingStateLoss() } }
                        } else {
                            val message = info.outputData.getString("error")
                                ?: getString(R.string.home_sync_unknown_error)
                            syncStatusText?.text = getString(R.string.home_sync_status_error, message)
                            syncDialog?.dismissWithError(message) {
                                if (isAdded) sincronizarConModal()
                            }
                        }
                        syncDialog = null
                    }
                }
            }
    }

    // ═══════════════════════════════════════════════════════════════
    //  FORMATTERS
    // ═══════════════════════════════════════════════════════════════

    private fun formatDisplayName(user: UserEntity?): String {
        if (user == null) return getString(R.string.home_header_default_user)
        val full = listOfNotNull(user.nombre, user.apellidosCompletos)
            .joinToString(" ").trim()
        if (full.isNotBlank()) return full
        return user.email?.takeIf { it.isNotBlank() }
            ?: user.uid.ifBlank { getString(R.string.home_header_default_user) }
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
        return if (parts.isEmpty()) getString(R.string.home_header_assignment_placeholder)
        else parts.joinToString(" • ")
    }

    private fun formatCurrentDate(): String {
        val now = ZonedDateTime.now()
        val date = dateFormatter.format(now).replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(locale) else char.toString()
        }
        val time = timeFormatter.format(now)
        return getString(R.string.home_header_date_format, date, time)
    }

    private fun formatPendingAverias(items: List<HomeViewModel.AveriasPendientesPorAgencia>): String {
        if (items.isEmpty()) return getString(R.string.home_cards_placeholder)
        return items.joinToString("\n") { "• ${it.agencia} ${it.pendientes}" }
    }

    private fun formatRelativeSync(timestamp: Long?): String {
        if (timestamp == null || timestamp <= 0) return getString(R.string.home_last_sync_never)
        val duration = Duration.between(Instant.ofEpochMilli(timestamp), Instant.now())
        val minutes = duration.toMinutes()
        return when {
            minutes < 1 -> getString(R.string.home_last_sync_just_now)
            minutes < 60 -> getString(R.string.home_last_sync_minutes, minutes.toInt())
            minutes < 60 * 24 -> getString(R.string.home_last_sync_hours, duration.toHours().toInt())
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
        val progress = (done.coerceIn(0, total) * 100f / total).toInt()
        indicator.setProgressCompat(progress, true)
        label.text = getString(
            R.string.home_sync_progress_format,
            done.coerceIn(0, total),
            total,
            message ?: getString(R.string.home_sync_status_running)
        )
    }

    // ═══════════════════════════════════════════════════════════════
    //  ANIMACIONES DE CONTEO (0 → valor final)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Anima un TextView de enteros desde 0 hasta [target] solo la primera vez.
     * Las actualizaciones posteriores (re-sync) se aplican directamente.
     */
    private fun animateToCount(textView: TextView, target: Int, viewId: Int) {
        if (viewId in animatedCounters) {
            textView.text = target.toString()
            return
        }
        animatedCounters.add(viewId)
        if (target == 0) {
            textView.text = "0"
            return
        }
        ValueAnimator.ofInt(0, target).apply {
            duration = 900L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { textView.text = (animatedValue as Int).toString() }
        }.start()
    }

    /**
     * Anima un valor decimal (km u horas) desde 0.0 hasta [target] solo la primera vez.
     * El resultado se formatea como "%1$.1f %2$s".
     */
    private fun animateToEtmValue(textView: TextView, target: Double, unidad: String, viewId: Int) {
        if (viewId in animatedCounters) {
            textView.text = getString(R.string.home_card_medidor_value_format, target, unidad)
            return
        }
        animatedCounters.add(viewId)
        ValueAnimator.ofFloat(0f, target.toFloat()).apply {
            // Para valores grandes (km), usar duración mayor para que el conteo sea visible
            duration = if (target > 500) 1200L else 900L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val v = (animatedValue as Float).toDouble()
                textView.text = getString(R.string.home_card_medidor_value_format, v, unidad)
            }
        }.start()
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
}
