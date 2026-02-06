package com.Arasoftsolutions.tecniapp_ice.ui.planillas.model

import java.time.LocalDate

enum class SyncStatusUi {
    PENDING,
    SYNCING,
    SYNCED,
    ERROR
}

enum class PlanillaModeUi {
    PERSONA,
    CUADRILLA
}

enum class PlanillaModeFilter {
    ALL,
    PERSONA,
    CUADRILLA
}

data class ResumenUi(
    val totalHoras: Int = 0,
    val totalGroups: Int = 0,
    val topOrdenes: List<String> = emptyList(),
    val porcentajeConfirmados: Int = 0
)

data class PlanillaUi(
    val planillaId: String,
    val fecha: String,
    val mode: PlanillaModeUi,
    val ownerUid: String,
    val totalHoras: Int,
    val syncStatus: SyncStatusUi,
    val groupIds: List<String>,
    val canViewPdf: Boolean
)

data class ActivityGroupUi(
    val groupId: String,
    val ordenSap: String,
    val tecnico: String,
    val fecha: String,
    val syncStatus: SyncStatusUi
)

data class WorkLogUi(
    val logId: String,
    val ordenSap: String,
    val horaEntrada: String,
    val horaSalida: String,
    val pausaMin: Int,
    val horasOrdMin: Int,
    val observaciones: String?,
    val updatedAt: String,
    val updatedBy: String,
    val syncStatus: SyncStatusUi
)

data class PlanillasHomeUiState(
    val isLoading: Boolean = false,
    val isOffline: Boolean = false,
    val errorMessage: String? = null,
    val selectedDate: LocalDate = LocalDate.now(),
    val modeFilter: PlanillaModeFilter = PlanillaModeFilter.ALL,
    val searchQuery: String = "",
    val selectedCircuito: String? = null,
    val selectedAgencia: String? = null,
    val selectedModulo: String? = null,
    val resumen: ResumenUi = ResumenUi(),
    val planillas: List<PlanillaUi> = emptyList(),
    val activityGroups: List<ActivityGroupUi> = emptyList(),
    val canManageGroups: Boolean = false
)

sealed interface PlanillasIntent {
    data class OnDateSelected(val date: LocalDate) : PlanillasIntent
    data class OnSearchQuery(val query: String) : PlanillasIntent
    data class OnFilterCircuito(val id: String?) : PlanillasIntent
    data class OnFilterAgencia(val id: String?) : PlanillasIntent
    data class OnFilterModulo(val id: String?) : PlanillasIntent
    data class OnModeFilter(val filter: PlanillaModeFilter) : PlanillasIntent
    object OnRefresh : PlanillasIntent
    data class OnGeneratePdf(val mode: PlanillaModeUi) : PlanillasIntent
    data class OnRetrySync(val entityId: String) : PlanillasIntent
    data class OnOpenPlanilla(val id: String) : PlanillasIntent
}
