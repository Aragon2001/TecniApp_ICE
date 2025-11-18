package com.Arasoftsolutions.tecniapp_ice.ui.inventario

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Arasoftsolutions.tecniapp_ice.Database.entities.InventarioConVehiculo
import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculosEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class InventarioMensaje {
    data class Exito(val texto: String) : InventarioMensaje()
    data class Error(val texto: String) : InventarioMensaje()
}

data class InventarioUiState(
    val vehiculos: List<VehiculosEntity> = emptyList(),
    val inventario: List<InventarioConVehiculo> = emptyList(),
    val vehiculoSeleccionado: Int? = null,
    val isProcessing: Boolean = false
)

class InventarioViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = RoomRepository.getInstance(app)

    private val _mensajes = MutableStateFlow<InventarioMensaje?>(null)
    val mensajes: StateFlow<InventarioMensaje?> = _mensajes.asStateFlow()

    private val _uiState = MutableStateFlow(InventarioUiState())
    val uiState: StateFlow<InventarioUiState> = _uiState.asStateFlow()

    init {
        cargarDatosIniciales()
    }

    private fun cargarDatosIniciales() {
        viewModelScope.launch {
            val vehiculosFlow = repository.observarVehiculosCatalogo()
            val inventarioFlow = repository.observarInventarioGeneral()
            combine(vehiculosFlow, inventarioFlow) { vehiculos, inventario ->
                vehiculos to inventario
            }.collect { (vehiculos, inventario) ->
                val seleccionado = _uiState.value.vehiculoSeleccionado ?: vehiculos.firstOrNull()?.id
                _uiState.value = _uiState.value.copy(
                    vehiculos = vehiculos,
                    inventario = inventario.filter { seleccionado == null || it.item.vehiculoId == seleccionado },
                    vehiculoSeleccionado = seleccionado
                )
            }
        }
    }

    fun seleccionarVehiculo(id: Int?) {
        _uiState.value = _uiState.value.copy(vehiculoSeleccionado = id)
        filtrarInventario()
    }

    private fun filtrarInventario() {
        val seleccionado = _uiState.value.vehiculoSeleccionado
        val inventario = _uiState.value.inventario
        _uiState.value = _uiState.value.copy(
            inventario = if (seleccionado == null) inventario else inventario.filter { it.item.vehiculoId == seleccionado }
        )
    }

    fun ajustarCantidadManual(codigo: String, descripcion: String, cantidad: Double) {
        val vehiculoId = _uiState.value.vehiculoSeleccionado ?: return
        if (codigo.isBlank() || cantidad == 0.0) return
        _uiState.value = _uiState.value.copy(isProcessing = true)
        viewModelScope.launch {
            repository.ajustarInventario(vehiculoId, codigo.trim(), descripcion.trim(), cantidad)
            _uiState.value = _uiState.value.copy(isProcessing = false)
            _mensajes.value = InventarioMensaje.Exito("Inventario actualizado")
        }
    }

    fun procesarCsv(uri: Uri) {
        val vehiculoId = _uiState.value.vehiculoSeleccionado ?: return
        _uiState.value = _uiState.value.copy(isProcessing = true)
        viewModelScope.launch {
            val pares = leerCsv(uri)
            if (pares.isEmpty()) {
                _mensajes.value = InventarioMensaje.Error("El archivo está vacío")
                _uiState.value = _uiState.value.copy(isProcessing = false)
                return@launch
            }
            repository.cargarInventarioDesdeCsv(vehiculoId, pares)
            _uiState.value = _uiState.value.copy(isProcessing = false)
            _mensajes.value = InventarioMensaje.Exito("Inventario importado desde CSV")
        }
    }

    private suspend fun leerCsv(uri: Uri): List<Pair<String, Double>> = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        val input = resolver.openInputStream(uri) ?: return@withContext emptyList()
        BufferedReader(InputStreamReader(input)).useLines { lines ->
            lines.mapNotNull { row ->
                val parts = row.split(",")
                if (parts.size < 2) return@mapNotNull null
                val codigo = parts[0].trim()
                val cantidad = parts[1].toDoubleOrNull() ?: return@mapNotNull null
                if (codigo.isEmpty()) return@mapNotNull null
                codigo to cantidad
            }.toList()
        }
    }

    fun consumirMensaje() {
        _mensajes.value = null
    }
}
