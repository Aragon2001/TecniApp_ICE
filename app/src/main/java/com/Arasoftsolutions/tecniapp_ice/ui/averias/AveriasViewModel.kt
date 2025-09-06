// ui/averias/AveriasViewModel.kt
package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.Arasoftsolutions.tecniapp_ice.BuildConfig


enum class Estado { PENDIENTE, ASIGNADA, EN_ATENCION, RESUELTA }
data class ZonaUI(val id: String, val nombreVisible: String)
data class AveriasUiState(val loading: Boolean = false, val items: List<AveriaUI> = emptyList())

@OptIn(FlowPreview::class)
class AveriasViewModel(app: Application) : AndroidViewModel(app) {

  private val db = com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase.getInstance(app)

  private val repo = AveriasRepository(db)

  private val q = MutableStateFlow("")
  private val estado = MutableStateFlow("Todos")
  private val zonaIndex = MutableStateFlow(0)
  private val _zonas = MutableStateFlow(
    listOf(
      ZonaUI("Guapiles","Guápiles"),
      ZonaUI("Guacimo","Guácimo"),
      ZonaUI("Cariari","Cariari"),
      ZonaUI("Tortuguero","Tortuguero")
    )
  )
  val zonas: StateFlow<List<ZonaUI>> = _zonas.asStateFlow()
  private val isLoading = MutableStateFlow(false)

  private fun agenciesSel(): List<String> = when (zonaIndex.value) {
    0 -> listOf("Guapiles","Guacimo","Cariari","Tortuguero")
    else -> listOf(_zonas.value[zonaIndex.value - 1].id)
  }

  val uiState: StateFlow<AveriasUiState> =
    combine(
      q.debounce(250).map { it.trim() }.distinctUntilChanged(),
      estado,
      zonaIndex
    ) { qv, est, _ -> Triple(qv, est, agenciesSel()) }
      .flatMapLatest { (qv, est, ags) ->
        repo.observe(ags, if (est=="Todos") "" else est, qv)
      }
      .map { list ->
        AveriasUiState(
          loading = isLoading.value,
          items = list.map { e ->
            AveriaUI(
              id = e.caseId,
              descripcion = "Avería #${e.caseId}",
              fechaMillis = e.fechaInicioMillis,
              causa = e.causa ?: "Pendiente de verificar",
              estado = e.estado,
              tecnico = e.tecnicoAsignadoNombre ?: "",
              observaciones = e.observaciones ?: "—",
              nise = e.nise ?: "",
              agencia = e.nombreAgencia ?: (e.agencia ?: ""),
              region = e.region ?: "",
              zonaTag = e.agenciaTag,
              lat = e.lat ?: 0.0,
              lng = e.lng ?: 0.0
            )
          }
        )
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AveriasUiState())

  init { createNotificationChannel() }

  fun setQuery(value: String) = viewModelScope.launch { q.emit(value) }
  fun setEstado(value: Estado?) = viewModelScope.launch { estado.emit(value?.name ?: "Todos") }
  fun setZonaIndex(idx: Int) = viewModelScope.launch { zonaIndex.emit(idx) }

  fun syncNow() {
    viewModelScope.launch {
      isLoading.emit(true)
      try {
        val nuevos = repo.syncFromIce(BuildConfig.ICE_BEARER) // puede ser null
        // aquí puedes notificar "nuevos" si querés (se deja al Worker para no duplicar)
      } finally { isLoading.emit(false) }
    }
  }

  fun onAsignar(ui: AveriaUI) {
    val uid = /* TODO: obtener de Auth */ "UID"
    val nombre = /* TODO */ "Técnico"
    val vehiculo = /* TODO */ "ABC-123"
    viewModelScope.launch { repo.asignar(ui.id, uid, nombre, vehiculo) }
  }


  fun onAtender(ui: AveriaUI) {
    viewModelScope.launch {
      repo.enAtencion(
        caseId = ui.id,
        causa = ui.causa.ifBlank { "Pendiente de verificar" },
        obs = "En atención"
      )
    }
  }

  fun onCerrar(ui: AveriaUI) {
    viewModelScope.launch { repo.cerrar(ui.id, ui.causa.ifBlank { "Verificada" }, "Cierre desde app") }
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val mgr = getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      mgr.createNotificationChannel(
        NotificationChannel("averias_channel","Averías", NotificationManager.IMPORTANCE_HIGH)
      )
    }
  }
}
