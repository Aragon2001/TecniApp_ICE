package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import android.widget.CompoundButton
import android.widget.TextView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentAveriasBinding
import com.Arasoftsolutions.tecniapp_ice.databinding.DialogNotificationFiltersBinding
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.chip.Chip
import android.view.inputmethod.EditorInfo
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import androidx.core.util.Pair
import com.Arasoftsolutions.tecniapp_ice.ui.averias.Estado.*
import kotlin.math.abs

class AveriasFragment : Fragment() {

    companion object {
        const val ARG_INITIAL_ESTADO = "initial_estado"
    }

    private var _b: FragmentAveriasBinding? = null
    private val b get() = _b!!

    private val vm: AveriasViewModel by viewModels()
    private lateinit var adapter: AveriasAdapter
    private var notificationSheet: BottomSheetDialog? = null
    private var notificationSheetScope: CoroutineScope? = null
    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private var notificationMenuItem: MenuItem? = null
    // TODO(Codex): Guardar referencia al ítem de menú para refrescar el icono de notificaciones


    override fun onStart() {
    super.onStart()
    AveriasForegroundTracker.isAveriasVisible = true
}

override fun onStop() {
    AveriasForegroundTracker.isAveriasVisible = false
    super.onStop()
}

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentAveriasBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_averias, menu)
                notificationMenuItem = menu.findItem(R.id.action_notification_filters)
                updateNotificationIcon(vm.notificationsEnabled.value)
                // TODO(Codex): Refrescar icono de campana según preferencia almacenada
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean {
                return when (item.itemId) {
                    R.id.action_notification_filters -> {
                        showNotificationFiltersSheet()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.STARTED)

        b.fabFilters.setOnClickListener {
            b.appBarLayout.setExpanded(true, true)
        }

        b.appBarLayout.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
            val isExpanded = abs(verticalOffset) < appBarLayout.totalScrollRange
            b.fabFilters.visibility = if (isExpanded) View.GONE else View.VISIBLE
        }

        b.btnDateFilter.setOnClickListener { showDateRangePicker() }
        b.btnClearDate.setOnClickListener {
            if (vm.fechaFiltroState.value != null) {
                vm.clearFechaFiltro()
                Snackbar.make(b.root, R.string.averias_filtro_fecha_limpio, Snackbar.LENGTH_SHORT).show()
            }
        }
        // TODO(Codex): Proveer acción explícita para limpiar el filtro de fechas

        b.etBuscar.addTextChangedListener { text ->
            vm.setQuery(text?.toString().orEmpty())
        }

        // Recycler
        adapter = AveriasAdapter(
            onVerDetalle = { showDetalle(it) },
            onVerMapa = { openMap(it) },
            onAsignar = { vm.onToggleAsignacion(it) },
            onAtender = { handleAtender(it) },
            onResolver = { handleResolver(it) },
            onRevertir = { handleRevertir(it) }
        )

        b.recyclerViewAverias.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@AveriasFragment.adapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }

        // Usuario actual
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.usuarioActual.collectLatest { user ->
                    adapter.currentUserUid = user?.uid
                    adapter.currentUserRegion = vm.resolveUserRegionLabel(user)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.notificationsEnabled.collectLatest { enabled ->
                    updateNotificationIcon(enabled)
                }
            }
        }
        // TODO(Codex): Escuchar cambios de preferencias de notificaciones para actualizar la UI

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.fechaFiltroState.collectLatest { rango ->
                    updateDateFilter(rango)
                }
            }
        }
        // TODO(Codex): Sincronizar el texto del filtro de fecha con la selección actual

        // Pull to refresh → Sync
        var refreshTriggeredByUser = false
        b.swipeRefresh.setOnRefreshListener {
            refreshTriggeredByUser = true
            vm.syncNow()
        }

        // Chip Group Estado
        b.chipGroupEstado.setOnCheckedStateChangeListener { _, checkedIds ->
            val state = when (checkedIds.firstOrNull()) {
                b.chipTodos.id -> null
                b.chipPendiente.id -> Estado.PENDIENTE
                b.chipAsignada.id -> Estado.ASIGNADA
                b.chipEnAtencion.id -> Estado.EN_ATENCION
                b.chipResuelta.id -> Estado.RESUELTA
                b.chipAnulada.id -> Estado.ANULADA
                else -> null
            }
            vm.setEstado(state)
        }
        b.chipGroupEstado.check(b.chipTodos.id)

        if (savedInstanceState == null) {
            val estadoInicial = arguments?.getString(ARG_INITIAL_ESTADO)
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { Estado.valueOf(it) }.getOrNull() }
            estadoInicial?.let { estado ->
                val chipId = when (estado) {
                    ASIGNADA -> b.chipAsignada.id
                    EN_ATENCION -> b.chipEnAtencion.id
                    RESUELTA -> b.chipResuelta.id
                    PENDIENTE -> b.chipPendiente.id
                    ANULADA -> TODO()
                }
                b.chipGroupEstado.check(chipId)
            }
        }
        arguments?.remove(ARG_INITIAL_ESTADO)

        // Dropdown Regiones
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.regiones.collectLatest { regiones ->
                    val nombres = regiones.map { it.nombreVisible }
                    b.actvRegion.setAdapter(
                        ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, nombres)
                    )
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.regionSeleccionada.collectLatest { region ->
                    if (b.actvRegion.text?.toString() != region.nombreVisible) {
                        b.actvRegion.setText(region.nombreVisible, false)
                    }
                }
            }
        }

        b.actvRegion.setOnItemClickListener { _, _, position, _ ->
            vm.setRegionIndex(position)
        }

        // Dropdown Agencias
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.agencias.collectLatest { agencias ->
                    val nombres = agencias.map { it.nombreVisible }
                    b.actvAgencia.setAdapter(
                        ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, nombres)
                    )
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.agenciaSeleccionada.collectLatest { agencia ->
                    if (b.actvAgencia.text?.toString() != agencia.nombreVisible) {
                        b.actvAgencia.setText(agencia.nombreVisible, false)
                    }
                }
            }
        }

        b.actvAgencia.setOnItemClickListener { _, _, position, _ ->
            vm.setAgenciaIndex(position)
        }



        // Observa estado UI y mensajes
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.uiState.collectLatest { state ->
                        if (refreshTriggeredByUser && !state.loading) {
                            refreshTriggeredByUser = false
                        }
                        b.swipeRefresh.isRefreshing = refreshTriggeredByUser && state.loading
                        adapter.submitList(state.items)
                        b.tvVacio.visibility = if (state.items.isEmpty() && !state.loading) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    vm.messages.collectLatest { message ->
                        Snackbar.make(b.root, message, Snackbar.LENGTH_LONG).show()
                    }
                }
                launch {
                    vm.shareRequests.collectLatest { item ->
                        PdfGenerator.exportAveria(requireContext(), item)
                    }
                }
            }
        }

        // Sincronizar datos iniciales
        vm.syncNow()
    }
    private fun handleAtender(item: AveriaUI) {
        when (Estado.fromLabel(item.estado)) {
            ASIGNADA -> showDetalle(item)
            EN_ATENCION -> vm.onCancelarAtencion(item)
            else -> showDetalle(item)
        }
    }

    private fun handleResolver(item: AveriaUI) {
        when (Estado.fromLabel(item.estado)) {
            EN_ATENCION -> showDetalle(item)
            RESUELTA -> viewLifecycleOwner.lifecycleScope.launch {
                PdfGenerator.exportAveria(requireContext(), item)
            }
            else -> showDetalle(item)
        }
    }

    private fun handleRevertir(item: AveriaUI) {
        if (Estado.fromLabel(item.estado) != ANULADA) {
            showDetalle(item)
            return
        }
        vm.onRevertirAnulada(item)
    }

    private fun showNotificationFiltersSheet() {
        notificationSheet?.let {
            if (!it.isShowing) it.show()
            return
        }

        val sheetBinding = DialogNotificationFiltersBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(requireContext()).apply {
            setContentView(sheetBinding.root)
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        notificationSheetScope = scope
        notificationSheet = dialog

        val switchListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
            vm.setNotificationsEnabled(isChecked)
        }
        val initialEnabled = vm.notificationsEnabled.value
        sheetBinding.switchNotifications.isChecked = initialEnabled
        applyNotificationEnabledState(sheetBinding, initialEnabled)
        renderNotificationChips(sheetBinding, vm.notificationAgencies.value, initialEnabled)
        sheetBinding.actvNotificationAgency.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                vm.notificationSuggestions.value
            )
        )
        sheetBinding.switchNotifications.setOnCheckedChangeListener(switchListener)

        fun addAgency(value: String) {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return
            vm.addNotificationAgency(trimmed)
            sheetBinding.actvNotificationAgency.setText("", false)
        }

        sheetBinding.actvNotificationAgency.setOnItemClickListener { parent, _, position, _ ->
            val value = parent.getItemAtPosition(position)?.toString().orEmpty()
            addAgency(value)
        }
        sheetBinding.actvNotificationAgency.setOnEditorActionListener { textView, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val value = textView.text?.toString().orEmpty()
                addAgency(value)
                true
            } else {
                false
            }
        }

        sheetBinding.btnGuardarNotificaciones.setOnClickListener {
            Toast.makeText(requireContext(), R.string.settings_notifications_saved, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        scope.launch {
            vm.notificationSuggestions.collectLatest { sugerencias ->
                sheetBinding.actvNotificationAgency.setAdapter(
                    ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_dropdown_item_1line,
                        sugerencias
                    )
                )
            }
        }
        scope.launch {
            vm.notificationsEnabled.collectLatest { enabled ->
                if (sheetBinding.switchNotifications.isChecked != enabled) {
                    sheetBinding.switchNotifications.setOnCheckedChangeListener(null)
                    sheetBinding.switchNotifications.isChecked = enabled
                    sheetBinding.switchNotifications.setOnCheckedChangeListener(switchListener)
                }
                applyNotificationEnabledState(sheetBinding, enabled)
                renderNotificationChips(sheetBinding, vm.notificationAgencies.value, enabled)
            }
        }
        scope.launch {
            vm.notificationAgencies.collectLatest { agencias ->
                renderNotificationChips(sheetBinding, agencias, vm.notificationsEnabled.value)
            }
        }

        dialog.setOnDismissListener {
            sheetBinding.actvNotificationAgency.setOnItemClickListener(null)
            sheetBinding.actvNotificationAgency.setOnEditorActionListener(null)
            notificationSheetScope?.cancel()
            notificationSheetScope = null
            notificationSheet = null
        }

        dialog.show()
    }


    private fun updateDateFilter(range: AveriasViewModel.FechaFiltro?) {
        val text = if (range == null) {
            getString(R.string.averias_filtro_fecha_todas)
        } else {
            val start = Date(range.inicioMillis)
            val endInclusive = Date((range.finExclusiveMillis - 1).coerceAtLeast(range.inicioMillis))
            getString(
                R.string.averias_filtro_fecha_rango,
                dateFormatter.format(start),
                dateFormatter.format(endInclusive)
            )
        }
        b.btnDateFilter.text = text
        b.btnClearDate.visibility = if (range == null) View.GONE else View.VISIBLE
        // TODO(Codex): Mostrar botón para limpiar el filtro únicamente cuando esté activo
    }

    private fun showDateRangePicker() {
        val builder = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(getString(R.string.averias_filtrar_fecha))

        vm.fechaFiltroState.value?.let { current ->
            val zone = ZoneId.systemDefault()
            val startDate = Instant.ofEpochMilli(current.inicioMillis).atZone(zone).toLocalDate()
            val endInclusiveMillis = (current.finExclusiveMillis - 1).coerceAtLeast(current.inicioMillis)
            val endDate = Instant.ofEpochMilli(endInclusiveMillis).atZone(zone).toLocalDate()
            val selectionStart = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val selectionEnd = endDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            builder.setSelection(Pair(selectionStart, selectionEnd))
        }

        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { selection ->
            val start = selection.first
            val end = selection.second
            if (start != null && end != null) {
                vm.setFechaFiltro(start, end)
            }
        }
        picker.show(parentFragmentManager, "averias_date_range")
    }

    private fun updateNotificationIcon(enabled: Boolean) {
        val iconRes = if (enabled) R.drawable.ic_notification else R.drawable.ic_notification_off
        notificationMenuItem?.setIcon(iconRes)
        // TODO(Codex): Alternar iconografía de notificaciones habilitadas/deshabilitadas
    }

    private fun openMap(item: AveriaUI) {
        val lat = item.lat
        val lng = item.lng
        if (lat == 0.0 && lng == 0.0) {
            Snackbar.make(b.root, R.string.averia_error_sin_coordenadas, Snackbar.LENGTH_SHORT).show()
            return
        }
        AveriaMapLauncher.show(
            requireContext(),
            lat,
            lng,
            item.descripcion.takeIf { it.isNotBlank() } ?: item.id
        ) {
            Snackbar.make(b.root, R.string.averia_error_app_mapa, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun showDetalle(item: AveriaUI) {
        AveriaDetalleBottomSheet.newInstance(item).show(childFragmentManager, "detalle_averia")
    }

    /**
     * Renderiza los chips dentro del diálogo (BottomSheet)
     */
    private fun renderNotificationChips(
        sheetBinding: DialogNotificationFiltersBinding,
        agencias: List<String>,
        notificationsEnabled: Boolean
    ) {
        val group = sheetBinding.chipGroupNotificationAgencies
        group.removeAllViews()
        agencias.forEach { nombre ->
            val chip = Chip(requireContext()).apply {
                text = nombre
                isCheckable = false
                isCloseIconVisible = true
                alpha = if (notificationsEnabled) 1f else 0.6f
                setOnCloseIconClickListener { vm.removeNotificationAgency(nombre) }
            }
            group.addView(chip)
        }
        sheetBinding.tvNotificationFiltersEmpty.visibility =
            if (agencias.isEmpty()) View.VISIBLE else View.GONE
        sheetBinding.tvNotificationFiltersEmpty.alpha = if (notificationsEnabled) 1f else 0.6f
        sheetBinding.tvNotificationFiltersEmpty.text = if (notificationsEnabled) {
            getString(R.string.averia_notificacion_filtro_vacio)
        } else {
            getString(R.string.averia_notificacion_filtro_desactivado)
        }
        group.alpha = if (notificationsEnabled) 1f else 0.6f
    }

    /**
     * Renderiza los chips visibles en el fragment principal
     */

    /**
     * Aplica el estado visual de "notificaciones activadas/desactivadas"
     * tanto para el diálogo como para la vista principal.
     */
    private fun applyNotificationEnabledState(
        sheetBinding: DialogNotificationFiltersBinding,
        enabled: Boolean
    ) {
        val alpha = if (enabled) 1f else 0.6f
        sheetBinding.tilNotificationAgency.alpha = alpha
        sheetBinding.actvNotificationAgency.isEnabled = enabled
        sheetBinding.tvNotificationFilterTitle.alpha = alpha
        sheetBinding.tvNotificationSwitchHelper.alpha = alpha
        sheetBinding.tvNotificationSwitchHelper.text = if (enabled) {
            getString(R.string.averia_notificacion_switch_helper)
        } else {
            getString(R.string.averia_notificacion_filtro_desactivado)
        }
        sheetBinding.chipGroupNotificationAgencies.alpha = alpha
        sheetBinding.tvNotificationFiltersEmpty.alpha = alpha
    }


    override fun onDestroyView() {
        notificationSheet?.setOnDismissListener(null)
        notificationSheet?.dismiss()
        notificationSheet = null
        notificationSheetScope?.cancel()
        notificationSheetScope = null
        _b = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        vm.syncNow()
    }
}
