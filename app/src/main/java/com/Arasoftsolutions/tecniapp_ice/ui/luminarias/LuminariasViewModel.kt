package com.Arasoftsolutions.tecniapp_ice.ui.luminarias

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Arasoftsolutions.tecniapp_ice.Database.entities.InventarioConVehiculo
import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculosEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed class LuminariaMensaje {
    data class Exito(val texto: String) : LuminariaMensaje()
    data class Error(val texto: String) : LuminariaMensaje()
}

data class LuminariaUiState(
    val vehiculos: List<VehiculosEntity> = emptyList(),
    val inventario: List<InventarioConVehiculo> = emptyList(),
    val vehiculoSeleccionado: Int? = null,
    val codigoMaterial: String = "",
    val cantidad: String = "1",
    val localizacion: String = "",
    val isProcessing: Boolean = false
)

class LuminariasViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = RoomRepository.getInstance(app)

    private val _uiState = MutableStateFlow(LuminariaUiState())
    val uiState: StateFlow<LuminariaUiState> = _uiState.asStateFlow()

    private val _mensaje = MutableStateFlow<LuminariaMensaje?>(null)
    val mensaje: StateFlow<LuminariaMensaje?> = _mensaje.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.observarVehiculosCatalogo(),
                repository.observarInventarioGeneral()
            ) { vehiculos, inventario -> vehiculos to inventario }
                .collect { (vehiculos, inventario) ->
                    val seleccionado = _uiState.value.vehiculoSeleccionado ?: vehiculos.firstOrNull()?.id
                    _uiState.value = _uiState.value.copy(
                        vehiculos = vehiculos,
                        inventario = inventario.filter { it.item.vehiculoId == seleccionado },
                        vehiculoSeleccionado = seleccionado
                    )
                }
        }
    }

    fun seleccionarVehiculo(id: Int?) {
        _uiState.value = _uiState.value.copy(
            vehiculoSeleccionado = id,
            inventario = uiState.value.inventario.filter { id == null || it.item.vehiculoId == id }
        )
    }

    fun actualizarCodigo(codigo: String) {
        _uiState.value = _uiState.value.copy(codigoMaterial = codigo)
    }

    fun actualizarCantidad(valor: String) {
        _uiState.value = _uiState.value.copy(cantidad = valor)
    }

    fun actualizarLocalizacion(valor: String) {
        _uiState.value = _uiState.value.copy(localizacion = valor)
    }

    fun registrarReparacion() {
        val vehiculoId = _uiState.value.vehiculoSeleccionado ?: run {
            _mensaje.value = LuminariaMensaje.Error("Selecciona un vehículo")
            return
        }
        val codigo = _uiState.value.codigoMaterial.trim()
        val cantidadNum = _uiState.value.cantidad.toDoubleOrNull() ?: 0.0
        val localizacion = _uiState.value.localizacion.trim()
        if (codigo.isBlank() || cantidadNum <= 0 || localizacion.isBlank()) {
            _mensaje.value = LuminariaMensaje.Error("Completa los campos requeridos")
            return
        }
        _uiState.value = _uiState.value.copy(isProcessing = true)
        viewModelScope.launch {
            val descripcion = repository.obtenerMaterialPorCodigo(codigo)?.descripcion ?: codigo
            repository.registrarReparacionLuminaria(
                vehiculoId = vehiculoId,
                localizacion = localizacion,
                codigoMaterial = codigo,
                descripcionMaterial = descripcion,
                cantidad = cantidadNum
            )
            _uiState.value = _uiState.value.copy(
                isProcessing = false,
                codigoMaterial = "",
                cantidad = "1",
                localizacion = ""
            )
            _mensaje.value = LuminariaMensaje.Exito("Reparación registrada y material descontado")
        }
    }

    fun consumirMensaje() {
        _mensaje.value = null
    }
}
