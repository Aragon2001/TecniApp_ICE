package com.Arasoftsolutions.tecniapp_ice.ui.planillas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentPlanillasHomeBinding
import com.Arasoftsolutions.tecniapp_ice.ui.planillas.adapter.ActivityGroupsAdapter
import com.Arasoftsolutions.tecniapp_ice.ui.planillas.adapter.PlanillasAdapter
import com.Arasoftsolutions.tecniapp_ice.ui.planillas.model.PlanillaModeFilter
import com.Arasoftsolutions.tecniapp_ice.ui.planillas.model.PlanillaModeUi
import com.Arasoftsolutions.tecniapp_ice.ui.planillas.model.PlanillasIntent
import com.Arasoftsolutions.tecniapp_ice.ui.planillas.viewmodel.PlanillasViewModel
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PlanillasHomeFragment : Fragment() {
    private var _binding: FragmentPlanillasHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlanillasViewModel by viewModels()

    private val planillasAdapter = PlanillasAdapter(
        onOpen = { planillaId ->
            viewModel.onIntent(PlanillasIntent.OnOpenPlanilla(planillaId))
        },
        onRetry = { planillaId ->
            viewModel.onIntent(PlanillasIntent.OnRetrySync(planillaId))
        }
    )

    private val activityGroupsAdapter = ActivityGroupsAdapter(
        onOpen = { groupId ->
            // TODO: navegar a WorkLogsDetail
        },
        onRetry = { groupId ->
            viewModel.onIntent(PlanillasIntent.OnRetrySync(groupId))
        }
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlanillasHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.planillasRecycler.adapter = planillasAdapter
        binding.activityGroupsRecycler.adapter = activityGroupsAdapter

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.onIntent(PlanillasIntent.OnRefresh)
        }

        binding.segmentedPersona.setOnClickListener {
            viewModel.onIntent(PlanillasIntent.OnModeFilter(PlanillaModeFilter.PERSONA))
        }
        binding.segmentedCuadrilla.setOnClickListener {
            viewModel.onIntent(PlanillasIntent.OnModeFilter(PlanillaModeFilter.CUADRILLA))
        }
        binding.segmentedTodas.setOnClickListener {
            viewModel.onIntent(PlanillasIntent.OnModeFilter(PlanillaModeFilter.ALL))
        }

        binding.generatePdfButton.setOnClickListener {
            viewModel.onIntent(PlanillasIntent.OnGeneratePdf(PlanillaModeUi.PERSONA))
        }

        binding.dateFilterButton.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Seleccionar fecha")
                .build()
            picker.addOnPositiveButtonClickListener { millis ->
                val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                viewModel.onIntent(PlanillasIntent.OnDateSelected(date))
            }
            picker.show(parentFragmentManager, "planillasDatePicker")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.swipeRefresh.isRefreshing = state.isLoading
                    binding.emptyState.isVisible = !state.isLoading && state.planillas.isEmpty()
                    binding.offlineState.isVisible = state.isOffline
                    binding.errorState.isVisible = state.errorMessage != null

                    binding.resumenHoras.text = state.resumen.totalHoras.toString()
                    binding.resumenGroups.text = state.resumen.totalGroups.toString()
                    binding.resumenConfirmados.text = "${state.resumen.porcentajeConfirmados}%"

                    planillasAdapter.submitList(state.planillas)
                    activityGroupsAdapter.submitList(state.activityGroups)
                    binding.activityGroupsSection.isVisible = state.canManageGroups
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
