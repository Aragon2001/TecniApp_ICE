package com.Arasoftsolutions.tecniapp_ice.ui.averias

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
import com.google.android.material.search.SearchView
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
        // Toolbar + Search
        b.toolbar.setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        b.searchBar.setOnClickListener { b.searchView.show() }
        setupSearch()

        // Recycler
        adapter = AveriasAdapter(
            onVerDetalle = { vm.onVerDetalle(childFragmentManager, it) },
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

        // Chips estado
        b.chipTodos.setOnClickListener { vm.setEstado(null) }
        b.chipPendiente.setOnClickListener { vm.setEstado(Estado.PENDIENTE) }
        b.chipAsignada.setOnClickListener { vm.setEstado(Estado.ASIGNADA) }
        b.chipEnAtencion.setOnClickListener { vm.setEstado(Estado.EN_ATENCION) }
        b.chipResuelta.setOnClickListener { vm.setEstado(Estado.RESUELTA) }

        // Dropdown Zonas
        viewLifecycleOwner.lifecycleScope.launch {
            vm.zonas.collectLatest { zonas ->
                val nombres = listOf("Todas") + zonas.map { it.nombreVisible }
                b.actvZona.setAdapter(ArrayAdapter(requireContext(), R.layout.list_item_dropdown, nombres))
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

    private fun setupSearch() {
        val sv: SearchView = b.searchView
        sv.editText.addTextChangedListener { vm.setQuery(it?.toString().orEmpty()) }
        sv.addTransitionListener { _, _, newState ->
            if (newState == SearchView.TransitionState.HIDDEN) {
                b.searchBar.text = sv.text
            }
        }
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
