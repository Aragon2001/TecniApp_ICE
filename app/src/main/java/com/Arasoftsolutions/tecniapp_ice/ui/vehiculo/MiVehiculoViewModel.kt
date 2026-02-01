package com.Arasoftsolutions.tecniapp_ice.ui.vehiculo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Arasoftsolutions.tecniapp_ice.Database.entities.EtmRegistroEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculosEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MiVehiculoUiState(
    val vehiculo: VehiculosEntity? = null,
    val tipoVehiculo: TipoVehiculo = TipoVehiculo.LIVIANO,
    val registroHoy: EtmRegistroEntity? = null,
    val registrosRecientes: List<EtmRegistroEntity> = emptyList(),
    val nombreUsuario: String = "",
    val isLoading: Boolean = false
)

class MiVehiculoViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = RoomRepository.getInstance(app)
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(MiVehiculoUiState())
    val uiState: StateFlow<MiVehiculoUiState> = _uiState.asStateFlow()

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

            val placaLong = placaStr.toLongOrNull() ?: return@launch
            val vehiculo = repository.obtenerVehiculoPorPlaca(placaLong) ?: run {
                _uiState.value = _uiState.value.copy(vehiculo = null)
                return@launch
            }

            val tipo = inferirTipoVehiculo(vehiculo.tipo)
            val nombre = usuario.nombre?.trim().orEmpty()
            val hoy = fechaHoy()

            repository.observarRegistrosEtm(placaStr, 30).collect { registros ->
                val registroHoy = repository.obtenerRegistroEtmHoy(placaStr, hoy)
                _uiState.value = _uiState.value.copy(
                    vehiculo = vehiculo,
                    tipoVehiculo = tipo,
                    registroHoy = registroHoy,
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

    fun registrarInicial(valor: Double) {
        viewModelScope.launch {
            val state = _uiState.value
            val v = state.vehiculo ?: return@launch
            val placa = v.placa.toString()
            val uid = auth.currentUser?.uid ?: return@launch

            repository.guardarRegistroEtm(
                EtmRegistroEntity(
                    placa = placa,
                    vehiculoId = v.id,
                    fecha = fechaHoy(),
                    valorInicial = valor,
                    valorFinal = null,
                    tecnicoUid = uid,
                    tecnicoNombre = state.nombreUsuario,
                    cerrado = false
                )
            )
            refrescar()
        }
    }

    fun registrarFinal(valor: Double) {
        viewModelScope.launch {
            val state = _uiState.value
            val reg = state.registroHoy ?: return@launch
            // Actualizar registro existente con valor final
            repository.guardarRegistroEtm(
                reg.copy(valorFinal = valor, cerrado = true)
            )
            refrescar()
        }
    }

    fun obtenerPlaca(): String? = _uiState.value.vehiculo?.placa?.toString()
}
