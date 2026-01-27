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
    val materiales: List<MaterialEntity> = emptyList(),
    val tecnicos: List<TecnicoEntity> = emptyList(),
    val reparacionesPendientes: List<LuminariaReparacionEntity> = emptyList(),
    val reparacionesReparadas: List<LuminariaReparacionEntity> = emptyList(),
    val vehiculosAgencia: List<VehiculosEntity> = emptyList(),
    val vehiculoUsuarioId: Int? = null,
    val vehiculoFiltroId: Int? = null,
    val vehiculoRegistroId: Int? = null,
    val agenciaUsuario: String? = null,
    val rolUsuario: String? = null,
    val esSupervisor: Boolean = false,
    val esAdministrador: Boolean = false,
    val puedeImportarExcel: Boolean = false,
    val puedeDescargarMachote: Boolean = false,
    val puedeEnviarMachote: Boolean = false,
    val puedeRegistrarReparacion: Boolean = true,
    val puedeReasignarVehiculo: Boolean = false,
    val puedeFiltrarVehiculo: Boolean = false,
    val puedeEliminarLuminarias: Boolean = false,
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

data class LuminariaCatalogoState(
    val materiales: List<MaterialEntity>,
    val tecnicos: List<TecnicoEntity>,
    val reparaciones: List<LuminariaReparacionEntity>,
    val vehiculos: List<VehiculosEntity>
)

class LuminariasViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = RoomRepository.getInstance(app)
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(LuminariaUiState())
    val uiState: StateFlow<LuminariaUiState> = _uiState.asStateFlow()

    private val _mensaje = MutableStateFlow<LuminariaMensaje?>(null)
    val mensaje: StateFlow<LuminariaMensaje?> = _mensaje.asStateFlow()

    private var reparacionesAgenciaCache: List<LuminariaReparacionEntity> = emptyList()
    private var vehiculoPreferidoId: Int? = null
    private val localizacionRegex = Regex("^(\\d{4})-(\\d{3})-(\\d{2})-(\\d{2})$")

    init {
        viewModelScope.launch {
            cargarPreferenciasUsuario()
        }
        viewModelScope.launch {
            combine(
                repository.observarMateriales(),
                repository.observarTecnicos(),
                repository.observarReparaciones(),
                repository.observarVehiculosCatalogo()
            ) { materiales, tecnicos, reparaciones, vehiculos ->
                LuminariaCatalogoState(materiales, tecnicos, reparaciones, vehiculos)
            }.collect { (materiales, tecnicos, reparaciones, vehiculos) ->
                val agenciaUsuario = _uiState.value.agenciaUsuario?.trim().orEmpty()
                val vehiculosPorId = vehiculos.associateBy { it.id }
                val filtradasPorAgencia = if (agenciaUsuario.isBlank()) {
                    reparaciones
                } else {
                    reparaciones.filter { reparacion ->
                        val agencia = vehiculosPorId[reparacion.vehiculoId]?.agencia?.trim().orEmpty()
                        agencia.equals(agenciaUsuario, ignoreCase = true)
                    }
                }
                reparacionesAgenciaCache = filtradasPorAgencia
                val vehiculosAgencia = if (agenciaUsuario.isBlank()) {
                    vehiculos
                } else {
                    vehiculos.filter { it.agencia.equals(agenciaUsuario, ignoreCase = true) }
                }.sortedBy { it.placa }
                val (pendientes, reparadas) = filtrarReparaciones(
                    reparacionesAgenciaCache,
                    _uiState.value.busquedaLocalizacion,
                    _uiState.value.vehiculoFiltroId
                )
                _uiState.update {
                    it.copy(
                        materiales = materiales,
                        tecnicos = tecnicos,
                        vehiculosAgencia = vehiculosAgencia,
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
            val esSupervisor = rolLower == "supervisor"
            val esAdministrador = rolLower == "administrador"
            val puedeFiltrarVehiculo = esSupervisor || esAdministrador
            current.copy(
                vehiculoUsuarioId = vehiculoPreferidoId,
                vehiculoFiltroId = if (puedeFiltrarVehiculo) null else vehiculoPreferidoId,
                vehiculoRegistroId = vehiculoPreferidoId,
                ejecutorNombre = current.ejecutorNombre.ifBlank { nombre },
                ejecutorCedula = current.ejecutorCedula ?: usuario.cedula,
                agenciaUsuario = usuario.agencia?.trim()?.takeIf { it.isNotBlank() },
                rolUsuario = rolNormalizado.ifBlank { null },
                esSupervisor = esSupervisor,
                esAdministrador = esAdministrador,
                puedeImportarExcel = esSupervisor || esAdministrador,
                puedeDescargarMachote = esSupervisor || esAdministrador,
                puedeEnviarMachote = esSupervisor || esAdministrador,
                puedeRegistrarReparacion = true,
                puedeReasignarVehiculo = esSupervisor || esAdministrador,
                puedeFiltrarVehiculo = puedeFiltrarVehiculo,
                puedeEliminarLuminarias = esSupervisor || esAdministrador || rolLower == "tecnico"
            )
        }
    }

    fun actualizarLocalizacion(valor: String) {
        _uiState.value = _uiState.value.copy(localizacion = normalizarLocalizacion(valor))
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
        val estadoUi = _uiState.value
        val vehiculoId = (estadoUi.vehiculoRegistroId ?: estadoUi.vehiculoUsuarioId) ?: run {
            _mensaje.value = LuminariaMensaje.Error("No se encontró un vehículo asignado al usuario")
            return
        }
        val localizacion = normalizarLocalizacion(estadoUi.localizacion)
        if (localizacion.isBlank()) {
            _mensaje.value = LuminariaMensaje.Error("Ingresa el número de localización")
            return
        }
        val materiales = estadoUi.materialesSeleccionados
        val estado = estadoUi.estadoSeleccionado
        val requiereMateriales = estado == LuminariaEstado.REPARADA && (estadoUi.esSupervisor || estadoUi.esAdministrador)
        if (requiereMateriales && materiales.isEmpty()) {
            _mensaje.value = LuminariaMensaje.Error("Agrega al menos un material")
            return
        }
        val ejecutorNombre = estadoUi.ejecutorNombre.trim()
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
                ejecutorCedula = estadoUi.ejecutorCedula
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
            val reparacion = repository.obtenerReparacionLuminaria(id)
            if (reparacion != null &&
                LuminariaEstado.fromRaw(reparacion.estado) == LuminariaEstado.REPARADA
            ) {
                repository.marcarReparacionLuminariaPendiente(id)
                _mensaje.value = LuminariaMensaje.Exito("Reparación enviada a pendientes")
            } else {
                repository.eliminarReparacionLuminaria(id)
                _mensaje.value = LuminariaMensaje.Exito("Reparación eliminada")
            }
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
        val localizacionNormalizada = normalizarLocalizacion(nuevaLocalizacion)
        if (localizacionNormalizada.isBlank()) {
            _mensaje.value = LuminariaMensaje.Error("Completa la localización")
            return
        }
        val requiereMateriales = estado == LuminariaEstado.REPARADA &&
            (_uiState.value.esSupervisor || _uiState.value.esAdministrador)
        if (requiereMateriales && materiales.isEmpty()) {
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
                localizacionNormalizada,
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
        val (pendientes, reparadas) = filtrarReparaciones(
            reparacionesAgenciaCache,
            valor,
            _uiState.value.vehiculoFiltroId
        )
        _uiState.update {
            it.copy(
                reparacionesPendientes = pendientes,
                reparacionesReparadas = reparadas
            )
        }
    }

    fun actualizarVehiculoFiltro(vehiculoId: Int?) {
        _uiState.update { it.copy(vehiculoFiltroId = vehiculoId) }
        val (pendientes, reparadas) = filtrarReparaciones(
            reparacionesAgenciaCache,
            _uiState.value.busquedaLocalizacion,
            vehiculoId
        )
        _uiState.update {
            it.copy(
                reparacionesPendientes = pendientes,
                reparacionesReparadas = reparadas
            )
        }
    }

    fun prepararFormularioRegistro() {
        _uiState.update { current ->
            val estadoInicial = if (current.esSupervisor || current.esAdministrador) {
                LuminariaEstado.PENDIENTE
            } else {
                LuminariaEstado.REPARADA
            }
            current.copy(
                localizacion = "",
                materialesSeleccionados = emptyList(),
                estadoSeleccionado = estadoInicial,
                vehiculoRegistroId = current.vehiculoRegistroId ?: current.vehiculoUsuarioId
            )
        }
    }

    fun actualizarVehiculoRegistro(vehiculoId: Int?) {
        _uiState.update { it.copy(vehiculoRegistroId = vehiculoId) }
    }

    fun procesarExcel(uri: android.net.Uri) {
        if (!_uiState.value.puedeImportarExcel) {
            _mensaje.value = LuminariaMensaje.Error("No tienes permisos para cargar luminarias")
            return
        }
        val vehiculos = _uiState.value.vehiculosAgencia
        if (vehiculos.isEmpty()) {
            _mensaje.value = LuminariaMensaje.Error("No se encontraron camiones disponibles para importar")
            return
        }
        _uiState.value = _uiState.value.copy(isProcessing = true)
        viewModelScope.launch {
            try {
                val resultado = leerXlsxLuminarias(uri, vehiculos)
                if (resultado.registrosPorVehiculo.isEmpty()) {
                    _mensaje.value = LuminariaMensaje.Error("El archivo XLSX está vacío")
                    _uiState.value = _uiState.value.copy(isProcessing = false)
                    return@launch
                }
                resultado.registrosPorVehiculo.forEach { (vehiculoId, registros) ->
                    repository.registrarLuminariasPendientes(
                        vehiculoId = vehiculoId,
                        registros = registros.map { registro ->
                            registro.copy(localizacion = normalizarLocalizacion(registro.localizacion))
                        },
                        ejecutorNombre = _uiState.value.ejecutorNombre.trim(),
                        ejecutorCedula = _uiState.value.ejecutorCedula
                    )
                }
                _uiState.value = _uiState.value.copy(isProcessing = false)
                val resumen = buildString {
                    append("Luminarias cargadas en ")
                    append(resultado.registrosPorVehiculo.size)
                    append(" camiones.")
                    if (resultado.hojasIgnoradas.isNotEmpty()) {
                        append(" Hojas ignoradas: ")
                        append(resultado.hojasIgnoradas.joinToString())
                    }
                }
                _mensaje.value = LuminariaMensaje.Exito(resumen)
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(isProcessing = false)
                _mensaje.value = LuminariaMensaje.Error("No se pudo leer el archivo XLSX")
            }
        }
    }

    private data class LuminariaXlsxResultado(
        val registrosPorVehiculo: Map<Int, List<LuminariaCsvRegistro>>,
        val hojasIgnoradas: List<String>
    )

    private suspend fun leerXlsxLuminarias(
        uri: android.net.Uri,
        vehiculos: List<VehiculosEntity>
    ): LuminariaXlsxResultado =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            val input = resolver.openInputStream(uri) ?: return@withContext LuminariaXlsxResultado(emptyMap(), emptyList())
            input.use { stream ->
                val workbook = org.apache.poi.xssf.usermodel.XSSFWorkbook(stream)
                workbook.use { wb ->
                    val formatter = org.apache.poi.ss.usermodel.DataFormatter()
                    val vehiculosPorPlaca = construirIndiceVehiculos(vehiculos)
                    val registrosPorVehiculo = linkedMapOf<Int, List<LuminariaCsvRegistro>>()
                    val hojasIgnoradas = mutableListOf<String>()
                    for (i in 0 until wb.numberOfSheets) {
                        val sheet = wb.getSheetAt(i)
                        val sheetName = sheet.sheetName
                        val placaNormalizada = normalizarPlacaKey(sheetName)
                        val placaSoloDigitos = placaNormalizada.filter(Char::isDigit)
                        val placaSinCeros = placaSoloDigitos.trimStart('0')
                        val vehiculo = listOf(placaNormalizada, placaSoloDigitos, placaSinCeros)
                            .firstNotNullOfOrNull { key -> vehiculosPorPlaca[key] }
                        if (vehiculo == null) {
                            hojasIgnoradas.add(sheetName)
                            continue
                        }
                        val registros = leerSheetLuminarias(sheet, formatter)
                        if (registros.isNotEmpty()) {
                            registrosPorVehiculo[vehiculo.id] = registros
                        }
                    }
                    LuminariaXlsxResultado(registrosPorVehiculo, hojasIgnoradas)
                }
            }
        }

    private fun leerSheetLuminarias(
        sheet: org.apache.poi.ss.usermodel.Sheet,
        formatter: org.apache.poi.ss.usermodel.DataFormatter
    ): List<LuminariaCsvRegistro> {
        val lastRow = sheet.lastRowNum
        if (lastRow < 0) return emptyList()
        var headerRowIndex = 0
        while (headerRowIndex <= lastRow) {
            val row = sheet.getRow(headerRowIndex)
            val rowValues = row?.let { extractRowValues(it, formatter) }.orEmpty()
            if (rowValues.any { it.isNotBlank() }) {
                break
            }
            headerRowIndex++
        }
        if (headerRowIndex > lastRow) return emptyList()
        val headerRow = sheet.getRow(headerRowIndex)
        val headerValues = headerRow?.let { extractRowValues(it, formatter) }.orEmpty()
        val normalizedHeader = headerValues.map { normalizeHeader(it) }
        val hasHeader = normalizedHeader.any { it.contains("localizacion") }
        val localizacionIndex = normalizedHeader.indexOfFirst { it.contains("localizacion") }.takeIf { it >= 0 } ?: 0
        val clienteIndex = normalizedHeader.indexOfFirst { it.contains("cliente") }
        val contactoIndex = normalizedHeader.indexOfFirst { it.contains("contacto") || it.contains("telefono") }
        val observacionesIndex = normalizedHeader.indexOfFirst { it.contains("observacion") }
        val startIndex = if (hasHeader) headerRowIndex + 1 else headerRowIndex
        val registros = linkedMapOf<String, LuminariaCsvRegistro>()
        for (rowIndex in startIndex..lastRow) {
            val row = sheet.getRow(rowIndex) ?: continue
            val columns = extractRowValues(row, formatter)
            if (columns.all { it.isBlank() }) continue
            val localizacion = columns.getOrNull(localizacionIndex)?.trim().orEmpty()
            if (localizacion.isBlank()) continue
            val cliente = columns.getOrNull(clienteIndex)?.trim().takeIf { !it.isNullOrEmpty() }
            val contacto = columns.getOrNull(contactoIndex)?.trim().takeIf { !it.isNullOrEmpty() }
            val observaciones = columns.getOrNull(observacionesIndex)?.trim().takeIf { !it.isNullOrEmpty() }
            val existente = registros[localizacion]
            registros[localizacion] = if (existente == null) {
                LuminariaCsvRegistro(localizacion, cliente, contacto, observaciones)
            } else {
                existente.copy(
                    cliente = existente.cliente ?: cliente,
                    contacto = existente.contacto ?: contacto,
                    observaciones = existente.observaciones ?: observaciones
                )
            }
        }
        return registros.values.toList()
    }

    private fun extractRowValues(
        row: org.apache.poi.ss.usermodel.Row,
        formatter: org.apache.poi.ss.usermodel.DataFormatter
    ): List<String> {
        val lastCell = row.lastCellNum.toInt().takeIf { it >= 0 } ?: return emptyList()
        return (0 until lastCell).map { index ->
            formatter.formatCellValue(row.getCell(index)).trim()
        }
    }

    private fun normalizeHeader(value: String): String {
        val normalized = java.text.Normalizer.normalize(value.trim(), java.text.Normalizer.Form.NFD)
        return normalized.replace("\\p{M}+".toRegex(), "").lowercase()
    }

    private fun construirIndiceVehiculos(vehiculos: List<VehiculosEntity>): Map<String, VehiculosEntity> {
        val index = mutableMapOf<String, VehiculosEntity>()
        vehiculos.forEach { vehiculo ->
            val placaRaw = vehiculo.placa.toString()
            val placaKey = normalizarPlacaKey(placaRaw)
            val digits = placaKey.filter(Char::isDigit)
            val digitsSinCeros = digits.trimStart('0')
            listOf(placaKey, digits, digitsSinCeros)
                .filter { it.isNotBlank() }
                .forEach { key ->
                    index.putIfAbsent(key, vehiculo)
                }
        }
        return index
    }

    private fun normalizarPlacaKey(raw: String): String {
        val normalized = java.text.Normalizer.normalize(raw.trim(), java.text.Normalizer.Form.NFD)
        return normalized
            .replace("\\p{M}+".toRegex(), "")
            .uppercase()
            .replace("[^A-Z0-9]".toRegex(), "")
    }

    fun enviarMensaje(texto: String, esError: Boolean = false) {
        _mensaje.value = if (esError) LuminariaMensaje.Error(texto) else LuminariaMensaje.Exito(texto)
    }

    fun obtenerNombreMachote(): String {
        val agencia = _uiState.value.agenciaUsuario?.trim().orEmpty()
        val sufijo = if (agencia.isBlank()) "General" else agencia.replace("\\s+".toRegex(), "_")
        val fecha = java.time.LocalDate.now()
        return "Machote_Luminarias_${sufijo}_${fecha}.xlsx"
    }

    fun normalizarLocalizacion(valor: String): String {
        val trimmed = valor.trim()
        if (trimmed.isBlank()) return ""
        if (localizacionRegex.matches(trimmed)) return trimmed
        val digits = trimmed.filter(Char::isDigit)
        if (digits.isBlank()) return ""
        val padded = if (digits.length < 11) digits.padStart(11, '0') else digits
        val finalDigits = padded.take(11)
        return "${finalDigits.substring(0, 4)}-${finalDigits.substring(4, 7)}-${finalDigits.substring(7, 9)}-${finalDigits.substring(9, 11)}"
    }

    suspend fun buscarMedidorPorLocalizacion(localizacion: String) =
        localizacion.trim().toLongOrNull()?.let { repository.buscarMedidorPorLocalizacion(it) }

    private fun filtrarReparaciones(
        reparaciones: List<LuminariaReparacionEntity>,
        busqueda: String,
        vehiculoFiltroId: Int?
    ): Pair<List<LuminariaReparacionEntity>, List<LuminariaReparacionEntity>> {
        val texto = busqueda.trim().lowercase()
        val filtradasPorVehiculo = if (vehiculoFiltroId == null) {
            reparaciones
        } else {
            reparaciones.filter { it.vehiculoId == vehiculoFiltroId }
        }
        val filtradas = filtradasPorVehiculo.filter { reparacion ->
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

    fun reasignarVehiculo(id: Long, nuevoVehiculoId: Int) {
        viewModelScope.launch {
            val reparacion = repository.obtenerReparacionLuminaria(id)
            if (reparacion == null) {
                _mensaje.value = LuminariaMensaje.Error("No se encontró la luminaria")
                return@launch
            }
            val estado = LuminariaEstado.fromRaw(reparacion.estado)
            if (estado != LuminariaEstado.PENDIENTE) {
                _mensaje.value = LuminariaMensaje.Error("Solo puedes reasignar luminarias pendientes")
                return@launch
            }
            repository.actualizarVehiculoLuminaria(id, nuevoVehiculoId)
            _mensaje.value = LuminariaMensaje.Exito("Camión reasignado")
        }
    }

    fun actualizarReparacion(
        id: Long,
        nuevaLocalizacion: String,
        materiales: List<LuminariaMaterialSeleccionado>,
        estado: LuminariaEstado,
        ejecutorNombre: String,
        ejecutorCedula: String?,
        vehiculoIdSeleccionado: Int?
    ) {
        viewModelScope.launch {
            val reparacion = repository.obtenerReparacionLuminaria(id)
            if (reparacion != null && vehiculoIdSeleccionado != null && reparacion.vehiculoId != vehiculoIdSeleccionado) {
                if (LuminariaEstado.fromRaw(reparacion.estado) == LuminariaEstado.PENDIENTE) {
                    repository.actualizarVehiculoLuminaria(id, vehiculoIdSeleccionado)
                }
            }
            actualizarReparacion(
                id,
                nuevaLocalizacion,
                materiales,
                estado,
                ejecutorNombre,
                ejecutorCedula
            )
        }
    }
}
