package com.Arasoftsolutions.tecniapp_ice.ui.reportes

import android.app.Application
import android.util.Log
import android.text.TextUtils
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AveriaEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.InventarioConVehiculo
import com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaReparacionEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.MaterialEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.ui.averias.MaterialUso
import com.Arasoftsolutions.tecniapp_ice.ui.averias.MaterialesSerializer
import com.Arasoftsolutions.tecniapp_ice.ui.reportes.ExcelReportExporter
import com.Arasoftsolutions.tecniapp_ice.ui.reportes.ExcelReportExporter.ExportPayload
import com.Arasoftsolutions.tecniapp_ice.ui.reportes.ExcelReportExporter.MIME_TYPE_XLSX
import com.google.firebase.auth.FirebaseAuth
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.DecimalFormat

private val functions by lazy { FirebaseFunctions.getInstance() }
private val storage by lazy { FirebaseStorage.getInstance() }


private data class AveriaReporteInterno(
    val entity: AveriaEntity,
    val finalMillis: Long,
    val materiales: List<MaterialUso>
)

private data class MaterialAcumulado(
    var codigo: String,
    var descripcion: String,
    var total: Int,
    val averias: MutableSet<String>
)

enum class ReportType(@StringRes val titleRes: Int, val fileNameKey: String) {
    AVERIAS(R.string.reportes_chip_averias, "averias"),
    MATERIALES_POR_AVERIA(R.string.reportes_chip_material_por_averia, "material_por_averia"),
    MATERIALES_TOTALES(R.string.reportes_chip_material_total, "material_total"),
    LUMINARIAS_REPARADAS(R.string.reportes_chip_luminarias, "luminarias_reparadas")
}

data class AveriaReportItem(
    val caseId: String,
    val fechaTexto: String,
    val agencia: String,
    val estado: String,
    val atendidoPor: String,
    val vehiculo: String?,
    val materialesResumen: String,
    val materialesCantidad: Int,
    val tieneMateriales: Boolean
)

data class MaterialPorAveriaReportItem(
    val caseId: String,
    val fechaTexto: String,
    val agencia: String,
    val vehiculo: String?,
    val materiales: List<MaterialUso>,
    val materialesDetalle: List<MaterialDetalleReportItem>,
    val resumen: String,
    val tieneMateriales: Boolean
)

data class MaterialDetalleReportItem(
    val codigo: String,
    val descripcion: String,
    val cantidad: Int,
    val existenciaActual: Double,
    val existenciaTexto: String
)

data class MaterialTotalItem(
    val codigo: String,
    val descripcion: String,
    val total: Int,
    val averias: Int,
    val existenciaActual: Double,
    val existenciaTexto: String
)

data class LuminariaReparadaReportItem(
    val id: Long,
    val fechaTexto: String,
    val localizacion: String,
    val localizacionTexto: String,
    val materialesTexto: String,
    val cantidadTotal: Double,
    val cantidadTexto: String,
    val estadoTexto: String,
    val ejecutorTexto: String,
    val vehiculoTexto: String
)

data class ReportSectionState<T>(
    val isLoading: Boolean = false,
    val items: List<T> = emptyList(),
    val hasContent: Boolean = false
)

data class ResumenTotales(
    val totalAverias: Int,
    val totalMateriales: Int,
    val totalMaterialesDistintos: Int
)

sealed class ReportExportData {
    data class Averias(val items: List<AveriaReportItem>) : ReportExportData()
    data class MaterialesPorAveria(val items: List<MaterialPorAveriaReportItem>) : ReportExportData()
    data class MaterialesTotales(val items: List<MaterialTotalItem>) : ReportExportData()
    data class LuminariasReparadas(val items: List<LuminariaReparadaReportItem>) : ReportExportData()
}

data class ReportesUiState(
    val fechaInicio: LocalDate,
    val fechaFin: LocalDate,
    val rangoTexto: String,
    val reporteSeleccionado: ReportType = ReportType.AVERIAS,
    val resumen: ResumenTotales? = null,
    val isDefaultRange: Boolean = false,
    val isGlobalLoading: Boolean = false,
    val isEmailSending: Boolean = false,
    val averiasState: ReportSectionState<AveriaReportItem> = ReportSectionState(),
    val materialesPorAveriaState: ReportSectionState<MaterialPorAveriaReportItem> = ReportSectionState(),
    val materialesTotalesState: ReportSectionState<MaterialTotalItem> = ReportSectionState(),
    val luminariasState: ReportSectionState<LuminariaReparadaReportItem> = ReportSectionState()
)

private data class DatosBase(
    val averias: List<AveriaReporteInterno>,
    val materialesTotales: List<MaterialTotalItem>,
    val totalMateriales: Int,
    val existenciasActuales: Map<String, Double>
)

private data class DatosLuminarias(
    val reparaciones: List<LuminariaReparacionEntity>,
    val totalMateriales: Double,
    val codigosDistintos: Int,
    val existenciasActuales: Map<String, Double>
)

class ReportesViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "ReportesViewModel"
        private val RESUMEN_REGEX = Regex("^(\\d+)[xX]\\s+(.+)$")
    }

    private val database = AppDatabase.getInstance(app)
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val locale: Locale = Locale.getDefault()
    private val rangeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", locale)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", locale)
    private val fileNameFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val quantityFormatter = DecimalFormat("#,##0.##")

    private val desconocidoMaterial = app.getString(R.string.reportes_material_desconocido)

    private val initialRange: Pair<LocalDate, LocalDate> = LocalDate.now().minusDays(6) to LocalDate.now()

    private val _uiState = MutableStateFlow(
        ReportesUiState(
            fechaInicio = initialRange.first,
            fechaFin = initialRange.second,
            rangoTexto = buildRangeText(initialRange.first, initialRange.second),
            isDefaultRange = true
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()
    private var cachedBase: DatosBase? = null
    private var cachedRange: Pair<LocalDate, LocalDate>? = null

    fun actualizarRangoFechas(inicio: LocalDate, fin: LocalDate) {
        val (nuevoInicio, nuevoFin) = if (inicio.isAfter(fin)) {
            fin to inicio
        } else {
            inicio to fin
        }

        val current = uiState.value
        if (current.fechaInicio == nuevoInicio && current.fechaFin == nuevoFin) return

        cachedBase = null
        cachedRange = null

        val esRangoInicial = nuevoInicio == initialRange.first && nuevoFin == initialRange.second
        _uiState.update {
            ReportesUiState(
                fechaInicio = nuevoInicio,
                fechaFin = nuevoFin,
                rangoTexto = buildRangeText(nuevoInicio, nuevoFin),
                reporteSeleccionado = it.reporteSeleccionado,
                isDefaultRange = esRangoInicial
            )
        }
    }

    fun restablecerRango() {
        val (inicio, fin) = initialRange
        actualizarRangoFechas(inicio, fin)
    }

    fun seleccionarTipo(tipo: ReportType) {
        if (uiState.value.reporteSeleccionado == tipo) return
        _uiState.update { it.copy(reporteSeleccionado = tipo) }
    }

    fun generarReporteSeleccionado() {
        generarReporte(uiState.value.reporteSeleccionado)
    }

    fun generarReporte(tipo: ReportType) {
        val state = uiState.value
        setSectionLoading(tipo)
        viewModelScope.launch {
            try {
                when (tipo) {
                    ReportType.AVERIAS -> {
                        val base = obtenerDatosBase(state.fechaInicio, state.fechaFin)
                        val resumen = ResumenTotales(
                            totalAverias = base.averias.size,
                            totalMateriales = base.totalMateriales,
                            totalMaterialesDistintos = base.materialesTotales.size
                        )
                        val items = mapAverias(base)
                        setAveriasSuccess(items, resumen)
                    }
                    ReportType.MATERIALES_POR_AVERIA -> {
                        val base = obtenerDatosBase(state.fechaInicio, state.fechaFin)
                        val resumen = ResumenTotales(
                            totalAverias = base.averias.size,
                            totalMateriales = base.totalMateriales,
                            totalMaterialesDistintos = base.materialesTotales.size
                        )
                        val items = mapMaterialesPorAveria(base)
                        setMaterialesPorAveriaSuccess(items, resumen)
                    }
                    ReportType.MATERIALES_TOTALES -> {
                        val base = obtenerDatosBase(state.fechaInicio, state.fechaFin)
                        val resumen = ResumenTotales(
                            totalAverias = base.averias.size,
                            totalMateriales = base.totalMateriales,
                            totalMaterialesDistintos = base.materialesTotales.size
                        )
                        val items = base.materialesTotales
                        setMaterialesTotalesSuccess(items, resumen)
                    }
                    ReportType.LUMINARIAS_REPARADAS -> {
                        val base = obtenerDatosLuminarias(state.fechaInicio, state.fechaFin)
                        val resumen = ResumenTotales(
                            totalAverias = base.reparaciones.size,
                            totalMateriales = base.totalMateriales.roundToInt(),
                            totalMaterialesDistintos = base.codigosDistintos
                        )
                        val items = mapLuminarias(base)
                        setLuminariasSuccess(items, resumen)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error generando reporte", t)
                setSectionFailure(tipo)
                val mensaje = getApplication<Application>().getString(R.string.reportes_error_carga)
                _messages.tryEmit(mensaje)
            }
        }
    }

    fun enviarReportePorCorreo() {
        val state = uiState.value
        if (state.isEmailSending || state.isGlobalLoading) return

        val tipo = state.reporteSeleccionado
        val datos = obtenerDatosParaExportar(tipo)
        if (datos == null) {
            _messages.tryEmit(getApplication<Application>().getString(R.string.reportes_correo_error_sin_datos))
            return
        }

        val destino = auth.currentUser?.email?.takeIf { it.isNotBlank() }
        if (destino == null) {
            _messages.tryEmit(getApplication<Application>().getString(R.string.reportes_correo_error_sin_email))
            return
        }

        val saludo = auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: getApplication<Application>().getString(R.string.reportes_correo_saludo_generico)

        val nombreReporte = getApplication<Application>().getString(tipo.titleRes)
        val totalRegistros = contarRegistros(datos)

        // (Opcional) lo dejas si lo sigues usando en UI o logs
        val cuerpo = construirCuerpoCorreo(
            saludo = saludo,
            nombreReporte = nombreReporte,
            rango = state.rangoTexto,
            resumen = state.resumen,
            totalRegistros = totalRegistros
        )

        _uiState.update { it.copy(isEmailSending = true) }

        viewModelScope.launch {
            try {
                // 1) Generar Excel (como ya lo tenías)
                val exportPayload = ExportPayload(
                    tipo = tipo,
                    data = datos,
                    resumen = state.resumen,
                    rango = state.rangoTexto
                )

                val workbook = withContext(Dispatchers.Default) {
                    ExcelReportExporter.buildWorkbook(getApplication(), exportPayload)
                }

                val bytes = withContext(Dispatchers.IO) {
                    ByteArrayOutputStream().use { output ->
                        workbook.use { wb -> wb.write(output) }
                        output.toByteArray()
                    }
                }

                // 2) Subir a Firebase Storage
                val fileName = generarNombreArchivo(tipo, state.fechaInicio, state.fechaFin)
                val uid = auth.currentUser?.uid ?: "anon"

                val ref = storage.reference
                    .child("reportes")
                    .child(uid)
                    .child(fileName)

                ref.putBytes(bytes).await()
                val downloadUrl = ref.downloadUrl.await().toString()

                // 3) Llamar Cloud Function para enviar el correo con link
                val result = functions
                    .getHttpsCallable("sendReport")
                    .call(
                        mapOf(
                            "email" to destino,
                            "reportName" to nombreReporte,
                            "downloadUrl" to downloadUrl,
                            "subtitle" to "Rango: ${state.rangoTexto}"
                        )
                    ).await()

                _uiState.update { it.copy(isEmailSending = false) }
                _messages.tryEmit(getApplication<Application>().getString(R.string.reportes_correo_exito, destino))

            } catch (t: Throwable) {
                Log.e(TAG, "Error enviando reporte por correo", t)
                _uiState.update { it.copy(isEmailSending = false) }
                _messages.tryEmit(getApplication<Application>().getString(R.string.reportes_correo_error_generacion))
            }
        }
    }


    fun obtenerDatosParaExportar(tipo: ReportType): ReportExportData? {
        val state = uiState.value
        return when (tipo) {
            ReportType.AVERIAS -> {
                if (!state.averiasState.hasContent) return null
                ReportExportData.Averias(state.averiasState.items)
            }
            ReportType.MATERIALES_POR_AVERIA -> {
                if (!state.materialesPorAveriaState.hasContent) return null
                ReportExportData.MaterialesPorAveria(state.materialesPorAveriaState.items)
            }
            ReportType.MATERIALES_TOTALES -> {
                if (!state.materialesTotalesState.hasContent) return null
                ReportExportData.MaterialesTotales(state.materialesTotalesState.items)
            }
            ReportType.LUMINARIAS_REPARADAS -> {
                if (!state.luminariasState.hasContent) return null
                ReportExportData.LuminariasReparadas(state.luminariasState.items)
            }
        }
    }

    private suspend fun obtenerDatosLuminarias(inicio: LocalDate, fin: LocalDate): DatosLuminarias {
        val zona = ZoneId.systemDefault()
        val inicioMillis = inicio.atStartOfDay(zona).toInstant().toEpochMilli()
        val finExclusiveMillis = fin.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli()
        val (reparaciones, inventario) = withContext(Dispatchers.IO) {
            val reparaciones = database.inventarioDao().observarReparaciones().first()
            val inventario = database.inventarioDao().observarInventarioGeneral().first()
            reparaciones to inventario
        }
        val reparacionesFiltradas = reparaciones.filter { it.fechaRegistro in inicioMillis until finExclusiveMillis }
            .sortedByDescending { it.fechaRegistro }
        val materiales = reparaciones.flatMap {
            com.Arasoftsolutions.tecniapp_ice.ui.luminarias.LuminariaMaterialSerializer.fromJson(it.materialesJson)
        }
        val totalMateriales = materiales.sumOf { it.cantidad }
        val codigosDistintos = materiales.map { it.codigo }.filter { it.isNotBlank() }.distinct().size
        return DatosLuminarias(reparaciones, totalMateriales, codigosDistintos)
    }

    private suspend fun obtenerDatosBase(inicio: LocalDate, fin: LocalDate): DatosBase {
        val cached = cachedBase
        val range = cachedRange
        if (cached != null && range?.first == inicio && range.second == fin) {
            return cached
        }

        val resultado = withContext(Dispatchers.IO) {
            val averias = database.averiaDao().all()
            val materialesCatalogo = database.materialDao().observarMateriales().first()
            val inventario = database.inventarioDao().observarInventarioGeneral().first()
            construirDatosBase(averias, materialesCatalogo, inventario, inicio, fin)
        }

        cachedBase = resultado
        cachedRange = inicio to fin
        return resultado
    }

    private fun construirDatosBase(
        averias: List<AveriaEntity>,
        catalogo: List<MaterialEntity>,
        inventario: List<InventarioConVehiculo>,
        inicio: LocalDate,
        fin: LocalDate
    ): DatosBase {
        val zona = ZoneId.systemDefault()
        val inicioMillis = inicio.atStartOfDay(zona).toInstant().toEpochMilli()
        val finExclusiveMillis = fin.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli()
        val currentUid = auth.currentUser?.uid?.takeIf { it.isNotBlank() }
        val currentNombre = auth.currentUser?.displayName?.trim()?.lowercase(locale)

        val catalogoPorCodigo = catalogo.associateBy { it.codigo }
        val catalogoPorDescripcion = catalogo.associateBy { it.descripcion.trim().lowercase(locale) }

        val atendidas = averias.mapNotNull { entity ->
            val finalMillis = obtenerFechaAtencion(entity) ?: return@mapNotNull null
            if (finalMillis < inicioMillis || finalMillis >= finExclusiveMillis) return@mapNotNull null
            if (currentUid != null || currentNombre != null) {
                val uid = entity.atendidoPorUid
                val nombre = entity.atendidoPorNombre?.trim()?.lowercase(locale)
                val matchesUid = currentUid != null && uid != null && uid.equals(currentUid, ignoreCase = true)
                val matchesNombre = currentNombre != null && nombre != null && nombre == currentNombre
                if (!matchesUid && !matchesNombre) return@mapNotNull null
            }
            val materiales = obtenerMateriales(entity, catalogoPorCodigo, catalogoPorDescripcion)
            AveriaReporteInterno(entity, finalMillis, materiales)
        }.sortedByDescending { it.finalMillis }

        val acumulados = mutableMapOf<String, MaterialAcumulado>()
        var totalMateriales = 0
        atendidas.forEach { raw ->
            raw.materiales.forEach { uso ->
                if (uso.cantidad <= 0) return@forEach
                val key = if (uso.codigo.isNotBlank()) {
                    uso.codigo
                } else {
                    uso.descripcion.lowercase(locale)
                }
                val acumulado = acumulados.getOrPut(key) {
                    MaterialAcumulado(
                        codigo = uso.codigo,
                        descripcion = uso.descripcion,
                        total = 0,
                        averias = mutableSetOf()
                    )
                }
                if (acumulado.codigo.isBlank() && uso.codigo.isNotBlank()) {
                    acumulado.codigo = uso.codigo
                }
                if (acumulado.descripcion.isBlank() && uso.descripcion.isNotBlank()) {
                    acumulado.descripcion = uso.descripcion
                }
                acumulado.total += uso.cantidad
                acumulado.averias += raw.entity.caseId
                totalMateriales += uso.cantidad
            }
        }

        val existenciasActuales = buildExistenciasActuales(inventario)
        val materialesTotales = acumulados.values
            .map { acumulado ->
                val descripcion = acumulado.descripcion.ifBlank { desconocidoMaterial }
                val codigo = acumulado.codigo
                val existencia = buscarExistenciaActual(existenciasActuales, codigo, descripcion)
                MaterialTotalItem(
                    codigo = codigo,
                    descripcion = descripcion,
                    total = acumulado.total,
                    averias = acumulado.averias.size,
                    existenciaActual = existencia,
                    existenciaTexto = formatCantidad(existencia)
                )
            }
            .sortedWith(
                compareByDescending<MaterialTotalItem> { it.total }
                    .thenBy { it.descripcion.lowercase(locale) }
            )

        return DatosBase(
            averias = atendidas,
            materialesTotales = materialesTotales,
            totalMateriales = totalMateriales,
            existenciasActuales = existenciasActuales
        )
    }

    private fun mapAverias(base: DatosBase): List<AveriaReportItem> {
        return base.averias.map { raw ->
            val entidad = raw.entity
            val resumen = MaterialesSerializer.toSummary(raw.materiales)
            val totalMateriales = raw.materiales.sumOf { it.cantidad }
            AveriaReportItem(
                caseId = entidad.caseId,
                fechaTexto = formatDateTime(raw.finalMillis),
                agencia = obtenerAgencia(entidad),
                estado = entidad.estado.trim(),
                atendidoPor = obtenerAtendido(entidad),
                vehiculo = entidad.vehiculoAsignado?.trim()?.takeIf { it.isNotEmpty() },
                materialesResumen = resumen,
                materialesCantidad = totalMateriales,
                tieneMateriales = raw.materiales.isNotEmpty()
            )
        }
    }

    private fun mapMaterialesPorAveria(base: DatosBase): List<MaterialPorAveriaReportItem> {
        return base.averias.map { raw ->
            val entidad = raw.entity
            val resumen = MaterialesSerializer.toSummary(raw.materiales)
            val materialesDetalle = raw.materiales.map { uso ->
                val existencia = buscarExistenciaActual(base.existenciasActuales, uso.codigo, uso.descripcion)
                MaterialDetalleReportItem(
                    codigo = uso.codigo,
                    descripcion = uso.descripcion,
                    cantidad = uso.cantidad,
                    existenciaActual = existencia,
                    existenciaTexto = formatCantidad(existencia)
                )
            }
            MaterialPorAveriaReportItem(
                caseId = entidad.caseId,
                fechaTexto = formatDateTime(raw.finalMillis),
                agencia = obtenerAgencia(entidad),
                vehiculo = entidad.vehiculoAsignado?.trim()?.takeIf { it.isNotEmpty() },
                materiales = raw.materiales,
                materialesDetalle = materialesDetalle,
                resumen = resumen,
                tieneMateriales = raw.materiales.isNotEmpty()
            )
        }
    }

    private suspend fun mapLuminarias(base: DatosLuminarias): List<LuminariaReparadaReportItem> {
        val vehiculos = withContext(Dispatchers.IO) { database.vehiculoDao().getAll() }
        val vehiculosPorId = vehiculos.associateBy { it.id }
        return base.reparaciones.map { reparacion ->
            val vehiculo = vehiculosPorId[reparacion.vehiculoId]
            val vehiculoTexto = buildString {
                append(getApplication<Application>().getString(R.string.reportes_luminarias_vehiculo))
                append(" ")
                append(vehiculo?.placa?.toString().orEmpty().ifBlank { "-" })
                val agencia = vehiculo?.agencia?.trim().orEmpty()
                if (agencia.isNotBlank()) {
                    append(" · ")
                    append(agencia)
                }
            }
            val materiales = com.Arasoftsolutions.tecniapp_ice.ui.luminarias.LuminariaMaterialSerializer
                .fromJson(reparacion.materialesJson)
            val resumenMateriales = com.Arasoftsolutions.tecniapp_ice.ui.luminarias.LuminariaMaterialSerializer
                .toSummary(materiales)
                .ifBlank { desconocidoMaterial }
            val total = materiales.sumOf { it.cantidad }
            val estadoTexto = if (com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado.fromRaw(reparacion.estado) ==
                com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado.PENDIENTE
            ) {
                getApplication<Application>().getString(R.string.reportes_luminarias_estado_pendiente)
            } else {
                getApplication<Application>().getString(R.string.reportes_luminarias_estado_reparada)
            }
            LuminariaReparadaReportItem(
                id = reparacion.id,
                fechaTexto = formatDateTime(reparacion.fechaRegistro),
                localizacion = reparacion.localizacion,
                localizacionTexto = getApplication<Application>().getString(
                    R.string.reportes_luminarias_localizacion,
                    reparacion.localizacion
                ),
                materialesTexto = resumenMateriales,
                cantidadTotal = total,
                cantidadTexto = getApplication<Application>().getString(
                    R.string.reportes_luminarias_cantidad,
                    total
                ),
                estadoTexto = estadoTexto,
                ejecutorTexto = if (reparacion.ejecutorNombre.isNotBlank()) {
                    getApplication<Application>().getString(
                        R.string.reportes_luminarias_ejecutor,
                        reparacion.ejecutorNombre
                    )
                } else {
                    getApplication<Application>().getString(R.string.reportes_luminarias_ejecutor_sin_datos)
                },
                vehiculoTexto = vehiculoTexto
            )
        }
    }

    private fun setSectionLoading(tipo: ReportType) {
        _uiState.update { current ->
            when (tipo) {
                ReportType.AVERIAS -> current.copy(
                    isGlobalLoading = true,
                    averiasState = current.averiasState.copy(isLoading = true),
                    materialesPorAveriaState = current.materialesPorAveriaState.copy(isLoading = false),
                    materialesTotalesState = current.materialesTotalesState.copy(isLoading = false),
                    luminariasState = current.luminariasState.copy(isLoading = false)
                )
                ReportType.MATERIALES_POR_AVERIA -> current.copy(
                    isGlobalLoading = true,
                    materialesPorAveriaState = current.materialesPorAveriaState.copy(isLoading = true),
                    averiasState = current.averiasState.copy(isLoading = false),
                    materialesTotalesState = current.materialesTotalesState.copy(isLoading = false),
                    luminariasState = current.luminariasState.copy(isLoading = false)
                )
                ReportType.MATERIALES_TOTALES -> current.copy(
                    isGlobalLoading = true,
                    materialesTotalesState = current.materialesTotalesState.copy(isLoading = true),
                    averiasState = current.averiasState.copy(isLoading = false),
                    materialesPorAveriaState = current.materialesPorAveriaState.copy(isLoading = false),
                    luminariasState = current.luminariasState.copy(isLoading = false)
                )
                ReportType.LUMINARIAS_REPARADAS -> current.copy(
                    isGlobalLoading = true,
                    luminariasState = current.luminariasState.copy(isLoading = true),
                    averiasState = current.averiasState.copy(isLoading = false),
                    materialesPorAveriaState = current.materialesPorAveriaState.copy(isLoading = false),
                    materialesTotalesState = current.materialesTotalesState.copy(isLoading = false)
                )
            }
        }
    }

    private fun setAveriasSuccess(items: List<AveriaReportItem>, resumen: ResumenTotales) {
        _uiState.update { current ->
            current.copy(
                isGlobalLoading = false,
                resumen = resumen,
                averiasState = ReportSectionState(isLoading = false, items = items, hasContent = true),
                materialesPorAveriaState = current.materialesPorAveriaState.copy(isLoading = false),
                materialesTotalesState = current.materialesTotalesState.copy(isLoading = false)
            )
        }
    }

    private fun setMaterialesPorAveriaSuccess(
        items: List<MaterialPorAveriaReportItem>,
        resumen: ResumenTotales
    ) {
        _uiState.update { current ->
            current.copy(
                isGlobalLoading = false,
                resumen = resumen,
                materialesPorAveriaState = ReportSectionState(isLoading = false, items = items, hasContent = true),
                averiasState = current.averiasState.copy(isLoading = false),
                materialesTotalesState = current.materialesTotalesState.copy(isLoading = false)
            )
        }
    }

    private fun setMaterialesTotalesSuccess(
        items: List<MaterialTotalItem>,
        resumen: ResumenTotales
    ) {
        _uiState.update { current ->
            current.copy(
                isGlobalLoading = false,
                resumen = resumen,
                materialesTotalesState = ReportSectionState(isLoading = false, items = items, hasContent = true),
                averiasState = current.averiasState.copy(isLoading = false),
                materialesPorAveriaState = current.materialesPorAveriaState.copy(isLoading = false),
                luminariasState = current.luminariasState.copy(isLoading = false)
            )
        }
    }

    private fun setLuminariasSuccess(
        items: List<LuminariaReparadaReportItem>,
        resumen: ResumenTotales
    ) {
        _uiState.update { current ->
            current.copy(
                isGlobalLoading = false,
                resumen = resumen,
                luminariasState = ReportSectionState(isLoading = false, items = items, hasContent = true),
                averiasState = current.averiasState.copy(isLoading = false),
                materialesPorAveriaState = current.materialesPorAveriaState.copy(isLoading = false),
                materialesTotalesState = current.materialesTotalesState.copy(isLoading = false)
            )
        }
    }

    private fun setSectionFailure(tipo: ReportType) {
        _uiState.update { current ->
            when (tipo) {
                ReportType.AVERIAS -> current.copy(
                    isGlobalLoading = false,
                    averiasState = current.averiasState.copy(isLoading = false)
                )
                ReportType.MATERIALES_POR_AVERIA -> current.copy(
                    isGlobalLoading = false,
                    materialesPorAveriaState = current.materialesPorAveriaState.copy(isLoading = false)
                )
                ReportType.MATERIALES_TOTALES -> current.copy(
                    isGlobalLoading = false,
                    materialesTotalesState = current.materialesTotalesState.copy(isLoading = false)
                )
                ReportType.LUMINARIAS_REPARADAS -> current.copy(
                    isGlobalLoading = false,
                    luminariasState = current.luminariasState.copy(isLoading = false)
                )
            }
        }
    }

    private fun obtenerFechaAtencion(entity: AveriaEntity): Long? {
        return listOfNotNull(
            entity.atencionHoraFinalMillis,
            entity.horaFinalMillis,
            entity.atencionHoraInicioMillis,
            entity.horaInicioMillis,
            entity.fechaInicioMillis.takeIf { it > 0 }
        ).firstOrNull { it > 0 }
    }

    private fun obtenerMateriales(
        entity: AveriaEntity,
        catalogoPorCodigo: Map<String, MaterialEntity>,
        catalogoPorDescripcion: Map<String, MaterialEntity>
    ): List<MaterialUso> {
        val detalle = MaterialesSerializer.fromJson(entity.materialesDetalleJson)
        val materialesBase = if (detalle.isNotEmpty()) {
            detalle
        } else {
            parsearResumenMateriales(entity.materialesTexto)
        }
        return materialesBase.map { uso ->
            normalizarMaterial(uso, catalogoPorCodigo, catalogoPorDescripcion)
        }.filter { it.cantidad > 0 }
    }

    private fun normalizarMaterial(
        uso: MaterialUso,
        catalogoPorCodigo: Map<String, MaterialEntity>,
        catalogoPorDescripcion: Map<String, MaterialEntity>
    ): MaterialUso {
        val codigoLimpio = uso.codigo.trim()
        val descripcionLimpia = uso.descripcion.trim()
        val cantidad = uso.cantidad.coerceAtLeast(0)

        val descripcionResuelta = when {
            descripcionLimpia.isNotEmpty() -> descripcionLimpia
            codigoLimpio.isNotEmpty() -> catalogoPorCodigo[codigoLimpio]?.descripcion?.takeIf { it.isNotBlank() }
                ?: codigoLimpio
            else -> desconocidoMaterial
        }

        val codigoResuelto = when {
            codigoLimpio.isNotEmpty() -> codigoLimpio
            descripcionResuelta.isNotBlank() -> catalogoPorDescripcion[descripcionResuelta.lowercase(locale)]?.codigo ?: ""
            else -> ""
        }

        return MaterialUso(
            codigo = codigoResuelto,
            descripcion = descripcionResuelta,
            cantidad = cantidad
        )
    }

    private fun parsearResumenMateriales(resumen: String?): List<MaterialUso> {
        if (resumen.isNullOrBlank()) return emptyList()
        return resumen.split(",")
            .mapNotNull { item ->
                val texto = item.trim()
                if (texto.isEmpty()) return@mapNotNull null
                val match = RESUMEN_REGEX.find(texto)
                if (match != null) {
                    val cantidad = match.groupValues[1].toIntOrNull() ?: 1
                    val descripcion = match.groupValues[2].trim()
                    MaterialUso("", descripcion, cantidad)
                } else {
                    MaterialUso("", texto, 1)
                }
            }
    }

    private fun obtenerAgencia(entity: AveriaEntity): String {
        return entity.nombreAgencia?.trim()?.takeIf { it.isNotEmpty() }
            ?: entity.agencia?.trim()?.takeIf { it.isNotEmpty() }
            ?: ""
    }

    private fun obtenerAtendido(entity: AveriaEntity): String {
        return entity.atendidoPorNombre?.trim()?.takeIf { it.isNotEmpty() }
            ?: entity.tecnicoAsignadoNombre?.trim()?.takeIf { it.isNotEmpty() }
            ?: ""
    }

    private fun buildExistenciasActuales(items: List<InventarioConVehiculo>): Map<String, Double> {
        val acumulado = mutableMapOf<String, Double>()
        items.forEach { item ->
            val key = buildExistenciaKey(item.item.codigoMaterial, item.item.descripcionMaterial) ?: return@forEach
            acumulado[key] = (acumulado[key] ?: 0.0) + item.item.cantidadDisponible
        }
        return acumulado
    }

    private fun buscarExistenciaActual(
        existencias: Map<String, Double>,
        codigo: String,
        descripcion: String
    ): Double {
        val key = buildExistenciaKey(codigo, descripcion) ?: return 0.0
        return existencias[key] ?: 0.0
    }

    private fun buildExistenciaKey(codigo: String, descripcion: String): String? {
        val limpioCodigo = codigo.trim()
        if (limpioCodigo.isNotEmpty()) return limpioCodigo
        val limpiaDescripcion = descripcion.trim()
        return limpiaDescripcion.takeIf { it.isNotEmpty() }?.lowercase(locale)
    }

    private fun formatCantidad(cantidad: Double): String = quantityFormatter.format(cantidad)

    private fun formatDateTime(millis: Long): String {
        return Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .format(dateTimeFormatter)
    }

    private fun buildRangeText(inicio: LocalDate, fin: LocalDate): String {
        return "${inicio.format(rangeFormatter)} – ${fin.format(rangeFormatter)}"
    }

    private fun construirCuerpoCorreo(
        saludo: String,
        nombreReporte: String,
        rango: String,
        resumen: ResumenTotales?,
        totalRegistros: Int
    ): String {
        val app = getApplication<Application>()
        val registrosTexto = app.resources.getQuantityString(
            R.plurals.reportes_correo_registros,
            totalRegistros,
            totalRegistros
        )

        val safeSaludo = TextUtils.htmlEncode(saludo)
        val safeReporte = TextUtils.htmlEncode(nombreReporte)
        val safeRango = TextUtils.htmlEncode(rango)
        val safeRegistros = TextUtils.htmlEncode(registrosTexto)
        val safeCierre = TextUtils.htmlEncode(app.getString(R.string.reportes_correo_cierre))
        val resumenHtml = construirResumenCorreo(resumen)

        return """
            <html>
            <body style="font-family:'Roboto', Arial, sans-serif; color:#0F172A;">
                <h2 style="color:#0F9D58; margin-bottom:12px;">¡Hola $safeSaludo!</h2>
                <p style="margin:0 0 12px;">Te compartimos el reporte <strong>$safeReporte</strong> del periodo <strong>$safeRango</strong>.</p>
                <p style="margin:0 0 12px;">$safeRegistros</p>
                $resumenHtml
                <p style="margin:16px 0 4px;">$safeCierre</p>
                <p style="margin:0; font-weight:600; color:#004C8C;">Equipo TecniApp ICE</p>
            </body>
            </html>
        """.trimIndent()
    }

    private fun construirResumenCorreo(resumen: ResumenTotales?): String {
        if (resumen == null) return ""
        return """
            <div style="margin:16px 0; padding:16px; border-radius:12px; background-color:#F1F5F9;">
                <p style="margin:0 0 8px; font-weight:600; color:#004C8C;">Resumen rápido</p>
                <ul style="margin:0; padding-left:18px; line-height:1.6;">
                    <li><strong>Averías atendidas:</strong> ${resumen.totalAverias}</li>
                    <li><strong>Unidades de material:</strong> ${resumen.totalMateriales}</li>
                    <li><strong>Códigos distintos:</strong> ${resumen.totalMaterialesDistintos}</li>
                </ul>
            </div>
        """.trimIndent()
    }

    private fun contarRegistros(data: ReportExportData): Int {
        return when (data) {
            is ReportExportData.Averias -> data.items.size
            is ReportExportData.MaterialesPorAveria -> data.items.size
            is ReportExportData.MaterialesTotales -> data.items.size
            is ReportExportData.LuminariasReparadas -> data.items.size
        }
    }

    private fun generarNombreArchivo(tipo: ReportType, inicio: LocalDate, fin: LocalDate): String {
        val inicioTexto = inicio.format(fileNameFormatter)
        val finTexto = fin.format(fileNameFormatter)
        return "Reporte_${tipo.fileNameKey}_${inicioTexto}_${finTexto}.xlsx"
    }
}
