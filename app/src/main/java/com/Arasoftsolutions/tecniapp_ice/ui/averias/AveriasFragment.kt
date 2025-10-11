package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CompoundButton
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

class AveriasFragment : Fragment() {

    private var _b: FragmentAveriasBinding? = null
    private val b get() = _b!!

    private val vm: AveriasViewModel by viewModels()
    private lateinit var adapter: AveriasAdapter
    private var notificationSheet: BottomSheetDialog? = null
    private var notificationSheetScope: CoroutineScope? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentAveriasBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.toolbar.title = getString(R.string.averias_title)
        b.toolbar.inflateMenu(R.menu.menu_averias)
        b.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_notification_filters -> {
                    showNotificationFiltersSheet()
                    true
                }
                else -> false
            }
        }

        // Configurar FAB para mostrar filtros
        b.fabFilters.setOnClickListener {
            b.appBarLayout.setExpanded(true, true) // Expande el AppBar
        }

        // Ocultar FAB cuando los filtros están visibles
        b.appBarLayout.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
            val isExpanded = Math.abs(verticalOffset) < appBarLayout.totalScrollRange
            b.fabFilters.visibility = if (isExpanded) View.GONE else View.VISIBLE
        }

        b.etBuscar.addTextChangedListener { text ->
            vm.setQuery(text?.toString().orEmpty())
        }

        val notificationSwitchListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
            vm.setNotificationsEnabled(isChecked)
        }
        b.switchNotifications.setOnCheckedChangeListener(notificationSwitchListener)

        b.actvNotificationAgency.setOnItemClickListener { parent, _, position, _ ->
            val value = parent.getItemAtPosition(position)?.toString()?.trim().orEmpty()
            if (value.isNotEmpty()) {
                vm.addNotificationAgency(value)
                b.actvNotificationAgency.setText("", false)
            }
        }
        b.actvNotificationAgency.setOnEditorActionListener { textView, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val value = textView.text?.toString()?.trim().orEmpty()
                if (value.isNotEmpty()) {
                    vm.addNotificationAgency(value)
                    textView.text = null
                }
                true
            } else {
                false
            }
        }

        // Recycler
        adapter = AveriasAdapter(
            onVerDetalle = { showDetalle(it) },
            onAsignar = { vm.onToggleAsignacion(it) },
            onAtender = { handleAtender(it) },
            onResolver = { handleResolver(it) }
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
                    adapter.notifyDataSetChanged()
                }
            }
        }

        // Pull to refresh → Sync
        b.swipeRefresh.setOnRefreshListener {
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
                else -> null
            }
            vm.setEstado(state)
        }
        b.chipGroupEstado.check(b.chipTodos.id)

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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.notificationSuggestions.collectLatest { sugerencias ->
                        b.actvNotificationAgency.setAdapter(
                            ArrayAdapter(
                                requireContext(),
                                android.R.layout.simple_dropdown_item_1line,
                                sugerencias
                            )
                        )
                    }
                }
                launch {
                    vm.notificationsEnabled.collectLatest { enabled ->
                        if (b.switchNotifications.isChecked != enabled) {
                            b.switchNotifications.setOnCheckedChangeListener(null)
                            b.switchNotifications.isChecked = enabled
                            b.switchNotifications.setOnCheckedChangeListener(notificationSwitchListener)
                        }
                        val alpha = if (enabled) 1f else 0.6f
                        b.tilNotificationAgency.alpha = alpha
                        b.tvNotificationFilterTitle.alpha = alpha
                        b.tvNotificationSwitchHelper.alpha = alpha
                        renderNotificationChips(vm.notificationAgencies.value, enabled)
                    }
                }
                launch {
                    vm.notificationAgencies.collectLatest { agencias ->
                        renderNotificationChips(agencias, vm.notificationsEnabled.value)
                    }
                }
            }
        }

        // Observa estado UI y mensajes
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.uiState.collectLatest { state ->
                        b.swipeRefresh.isRefreshing = state.loading
                        adapter.submitList(state.items)
                        b.tvVacio.visibility = if (state.items.isEmpty() && !state.loading) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    vm.messages.collectLatest { message ->
                        Snackbar.make(b.root, message, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }

        // Sincronizar datos iniciales
        vm.syncNow()
    }
    private fun handleAtender(item: AveriaUI) {
        when (Estado.fromLabel(item.estado)) {
            Estado.ASIGNADA -> showDetalle(item)
            Estado.EN_ATENCION -> vm.onCancelarAtencion(item)
            else -> showDetalle(item)
        }
    }

    private fun handleResolver(item: AveriaUI) {
        when (Estado.fromLabel(item.estado)) {
            Estado.EN_ATENCION -> showDetalle(item)
            Estado.RESUELTA -> viewLifecycleOwner.lifecycleScope.launch {
                PdfGenerator.exportAveria(requireContext(), item)
            }
            else -> showDetalle(item)
        }
    }

    private fun showNotificationFiltersSheet() {
        if (notificationSheet?.isShowing == true) return

        notificationSheet?.setOnDismissListener(null)
        notificationSheet?.dismiss()
        notificationSheetScope?.cancel()

        val sheetBinding = DialogNotificationFiltersBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(sheetBinding.root)

        val sheetScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
        notificationSheetScope = sheetScope
        notificationSheet = dialog

        val suggestionsAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            vm.notificationSuggestions.value.toMutableList()
        )
        sheetBinding.actvNotificationAgency.setAdapter(suggestionsAdapter)

        sheetBinding.actvNotificationAgency.setOnItemClickListener { parent, _, position, _ ->
            val value = parent.getItemAtPosition(position)?.toString()?.trim().orEmpty()
            if (value.isNotEmpty()) {
                vm.addNotificationAgency(value)
                sheetBinding.actvNotificationAgency.setText("", false)
            }
        }
        sheetBinding.actvNotificationAgency.setOnEditorActionListener { textView, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val value = textView.text?.toString()?.trim().orEmpty()
                if (value.isNotEmpty()) {
                    vm.addNotificationAgency(value)
                    textView.text = null
                }
                true
            } else {
                false
            }
        }

        val switchListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
            vm.setNotificationsEnabled(isChecked)
        }

        var currentEnabled = vm.notificationsEnabled.value
        sheetBinding.switchNotifications.setOnCheckedChangeListener(null)
        sheetBinding.switchNotifications.isChecked = currentEnabled
        sheetBinding.switchNotifications.setOnCheckedChangeListener(switchListener)

        applyNotificationEnabledState(sheetBinding, currentEnabled)
        renderNotificationChips(sheetBinding, vm.notificationAgencies.value, currentEnabled)

        sheetScope.launch {
            vm.notificationSuggestions.collectLatest { sugerencias ->
                suggestionsAdapter.clear()
                suggestionsAdapter.addAll(sugerencias)
                suggestionsAdapter.notifyDataSetChanged()
            }
        }
        sheetScope.launch {
            vm.notificationsEnabled.collectLatest { enabled ->
                currentEnabled = enabled
                if (sheetBinding.switchNotifications.isChecked != enabled) {
                    sheetBinding.switchNotifications.setOnCheckedChangeListener(null)
                    sheetBinding.switchNotifications.isChecked = enabled
                    sheetBinding.switchNotifications.setOnCheckedChangeListener(switchListener)
                }
                applyNotificationEnabledState(sheetBinding, enabled)
                renderNotificationChips(sheetBinding, vm.notificationAgencies.value, enabled)
            }
        }
        sheetScope.launch {
            vm.notificationAgencies.collectLatest { agencias ->
                renderNotificationChips(sheetBinding, agencias, currentEnabled)
            }
        }

        dialog.setOnDismissListener {
            notificationSheetScope?.cancel()
            notificationSheetScope = null
            notificationSheet = null
        }

        dialog.show()
    }

    private fun showDetalle(item: AveriaUI) {
        AveriaDetalleBottomSheet.newInstance(item).show(childFragmentManager, "detalle_averia")
    }

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
        sheetBinding.chipGroupNotificationAgencies.alpha = if (notificationsEnabled) 1f else 0.6f
    }

    private fun applyNotificationEnabledState(
        sheetBinding: DialogNotificationFiltersBinding,
        enabled: Boolean
    ) {
        val alpha = if (enabled) 1f else 0.6f
        sheetBinding.tilNotificationAgency.alpha = alpha
        sheetBinding.tvNotificationFilterTitle.alpha = alpha
        sheetBinding.tvNotificationSwitchHelper.alpha = alpha
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
