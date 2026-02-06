package com.Arasoftsolutions.tecniapp_ice.ui.planillas.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Arasoftsolutions.tecniapp_ice.ui.planillas.model.PlanillaModeFilter
import com.Arasoftsolutions.tecniapp_ice.ui.planillas.model.PlanillaModeUi
import com.Arasoftsolutions.tecniapp_ice.ui.planillas.model.PlanillasHomeUiState
import com.Arasoftsolutions.tecniapp_ice.ui.planillas.model.PlanillasIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

class PlanillasViewModel : ViewModel() {
    private val _state = MutableStateFlow(PlanillasHomeUiState())
    val state: StateFlow<PlanillasHomeUiState> = _state.asStateFlow()

    fun onIntent(intent: PlanillasIntent) {
        when (intent) {
            is PlanillasIntent.OnDateSelected -> updateDate(intent.date)
            is PlanillasIntent.OnSearchQuery -> updateSearch(intent.query)
            is PlanillasIntent.OnFilterCircuito -> updateCircuito(intent.id)
            is PlanillasIntent.OnFilterAgencia -> updateAgencia(intent.id)
            is PlanillasIntent.OnFilterModulo -> updateModulo(intent.id)
            is PlanillasIntent.OnModeFilter -> updateMode(intent.filter)
            is PlanillasIntent.OnGeneratePdf -> generatePdf(intent.mode)
            is PlanillasIntent.OnRetrySync -> retrySync(intent.entityId)
            is PlanillasIntent.OnOpenPlanilla -> openPlanilla(intent.id)
            PlanillasIntent.OnRefresh -> refresh()
        }
    }

    private fun updateDate(date: LocalDate) {
        _state.update { it.copy(selectedDate = date) }
        refresh()
    }

    private fun updateSearch(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    private fun updateCircuito(id: String?) {
        _state.update { it.copy(selectedCircuito = id) }
    }

    private fun updateAgencia(id: String?) {
        _state.update { it.copy(selectedAgencia = id) }
    }

    private fun updateModulo(id: String?) {
        _state.update { it.copy(selectedModulo = id) }
    }

    private fun updateMode(filter: PlanillaModeFilter) {
        _state.update { it.copy(modeFilter = filter) }
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            // TODO: cargar desde Room + combinar estado de sync
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun generatePdf(mode: PlanillaModeUi) {
        viewModelScope.launch {
            // TODO: disparar generación de PDF en repositorio offline-first
        }
    }

    private fun retrySync(entityId: String) {
        viewModelScope.launch {
            // TODO: encolar reintento de sync
        }
    }

    private fun openPlanilla(id: String) {
        // TODO: navegación desde el host
    }
}
