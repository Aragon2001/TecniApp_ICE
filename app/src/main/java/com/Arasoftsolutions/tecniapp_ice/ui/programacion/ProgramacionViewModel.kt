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
    val rol: String = "TECNICO",
    val uid: String = "",
    val subregion: String = "",
    val agenciaTag: String = "",
    val vehiculoPropio: String = "",
    val vehiculoFiltro: String? = null,
    val estadoFiltro: String? = null,
    val items: List<ProgramacionEntity> = emptyList(),
    val loading: Boolean = true,
    val message: String? = null
) {
    val isSupervisor get() = rol.equals("SUPERVISOR", ignoreCase = true) || rol.equals("ADMINISTRADOR", ignoreCase = true)
    val isAdmin get() = rol.equals("ADMINISTRADOR", ignoreCase = true)
    val isTecnico get() = !isSupervisor
}

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
            val rawRol = user?.rol.orEmpty()
            val rol = when {
                rawRol.contains("admin", ignoreCase = true) -> "ADMINISTRADOR"
                rawRol.contains("super", ignoreCase = true) -> "SUPERVISOR"
                else -> "TECNICO"
            }
            val subregion = user?.subregion.orEmpty()
            val agenciaTag = user?.agencia.orEmpty().ifBlank { subregion }
            val vehiculoPropio = user?.placaVehiculo?.trim().orEmpty()

            userState.value = userState.value.copy(
                rol = rol,
                uid = uid,
                subregion = subregion,
                agenciaTag = agenciaTag,
                vehiculoPropio = vehiculoPropio,
                vehiculoFiltro = if (rol == "TECNICO") vehiculoPropio.ifBlank { null } else null
            )

            val flow = if (rol == "TECNICO" && vehiculoPropio.isNotBlank()) {
                repository.observeTodasOrdenesTecnico(agenciaTag, vehiculoPropio, uid)
            } else {
                repository.observeOrdenesSupervisor(agenciaTag)
            }
            flow.collect { sourceItems.value = it }
        }
    }

    fun sync() {
        viewModelScope.launch {
            val current = userState.value
            if (current.subregion.isBlank()) return@launch
            repository.syncScoped(
                current.subregion,
                if (current.isTecnico) current.vehiculoPropio.ifBlank { null } else null
            )
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

    // ─── Supervisor ──────────────────────────────────────────────────────────

    fun crearOrden(input: NuevaOrdenInput) {
        viewModelScope.launch {
            val state = userState.value
            if (!state.isSupervisor) {
                userState.value = state.copy(message = "No autorizado")
                return@launch
            }
            val user = usuarioDao.getByUid(state.uid) ?: return@launch
            val vehiculo = vehiculoDao.buscarPorVehiculoId(input.vehiculoId) ?: return@launch
            val locResult = LocalizacionUtils.validarYNormalizar(input.localizacionRaw)
            if (locResult.isFailure) {
                userState.value = state.copy(message = locResult.exceptionOrNull()?.message)
                return@launch
            }
            val locNormalizada = locResult.getOrThrow()
            val now = System.currentTimeMillis()
            val entity = ProgramacionEntity(
                programacionId = UUID.randomUUID().toString(),
                vehiculoId = input.vehiculoId,
                placa = vehiculo.placaRaw.ifBlank { vehiculo.placa.toString() },
                localizacion = locNormalizada,
                localizacionOriginal = input.localizacionRaw,
                localizacionNormalizada = locNormalizada,
                circuito = "",
                cuenta = "",
                actividad = input.detalleTrabajo,
                descripcion = input.detalleTrabajo,
                lat = input.lat,
                lng = input.lng,
                estado = ProgramacionRepository.ESTADO_PENDIENTE,
                observaciones = null,
                fechaAsignacion = now,
                fechaEjecucion = null,
                supervisorId = user.uid,
                supervisorNombre = listOfNotNull(user.nombre, user.apellidos).joinToString(" ").ifBlank { null },
                tecnicoId = input.vehiculoId,
                subregion = user.subregion.orEmpty(),
                agenciaTag = user.agencia.orEmpty().ifBlank { user.subregion.orEmpty() },
                fotosSupervisorJson = if (input.fotos.isNotEmpty())
                    input.fotos.joinToString(",", "[", "]") { "\"$it\"" } else null,
                updatedAt = now
            )
            repository.crearOrden(entity, input.fotos)
                .onSuccess { userState.value = state.copy(message = "Orden creada") }
                .onFailure { userState.value = state.copy(message = it.message ?: "Error al crear") }
        }
    }

    fun cancelarOrden(id: String, motivo: String?) {
        viewModelScope.launch {
            val state = userState.value
            if (!state.isSupervisor) return@launch
            repository.cancelarOrden(id, state.uid, motivo)
                .onSuccess { userState.value = state.copy(message = "Orden cancelada") }
                .onFailure { userState.value = state.copy(message = it.message) }
        }
    }

    fun eliminarOrden(id: String, motivo: String?) {
        viewModelScope.launch {
            val state = userState.value
            if (!state.isSupervisor) return@launch
            repository.eliminarOrden(id, state.uid, motivo)
                .onSuccess { userState.value = state.copy(message = "Orden eliminada") }
                .onFailure { userState.value = state.copy(message = it.message) }
        }
    }

    fun restaurarOrden(id: String) {
        viewModelScope.launch {
            val state = userState.value
            if (!state.isAdmin) return@launch
            repository.restaurarOrden(id)
                .onSuccess { userState.value = state.copy(message = "Orden restaurada") }
                .onFailure { userState.value = state.copy(message = it.message) }
        }
    }

    // ─── Técnico ──────────────────────────────────────────────────────────────

    fun iniciarAtencion(id: String) {
        viewModelScope.launch {
            val state = userState.value
            if (!state.isTecnico) return@launch
            val user = usuarioDao.getByUid(state.uid) ?: return@launch
            val nombre = listOfNotNull(user.nombre, user.apellidos).joinToString(" ").ifBlank { user.uid }
            repository.iniciarAtencion(id, state.uid, nombre)
                .onSuccess { userState.value = state.copy(message = "Atención iniciada") }
                .onFailure { userState.value = state.copy(message = it.message) }
        }
    }

    fun finalizarAtencion(
        id: String,
        descripcion: String,
        fotosAtencion: List<String>,
        tecnicosParticipantes: List<TecnicoParticipante>,
        gastoMateriales: Boolean,
        materiales: List<MaterialGastado>
    ) {
        viewModelScope.launch {
            val state = userState.value
            if (!state.isTecnico) return@launch
            repository.finalizarAtencion(
                id, state.uid, descripcion, fotosAtencion,
                tecnicosParticipantes, gastoMateriales, materiales
            )
                .onSuccess { userState.value = state.copy(message = "Orden finalizada") }
                .onFailure { userState.value = state.copy(message = it.message) }
        }
    }

    fun reabrirOrden(id: String, motivo: String?) {
        viewModelScope.launch {
            val state = userState.value
            repository.reabrirOrden(id, state.uid, motivo)
                .onSuccess { userState.value = state.copy(message = "Orden reabierta") }
                .onFailure { userState.value = state.copy(message = it.message) }
        }
    }

    // Compatibilidad con código viejo
    fun crearProgramacion(input: NuevaProgramacionInput) {
        crearOrden(
            NuevaOrdenInput(
                vehiculoId = input.vehiculoId,
                localizacionRaw = input.localizacion,
                detalleTrabajo = "${input.actividad} ${input.descripcion.orEmpty()}".trim(),
                lat = input.lat,
                lng = input.lng,
                fotos = input.fotosAsignacion
            )
        )
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
            val state = userState.value
            userState.value = state.copy(
                message = if (result.isSuccess) "Estado actualizado"
                else result.exceptionOrNull()?.message ?: "No se pudo actualizar"
            )
        }
    }
}

data class NuevaOrdenInput(
    val vehiculoId: String,
    val localizacionRaw: String,
    val detalleTrabajo: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val fotos: List<String> = emptyList()
)

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
