package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.content.Intent
import android.net.Uri
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
        b.toolbar.setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        b.etBuscar.addTextChangedListener { vm.setQuery(it?.toString().orEmpty()) }

        // Recycler
        adapter = AveriasAdapter(
            onVerDetalle = { openMap(it) },
            onAsignar = { vm.onAsignar(it) },
            onAtender = { vm.onAtender(it) }
        )
        b.recyclerViewAverias.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@AveriasFragment.adapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }

        // Pull to refresh → Sync
        b.swipeRefresh.setOnRefreshListener { vm.syncNow() }

        b.chipGroupEstado.setOnCheckedStateChangeListener { _, checkedIds ->
            val state = when (checkedIds.firstOrNull()) {
                b.chipPendiente.id -> Estado.PENDIENTE
                b.chipAsignada.id -> Estado.ASIGNADA
                b.chipEnAtencion.id -> Estado.EN_ATENCION
                b.chipResuelta.id -> Estado.RESUELTA
                else -> null
            }
            vm.setEstado(state)
        }

        // Dropdown Zonas
        viewLifecycleOwner.lifecycleScope.launch {
            vm.zonas.collectLatest { zonas ->
                val nombres = listOf("Todas") + zonas.map { it.nombreVisible }
                b.actvZona.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, nombres))
                b.actvZona.setText("Todas", false)
            }
        }
        b.actvZona.setOnItemClickListener { _, _, position, _ -> vm.setZonaIndex(position) }

        // Observa estado UI
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.uiState.collectLatest { state ->
                        b.swipeRefresh.isRefreshing = state.loading
                        adapter.submitList(state.items)
                        b.tvVacio.visibility = if (state.items.isEmpty() && !state.loading) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun openMap(item: AveriaUI) {
        if (item.lat == 0.0 && item.lng == 0.0) return
        val uri = Uri.parse("geo:${item.lat},${item.lng}?q=${item.lat},${item.lng}")
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
