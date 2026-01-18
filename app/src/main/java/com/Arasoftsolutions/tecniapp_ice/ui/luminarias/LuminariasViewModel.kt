package com.Arasoftsolutions.tecniapp_ice.ui.luminarias

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado
import com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaReparacionEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.MaterialEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.TecnicoEntity
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
    val materiales: List<MaterialEntity> = emptyList(),
    val tecnicos: List<TecnicoEntity> = emptyList(),
    val reparacionesPendientes: List<LuminariaReparacionEntity> = emptyList(),
    val reparacionesReparadas: List<LuminariaReparacionEntity> = emptyList(),
    val vehiculoUsuarioId: Int? = null,
    val rolUsuario: String? = null,
    val esSupervisor: Boolean = false,
    val puedeImportarCsv: Boolean = false,
    val localizacion: String = "",
    val materialesSeleccionados: List<LuminariaMaterialSeleccionado> = emptyList(),
    val estadoSeleccionado: LuminariaEstado = LuminariaEstado.REPARADA,
    val ejecutorNombre: String = "",
    val ejecutorCedula: String? = null,
    val busquedaLocalizacion: String = "",
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
                repository.observarMateriales(),
                repository.observarTecnicos(),
                repository.observarReparaciones()
            ) { materiales, tecnicos, reparaciones ->
                Triple(materiales, tecnicos, reparaciones)
            }.collect { (materiales, tecnicos, reparaciones) ->
                reparacionesCache = reparaciones
                val (pendientes, reparadas) = filtrarReparaciones(_uiState.value.busquedaLocalizacion)
                _uiState.update {
                    it.copy(
                        materiales = materiales,
                        tecnicos = tecnicos,
                        reparacionesPendientes = pendientes,
                        reparacionesReparadas = reparadas
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
        val rolNormalizado = usuario.rol?.trim().orEmpty()
        val rolLower = rolNormalizado.lowercase()
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
                vehiculoUsuarioId = vehiculoPreferidoId,
                ejecutorNombre = current.ejecutorNombre.ifBlank { nombre },
                ejecutorCedula = current.ejecutorCedula ?: usuario.cedula,
                rolUsuario = rolNormalizado.ifBlank { null },
                esSupervisor = rolLower == "supervisor",
                puedeImportarCsv = rolLower == "administrador" || rolLower == "supervisor"
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

    fun actualizarMaterialesSeleccionados(materiales: List<LuminariaMaterialSeleccionado>) {
        _uiState.value = _uiState.value.copy(materialesSeleccionados = materiales)
    }

    fun registrarReparacion() {
        val vehiculoId = _uiState.value.vehiculoUsuarioId ?: run {
            _mensaje.value = LuminariaMensaje.Error("No se encontró un vehículo asignado al usuario")
            return
        }
        val localizacion = _uiState.value.localizacion.trim()
        if (localizacion.isBlank()) {
            _mensaje.value = LuminariaMensaje.Error("Ingresa el número de localización")
            return
        }
        val materiales = _uiState.value.materialesSeleccionados
        val estado = _uiState.value.estadoSeleccionado
        if (estado == LuminariaEstado.REPARADA && materiales.isEmpty()) {
            _mensaje.value = LuminariaMensaje.Error("Agrega al menos un material")
            return
        }
        val ejecutorNombre = _uiState.value.ejecutorNombre.trim()
        if (estado == LuminariaEstado.REPARADA && ejecutorNombre.isBlank()) {
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
                estado = estado,
                ejecutorNombre = ejecutorNombre,
                ejecutorCedula = _uiState.value.ejecutorCedula
            )
            _uiState.value = _uiState.value.copy(
                isProcessing = false,
                localizacion = "",
                materialesSeleccionados = emptyList(),
                estadoSeleccionado = LuminariaEstado.REPARADA
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
        if (estado == LuminariaEstado.REPARADA && materiales.isEmpty()) {
            _mensaje.value = LuminariaMensaje.Error("Agrega al menos un material")
            return
        }
        if (estado == LuminariaEstado.REPARADA && ejecutorNombre.isBlank()) {
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

    fun actualizarBusquedaLocalizacion(valor: String) {
        _uiState.update { it.copy(busquedaLocalizacion = valor) }
        val (pendientes, reparadas) = filtrarReparaciones(valor)
        _uiState.update {
            it.copy(
                reparacionesPendientes = pendientes,
                reparacionesReparadas = reparadas
            )
        }
    }

    fun prepararFormularioRegistro() {
        _uiState.update {
            it.copy(
                localizacion = "",
                materialesSeleccionados = emptyList(),
                estadoSeleccionado = LuminariaEstado.REPARADA
            )
        }
    }

    fun procesarCsv(uri: android.net.Uri) {
        val vehiculoId = _uiState.value.vehiculoUsuarioId
        if (vehiculoId == null) {
            _mensaje.value = LuminariaMensaje.Error("No se encontró un vehículo asignado al usuario")
            return
        }
        _uiState.value = _uiState.value.copy(isProcessing = true)
        viewModelScope.launch {
            val localizaciones = leerCsvLocalizaciones(uri)
            if (localizaciones.isEmpty()) {
                _mensaje.value = LuminariaMensaje.Error("El archivo está vacío")
                _uiState.value = _uiState.value.copy(isProcessing = false)
                return@launch
            }
            repository.registrarLuminariasPendientes(
                vehiculoId = vehiculoId,
                localizaciones = localizaciones,
                ejecutorNombre = _uiState.value.ejecutorNombre.trim(),
                ejecutorCedula = _uiState.value.ejecutorCedula
            )
            _uiState.value = _uiState.value.copy(isProcessing = false)
            _mensaje.value = LuminariaMensaje.Exito("Lista de luminarias cargada")
        }
    }

    private suspend fun leerCsvLocalizaciones(uri: android.net.Uri): List<String> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            val input = resolver.openInputStream(uri) ?: return@withContext emptyList()
            java.io.BufferedReader(java.io.InputStreamReader(input)).useLines { lines ->
                lines.mapNotNull { row ->
                    val parts = row.split(",")
                    if (parts.isEmpty()) return@mapNotNull null
                    val raw = parts.first().trim()
                    if (raw.isBlank()) return@mapNotNull null
                    if (raw.equals("localizacion", ignoreCase = true)) return@mapNotNull null
                    raw
                }.distinct().toList()
            }
        }

    suspend fun buscarMedidorPorLocalizacion(localizacion: String) =
        localizacion.trim().toLongOrNull()?.let { repository.buscarMedidorPorLocalizacion(it) }

    private fun filtrarReparaciones(busqueda: String): Pair<List<LuminariaReparacionEntity>, List<LuminariaReparacionEntity>> {
        val texto = busqueda.trim().lowercase()
        val filtradas = reparacionesCache.filter { reparacion ->
            texto.isBlank() || reparacion.localizacion.lowercase().contains(texto)
        }
        val pendientes = filtradas
            .filter { LuminariaEstado.fromRaw(it.estado) == LuminariaEstado.PENDIENTE }
            .sortedWith(compareBy({ it.localizacion.toLongOrNull() ?: Long.MAX_VALUE }, { it.localizacion }))
        val reparadas = filtradas
            .filter { LuminariaEstado.fromRaw(it.estado) == LuminariaEstado.REPARADA }
            .sortedWith(compareBy({ it.localizacion.toLongOrNull() ?: Long.MAX_VALUE }, { it.localizacion }))
        return pendientes to reparadas
    }
}
