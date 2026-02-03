package com.Arasoftsolutions.tecniapp_ice.ui.vehiculo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculosEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.Database.utils.VehiculoPlacaUtils
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MiVehiculoUiState(
    val vehiculo: VehiculosEntity? = null,
    val tipoVehiculo: TipoVehiculo = TipoVehiculo.LIVIANO,
    val registroHoy: RegistroDiarioVehiculo? = null,
    val ultimoRegistro: RegistroDiarioVehiculo? = null,
    val registrosRecientes: List<RegistroDiarioVehiculo> = emptyList(),
    val nombreUsuario: String = "",
    val isLoading: Boolean = false
)

class MiVehiculoViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = RoomRepository.getInstance(app)
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(MiVehiculoUiState())
    val uiState: StateFlow<MiVehiculoUiState> = _uiState.asStateFlow()

    private val _eventos = MutableSharedFlow<String>()
    val eventos: SharedFlow<String> = _eventos

    private val formatoFecha = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

    init {
        cargarDatos()
    }

    private fun fechaHoy(): String = formatoFecha.format(Date())

    private fun cargarDatos() {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val usuario = repository.obtenerUsuario(uid) ?: return@launch
            val placaStr = usuario.placaVehiculo?.trim().orEmpty()
            if (placaStr.isBlank()) {
                _uiState.value = _uiState.value.copy(vehiculo = null)
                return@launch
            }

            val placaLong = VehiculoPlacaUtils.parsePlacaLong(placaStr) ?: return@launch
            val nombre = usuario.nombre?.trim().orEmpty()
            val hoy = fechaHoy()

            repository.observarVehiculoPorPlaca(placaLong).collectLatest { vehiculo ->
                if (vehiculo == null) {
                    _uiState.value = _uiState.value.copy(vehiculo = null)
                    return@collectLatest
                }
                val tipo = inferirTipoVehiculo(vehiculo.tipo)
                val registros = parseRegistrosDiarios(vehiculo.registrosDiariosJson)
                val registroHoy = registros.firstOrNull { it.fecha == hoy }
                val ultimoRegistro = registros.firstOrNull()
                _uiState.value = _uiState.value.copy(
                    vehiculo = vehiculo,
                    tipoVehiculo = tipo,
                    registroHoy = registroHoy,
                    ultimoRegistro = ultimoRegistro,
                    registrosRecientes = registros,
                    nombreUsuario = nombre
                )
            }
        }
    }

    fun refrescar() {
        viewModelScope.launch {
            cargarDatos()
        }
    }

    fun registrarInicial(
        valor: Double,
        circuito: String?,
        actividad: String?,
        cuenta: String?,
        numeroCaso: String?,
        lugar: String?,
        horasLaboradas: Int?
    ) {
        viewModelScope.launch {
            val state = _uiState.value
            val v = state.vehiculo ?: return@launch
            if (state.registroHoy != null) {
                _eventos.emit("Ya existe un registro para hoy.")
                return@launch
            }
            val ultimoValor = state.ultimoRegistro?.valorFinal ?: state.ultimoRegistro?.valorInicial
            if (ultimoValor != null && valor < ultimoValor) {
                _eventos.emit("No se permiten valores regresivos.")
                return@launch
            }

            val nuevoRegistro = RegistroDiarioVehiculo(
                fecha = fechaHoy(),
                valorInicial = valor,
                valorFinal = null,
                cerrado = false,
                registradoEn = System.currentTimeMillis(),
                registradoPor = state.nombreUsuario.ifBlank { null },
                circuito = circuito,
                actividad = actividad,
                cuenta = cuenta,
                numeroCaso = numeroCaso,
                lugar = lugar,
                horasLaboradas = horasLaboradas
            )
            val registrosActuales = state.registrosRecientes.filterNot { it.fecha == nuevoRegistro.fecha }
            val nuevos = listOf(nuevoRegistro) + registrosActuales
            val registroJson = serializeRegistrosDiarios(nuevos)
            val kilometrajeActual = if (state.tipoVehiculo.usaKilometraje) valor else v.kilometrajeActual
            val orimetroActual = if (state.tipoVehiculo.usaOrimetro) valor else v.orimetroActual
            repository.actualizarRegistroDiarioVehiculo(
                vehiculoId = v.id,
                fecha = nuevoRegistro.fecha,
                inicial = nuevoRegistro.valorInicial,
                final = null,
                cerrado = false,
                kilometrajeActual = kilometrajeActual,
                orimetroActual = orimetroActual,
                registrosJson = registroJson
            )
            refrescar()
        }
    }

    fun registrarFinal(valor: Double) {
        viewModelScope.launch {
            val state = _uiState.value
            val reg = state.registroHoy ?: return@launch
            if (valor < reg.valorInicial) {
                _eventos.emit("El valor final no puede ser menor al inicial.")
                return@launch
            }
            val actualizado = reg.copy(valorFinal = valor, cerrado = true)
            val registrosActuales = state.registrosRecientes.map {
                if (it.fecha == actualizado.fecha) actualizado else it
            }
            val registroJson = serializeRegistrosDiarios(registrosActuales)
            val kilometrajeActual = if (state.tipoVehiculo.usaKilometraje) valor else state.vehiculo?.kilometrajeActual
            val orimetroActual = if (state.tipoVehiculo.usaOrimetro) valor else state.vehiculo?.orimetroActual
            repository.actualizarRegistroDiarioVehiculo(
                vehiculoId = state.vehiculo?.id ?: return@launch,
                fecha = actualizado.fecha,
                inicial = actualizado.valorInicial,
                final = actualizado.valorFinal,
                cerrado = true,
                kilometrajeActual = kilometrajeActual,
                orimetroActual = orimetroActual,
                registrosJson = registroJson
            )
            refrescar()
        }
    }

    fun obtenerPlaca(): String? = _uiState.value.vehiculo?.placa?.toString()
}
