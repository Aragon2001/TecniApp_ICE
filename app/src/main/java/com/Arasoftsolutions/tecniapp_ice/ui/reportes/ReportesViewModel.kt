package com.Arasoftsolutions.tecniapp_ice.ui.reportes

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AveriaEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.MaterialEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.ui.averias.MaterialUso
import com.Arasoftsolutions.tecniapp_ice.ui.averias.MaterialesSerializer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    AVERIAS(R.string.reportes_tipo_averias, "averias"),
    MATERIALES_POR_AVERIA(R.string.reportes_tipo_material_por_averia, "material_por_averia"),
    MATERIALES_TOTALES(R.string.reportes_tipo_material_total, "material_total")
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
    val materiales: List<MaterialUso>,
    val resumen: String,
    val tieneMateriales: Boolean
)

data class MaterialTotalItem(
    val codigo: String,
    val descripcion: String,
    val total: Int,
    val averias: Int
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
}

data class ReportesUiState(
    val fechaInicio: LocalDate,
    val fechaFin: LocalDate,
    val rangoTexto: String,
    val reporteSeleccionado: ReportType = ReportType.AVERIAS,
    val resumen: ResumenTotales? = null,
    val isGlobalLoading: Boolean = false,
    val averiasState: ReportSectionState<AveriaReportItem> = ReportSectionState(),
    val materialesPorAveriaState: ReportSectionState<MaterialPorAveriaReportItem> = ReportSectionState(),
    val materialesTotalesState: ReportSectionState<MaterialTotalItem> = ReportSectionState()
)

private data class DatosBase(
    val averias: List<AveriaReporteInterno>,
    val materialesTotales: List<MaterialTotalItem>,
    val totalMateriales: Int
)

class ReportesViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "ReportesViewModel"
        private val RESUMEN_REGEX = Regex("^(\\d+)[xX]\\s+(.+)$")
    }

    private val database = AppDatabase.getInstance(app)
    private val locale: Locale = Locale.getDefault()
    private val rangeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", locale)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", locale)

    private val desconocidoMaterial = app.getString(R.string.reportes_material_desconocido)

    private val initialRange: Pair<LocalDate, LocalDate> = LocalDate.now().minusDays(6) to LocalDate.now()

    private val _uiState = MutableStateFlow(
        ReportesUiState(
            fechaInicio = initialRange.first,
            fechaFin = initialRange.second,
            rangoTexto = buildRangeText(initialRange.first, initialRange.second)
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

        _uiState.update {
            ReportesUiState(
                fechaInicio = nuevoInicio,
                fechaFin = nuevoFin,
                rangoTexto = buildRangeText(nuevoInicio, nuevoFin),
                reporteSeleccionado = it.reporteSeleccionado
            )
        }
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
                val base = obtenerDatosBase(state.fechaInicio, state.fechaFin)
                val resumen = ResumenTotales(
                    totalAverias = base.averias.size,
                    totalMateriales = base.totalMateriales,
                    totalMaterialesDistintos = base.materialesTotales.size
                )

                when (tipo) {
                    ReportType.AVERIAS -> {
                        val items = mapAverias(base)
                        setAveriasSuccess(items, resumen)
                    }
                    ReportType.MATERIALES_POR_AVERIA -> {
                        val items = mapMaterialesPorAveria(base)
                        setMaterialesPorAveriaSuccess(items, resumen)
                    }
                    ReportType.MATERIALES_TOTALES -> {
                        val items = base.materialesTotales
                        setMaterialesTotalesSuccess(items, resumen)
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
            construirDatosBase(averias, materialesCatalogo, inicio, fin)
        }

        cachedBase = resultado
        cachedRange = inicio to fin
        return resultado
    }

    private fun construirDatosBase(
        averias: List<AveriaEntity>,
        catalogo: List<MaterialEntity>,
        inicio: LocalDate,
        fin: LocalDate
    ): DatosBase {
        val zona = ZoneId.systemDefault()
        val inicioMillis = inicio.atStartOfDay(zona).toInstant().toEpochMilli()
        val finExclusiveMillis = fin.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli()

        val catalogoPorCodigo = catalogo.associateBy { it.codigo }
        val catalogoPorDescripcion = catalogo.associateBy { it.descripcion.trim().lowercase(locale) }

        val atendidas = averias.mapNotNull { entity ->
            val finalMillis = obtenerFechaAtencion(entity) ?: return@mapNotNull null
            if (finalMillis < inicioMillis || finalMillis >= finExclusiveMillis) return@mapNotNull null
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

        val materialesTotales = acumulados.values
            .map { acumulado ->
                val descripcion = acumulado.descripcion.ifBlank { desconocidoMaterial }
                val codigo = acumulado.codigo
                MaterialTotalItem(
                    codigo = codigo,
                    descripcion = descripcion,
                    total = acumulado.total,
                    averias = acumulado.averias.size
                )
            }
            .sortedWith(
                compareByDescending<MaterialTotalItem> { it.total }
                    .thenBy { it.descripcion.lowercase(locale) }
            )

        return DatosBase(
            averias = atendidas,
            materialesTotales = materialesTotales,
            totalMateriales = totalMateriales
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
            MaterialPorAveriaReportItem(
                caseId = entidad.caseId,
                fechaTexto = formatDateTime(raw.finalMillis),
                agencia = obtenerAgencia(entidad),
                materiales = raw.materiales,
                resumen = resumen,
                tieneMateriales = raw.materiales.isNotEmpty()
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
                    materialesTotalesState = current.materialesTotalesState.copy(isLoading = false)
                )
                ReportType.MATERIALES_POR_AVERIA -> current.copy(
                    isGlobalLoading = true,
                    materialesPorAveriaState = current.materialesPorAveriaState.copy(isLoading = true),
                    averiasState = current.averiasState.copy(isLoading = false),
                    materialesTotalesState = current.materialesTotalesState.copy(isLoading = false)
                )
                ReportType.MATERIALES_TOTALES -> current.copy(
                    isGlobalLoading = true,
                    materialesTotalesState = current.materialesTotalesState.copy(isLoading = true),
                    averiasState = current.averiasState.copy(isLoading = false),
                    materialesPorAveriaState = current.materialesPorAveriaState.copy(isLoading = false)
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
                materialesPorAveriaState = current.materialesPorAveriaState.copy(isLoading = false)
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

    private fun formatDateTime(millis: Long): String {
        return Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .format(dateTimeFormatter)
    }

    private fun buildRangeText(inicio: LocalDate, fin: LocalDate): String {
        return "${inicio.format(rangeFormatter)} – ${fin.format(rangeFormatter)}"
    }
}
