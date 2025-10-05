package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
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
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AveriasFragment : Fragment() {

    private var _b: FragmentAveriasBinding? = null
    private val b get() = _b!!

    private val vm: AveriasViewModel by viewModels()
    private lateinit var adapter: AveriasAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentAveriasBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
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

    private fun showDetalle(item: AveriaUI) {
        AveriaDetalleBottomSheet.newInstance(item).show(childFragmentManager, "detalle_averia")
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        vm.syncNow()
    }
}
