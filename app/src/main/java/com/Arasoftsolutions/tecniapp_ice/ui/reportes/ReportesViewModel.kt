package com.Arasoftsolutions.tecniapp_ice.ui.reportes

import android.app.Application
import android.text.TextUtils
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AveriaEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.InventarioConVehiculo
import com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado
import com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaReparacionEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.MaterialEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.UserEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.ui.averias.MaterialUso
import com.Arasoftsolutions.tecniapp_ice.ui.averias.MaterialesSerializer
import com.Arasoftsolutions.tecniapp_ice.ui.reportes.ExcelReportExporter.ExportPayload
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking

private val functions by lazy { FirebaseFunctions.getInstance() }

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

private data class UserContext(
    val user: UserEntity?,
    val vehiculoId: Int?,
    val placaVehiculo: String?,
    val nombres: Set<String>
)

enum class ReportType(@StringRes val titleRes: Int, val fileNameKey: String) {
    MI_RESUMEN(R.string.reportes_tipo_mi_resumen, "mi_resumen"),
    MIS_AVERIAS(R.string.reportes_tipo_mis_averias, "mis_averias"),
    MIS_LUMINARIAS(R.string.reportes_tipo_mis_luminarias, "mis_luminarias"),
    MI_INVENTARIO(R.string.reportes_tipo_mi_inventario, "mi_inventario"),
    MI_BITACORA(R.string.reportes_tipo_mi_bitacora, "mi_bitacora")
}

enum class ReportTab {
    RESUMEN,
    OPERACION,
    MATERIALES,
    INVENTARIO,
    BITACORA
}

enum class ReportFileType {
    EXCEL,
    PDF
}

enum class ReportExportStatus {
    OK,
    ERROR
}

data class ResumenKpiItem(
    val titulo: String,
    val valor: String,
    val detalle: String? = null
)

data class MisAveriaReportItem(
    val caseId: String,
    val nise: String,
    val ubicacion: String,
    val estado: String,
    val fechaReporte: String,
    val fechaAtencion: String,
    val materialesCantidad: Int,
    val materialesResumen: String
)

data class MisLuminariaReportItem(
    val localizacion: String,
    val estado: String,
    val fecha: String,
    val vehiculo: String,
    val comunidad: String,
    val materialesResumen: String
)

data class InventarioReportItem(
    val codigo: String,
    val descripcion: String,
    val cantidad: Double,
    val unidad: String,
    val minimo: Double? = null,
    val origen: String? = null,
    val fechaActualizacion: String? = null
)

data class InventarioMovimientoResumen(
    val entradas: Double,
    val salidas: Double,
    val neto: Double
)

data class BitacoraResumen(
    val horasTrabajadas: String,
    val kilometros: String,
    val averiasAtendidas: Int,
    val luminariasReparadas: Int,
    val materialTop: List<MaterialTotalItem>
)

data class BitacoraEventItem(
    val tipo: String,
    val referencia: String,
    val fecha: String,
    val descripcion: String,
    val cantidad: String
)

data class ReportFilterSelection(
    val rango: String,
    val tecnico: String? = null,
    val camion: String? = null,
    val agencia: String? = null,
    val tipoReporte: String? = null,
    val estadoSync: String? = null
)

data class ReportSectionSummary(
    val type: ReportType,
    val title: String,
    val count: Int,
    val summary: String? = null
)

data class ExportHistoryItem(
    val id: String,
    val fileName: String,
    val fileType: ReportFileType,
    val dateTime: String,
    val fileSize: String? = null,
    val status: ReportExportStatus = ReportExportStatus.OK,
    val uri: String
)

data class MaterialTotalItem(
    val codigo: String,
    val descripcion: String,
    val total: Int,
    val averias: Int,
    val existenciaActual: Double,
    val existenciaTexto: String
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
    data class MiResumen(val items: List<ResumenKpiItem>) : ReportExportData()
    data class MisAverias(val items: List<MisAveriaReportItem>) : ReportExportData()
    data class MisLuminarias(val items: List<MisLuminariaReportItem>) : ReportExportData()
    data class MiInventario(
        val criticos: List<InventarioReportItem>,
        val generales: List<InventarioReportItem>,
        val movimientos: InventarioMovimientoResumen
    ) : ReportExportData()

    data class MiBitacora(
        val resumen: BitacoraResumen,
        val eventos: List<BitacoraEventItem>
    ) : ReportExportData()
}

data class ReportesUiState(
    val fechaInicio: LocalDate,
    val fechaFin: LocalDate,
    val rangoTexto: String,
    val reporteSeleccionado: ReportType = ReportType.MI_RESUMEN,
    val selectedTab: ReportTab = ReportTab.RESUMEN,
    val loading: Boolean = false,
    val error: String? = null,
    val filtrosSeleccionados: ReportFilterSelection = ReportFilterSelection(rango = ""),
    val secciones: List<ReportSectionSummary> = emptyList(),
    val historialExports: List<ExportHistoryItem> = emptyList(),
    val resumen: ResumenTotales? = null,
    val isDefaultRange: Boolean = false,
    val isGlobalLoading: Boolean = false,
    val isEmailSending: Boolean = false,
    val resumenState: ReportSectionState<ResumenKpiItem> = ReportSectionState(),
    val misAveriasState: ReportSectionState<MisAveriaReportItem> = ReportSectionState(),
    val misLuminariasState: ReportSectionState<MisLuminariaReportItem> = ReportSectionState(),
    val miInventarioState: ReportSectionState<InventarioReportItem> = ReportSectionState(),
    val miInventarioCriticoState: ReportSectionState<InventarioReportItem> = ReportSectionState(),
    val miBitacoraState: ReportSectionState<BitacoraEventItem> = ReportSectionState(),
    val miBitacoraResumen: BitacoraResumen? = null,
    val miInventarioMovimientos: InventarioMovimientoResumen? = null
)

private data class DatosBase(
    val averias: List<AveriaReporteInterno>,
    val materialesTotales: List<MaterialTotalItem>,
    val totalMateriales: Int,
    val existenciasActuales: Map<String, Double>,
    val pendientes: List<AveriaEntity>
)

class ReportesViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "ReportesViewModel"
        private val RESUMEN_REGEX = Regex("^(\\d+)[xX]\\s+(.+)$")
    }

    private val database = AppDatabase.getInstance(app)
    private val repository = RoomRepository.getInstance(app)
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
            isDefaultRange = true,
            filtrosSeleccionados = ReportFilterSelection(
                rango = buildRangeText(initialRange.first, initialRange.second)
            )
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()
    private var cachedBase: DatosBase? = null
    private var cachedRange: Pair<LocalDate, LocalDate>? = null

    init {
        val tecnico = auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
        _uiState.update { state ->
            state.copy(
                filtrosSeleccionados = state.filtrosSeleccionados.copy(tecnico = tecnico)
            )
        }
    }

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
                selectedTab = it.selectedTab,
                isDefaultRange = esRangoInicial,
                filtrosSeleccionados = it.filtrosSeleccionados.copy(
                    rango = buildRangeText(nuevoInicio, nuevoFin)
                ),
                historialExports = it.historialExports,
                secciones = it.secciones
            )
        }
    }

    fun restablecerRango() {
        val (inicio, fin) = initialRange
        actualizarRangoFechas(inicio, fin)
    }

    fun seleccionarTipo(tipo: ReportType) {
        if (uiState.value.reporteSeleccionado == tipo) return
        _uiState.update {
            it.copy(
                reporteSeleccionado = tipo,
                selectedTab = mapReportTypeToTab(tipo),
                filtrosSeleccionados = it.filtrosSeleccionados.copy(
                    tipoReporte = getApplication<Application>().getString(tipo.titleRes)
                )
            )
        }
    }

    fun seleccionarTab(tab: ReportTab) {
        if (uiState.value.selectedTab == tab) return
        val tipo = mapTabToReportType(tab)
        seleccionarTipo(tipo)
    }

    fun agregarExportHistorial(item: ExportHistoryItem) {
        _uiState.update { it.copy(historialExports = listOf(item) + it.historialExports) }
    }

    fun eliminarHistorial(item: ExportHistoryItem) {
        _uiState.update { state ->
            state.copy(historialExports = state.historialExports.filterNot { it.id == item.id })
        }
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
                    ReportType.MI_RESUMEN -> {
                        val resumen = construirResumenKpis(state.fechaInicio, state.fechaFin)
                        val resumenCards = ResumenTotales(
                            totalAverias = resumen.firstOrNull {
                                it.titulo == getString(R.string.reportes_kpi_averias_atendidas)
                            }?.valor?.toIntOrNull() ?: 0,
                            totalMateriales = resumen.firstOrNull {
                                it.titulo == getString(R.string.reportes_kpi_material_consumido)
                            }?.valor?.toIntOrNull() ?: 0,
                            totalMaterialesDistintos = resumen.firstOrNull {
                                it.titulo == getString(R.string.reportes_kpi_luminarias_reparadas)
                            }?.valor?.toIntOrNull() ?: 0
                        )
                        setResumenSuccess(resumen, resumenCards)
                    }
                    ReportType.MIS_AVERIAS -> {
                        val datos = construirMisAverias(state.fechaInicio, state.fechaFin)
                        val resumen = ResumenTotales(
                            totalAverias = datos.size,
                            totalMateriales = datos.sumOf { it.materialesCantidad },
                            totalMaterialesDistintos = datos.count { it.materialesCantidad > 0 }
                        )
                        setMisAveriasSuccess(datos, resumen)
                    }
                    ReportType.MIS_LUMINARIAS -> {
                        val datos = construirMisLuminarias(state.fechaInicio, state.fechaFin)
                        val resumen = ResumenTotales(
                            totalAverias = datos.size,
                            totalMateriales = datos.count { it.materialesResumen.isNotBlank() },
                            totalMaterialesDistintos = datos.count { it.estado.contains("pend", true) }
                        )
                        setMisLuminariasSuccess(datos, resumen)
                    }
                    ReportType.MI_INVENTARIO -> {
                        val inventario = construirMiInventario(state.fechaInicio, state.fechaFin)
                        val resumen = ResumenTotales(
                            totalAverias = inventario.generales.size,
                            totalMateriales = inventario.criticos.size,
                            totalMaterialesDistintos = inventario.generales.count { it.cantidad > 0 }
                        )
                        setMiInventarioSuccess(inventario, resumen)
                    }
                    ReportType.MI_BITACORA -> {
                        val bitacora = construirBitacora(state.fechaInicio, state.fechaFin)
                        val resumen = ResumenTotales(
                            totalAverias = bitacora.resumen.averiasAtendidas,
                            totalMateriales = bitacora.resumen.materialTop.sumOf { it.total },
                            totalMaterialesDistintos = bitacora.resumen.luminariasReparadas
                        )
                        setMiBitacoraSuccess(bitacora, resumen)
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

                val fileName = generarNombreArchivo(tipo, state.fechaInicio, state.fechaFin)
                val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)

                functions
                    .getHttpsCallable("sendReport")
                    .call(
                        mapOf(
                            "email" to destino,
                            "reportName" to nombreReporte,
                            "fileName" to fileName,
                            "fileBase64" to encoded,
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
            ReportType.MI_RESUMEN -> {
                if (!state.resumenState.hasContent) return null
                ReportExportData.MiResumen(state.resumenState.items)
            }
            ReportType.MIS_AVERIAS -> {
                if (!state.misAveriasState.hasContent) return null
                ReportExportData.MisAverias(state.misAveriasState.items)
            }
            ReportType.MIS_LUMINARIAS -> {
                if (!state.misLuminariasState.hasContent) return null
                ReportExportData.MisLuminarias(state.misLuminariasState.items)
            }
            ReportType.MI_INVENTARIO -> {
                if (!state.miInventarioState.hasContent && !state.miInventarioCriticoState.hasContent) return null
                ReportExportData.MiInventario(
                    criticos = state.miInventarioCriticoState.items,
                    generales = state.miInventarioState.items,
                    movimientos = state.miInventarioMovimientos ?: InventarioMovimientoResumen(0.0, 0.0, 0.0)
                )
            }
            ReportType.MI_BITACORA -> {
                val resumen = state.miBitacoraResumen ?: return null
                if (!state.miBitacoraState.hasContent) return null
                ReportExportData.MiBitacora(resumen, state.miBitacoraState.items)
            }
        }
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
        val placaVehiculo = runBlockingUserContext()?.placaVehiculo?.trim()?.takeIf { it.isNotBlank() }

        val catalogoPorCodigo = catalogo.associateBy { it.codigo }
        val catalogoPorDescripcion = catalogo.associateBy { it.descripcion.trim().lowercase(locale) }

        val atendidas = averias.mapNotNull { entity ->
            val finalMillis = obtenerFechaAtencion(entity) ?: return@mapNotNull null
            if (finalMillis < inicioMillis || finalMillis >= finExclusiveMillis) return@mapNotNull null
            if (!coincideConUsuario(entity, currentUid, currentNombre)) return@mapNotNull null
            if (!coincideConVehiculo(entity, placaVehiculo)) return@mapNotNull null
            val materiales = obtenerMateriales(entity, catalogoPorCodigo, catalogoPorDescripcion)
            AveriaReporteInterno(entity, finalMillis, materiales)
        }.sortedByDescending { it.finalMillis }

        val pendientes = averias.filter { entity ->
            entity.estado.isNotBlank() && !entity.estado.equals("Resuelta", ignoreCase = true) &&
                entity.fechaInicioMillis in inicioMillis until finExclusiveMillis &&
                coincideConUsuarioAsignado(entity, currentUid, currentNombre) &&
                coincideConVehiculo(entity, placaVehiculo)
        }

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
            existenciasActuales = existenciasActuales,
            pendientes = pendientes
        )
    }

    private suspend fun construirResumenKpis(inicio: LocalDate, fin: LocalDate): List<ResumenKpiItem> {
        val base = obtenerDatosBase(inicio, fin)
        val luminarias = construirMisLuminarias(inicio, fin)
        val totalMaterialesLuminarias = luminarias.count { it.materialesResumen.isNotBlank() }
        val horas = calcularHorasTrabajadas(base.averias)
        val horasTexto = if (horas > 0.0) {
            formatCantidad(horas)
        } else {
            getString(R.string.reportes_kpi_horas_trabajadas_sin_datos)
        }
        val materialesTotales = base.totalMateriales + totalMaterialesLuminarias
        val pendientes = base.pendientes.size
        val luminariasPendientes = luminarias.count { it.estado.contains("pend", true) }

        return listOf(
            ResumenKpiItem(
                titulo = getString(R.string.reportes_kpi_averias_atendidas),
                valor = base.averias.size.toString(),
                detalle = getString(R.string.reportes_kpi_detalle_rango)
            ),
            ResumenKpiItem(
                titulo = getString(R.string.reportes_kpi_averias_pendientes),
                valor = pendientes.toString(),
                detalle = getString(R.string.reportes_kpi_detalle_pendientes)
            ),
            ResumenKpiItem(
                titulo = getString(R.string.reportes_kpi_luminarias_reparadas),
                valor = luminarias.count { it.estado.contains("repar", true) }.toString(),
                detalle = getString(R.string.reportes_kpi_detalle_luminarias)
            ),
            ResumenKpiItem(
                titulo = getString(R.string.reportes_kpi_luminarias_pendientes),
                valor = luminariasPendientes.toString(),
                detalle = getString(R.string.reportes_kpi_detalle_luminarias_pendientes)
            ),
            ResumenKpiItem(
                titulo = getString(R.string.reportes_kpi_material_consumido),
                valor = materialesTotales.toString(),
                detalle = getString(R.string.reportes_kpi_detalle_material)
            ),
            ResumenKpiItem(
                titulo = getString(R.string.reportes_kpi_horas_trabajadas),
                valor = horasTexto,
                detalle = getString(R.string.reportes_kpi_detalle_horas)
            )
        )
    }

    private suspend fun construirMisAverias(inicio: LocalDate, fin: LocalDate): List<MisAveriaReportItem> {
        val base = obtenerDatosBase(inicio, fin)
        return base.averias.map { raw ->
            val entidad = raw.entity
            val resumen = MaterialesSerializer.toSummary(raw.materiales)
            val totalMateriales = raw.materiales.sumOf { it.cantidad }
            MisAveriaReportItem(
                caseId = entidad.caseId,
                nise = entidad.nise?.trim().orEmpty(),
                ubicacion = entidad.localizacion?.trim().orEmpty().ifBlank {
                    entidad.direccion?.trim().orEmpty()
                },
                estado = entidad.estado.trim(),
                fechaReporte = formatDateTime(entidad.fechaInicioMillis),
                fechaAtencion = formatDateTime(raw.finalMillis),
                materialesCantidad = totalMateriales,
                materialesResumen = resumen
            )
        }
    }

    private suspend fun construirMisLuminarias(inicio: LocalDate, fin: LocalDate): List<MisLuminariaReportItem> {
        val contexto = obtenerUserContext()
        val zona = ZoneId.systemDefault()
        val inicioMillis = inicio.atStartOfDay(zona).toInstant().toEpochMilli()
        val finExclusiveMillis = fin.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli()
        val (reparaciones, vehiculos) = withContext(Dispatchers.IO) {
            val reparaciones = database.inventarioDao().observarReparaciones().first()
            val vehiculos = database.vehiculoDao().getAll()
            reparaciones to vehiculos
        }
        val vehiculosPorId = vehiculos.associateBy { it.id }
        val filtradas = reparaciones.filter { reparacion ->
            reparacion.fechaRegistro in inicioMillis until finExclusiveMillis &&
                coincideConVehiculo(reparacion, contexto.vehiculoId) &&
                coincideConEjecutor(reparacion, contexto.nombres)
        }.sortedByDescending { it.fechaRegistro }

        return filtradas.map { reparacion ->
            val vehiculo = vehiculosPorId[reparacion.vehiculoId]
            val materiales = com.Arasoftsolutions.tecniapp_ice.ui.luminarias.LuminariaMaterialSerializer
                .fromJson(reparacion.materialesJson)
            val resumenMateriales = com.Arasoftsolutions.tecniapp_ice.ui.luminarias.LuminariaMaterialSerializer
                .toSummary(materiales)
                .ifBlank { desconocidoMaterial }
            val estado = when (LuminariaEstado.fromRaw(reparacion.estado)) {
                LuminariaEstado.PENDIENTE -> getString(R.string.reportes_estado_luminaria_pendiente)
                LuminariaEstado.REPARADA -> getString(R.string.reportes_estado_luminaria_reparada)
            }
            MisLuminariaReportItem(
                localizacion = reparacion.localizacion,
                estado = estado,
                fecha = formatDateTime(reparacion.fechaRegistro),
                vehiculo = vehiculo?.placa?.toString().orEmpty().ifBlank { "-" },
                comunidad = reparacion.cliente?.trim().orEmpty().ifBlank { "-" },
                materialesResumen = resumenMateriales
            )
        }
    }

    private suspend fun construirMiInventario(inicio: LocalDate, fin: LocalDate): ReportExportData.MiInventario {
        val contexto = obtenerUserContext()
        val vehiculoId = contexto.vehiculoId
        val inventario = withContext(Dispatchers.IO) {
            if (vehiculoId != null) {
                database.inventarioDao().observarInventarioPorVehiculo(vehiculoId).first()
            } else {
                database.inventarioDao().observarInventarioGeneral().first()
            }
        }
        val criticoLimite = 2.0
        val criticos = inventario.filter { it.item.cantidadDisponible <= criticoLimite }
        val mappedCriticos = criticos.map { item ->
            InventarioReportItem(
                codigo = item.item.codigoMaterial,
                descripcion = item.item.descripcionMaterial,
                cantidad = item.item.cantidadDisponible,
                unidad = getString(R.string.reportes_inventario_unidad_default)
            )
        }
        val mappedGenerales = inventario.map { item ->
            InventarioReportItem(
                codigo = item.item.codigoMaterial,
                descripcion = item.item.descripcionMaterial,
                cantidad = item.item.cantidadDisponible,
                unidad = getString(R.string.reportes_inventario_unidad_default)
            )
        }
        val movimientos = calcularMovimientosInventario(inicio, fin, contexto)
        return ReportExportData.MiInventario(mappedCriticos, mappedGenerales, movimientos)
    }

    private suspend fun construirBitacora(inicio: LocalDate, fin: LocalDate): ReportExportData.MiBitacora {
        val base = obtenerDatosBase(inicio, fin)
        val luminarias = construirMisLuminarias(inicio, fin)
        val horasTrabajadas = calcularHorasTrabajadas(base.averias)
        val kilometros = calcularKilometros(base.averias)
        val materialesTop = base.materialesTotales.take(5)
        val resumen = BitacoraResumen(
            horasTrabajadas = formatCantidad(horasTrabajadas),
            kilometros = formatCantidad(kilometros),
            averiasAtendidas = base.averias.size,
            luminariasReparadas = luminarias.count { it.estado.contains("repar", true) },
            materialTop = materialesTop
        )
        val eventos = buildBitacoraEventos(base, luminarias)
        return ReportExportData.MiBitacora(resumen, eventos)
    }

    private fun setSectionLoading(tipo: ReportType) {
        _uiState.update { current ->
            when (tipo) {
                ReportType.MI_RESUMEN -> current.copy(
                    isGlobalLoading = true,
                    resumenState = current.resumenState.copy(isLoading = true),
                    misAveriasState = current.misAveriasState.copy(isLoading = false),
                    misLuminariasState = current.misLuminariasState.copy(isLoading = false),
                    miInventarioState = current.miInventarioState.copy(isLoading = false),
                    miBitacoraState = current.miBitacoraState.copy(isLoading = false)
                )
                ReportType.MIS_AVERIAS -> current.copy(
                    isGlobalLoading = true,
                    misAveriasState = current.misAveriasState.copy(isLoading = true),
                    resumenState = current.resumenState.copy(isLoading = false),
                    misLuminariasState = current.misLuminariasState.copy(isLoading = false),
                    miInventarioState = current.miInventarioState.copy(isLoading = false),
                    miBitacoraState = current.miBitacoraState.copy(isLoading = false)
                )
                ReportType.MIS_LUMINARIAS -> current.copy(
                    isGlobalLoading = true,
                    misLuminariasState = current.misLuminariasState.copy(isLoading = true),
                    resumenState = current.resumenState.copy(isLoading = false),
                    misAveriasState = current.misAveriasState.copy(isLoading = false),
                    miInventarioState = current.miInventarioState.copy(isLoading = false),
                    miBitacoraState = current.miBitacoraState.copy(isLoading = false)
                )
                ReportType.MI_INVENTARIO -> current.copy(
                    isGlobalLoading = true,
                    miInventarioState = current.miInventarioState.copy(isLoading = true),
                    miInventarioCriticoState = current.miInventarioCriticoState.copy(isLoading = true),
                    resumenState = current.resumenState.copy(isLoading = false),
                    misAveriasState = current.misAveriasState.copy(isLoading = false),
                    misLuminariasState = current.misLuminariasState.copy(isLoading = false),
                    miBitacoraState = current.miBitacoraState.copy(isLoading = false)
                )
                ReportType.MI_BITACORA -> current.copy(
                    isGlobalLoading = true,
                    miBitacoraState = current.miBitacoraState.copy(isLoading = true),
                    resumenState = current.resumenState.copy(isLoading = false),
                    misAveriasState = current.misAveriasState.copy(isLoading = false),
                    misLuminariasState = current.misLuminariasState.copy(isLoading = false),
                    miInventarioState = current.miInventarioState.copy(isLoading = false)
                )
            }
        }
    }

    private fun setResumenSuccess(items: List<ResumenKpiItem>, resumen: ResumenTotales) {
        _uiState.update { current ->
            current.copy(
                isGlobalLoading = false,
                resumen = resumen,
                resumenState = ReportSectionState(isLoading = false, items = items, hasContent = true),
                misAveriasState = current.misAveriasState.copy(isLoading = false),
                misLuminariasState = current.misLuminariasState.copy(isLoading = false),
                miInventarioState = current.miInventarioState.copy(isLoading = false),
                miBitacoraState = current.miBitacoraState.copy(isLoading = false)
            )
        }
    }

    private fun setMisAveriasSuccess(items: List<MisAveriaReportItem>, resumen: ResumenTotales) {
        _uiState.update { current ->
            current.copy(
                isGlobalLoading = false,
                resumen = resumen,
                misAveriasState = ReportSectionState(isLoading = false, items = items, hasContent = true),
                resumenState = current.resumenState.copy(isLoading = false),
                misLuminariasState = current.misLuminariasState.copy(isLoading = false)
            )
        }
    }

    private fun setMisLuminariasSuccess(items: List<MisLuminariaReportItem>, resumen: ResumenTotales) {
        _uiState.update { current ->
            current.copy(
                isGlobalLoading = false,
                resumen = resumen,
                misLuminariasState = ReportSectionState(isLoading = false, items = items, hasContent = true),
                resumenState = current.resumenState.copy(isLoading = false),
                misAveriasState = current.misAveriasState.copy(isLoading = false)
            )
        }
    }

    private fun setMiInventarioSuccess(inventario: ReportExportData.MiInventario, resumen: ResumenTotales) {
        _uiState.update { current ->
            current.copy(
                isGlobalLoading = false,
                resumen = resumen,
                miInventarioState = ReportSectionState(isLoading = false, items = inventario.generales, hasContent = true),
                miInventarioCriticoState = ReportSectionState(isLoading = false, items = inventario.criticos, hasContent = true),
                miInventarioMovimientos = inventario.movimientos,
                resumenState = current.resumenState.copy(isLoading = false)
            )
        }
    }

    private fun setMiBitacoraSuccess(bitacora: ReportExportData.MiBitacora, resumen: ResumenTotales) {
        _uiState.update { current ->
            current.copy(
                isGlobalLoading = false,
                resumen = resumen,
                miBitacoraState = ReportSectionState(isLoading = false, items = bitacora.eventos, hasContent = true),
                miBitacoraResumen = bitacora.resumen,
                resumenState = current.resumenState.copy(isLoading = false)
            )
        }
    }

    private fun setSectionFailure(tipo: ReportType) {
        _uiState.update { current ->
            when (tipo) {
                ReportType.MI_RESUMEN -> current.copy(
                    isGlobalLoading = false,
                    resumenState = current.resumenState.copy(isLoading = false)
                )
                ReportType.MIS_AVERIAS -> current.copy(
                    isGlobalLoading = false,
                    misAveriasState = current.misAveriasState.copy(isLoading = false)
                )
                ReportType.MIS_LUMINARIAS -> current.copy(
                    isGlobalLoading = false,
                    misLuminariasState = current.misLuminariasState.copy(isLoading = false)
                )
                ReportType.MI_INVENTARIO -> current.copy(
                    isGlobalLoading = false,
                    miInventarioState = current.miInventarioState.copy(isLoading = false),
                    miInventarioCriticoState = current.miInventarioCriticoState.copy(isLoading = false)
                )
                ReportType.MI_BITACORA -> current.copy(
                    isGlobalLoading = false,
                    miBitacoraState = current.miBitacoraState.copy(isLoading = false)
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

    private fun calcularHorasTrabajadas(averias: List<AveriaReporteInterno>): Double {
        val millis = averias.sumOf { raw ->
            val start = raw.entity.atencionHoraInicioMillis ?: raw.entity.horaInicioMillis
            val end = raw.entity.atencionHoraFinalMillis ?: raw.entity.horaFinalMillis
            if (start != null && end != null && end > start) {
                end - start
            } else {
                0L
            }
        }
        return millis / 3_600_000.0
    }

    private fun calcularKilometros(averias: List<AveriaReporteInterno>): Double {
        return averias.sumOf { raw ->
            val start = raw.entity.kilometrajeInicio
            val end = raw.entity.kilometrajeFinal
            if (start != null && end != null && end > start) {
                end - start
            } else {
                0.0
            }
        }.absoluteValue
    }

    private suspend fun obtenerUserContext(): UserContext {
        val uid = auth.currentUser?.uid
        val user = if (uid != null) repository.obtenerUsuario(uid) else null
        val placa = user?.placaVehiculo?.trim()?.takeIf { it.isNotBlank() }
        val vehiculoId = placa?.toLongOrNull()?.let { repository.obtenerVehiculoPorPlaca(it)?.id }
        val nombres = buildSet {
            auth.currentUser?.displayName?.trim()?.takeIf { it.isNotBlank() }?.let { add(it.lowercase(locale)) }
            user?.nombre?.trim()?.takeIf { it.isNotBlank() }?.let { add(it.lowercase(locale)) }
            user?.apellidos?.trim()?.takeIf { it.isNotBlank() }?.let { add(it.lowercase(locale)) }
            val nombreCompleto = listOfNotNull(user?.nombre, user?.apellidos)
                .joinToString(" ") { it.trim() }
                .trim()
            if (nombreCompleto.isNotBlank()) {
                add(nombreCompleto.lowercase(locale))
            }
        }
        return UserContext(user, vehiculoId, placa, nombres)
    }

    private fun runBlockingUserContext(): UserContext? {
        return try {
            runBlocking { obtenerUserContext() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun coincideConUsuario(entity: AveriaEntity, uid: String?, nombre: String?): Boolean {
        if (uid == null && nombre == null) return true
        val matchesUid = uid != null && entity.atendidoPorUid?.equals(uid, ignoreCase = true) == true
        val matchesNombre = nombre != null && entity.atendidoPorNombre?.trim()?.lowercase(locale) == nombre
        return matchesUid || matchesNombre
    }

    private fun coincideConUsuarioAsignado(entity: AveriaEntity, uid: String?, nombre: String?): Boolean {
        if (uid == null && nombre == null) return true
        val matchesUid = uid != null && entity.tecnicoAsignadoUid?.equals(uid, ignoreCase = true) == true
        val matchesNombre = nombre != null && entity.tecnicoAsignadoNombre?.trim()?.lowercase(locale) == nombre
        return matchesUid || matchesNombre
    }

    private fun coincideConVehiculo(entity: AveriaEntity, placa: String?): Boolean {
        if (placa.isNullOrBlank()) return true
        return entity.vehiculoAsignado?.trim()?.equals(placa, ignoreCase = true) == true
    }

    private fun coincideConVehiculo(reparacion: LuminariaReparacionEntity, vehiculoId: Int?): Boolean {
        if (vehiculoId == null) return true
        return reparacion.vehiculoId == vehiculoId
    }

    private fun coincideConEjecutor(reparacion: LuminariaReparacionEntity, nombres: Set<String>): Boolean {
        if (nombres.isEmpty()) return true
        val ejecutor = reparacion.ejecutorNombre.trim().lowercase(locale)
        return nombres.contains(ejecutor)
    }

    private suspend fun calcularMovimientosInventario(
        inicio: LocalDate,
        fin: LocalDate,
        contexto: UserContext
    ): InventarioMovimientoResumen {
        val zona = ZoneId.systemDefault()
        val inicioMillis = inicio.atStartOfDay(zona).toInstant().toEpochMilli()
        val finExclusiveMillis = fin.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli()
        val averias = withContext(Dispatchers.IO) { database.averiaDao().all() }
        val consumoAverias = averias.filter { entity ->
            val fecha = obtenerFechaAtencion(entity) ?: return@filter false
            fecha in inicioMillis until finExclusiveMillis &&
                coincideConVehiculo(entity, contexto.placaVehiculo)
        }.sumOf { entity ->
            obtenerMateriales(entity, emptyMap(), emptyMap()).sumOf { it.cantidad }.toDouble()
        }
        val luminarias = withContext(Dispatchers.IO) { database.inventarioDao().observarReparaciones().first() }
        val consumoLuminarias = luminarias.filter { reparacion ->
            reparacion.fechaRegistro in inicioMillis until finExclusiveMillis &&
                coincideConVehiculo(reparacion, contexto.vehiculoId)
        }.sumOf { reparacion ->
            com.Arasoftsolutions.tecniapp_ice.ui.luminarias.LuminariaMaterialSerializer
                .fromJson(reparacion.materialesJson)
                .sumOf { it.cantidad }
        }
        val salidas = consumoAverias + consumoLuminarias
        val entradas = 0.0
        val neto = entradas - salidas
        return InventarioMovimientoResumen(entradas, salidas, neto)
    }

    private fun buildBitacoraEventos(
        base: DatosBase,
        luminarias: List<MisLuminariaReportItem>
    ): List<BitacoraEventItem> {
        val eventos = mutableListOf<BitacoraEventItem>()
        base.averias.forEach { raw ->
            val entidad = raw.entity
            eventos += BitacoraEventItem(
                tipo = getString(R.string.reportes_bitacora_tipo_averia),
                referencia = entidad.caseId,
                fecha = formatDateTime(raw.finalMillis),
                descripcion = entidad.causa?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.reportes_bitacora_sin_descripcion),
                cantidad = getString(R.string.reportes_bitacora_material_cantidad, raw.materiales.sumOf { it.cantidad })
            )
            raw.materiales.forEach { material ->
                eventos += BitacoraEventItem(
                    tipo = getString(R.string.reportes_bitacora_tipo_material),
                    referencia = entidad.caseId,
                    fecha = formatDateTime(raw.finalMillis),
                    descripcion = material.descripcion,
                    cantidad = material.cantidad.toString()
                )
            }
        }
        luminarias.forEach { luminaria ->
            eventos += BitacoraEventItem(
                tipo = getString(R.string.reportes_bitacora_tipo_luminaria),
                referencia = luminaria.localizacion,
                fecha = luminaria.fecha,
                descripcion = luminaria.estado,
                cantidad = luminaria.materialesResumen
            )
        }
        return eventos.sortedByDescending { it.fecha }
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
            is ReportExportData.MiResumen -> data.items.size
            is ReportExportData.MisAverias -> data.items.size
            is ReportExportData.MisLuminarias -> data.items.size
            is ReportExportData.MiInventario -> data.generales.size
            is ReportExportData.MiBitacora -> data.eventos.size
        }
    }

    private fun generarNombreArchivo(tipo: ReportType, inicio: LocalDate, fin: LocalDate): String {
        val inicioTexto = inicio.format(fileNameFormatter)
        val finTexto = fin.format(fileNameFormatter)
        return "Reporte_${tipo.fileNameKey}_${inicioTexto}_${finTexto}.xlsx"
    }

    private fun mapReportTypeToTab(tipo: ReportType): ReportTab =
        when (tipo) {
            ReportType.MI_RESUMEN -> ReportTab.RESUMEN
            ReportType.MIS_AVERIAS -> ReportTab.OPERACION
            ReportType.MIS_LUMINARIAS -> ReportTab.MATERIALES
            ReportType.MI_INVENTARIO -> ReportTab.INVENTARIO
            ReportType.MI_BITACORA -> ReportTab.BITACORA
        }

    private fun mapTabToReportType(tab: ReportTab): ReportType =
        when (tab) {
            ReportTab.RESUMEN -> ReportType.MI_RESUMEN
            ReportTab.OPERACION -> ReportType.MIS_AVERIAS
            ReportTab.MATERIALES -> ReportType.MIS_LUMINARIAS
            ReportTab.INVENTARIO -> ReportType.MI_INVENTARIO
            ReportTab.BITACORA -> ReportType.MI_BITACORA
        }

    private fun getString(@StringRes resId: Int): String = getApplication<Application>().getString(resId)

    private fun getString(@StringRes resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)
}
