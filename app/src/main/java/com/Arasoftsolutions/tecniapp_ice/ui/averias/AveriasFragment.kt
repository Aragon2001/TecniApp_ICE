package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.app.TimePickerDialog
import android.content.Intent
import android.content.res.ColorStateList
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
import androidx.core.content.ContextCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentAveriasBinding
import com.Arasoftsolutions.tecniapp_ice.databinding.DialogNotificationFiltersBinding
import com.Arasoftsolutions.tecniapp_ice.databinding.BottomsheetAveriasFiltrosBinding
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.google.firebase.auth.FirebaseAuth
import com.Arasoftsolutions.tecniapp_ice.ui.vehiculo.obtenerEstadoEtmVehiculo
import com.Arasoftsolutions.tecniapp_ice.ui.vehiculo.showRegistroVehiculoPendienteDialog
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.Instant
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.core.util.Pair
import com.Arasoftsolutions.tecniapp_ice.ui.averias.Estado.*
import kotlin.math.abs
import androidx.core.view.isVisible

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
    private var filtrosSheet: BottomSheetDialog? = null
    private var filtrosSheetScope: CoroutineScope? = null
    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private var notificationMenuItem: MenuItem? = null
    private var pendingDeepLinkCaseId: String? = null

    override fun onResume() {
        super.onResume()
        AveriasForegroundTracker.isAveriasVisible = true
        val deepLinkId = AveriaDeepLink.consume()
        if (!deepLinkId.isNullOrBlank()) {
            pendingDeepLinkCaseId = deepLinkId
        }
    }

    override fun onPause() {
        AveriasForegroundTracker.isAveriasVisible = false
        super.onPause()
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

        b.fabMapa.setOnClickListener {
            findNavController().navigate(R.id.action_nav_averias_to_averiasMapFragment)
        }

        b.appBarLayout.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
            val isExpanded = abs(verticalOffset) < appBarLayout.totalScrollRange
            b.fabFilters.visibility = if (isExpanded) View.GONE else View.VISIBLE
        }

        b.btnFiltrosAvanzados.setOnClickListener {
            showFiltrosAvanzadosSheet()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val repo = RoomRepository.getInstance(requireContext())
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            val estadoEtm = uid?.let { repo.obtenerEstadoEtmVehiculo(it) }
            if (estadoEtm != null && (estadoEtm.registroPendienteCierre != null || !estadoEtm.tieneRegistroHoy)) {
                showRegistroVehiculoPendienteDialog(
                    onRegistroGuardado = { },
                    onNoVehiculo = { findNavController().popBackStack(R.id.nav_home, false) }
                )
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
            onAsignar = { handleAsignar(it) },
            onAtender = { handleAtender(it) },
            onResolver = { handleResolver(it) },
            onRevertir = { handleRevertir(it) }
        )

        b.recyclerViewAverias.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@AveriasFragment.adapter
        }

        // Usuario actual — actualiza adapter y visibilidad de filtros por rol
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.usuarioActual.collectLatest { user ->
                    adapter.currentUserUid = user?.uid
                    adapter.currentUserRegion = vm.resolveUserRegionLabel(user)
                    adapter.currentUserVehiculo = user?.placaVehiculo
                }
            }
        }

        // Chips de agencia rápida (subregión del usuario)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.agenciasSubregion.collectLatest { agencias ->
                    buildAgenciaChips(agencias)
                }
            }
        }

        // Sincronizar chip seleccionado con el estado del ViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.agenciaSeleccionada.collectLatest { agencia ->
                    syncAgenciaChipSelection(agencia.id)
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
                vm.fechaFiltroState.collectLatest { _ -> refreshFiltroResumenUI() }
            }
        }

        // Pull to refresh → Sync
        b.swipeRefresh.setOnRefreshListener {
            vm.syncNow()
        }

        // Toggles de estado coloridos
        data class ToggleDef(val btn: com.google.android.material.button.MaterialButton, val estado: Estado?, val colorRes: Int)
        val toggles = listOf(
            ToggleDef(b.btnToggleTodos, null, R.color.primary),
            ToggleDef(b.btnTogglePendiente, Estado.PENDIENTE, R.color.chip_pendiente),
            ToggleDef(b.btnToggleAsignada, Estado.ASIGNADA, R.color.chip_asignada),
            ToggleDef(b.btnToggleEnAtencion, Estado.EN_ATENCION, R.color.chip_en_atencion),
            ToggleDef(b.btnToggleResuelta, Estado.RESUELTA, R.color.chip_resuelta),
            ToggleDef(b.btnToggleAnulada, Estado.ANULADA, R.color.chip_anulada),
        )

        var selectedEstado: Estado? = null

        fun applyToggleSelection(selected: Estado?) {
            selectedEstado = selected
            vm.setEstado(selected)
            toggles.forEach { def ->
                val color = ContextCompat.getColor(requireContext(), def.colorRes)
                val isActive = def.estado == selected
                def.btn.backgroundTintList = if (isActive)
                    ColorStateList.valueOf(color)
                else
                    ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
                def.btn.setTextColor(if (isActive)
                    ContextCompat.getColor(requireContext(), android.R.color.white)
                else
                    color)
                def.btn.strokeColor = ColorStateList.valueOf(color)
            }
        }

        toggles.forEach { def ->
            def.btn.setOnClickListener { applyToggleSelection(def.estado) }
        }
        applyToggleSelection(null) // default: Todos

        if (savedInstanceState == null) {
            val estadoInicial = arguments?.getString(ARG_INITIAL_ESTADO)
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { Estado.valueOf(it) }.getOrNull() }
            if (estadoInicial != null) {
                applyToggleSelection(estadoInicial)
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
                        b.swipeRefresh.isRefreshing = state.loading
                        adapter.submitList(state.items)
                        b.tvVacio.visibility = if (state.items.isEmpty() && !state.loading) View.VISIBLE else View.GONE

                        val pendingId = pendingDeepLinkCaseId
                        if (!pendingId.isNullOrBlank() && state.items.isNotEmpty()) {
                            pendingDeepLinkCaseId = null
                            val index = state.items.indexOfFirst { it.id == pendingId }
                            if (index >= 0) {
                                b.recyclerViewAverias.scrollToPosition(index)
                                b.root.post { showDetalle(state.items[index]) }
                            }
                        }
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

        if (savedInstanceState == null) {
            vm.syncNow()
        }
    }
    private fun handleAtender(item: AveriaUI) {
        when (Estado.fromLabel(item.estado)) {
            ASIGNADA -> showDetalle(item)
            EN_ATENCION -> vm.onCancelarAtencion(item)
            else -> showDetalle(item)
        }
    }

    private fun handleAsignar(item: AveriaUI) {
        val estado = Estado.fromLabel(item.estado)
        if (estado != PENDIENTE || !vm.canAssignToCrew()) {
            vm.onToggleAsignacion(item)
            return
        }

        val options = arrayOf(
            getString(R.string.averia_asignar_mi),
            getString(R.string.averia_asignar_cuadrilla)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.averia_asignar)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> vm.assignToSelf(item)
                    1 -> showCrewSelectionDialog(item)
                }
            }
            .show()
    }

    private fun showCrewSelectionDialog(item: AveriaUI) {
        val vehiculos = vm.vehiculosDisponibles.value
            .map { it.placa.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        if (vehiculos.isEmpty()) {
            Snackbar.make(b.root, R.string.averia_asignar_cuadrilla_sin_camiones, Snackbar.LENGTH_LONG).show()
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.averia_asignar_cuadrilla)
            .setItems(vehiculos.toTypedArray()) { _, which ->
                vehiculos.getOrNull(which)?.let { selected ->
                    vm.assignToVehiculo(item, selected)
                }
            }
            .show()
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

        // Etiqueta de región del usuario
        val regionLabel = vm.userRegionLabel.value.takeIf { it.isNotBlank() }
            ?: getString(R.string.averia_notificacion_todas_regiones)
        sheetBinding.tvUserRegionLabel.text =
            getString(R.string.averia_notificacion_region_label, regionLabel)

        // Construir chips toggle con todas las agencias de la región
        val initialSelected = vm.notificationAgencies.value.map { it.lowercase() }.toSet()
        buildRegionToggleChips(sheetBinding, vm.notificationRegionAgencies.value, initialSelected)

        // Switch
        val switchListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
            vm.setNotificationsEnabled(isChecked)
        }
        val initialEnabled = vm.notificationsEnabled.value
        sheetBinding.switchNotifications.isChecked = initialEnabled
        applyNotificationEnabledState(sheetBinding, initialEnabled)
        sheetBinding.switchNotifications.setOnCheckedChangeListener(switchListener)

        // Reactivo: cambio de estado del switch
        scope.launch {
            vm.notificationsEnabled.collectLatest { enabled ->
                if (sheetBinding.switchNotifications.isChecked != enabled) {
                    sheetBinding.switchNotifications.setOnCheckedChangeListener(null)
                    sheetBinding.switchNotifications.isChecked = enabled
                    sheetBinding.switchNotifications.setOnCheckedChangeListener(switchListener)
                }
                applyNotificationEnabledState(sheetBinding, enabled)
            }
        }

        // Guardar: recolectar chips marcados y persistir
        sheetBinding.btnGuardarNotificaciones.setOnClickListener {
            val selected = mutableSetOf<String>()
            val group = sheetBinding.chipGroupRegionAgencias
            for (i in 0 until group.childCount) {
                val chip = group.getChildAt(i) as? Chip ?: continue
                if (chip.isChecked) selected.add(chip.text.toString())
            }
            vm.setNotificationAgencies(selected)
            Toast.makeText(requireContext(), R.string.settings_notifications_saved, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            notificationSheetScope?.cancel()
            notificationSheetScope = null
            notificationSheet = null
        }

        dialog.show()
    }


    // ─── Estado local del filtro de fecha+hora (dentro del sheet) ───
    private var filtroDateStart: Long? = null   // UTC millis del DatePicker
    private var filtroDateEnd: Long? = null     // UTC millis del DatePicker
    private var filtroHoraInicio: LocalTime? = null
    private var filtroHoraFin: LocalTime? = null

    private fun showFiltrosAvanzadosSheet() {
        filtrosSheet?.let {
            if (!it.isShowing) it.show()
            return
        }

        // Sincronizar estado local con el filtro activo del ViewModel
        val rangoActual = vm.fechaFiltroState.value
        if (rangoActual != null) {
            val zone = ZoneId.systemDefault()
            filtroDateStart = rangoActual.inicioMillis
                .let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
            filtroDateEnd = (rangoActual.finExclusiveMillis - 1)
                .let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
            filtroHoraInicio = Instant.ofEpochMilli(rangoActual.inicioMillis).atZone(zone)
                .toLocalTime().let { if (it == LocalTime.MIDNIGHT) null else it }
            filtroHoraFin = Instant.ofEpochMilli(rangoActual.finExclusiveMillis - 1).atZone(zone)
                .toLocalTime().let { if (it == LocalTime.of(23, 59, 59)) null else it }
        }

        // Local sheet copies of hora state
        var filtroHoraInicioSheet: LocalTime? = filtroHoraInicio
        var filtroHoraFinSheet: LocalTime? = filtroHoraFin

        val sheetBinding = BottomsheetAveriasFiltrosBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(requireContext()).apply {
            setContentView(sheetBinding.root)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        filtrosSheetScope = scope
        filtrosSheet = dialog

        // ── Helpers para actualizar UI de fecha/hora en el sheet ──
        fun syncFechaBtn() {
            val startMs = filtroDateStart
            val endMs = filtroDateEnd
            if (startMs == null) {
                sheetBinding.btnFiltroFecha.text = getString(R.string.averias_filtro_fecha_todas)
                sheetBinding.btnFiltroClearFecha.visibility = View.GONE
            } else {
                val s = dateFormatter.format(Date(startMs))
                val e = dateFormatter.format(Date(endMs ?: startMs))
                sheetBinding.btnFiltroFecha.text = "$s → $e"
                sheetBinding.btnFiltroClearFecha.visibility = View.VISIBLE
            }
        }
        fun syncHoraBtn() {
            val fmt = "%02d:%02d"
            sheetBinding.btnFiltroHoraInicio.text = filtroHoraInicioSheet
                ?.let { getString(R.string.averias_hora_inicio_btn) + ": " + fmt.format(it.hour, it.minute) }
                ?: getString(R.string.averias_hora_inicio_btn)
            sheetBinding.btnFiltroHoraFin.text = filtroHoraFinSheet
                ?.let { getString(R.string.averias_hora_fin_btn) + ": " + fmt.format(it.hour, it.minute) }
                ?: getString(R.string.averias_hora_fin_btn)
        }
        syncFechaBtn()
        syncHoraBtn()

        // ── Botón Fecha ──
        sheetBinding.btnFiltroFecha.setOnClickListener {
            val builder = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText(getString(R.string.averias_filtrar_fecha))
            filtroDateStart?.let { s ->
                builder.setSelection(Pair(s, filtroDateEnd ?: s))
            }
            val picker = builder.build()
            picker.addOnPositiveButtonClickListener { sel ->
                filtroDateStart = sel.first
                filtroDateEnd = sel.second
                syncFechaBtn()
            }
            picker.show(parentFragmentManager, "fil_date")
        }
        sheetBinding.btnFiltroClearFecha.setOnClickListener {
            filtroDateStart = null
            filtroDateEnd = null
            filtroHoraInicio = null; filtroHoraFin = null
            filtroHoraInicioSheet = null; filtroHoraFinSheet = null
            syncFechaBtn()
            syncHoraBtn()
        }

        // ── Botón Hora Inicio ──
        sheetBinding.btnFiltroHoraInicio.setOnClickListener {
            val hora = filtroHoraInicioSheet ?: LocalTime.of(8, 0)
            TimePickerDialog(requireContext(), { _, h, m ->
                filtroHoraInicioSheet = LocalTime.of(h, m)
                filtroHoraInicio = filtroHoraInicioSheet
                syncHoraBtn()
            }, hora.hour, hora.minute, true).show()
        }

        // ── Botón Hora Final ──
        sheetBinding.btnFiltroHoraFin.setOnClickListener {
            val hora = filtroHoraFinSheet ?: LocalTime.of(18, 0)
            TimePickerDialog(requireContext(), { _, h, m ->
                filtroHoraFinSheet = LocalTime.of(h, m)
                filtroHoraFin = filtroHoraFinSheet
                syncHoraBtn()
            }, hora.hour, hora.minute, true).show()
        }

        // ── Agencia ──
        val agencias = vm.agencias.value.map { it.nombreVisible }
        sheetBinding.actvFiltroAgencia.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, agencias)
        )
        sheetBinding.actvFiltroAgencia.setText(vm.agenciaSeleccionada.value.nombreVisible, false)
        sheetBinding.actvFiltroAgencia.setOnItemClickListener { _, _, pos, _ -> vm.setAgenciaIndex(pos) }

        // ── Región ──
        val regiones = vm.regiones.value.map { it.nombreVisible }
        sheetBinding.actvFiltroRegion.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, regiones)
        )
        sheetBinding.actvFiltroRegion.setText(vm.regionSeleccionada.value.nombreVisible, false)
        sheetBinding.actvFiltroRegion.setOnItemClickListener { _, _, pos, _ -> vm.setRegionIndex(pos) }

        // ── Visibilidad por rol ──
        val rol = vm.usuarioActual.value?.rol?.trim()?.lowercase(Locale.getDefault()).orEmpty()
        val isSupervisorOrAdmin = rol == "supervisor" || rol == "administrador"
        sheetBinding.cardFiltroAgencia.visibility =
            if (isSupervisorOrAdmin) View.VISIBLE else View.GONE
        sheetBinding.cardFiltroRegion.visibility =
            if (rol == "administrador") View.VISIBLE else View.GONE

        // ── Pre-selección de región/agencia del usuario (solo técnicos) ──
        if (!isSupervisorOrAdmin) {
            val userRegionIdx = vm.getUserDefaultRegionIndex()
            if (userRegionIdx > 0) {
                vm.setRegionIndex(userRegionIdx)
                scope.launch {
                    vm.agencias.collectLatest { updatedAgencias ->
                        val agenciaIdx = vm.getUserDefaultAgenciaIndex()
                        val agenciasNames = updatedAgencias.map { it.nombreVisible }
                        sheetBinding.actvFiltroAgencia.setAdapter(
                            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, agenciasNames)
                        )
                        sheetBinding.actvFiltroAgencia.setText(
                            agenciasNames.getOrElse(agenciaIdx) { agenciasNames.firstOrNull().orEmpty() }, false
                        )
                    }
                }
            }
        }

        // ── Limpiar todo ──
        sheetBinding.btnFiltroLimpiarTodo.setOnClickListener {
            filtroDateStart = null; filtroDateEnd = null
            filtroHoraInicio = null; filtroHoraFin = null
            filtroHoraInicioSheet = null; filtroHoraFinSheet = null
            syncFechaBtn(); syncHoraBtn()
            vm.clearFechaFiltro()
            vm.clearHoraFiltro()
            vm.setRegionIndex(0); vm.setAgenciaIndex(0)
            sheetBinding.actvFiltroAgencia.setText(agencias.firstOrNull().orEmpty(), false)
            sheetBinding.actvFiltroRegion.setText(regiones.firstOrNull().orEmpty(), false)
        }

        // ── Aplicar ──
        sheetBinding.btnFiltroAplicar.setOnClickListener {
            applyFiltroFechaHora()
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            filtrosSheetScope?.cancel()
            filtrosSheetScope = null
            filtrosSheet = null
            refreshFiltroResumenUI()
        }

        dialog.show()
    }

    private fun applyFiltroFechaHora() {
        val startDateMs = filtroDateStart
        val horaInicio = filtroHoraInicio
        val horaFin = filtroHoraFin

        // Aplicar filtro de hora independientemente de la fecha
        if (horaInicio != null && horaFin != null) {
            vm.setHoraFiltro(horaInicio.hour, horaInicio.minute, horaFin.hour, horaFin.minute)
        } else {
            vm.clearHoraFiltro()
        }

        // Filtro de fecha (con o sin hora integrada)
        if (startDateMs == null) {
            // Si solo hay hora, limpiar fecha (la hora sola filtra por hora del día)
            if (horaInicio == null) vm.clearFechaFiltro()
            return
        }

        val zone = ZoneId.systemDefault()
        val startDate = Instant.ofEpochMilli(startDateMs).atZone(ZoneOffset.UTC).toLocalDate()
        val endDateMs = filtroDateEnd
        val endDate = endDateMs?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: startDate

        // Si hay hora integrada en fecha, usar precisos; si no, usar inicio/fin de día
        if (horaInicio != null || horaFin != null) {
            val inicioMs = (horaInicio ?: LocalTime.MIDNIGHT).let {
                startDate.atTime(it).atZone(zone).toInstant().toEpochMilli()
            }
            val finMs = (horaFin ?: LocalTime.of(23, 59, 59)).plusSeconds(1).let {
                endDate.atTime(it).atZone(zone).toInstant().toEpochMilli()
            }
            vm.setFechaFiltroPreciso(inicioMs, finMs)
        } else {
            vm.setFechaFiltroPreciso(
                startDate.atStartOfDay(zone).toInstant().toEpochMilli(),
                endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            )
        }
    }

    private fun refreshFiltroResumenUI() {
        val range = vm.fechaFiltroState.value
        val hayRegion = !vm.regionSeleccionada.value.id.isNullOrEmpty()
            && !vm.regionSeleccionada.value.nombreVisible.contains("todas", ignoreCase = true)
        val hayAgencia = !vm.agenciaSeleccionada.value.id.isNullOrEmpty()
            && !vm.agenciaSeleccionada.value.nombreVisible.contains("todas", ignoreCase = true)

        val partes = buildList {
            if (range != null) {
                val s = dateFormatter.format(Date(range.inicioMillis))
                val e = dateFormatter.format(Date((range.finExclusiveMillis - 1).coerceAtLeast(range.inicioMillis)))
                add("📅 $s → $e")
            }
            if (hayAgencia) add("🏢 ${vm.agenciaSeleccionada.value.nombreVisible}")
            if (hayRegion) add("🗺️ ${vm.regionSeleccionada.value.nombreVisible}")
        }

        val hayFiltros = partes.isNotEmpty()
        b.tvFiltroResumen.visibility = if (hayFiltros) View.VISIBLE else View.GONE
        b.tvFiltroResumen.text = partes.joinToString("  ·  ")
        actualizarBotoFiltroEstado(hayFiltros, partes.size)
    }

    private fun actualizarBotoFiltroEstado(activo: Boolean, count: Int) {
        val ctx = requireContext()
        if (activo) {
            b.btnFiltrosAvanzados.text = getString(R.string.averias_filtros_n_activos, count)
            b.btnFiltrosAvanzados.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(ctx, R.color.primary_container)
            )
            b.btnFiltrosAvanzados.setTextColor(
                ContextCompat.getColor(ctx, R.color.on_primary_container)
            )
            b.btnFiltrosAvanzados.iconTint = ColorStateList.valueOf(
                ContextCompat.getColor(ctx, R.color.on_primary_container)
            )
        } else {
            b.btnFiltrosAvanzados.text = getString(R.string.averias_filtros_avanzados)
            b.btnFiltrosAvanzados.backgroundTintList = ColorStateList.valueOf(
                android.graphics.Color.TRANSPARENT
            )
            b.btnFiltrosAvanzados.setTextColor(
                ContextCompat.getColor(ctx, R.color.primary)
            )
            b.btnFiltrosAvanzados.iconTint = ColorStateList.valueOf(
                ContextCompat.getColor(ctx, R.color.primary)
            )
        }
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

    private fun buildRegionToggleChips(
        sheetBinding: DialogNotificationFiltersBinding,
        regionAgencies: List<AgenciaUI>,
        selectedAgencies: Set<String>
    ) {
        val group = sheetBinding.chipGroupRegionAgencias
        group.removeAllViews()
        if (regionAgencies.isEmpty()) {
            sheetBinding.tvNotificationFiltersEmpty.visibility = View.VISIBLE
            return
        }
        sheetBinding.tvNotificationFiltersEmpty.visibility = View.GONE
        regionAgencies.forEach { agencia ->
            val nombre = agencia.nombreVisible
            val chip = Chip(requireContext()).apply {
                text = nombre
                isCheckable = true
                isChecked = selectedAgencies.contains(nombre.lowercase())
            }
            group.addView(chip)
        }
    }

    // ─── Chips de agencia rápida ───────────────────────────────────────────────

    private fun buildAgenciaChips(agencias: List<AgenciaUI>) {
        val group = b.chipGroupAgenciasRapidas
        group.removeAllViews()

        val hasAgencias = agencias.isNotEmpty()
        b.rowAgenciasRapidas.isVisible = hasAgencias
        if (!hasAgencias) return

        // Chip "Todas"
        val chipTodas = Chip(requireContext()).apply {
            text = getString(R.string.averias_agencia_rapida_todas)
            isCheckable = true
            isChecked = vm.agenciaSeleccionada.value.id == null
            chipBackgroundColor = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(
                    ContextCompat.getColor(requireContext(), R.color.primary),
                    ContextCompat.getColor(requireContext(), android.R.color.transparent)
                )
            )
            setTextColor(ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(
                    ContextCompat.getColor(requireContext(), android.R.color.white),
                    ContextCompat.getColor(requireContext(), R.color.primary)
                )
            ))
        }
        chipTodas.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                vm.setAgenciaIndex(0) // índice 0 = "Todas las agencias"
            }
        }
        group.addView(chipTodas)

        agencias.forEach { agencia ->
            val chip = Chip(requireContext()).apply {
                text = agencia.nombreVisible
                tag = agencia.id
                isCheckable = true
                chipBackgroundColor = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(
                        ContextCompat.getColor(requireContext(), R.color.primary),
                        ContextCompat.getColor(requireContext(), android.R.color.transparent)
                    )
                )
                setTextColor(ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(
                        ContextCompat.getColor(requireContext(), android.R.color.white),
                        ContextCompat.getColor(requireContext(), R.color.primary)
                    )
                ))
            }
            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    val idx = vm.agencias.value.indexOfFirst { it.id == agencia.id }
                    if (idx >= 0) vm.setAgenciaIndex(idx)
                } else {
                    // El chip se desmarcó (el usuario tocó el ya seleccionado); volver a "Todas"
                    vm.setAgenciaIndex(0)
                }
            }
            group.addView(chip)
        }
    }

    private fun syncAgenciaChipSelection(agenciaId: String?) {
        val group = b.chipGroupAgenciasRapidas
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as? Chip ?: continue
            chip.isChecked = if (agenciaId == null) {
                i == 0 // primer chip = "Todas"
            } else {
                chip.tag == agenciaId
            }
        }
    }

    private fun applyNotificationEnabledState(
        sheetBinding: DialogNotificationFiltersBinding,
        enabled: Boolean
    ) {
        val alpha = if (enabled) 1f else 0.5f
        sheetBinding.tvNotificationFilterTitle.alpha = alpha
        sheetBinding.tvNotificationSwitchHelper.text = if (enabled) {
            getString(R.string.averia_notificacion_selecciona_agencias)
        } else {
            getString(R.string.averia_notificacion_filtro_desactivado)
        }
        val group = sheetBinding.chipGroupRegionAgencias
        group.alpha = alpha
        for (i in 0 until group.childCount) {
            (group.getChildAt(i) as? Chip)?.isEnabled = enabled
        }
        sheetBinding.tvNotificationFiltersEmpty.alpha = alpha
    }


    override fun onDestroyView() {
        notificationSheet?.setOnDismissListener(null)
        notificationSheet?.dismiss()
        notificationSheet = null
        notificationSheetScope?.cancel()
        notificationSheetScope = null
        filtrosSheet?.setOnDismissListener(null)
        filtrosSheet?.dismiss()
        filtrosSheet = null
        filtrosSheetScope?.cancel()
        filtrosSheetScope = null
        _b = null
        super.onDestroyView()
    }
}