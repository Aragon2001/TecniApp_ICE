package com.Arasoftsolutions.tecniapp_ice.ui.programacion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentProgramacionBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class ProgramacionFragment : Fragment() {
    private var _binding: FragmentProgramacionBinding? = null
    private val binding: FragmentProgramacionBinding
        get() = requireNotNull(_binding)

    private val viewModel: ProgramacionViewModel by viewModels()
    private val adapter = ProgramacionAdapter { item ->
        ProgramacionDetalleBottomSheet.newInstance(item.programacionId)
            .show(childFragmentManager, "programacionDetalle")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProgramacionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvProgramaciones.layoutManager = LinearLayoutManager(requireContext())
        binding.rvProgramaciones.adapter = adapter

        val estados = listOf("TODOS", ProgramacionRepository.ESTADO_PENDIENTE, ProgramacionRepository.ESTADO_EN_PROCESO, ProgramacionRepository.ESTADO_EJECUTADA)
        binding.spinnerEstado.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, estados)
        binding.spinnerEstado.setSelection(0)
        binding.spinnerEstado.setOnItemSelectedListener(SimpleItemSelectedListener { pos ->
            viewModel.setEstadoFilter(estados.getOrNull(pos)?.takeUnless { it == "TODOS" })
        })

        binding.swipeRefresh.setOnRefreshListener { viewModel.sync() }
        binding.fabNueva.setOnClickListener {
            findNavController().navigate(R.id.action_nav_programacion_to_nuevaProgramacionFragment)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.swipeRefresh.isRefreshing = state.loading
                    binding.fabNueva.isVisible = state.isSupervisor
                    adapter.submitList(state.items)
                    binding.tvEmpty.isVisible = state.items.isEmpty() && !state.loading
                    if (state.message != null) {
                        Snackbar.make(binding.root, state.message, Snackbar.LENGTH_SHORT).show()
                        viewModel.clearMessage()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
