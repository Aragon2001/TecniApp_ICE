package com.Arasoftsolutions.tecniapp_ice.ui.luminarias

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado
import com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaReparacionEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.MaterialEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.TecnicoEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculosEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.apellidosCompletos
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class LuminariaMensaje {
    data class Exito(val texto: String) : LuminariaMensaje()
    data class Error(val texto: String) : LuminariaMensaje()
}

data class LuminariaUiState(
    val vehiculos: List<VehiculosEntity> = emptyList(),
    val materiales: List<MaterialEntity> = emptyList(),
    val tecnicos: List<TecnicoEntity> = emptyList(),
    val reparacionesPendientes: List<LuminariaReparacionEntity> = emptyList(),
    val reparacionesReparadas: List<LuminariaReparacionEntity> = emptyList(),
    val vehiculoSeleccionado: Int? = null,
    val vehiculoAutomatico: Boolean = false,
    val localizacion: String = "",
    val materialesSeleccionados: List<LuminariaMaterialSeleccionado> = emptyList(),
    val estadoSeleccionado: LuminariaEstado = LuminariaEstado.REPARADA,
    val ejecutorNombre: String = "",
    val ejecutorCedula: String? = null,
    val isProcessing: Boolean = false
)

data class LuminariaMaterialSeleccionado(
    val codigo: String,
    val descripcion: String,
    val cantidad: Double
)

class LuminariasViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = RoomRepository.getInstance(app)
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(LuminariaUiState())
    val uiState: StateFlow<LuminariaUiState> = _uiState.asStateFlow()

    private val _mensaje = MutableStateFlow<LuminariaMensaje?>(null)
    val mensaje: StateFlow<LuminariaMensaje?> = _mensaje.asStateFlow()

    private var reparacionesCache: List<LuminariaReparacionEntity> = emptyList()
    private var vehiculoPreferidoId: Int? = null

    init {
        viewModelScope.launch {
            cargarPreferenciasUsuario()
        }
        viewModelScope.launch {
            combine(
                repository.observarVehiculosCatalogo(),
                repository.observarMateriales(),
                repository.observarTecnicos(),
                repository.observarReparaciones()
            ) { vehiculos, materiales, tecnicos, reparaciones ->
                Quad(vehiculos, materiales, tecnicos, reparaciones)
            }.collect { (vehiculos, materiales, tecnicos, reparaciones) ->
                reparacionesCache = reparaciones
                val seleccionado = _uiState.value.vehiculoSeleccionado
                    ?: vehiculoPreferidoId
                    ?: vehiculos.firstOrNull()?.id
                val filtradas = reparaciones.filter { it.vehiculoId == seleccionado }
                val pendientes = filtradas.filter { LuminariaEstado.fromRaw(it.estado) == LuminariaEstado.PENDIENTE }
                val reparadas = filtradas.filter { LuminariaEstado.fromRaw(it.estado) == LuminariaEstado.REPARADA }
                _uiState.update {
                    it.copy(
                        vehiculos = vehiculos,
                        materiales = materiales,
                        tecnicos = tecnicos,
                        reparacionesPendientes = pendientes,
                        reparacionesReparadas = reparadas,
                        vehiculoSeleccionado = seleccionado
                    )
                }
            }
        }
    }

    private suspend fun cargarPreferenciasUsuario() {
        val uid = auth.currentUser?.uid ?: return
        val usuario = repository.obtenerUsuario(uid) ?: return
        val placa = usuario.placaVehiculo?.trim().orEmpty()
        val vehiculo = placa.toLongOrNull()?.let { repository.obtenerVehiculoPorPlaca(it) }
        vehiculoPreferidoId = vehiculo?.id
        val nombre = buildString {
            usuario.nombre?.trim()?.takeIf { it.isNotBlank() }?.let { append(it) }
            val apellidos = usuario.apellidosCompletos?.trim().orEmpty()
            if (apellidos.isNotBlank()) {
                if (isNotEmpty()) append(" ")
                append(apellidos)
            }
        }.ifBlank { usuario.nombre ?: "" }
        _uiState.update { current ->
            current.copy(
                vehiculoSeleccionado = current.vehiculoSeleccionado ?: vehiculoPreferidoId,
                vehiculoAutomatico = vehiculoPreferidoId != null,
                ejecutorNombre = current.ejecutorNombre.ifBlank { nombre },
                ejecutorCedula = current.ejecutorCedula ?: usuario.cedula
            )
        }
    }

    fun seleccionarVehiculo(id: Int?) {
        val filtradas = reparacionesCache.filter { id == null || it.vehiculoId == id }
        val pendientes = filtradas.filter { LuminariaEstado.fromRaw(it.estado) == LuminariaEstado.PENDIENTE }
        val reparadas = filtradas.filter { LuminariaEstado.fromRaw(it.estado) == LuminariaEstado.REPARADA }
        _uiState.update {
            it.copy(
                vehiculoSeleccionado = id,
                reparacionesPendientes = pendientes,
                reparacionesReparadas = reparadas
            )
        }
    }

    fun actualizarLocalizacion(valor: String) {
        _uiState.value = _uiState.value.copy(localizacion = valor)
    }

    fun actualizarEjecutor(nombre: String) {
        val tecnico = _uiState.value.tecnicos.firstOrNull { it.nombre.equals(nombre, ignoreCase = true) }
        _uiState.update {
            it.copy(
                ejecutorNombre = nombre,
                ejecutorCedula = tecnico?.cedula
            )
        }
    }

    fun actualizarEstado(estado: LuminariaEstado) {
        _uiState.value = _uiState.value.copy(estadoSeleccionado = estado)
    }

    fun agregarMaterial(codigo: String, descripcion: String, cantidad: Double) {
        if (cantidad <= 0) {
            _mensaje.value = LuminariaMensaje.Error("Ingresa una cantidad válida")
            return
        }
        val actuales = _uiState.value.materialesSeleccionados.toMutableList()
        val index = actuales.indexOfFirst { it.codigo == codigo }
        if (index >= 0) {
            val existente = actuales[index]
            actuales[index] = existente.copy(cantidad = existente.cantidad + cantidad)
        } else {
            actuales.add(LuminariaMaterialSeleccionado(codigo, descripcion, cantidad))
        }
        _uiState.value = _uiState.value.copy(materialesSeleccionados = actuales)
    }

    fun eliminarMaterial(codigo: String) {
        val actualizados = _uiState.value.materialesSeleccionados.filterNot { it.codigo == codigo }
        _uiState.value = _uiState.value.copy(materialesSeleccionados = actualizados)
    }

    fun registrarReparacion() {
        val vehiculoId = _uiState.value.vehiculoSeleccionado ?: run {
            _mensaje.value = LuminariaMensaje.Error("Selecciona un vehículo")
            return
        }
        val localizacion = _uiState.value.localizacion.trim()
        if (localizacion.isBlank()) {
            _mensaje.value = LuminariaMensaje.Error("Ingresa el número de localización")
            return
        }
        val materiales = _uiState.value.materialesSeleccionados
        if (materiales.isEmpty()) {
            _mensaje.value = LuminariaMensaje.Error("Agrega al menos un material")
            return
        }
        val ejecutorNombre = _uiState.value.ejecutorNombre.trim()
        if (ejecutorNombre.isBlank()) {
            _mensaje.value = LuminariaMensaje.Error("Indica quién realizó la reparación")
            return
        }
        _uiState.value = _uiState.value.copy(isProcessing = true)
        viewModelScope.launch {
            repository.registrarReparacionLuminaria(
                vehiculoId = vehiculoId,
                localizacion = localizacion,
                materiales = materiales.map {
                    LuminariaMaterialUso(it.codigo, it.descripcion, it.cantidad)
                },
                estado = _uiState.value.estadoSeleccionado,
                ejecutorNombre = ejecutorNombre,
                ejecutorCedula = _uiState.value.ejecutorCedula
            )
            _uiState.value = _uiState.value.copy(
                isProcessing = false,
                localizacion = "",
                materialesSeleccionados = emptyList()
            )
            _mensaje.value = LuminariaMensaje.Exito("Reparación registrada")
        }
    }

    fun eliminarReparacion(id: Long) {
        viewModelScope.launch {
            repository.eliminarReparacionLuminaria(id)
            _mensaje.value = LuminariaMensaje.Exito("Reparación eliminada")
        }
    }

    fun actualizarReparacion(
        id: Long,
        nuevaLocalizacion: String,
        materiales: List<LuminariaMaterialSeleccionado>,
        estado: LuminariaEstado,
        ejecutorNombre: String,
        ejecutorCedula: String?
    ) {
        if (nuevaLocalizacion.isBlank()) {
            _mensaje.value = LuminariaMensaje.Error("Completa la localización")
            return
        }
        if (materiales.isEmpty()) {
            _mensaje.value = LuminariaMensaje.Error("Agrega al menos un material")
            return
        }
        if (ejecutorNombre.isBlank()) {
            _mensaje.value = LuminariaMensaje.Error("Indica quién realizó la reparación")
            return
        }
        viewModelScope.launch {
            repository.actualizarReparacionLuminaria(
                id,
                nuevaLocalizacion,
                materiales.map { LuminariaMaterialUso(it.codigo, it.descripcion, it.cantidad) },
                estado,
                ejecutorNombre,
                ejecutorCedula
            )
            _mensaje.value = LuminariaMensaje.Exito("Reparación actualizada")
        }
    }

    fun consumirMensaje() {
        _mensaje.value = null
    }
}

private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
