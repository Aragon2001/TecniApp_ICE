package com.Arasoftsolutions.tecniapp_ice.ui.programacion

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Arasoftsolutions.tecniapp_ice.Database.entities.ProgramacionEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class ProgramacionUiState(
    val isSupervisor: Boolean = false,
    val subregion: String = "",
    val vehiculoFiltro: String? = null,
    val estadoFiltro: String? = null,
    val items: List<ProgramacionEntity> = emptyList(),
    val loading: Boolean = true,
    val message: String? = null
)

class ProgramacionViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val repository = ProgramacionRepository(db)
    private val usuarioDao = db.usuarioDao()
    private val vehiculoDao = db.vehiculoDao()
    private val auth = FirebaseAuth.getInstance()

    private val userState = MutableStateFlow(ProgramacionUiState())
    private val sourceItems = MutableStateFlow<List<ProgramacionEntity>>(emptyList())

    val uiState: StateFlow<ProgramacionUiState> = combine(userState, sourceItems) { state, raw ->
        val estado = state.estadoFiltro
        val vehiculo = state.vehiculoFiltro
        val filtered = raw.filter { item ->
            (estado.isNullOrBlank() || item.estado == estado) &&
                (vehiculo.isNullOrBlank() || item.vehiculoId == vehiculo)
        }
        state.copy(items = filtered, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgramacionUiState())

    init {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid.orEmpty()
            val user = usuarioDao.getByUid(uid)
            val role = user?.rol.orEmpty().lowercase()
            val isSupervisor = role.contains("super") || role.contains("admin")
            val subregion = user?.subregion.orEmpty()
            val vehiculoTecnico = if (isSupervisor) null else user?.placaVehiculo?.trim().takeIf { !it.isNullOrEmpty() }
            userState.value = userState.value.copy(
                isSupervisor = isSupervisor,
                subregion = subregion,
                vehiculoFiltro = vehiculoTecnico
            )

            repository.observeProgramaciones(subregion, vehiculoTecnico).collect {
                sourceItems.value = it
            }
        }
    }

    fun sync() {
        viewModelScope.launch {
            val current = userState.value
            if (current.subregion.isBlank()) return@launch
            repository.syncScoped(current.subregion, if (current.isSupervisor) null else current.vehiculoFiltro)
        }
    }

    fun setEstadoFilter(estado: String?) {
        userState.value = userState.value.copy(estadoFiltro = estado)
    }

    fun setVehiculoFilter(vehiculoId: String?) {
        userState.value = userState.value.copy(vehiculoFiltro = vehiculoId)
    }

    fun clearMessage() {
        userState.value = userState.value.copy(message = null)
    }

    fun crearProgramacion(input: NuevaProgramacionInput) {
        viewModelScope.launch {
            val user = usuarioDao.getByUid(auth.currentUser?.uid.orEmpty()) ?: return@launch
            val vehiculo = vehiculoDao.buscarPorVehiculoId(input.vehiculoId) ?: return@launch
            val now = System.currentTimeMillis()
            val entity = ProgramacionEntity(
                programacionId = UUID.randomUUID().toString(),
                vehiculoId = input.vehiculoId,
                placa = vehiculo.placaRaw.ifBlank { vehiculo.placa.toString() },
                localizacion = input.localizacion,
                circuito = input.circuito,
                cuenta = input.cuenta,
                actividad = input.actividad,
                descripcion = input.descripcion,
                lat = input.lat,
                lng = input.lng,
                estado = ProgramacionRepository.ESTADO_PENDIENTE,
                observaciones = null,
                fechaAsignacion = now,
                fechaEjecucion = null,
                supervisorId = user.uid,
                tecnicoId = input.tecnicoId,
                subregion = user.subregion.orEmpty(),
                updatedAt = now
            )
            runCatching { repository.crearProgramacion(entity, input.fotosAsignacion) }
                .onSuccess { userState.value = userState.value.copy(message = "Programación creada") }
                .onFailure { userState.value = userState.value.copy(message = it.message ?: "Error al crear") }
        }
    }

    fun actualizarEstado(item: ProgramacionEntity, nuevoEstado: String, observaciones: String?, fotosCierre: List<String>) {
        viewModelScope.launch {
            val result = repository.actualizarEstado(
                programacionId = item.programacionId,
                subregion = item.subregion,
                vehiculoId = item.vehiculoId,
                nuevoEstado = nuevoEstado,
                observaciones = observaciones,
                fotosCierre = fotosCierre
            )
            userState.value = userState.value.copy(message = if (result.isSuccess) "Estado actualizado" else (result.exceptionOrNull()?.message ?: "No se pudo actualizar"))
        }
    }
}

data class NuevaProgramacionInput(
    val vehiculoId: String,
    val tecnicoId: String,
    val localizacion: String,
    val circuito: String,
    val cuenta: String,
    val actividad: String,
    val descripcion: String?,
    val lat: Double?,
    val lng: Double?,
    val fotosAsignacion: List<String>
)
