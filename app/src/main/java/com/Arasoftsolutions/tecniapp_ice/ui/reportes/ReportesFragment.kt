package com.Arasoftsolutions.tecniapp_ice.ui.reportes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.util.Pair
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentReportesBinding
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

class ReportesFragment : Fragment() {

    private var _binding: FragmentReportesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReportesViewModel by viewModels()

    private lateinit var averiasAdapter: AveriasReportAdapter
    private lateinit var materialesPorAveriaAdapter: MaterialesPorAveriaAdapter
    private lateinit var materialTotalAdapter: MaterialTotalAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAdapters()
        setupListeners()
        observeState()
    }

    private fun setupAdapters() {
        averiasAdapter = AveriasReportAdapter()
        binding.recyclerAverias.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = averiasAdapter
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }

        materialesPorAveriaAdapter = MaterialesPorAveriaAdapter()
        binding.recyclerMaterialPorAveria.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = materialesPorAveriaAdapter
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }

        materialTotalAdapter = MaterialTotalAdapter()
        binding.recyclerMaterialTotal.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = materialTotalAdapter
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }
    }

    private fun setupListeners() {
        binding.btnCambiarFechas.setOnClickListener { mostrarSelectorRango() }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressIndicator.isVisible = state.isLoading
                    binding.tvRangoFechas.text = state.rangoTexto
                    binding.tvResumenTotales.text = getString(
                        R.string.reportes_totales_resumen,
                        state.totalAverias,
                        state.totalMateriales,
                        state.totalMaterialesDistintos
                    )

                    averiasAdapter.submitList(state.averias)
                    binding.recyclerAverias.isVisible = state.averias.isNotEmpty()
                    binding.tvAveriasVacio.isVisible = state.averias.isEmpty()

                    materialesPorAveriaAdapter.submitList(state.materialesPorAveria)
                    binding.recyclerMaterialPorAveria.isVisible = state.materialesPorAveria.isNotEmpty()
                    binding.tvMaterialPorAveriaVacio.isVisible = state.materialesPorAveria.isEmpty()

                    materialTotalAdapter.submitList(state.materialesTotales)
                    binding.recyclerMaterialTotal.isVisible = state.materialesTotales.isNotEmpty()
                    binding.tvMaterialTotalVacio.isVisible = state.materialesTotales.isEmpty()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.messages.collect { message ->
                if (!isAdded) return@collect
                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun mostrarSelectorRango() {
        val state = viewModel.uiState.value
        val tag = "reportes_rango"
        if (childFragmentManager.findFragmentByTag(tag) != null) return

        val selection = Pair(
            state.fechaInicio.toStartOfDayUtcMillis(),
            state.fechaFin.toStartOfDayUtcMillis()
        )

        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(getString(R.string.reportes_dialog_rango_titulo))
            .setSelection(selection)
            .build()

        picker.addOnPositiveButtonClickListener { range ->
            val start = range.first
            val end = range.second
            if (start != null && end != null) {
                val inicio = start.toLocalDate()
                val fin = end.toLocalDate()
                viewModel.actualizarRangoFechas(inicio, fin)
            }
        }

        picker.show(childFragmentManager, tag)
    }

    private fun LocalDate.toStartOfDayUtcMillis(): Long =
        this.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun Long.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
