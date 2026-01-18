package com.Arasoftsolutions.tecniapp_ice.ui.inventario

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Arasoftsolutions.tecniapp_ice.Database.entities.InventarioConVehiculo
import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculosEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.R
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
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
    val inventarioCompleto: List<InventarioConVehiculo> = emptyList(),
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
                val seleccionadoActual = _uiState.value.vehiculoSeleccionado
                val seleccionado = seleccionadoActual?.takeIf { id -> vehiculos.any { it.id == id } }
                _uiState.value = _uiState.value.copy(
                    vehiculos = vehiculos,
                    inventarioCompleto = inventario,
                    inventario = filtrarInventario(inventario, seleccionado),
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
        val inventario = _uiState.value.inventarioCompleto
        _uiState.value = _uiState.value.copy(
            inventario = filtrarInventario(inventario, seleccionado)
        )
    }

    fun ajustarCantidad(item: InventarioConVehiculo, delta: Double) {
        val vehiculoId = item.item.vehiculoId
        if (delta == 0.0) return
        _uiState.value = _uiState.value.copy(isProcessing = true)
        viewModelScope.launch {
            repository.ajustarInventario(
                vehiculoId,
                item.item.codigoMaterial.trim(),
                item.item.descripcionMaterial.trim(),
                delta
            )
            _uiState.value = _uiState.value.copy(isProcessing = false)
            _mensajes.value = InventarioMensaje.Exito("Inventario actualizado")
        }
    }

    fun eliminarItem(item: InventarioConVehiculo) {
        _uiState.value = _uiState.value.copy(isProcessing = true)
        viewModelScope.launch {
            repository.eliminarInventarioItem(item.item.id)
            _uiState.value = _uiState.value.copy(isProcessing = false)
            _mensajes.value = InventarioMensaje.Exito("Material eliminado del inventario")
        }
    }

    fun procesarCsv(uri: Uri) {
        val vehiculoId = _uiState.value.vehiculoSeleccionado
        if (vehiculoId == null) {
            val texto = getApplication<Application>().getString(R.string.inventario_error_seleccionar_camion)
            _mensajes.value = InventarioMensaje.Error(texto)
            return
        }
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
            _mensajes.value = InventarioMensaje.Exito("Inventario reemplazado con la carga CSV")
        }
    }

    fun procesarPdf(uri: Uri) {
        val vehiculoId = _uiState.value.vehiculoSeleccionado
        if (vehiculoId == null) {
            val texto = getApplication<Application>().getString(R.string.inventario_error_seleccionar_camion)
            _mensajes.value = InventarioMensaje.Error(texto)
            return
        }
        _uiState.value = _uiState.value.copy(isProcessing = true)
        viewModelScope.launch {
            try {
                val pares = leerPdf(uri)
                if (pares.isEmpty()) {
                    _mensajes.value = InventarioMensaje.Error(
                        getApplication<Application>().getString(R.string.inventario_pdf_error_vacio)
                    )
                    _uiState.value = _uiState.value.copy(isProcessing = false)
                    return@launch
                }
                val codigos = pares.map { it.first }.toSet()
                val codigosExistentes = repository.obtenerCodigosMateriales(codigos)
                repository.cargarInventarioDesdeLista(vehiculoId, pares)
                val faltantes = codigos.minus(codigosExistentes)
                val mensaje = if (faltantes.isNotEmpty()) {
                    getApplication<Application>().getString(
                        R.string.inventario_pdf_exito_con_faltantes,
                        faltantes.size
                    )
                } else {
                    getApplication<Application>().getString(R.string.inventario_pdf_exito)
                }
                _uiState.value = _uiState.value.copy(isProcessing = false)
                _mensajes.value = InventarioMensaje.Exito(mensaje)
            } catch (ex: Exception) {
                _uiState.value = _uiState.value.copy(isProcessing = false)
                _mensajes.value = InventarioMensaje.Error(
                    getApplication<Application>().getString(R.string.inventario_pdf_error_lectura)
                )
            }
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

    private suspend fun leerPdf(uri: Uri): List<Pair<String, Double>> = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        val input = resolver.openInputStream(uri) ?: return@withContext emptyList()
        PDFBoxResourceLoader.init(getApplication())
        input.use { stream ->
            val document = PDDocument.load(stream)
            document.use { pdf ->
                val stripper = PDFTextStripper().apply { sortByPosition = true }
                val text = stripper.getText(pdf)
                val acumulados = mutableMapOf<String, Double>()
                text.lineSequence().forEach { line ->
                    val columns = line.trim()
                        .split(Regex("\\s{2,}"))
                        .filter { it.isNotBlank() }
                    if (columns.isEmpty()) return@forEach
                    val codigo = columns.first().trim()
                    if (!codigo.matches(Regex("\\d{5,}"))) return@forEach
                    val cantidad = buscarCantidad(columns.drop(1)) ?: return@forEach
                    val actual = acumulados[codigo] ?: 0.0
                    acumulados[codigo] = actual + cantidad
                }
                acumulados.toList()
            }
        }
    }

    private fun buscarCantidad(columns: List<String>): Double? {
        columns.forEach { col ->
            val normalized = col.trim().replace(",", ".")
            if (normalized.matches(Regex("\\d+(?:\\.\\d+)?"))) {
                return normalized.toDoubleOrNull()
            }
        }
        return null
    }

    fun consumirMensaje() {
        _mensajes.value = null
    }

    private fun filtrarInventario(
        inventario: List<InventarioConVehiculo>,
        seleccionado: Int?
    ): List<InventarioConVehiculo> {
        return if (seleccionado == null) {
            inventario
        } else {
            inventario.filter { it.item.vehiculoId == seleccionado }
        }
    }
}
