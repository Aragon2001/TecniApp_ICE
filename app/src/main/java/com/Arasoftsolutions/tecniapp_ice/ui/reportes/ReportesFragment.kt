package com.Arasoftsolutions.tecniapp_ice.ui.reportes

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.core.util.Pair
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.Database.entities.ReporteGeneradoEntity
import com.Arasoftsolutions.tecniapp_ice.R
import androidx.navigation.fragment.findNavController
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriaDeepLink
import com.Arasoftsolutions.tecniapp_ice.ui.luminarias.LuminariaDeepLink
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentReportesBinding
import com.Arasoftsolutions.tecniapp_ice.databinding.SheetReportExportBinding
import com.Arasoftsolutions.tecniapp_ice.databinding.SheetReportHistoryBinding
import com.Arasoftsolutions.tecniapp_ice.ui.reportes.ExcelReportExporter.ExportPayload
import com.Arasoftsolutions.tecniapp_ice.ui.reportes.ExcelReportExporter.MIME_TYPE_XLSX
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.HorizontalBarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReportesFragment : Fragment() {

    private var _binding: FragmentReportesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReportesViewModel by viewModels()
    private lateinit var resumenAdapter: ResumenKpiAdapter
    private lateinit var misAveriasAdapter: MisAveriasAdapter
    private lateinit var misLuminariasAdapter: MisLuminariasAdapter
    private lateinit var inventarioGeneralAdapter: InventarioReporteAdapter
    private lateinit var inventarioCriticoAdapter: InventarioReporteAdapter
    private lateinit var inventarioConsumoAdapter: InventarioConsumoAdapter
    private lateinit var bitacoraAdapter: BitacoraEventosAdapter
    private lateinit var historyAdapter: ReportHistoryAdapter
    private val locale = Locale.getDefault()

    private val reportTypes = listOf(
        ReportType.MI_RESUMEN,
        ReportType.MIS_AVERIAS,
        ReportType.MIS_LUMINARIAS,
        ReportType.MI_INVENTARIO,
        ReportType.MI_BITACORA,
        ReportType.MI_ETM_CAMION
    )
    private var spinnerSelecting = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentReportesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAdapters()
        setupReportTypeSpinner()
        setupListeners()
        setupFab()
        setupCharts()
        observeState()
    }

    private fun setupAdapters() {
        resumenAdapter = ResumenKpiAdapter()
        binding.recyclerMiResumen.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = resumenAdapter
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }

        misAveriasAdapter = MisAveriasAdapter(
            onItemClick = { item -> confirmarAbrirAveria(item.caseId) }
        )
        binding.recyclerMisAverias.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = misAveriasAdapter
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }

        misLuminariasAdapter = MisLuminariasAdapter(
            onItemClick = { item -> confirmarAbrirLuminaria(item.id) }
        )
        binding.recyclerMisLuminarias.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = misLuminariasAdapter
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }

        inventarioGeneralAdapter = InventarioReporteAdapter()
        binding.recyclerInventarioGeneral.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = inventarioGeneralAdapter
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }

        inventarioCriticoAdapter = InventarioReporteAdapter()
        binding.recyclerInventarioCritico.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = inventarioCriticoAdapter
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }

        inventarioConsumoAdapter = InventarioConsumoAdapter()
        binding.recyclerInventarioConsumos.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = inventarioConsumoAdapter
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }

        bitacoraAdapter = BitacoraEventosAdapter()
        binding.recyclerBitacora.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = bitacoraAdapter
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }

        historyAdapter = ReportHistoryAdapter(
            onOpen = { item -> openReportUri(Uri.parse(item.uri)) },
            onShare = { item -> shareReport(Uri.parse(item.uri)) },
            onDelete = { item -> confirmarEliminarHistorial(item) }
        )
    }

    private fun setupReportTypeSpinner() {
        val labels = reportTypes.map { getString(it.titleRes) }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, labels)
        binding.spinnerTipoReporte.setAdapter(adapter)
        binding.spinnerTipoReporte.setText(getString(viewModel.uiState.value.reporteSeleccionado.titleRes), false)

        binding.spinnerTipoReporte.setOnItemClickListener { _, _, position, _ ->
            if (spinnerSelecting) return@setOnItemClickListener
            val tipo = reportTypes.getOrNull(position) ?: return@setOnItemClickListener
            viewModel.seleccionarTipo(tipo)
        }
    }

    private fun syncSpinnerSelection(tipo: ReportType) {
        val label = getString(tipo.titleRes)
        if (binding.spinnerTipoReporte.text?.toString() != label) {
            spinnerSelecting = true
            binding.spinnerTipoReporte.setText(label, false)
            spinnerSelecting = false
        }
    }

    private fun setupListeners() {
        binding.btnCambiarFechas.setOnClickListener { mostrarSelectorRango() }
        binding.btnExportarResumenPdf.setOnClickListener { exportarPdf() }
        binding.btnGenerarDescargo.setOnClickListener { mostrarDescargoMaterial() }
    }

    private fun setupFab() {
        binding.fabExportar.setOnClickListener { mostrarExportSheet() }
    }

    private fun setupCharts() {
        setupPieChart(binding.chartAverias)
        setupPieChart(binding.chartLuminarias)
        setupBarChart(binding.chartHorasSemanales)
        setupHorizontalBarChart(binding.chartOrdenes)
    }

    private fun setupPieChart(chart: PieChart) {
        chart.apply {
            setUsePercentValues(false)
            description.isEnabled = false
            isDrawHoleEnabled = true
            holeRadius = 55f
            transparentCircleRadius = 60f
            setHoleColor(Color.TRANSPARENT)
            setTransparentCircleColor(Color.TRANSPARENT)
            setEntryLabelTextSize(10f)
            setEntryLabelColor(Color.WHITE)
            legend.isEnabled = false
            isRotationEnabled = false
            setTouchEnabled(false)
        }
    }

    private fun setupBarChart(chart: BarChart) {
        chart.apply {
            description.isEnabled = false
            setFitBars(true)
            legend.isEnabled = false
            axisRight.isEnabled = false
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#22000000")
                textColor = Color.parseColor("#666666")
                textSize = 9f
                axisMinimum = 0f
            }
            xAxis.apply {
                position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = Color.parseColor("#444444")
                textSize = 9f
                granularity = 1f
            }
            setTouchEnabled(false)
            animateY(600)
        }
    }

    private fun setupHorizontalBarChart(chart: HorizontalBarChart) {
        chart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            axisRight.isEnabled = false
            axisLeft.apply {
                setDrawGridLines(false)
                axisMinimum = 0f
                setDrawLabels(false)
            }
            xAxis.apply {
                setDrawGridLines(false)
                textColor = Color.parseColor("#444444")
                textSize = 10f
                granularity = 1f
            }
            setTouchEnabled(false)
            animateX(400)
        }
    }

    private fun renderizarGraficaAverias(atendidas: Int, pendientes: Int) {
        if (atendidas == 0 && pendientes == 0) {
            binding.chartAverias.clear()
            binding.chartAverias.setNoDataText(getString(R.string.reportes_chart_sin_datos))
            return
        }
        val entries = mutableListOf<PieEntry>()
        if (atendidas > 0) entries.add(PieEntry(atendidas.toFloat(), getString(R.string.reportes_chart_atendidas)))
        if (pendientes > 0) entries.add(PieEntry(pendientes.toFloat(), getString(R.string.reportes_chart_pendientes)))
        val dataSet = PieDataSet(entries, "").apply {
            colors = listOf(Color.parseColor("#4CAF50"), Color.parseColor("#F44336"))
            sliceSpace = 2f
            selectionShift = 4f
            setDrawValues(true)
            valueTextSize = 11f
            valueTextColor = Color.WHITE
        }
        binding.chartAverias.data = PieData(dataSet)
        binding.chartAverias.invalidate()
        binding.tvAveriasAtendidas.text = getString(R.string.reportes_chart_averias_atendidas, atendidas)
        binding.tvAveriasPendientes.text = getString(R.string.reportes_chart_averias_pendientes, pendientes)
    }

    private fun renderizarGraficaLuminarias(reparadas: Int, pendientes: Int) {
        if (reparadas == 0 && pendientes == 0) {
            binding.chartLuminarias.clear()
            binding.chartLuminarias.setNoDataText(getString(R.string.reportes_chart_sin_datos))
            return
        }
        val entries = mutableListOf<PieEntry>()
        if (reparadas > 0) entries.add(PieEntry(reparadas.toFloat(), getString(R.string.reportes_chart_reparadas)))
        if (pendientes > 0) entries.add(PieEntry(pendientes.toFloat(), getString(R.string.reportes_chart_pendientes)))
        val dataSet = PieDataSet(entries, "").apply {
            colors = listOf(Color.parseColor("#4CAF50"), Color.parseColor("#FF9800"))
            sliceSpace = 2f
            selectionShift = 4f
            setDrawValues(true)
            valueTextSize = 11f
            valueTextColor = Color.WHITE
        }
        binding.chartLuminarias.data = PieData(dataSet)
        binding.chartLuminarias.invalidate()
        binding.tvLuminariasReparadas.text = getString(R.string.reportes_chart_luminarias_reparadas, reparadas)
        binding.tvLuminariasPendientes.text = getString(R.string.reportes_chart_luminarias_pendientes, pendientes)
    }

    private fun renderizarGraficaHoras(horasPorDia: List<Float>, semanaLabel: String) {
        val labels = listOf(
            getString(R.string.dia_lun), getString(R.string.dia_mar), getString(R.string.dia_mie),
            getString(R.string.dia_jue), getString(R.string.dia_vie), getString(R.string.dia_sab),
            getString(R.string.dia_dom)
        )
        val entries = horasPorDia.take(7).mapIndexed { i, h -> BarEntry(i.toFloat(), h) }
        val dataSet = BarDataSet(entries, "").apply {
            color = Color.parseColor("#1E88E5")
            setDrawValues(true)
            valueTextSize = 9f
            valueTextColor = Color.parseColor("#333333")
        }
        val barData = BarData(dataSet).apply { barWidth = 0.6f }
        binding.chartHorasSemanales.apply {
            data = barData
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.labelCount = 7
            invalidate()
        }
        binding.tvSemanaLabel.text = getString(R.string.reportes_chart_semana_label, semanaLabel)
    }

    private fun renderizarGraficaOrdenes(asignadas: Int, ejecutadas: Int) {
        val labels = listOf(
            getString(R.string.reportes_chart_asignadas),
            getString(R.string.reportes_chart_ejecutadas)
        )
        val entries = listOf(
            BarEntry(0f, asignadas.toFloat()),
            BarEntry(1f, ejecutadas.toFloat())
        )
        val dataSet = BarDataSet(entries, "").apply {
            colors = listOf(Color.parseColor("#FF9800"), Color.parseColor("#4CAF50"))
            setDrawValues(true)
            valueTextSize = 10f
            valueTextColor = Color.parseColor("#333333")
        }
        val barData = BarData(dataSet).apply { barWidth = 0.5f }
        binding.chartOrdenes.apply {
            data = barData
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.labelCount = 2
            invalidate()
        }
        binding.tvOrdenesAsignadas.text = getString(R.string.reportes_chart_ordenes_asignadas, asignadas)
        binding.tvOrdenesEjecutadas.text = getString(R.string.reportes_chart_ordenes_ejecutadas, ejecutadas)
    }

    private fun mostrarExportSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetBinding = SheetReportExportBinding.inflate(layoutInflater)
        val esMiResumen = viewModel.uiState.value.reporteSeleccionado == ReportType.MI_RESUMEN

        // Mi Resumen solo exporta PDF
        sheetBinding.actionExportExcel.isVisible = !esMiResumen
        sheetBinding.actionExportExcel.setOnClickListener {
            dialog.dismiss()
            prepararExportacion()
        }
        sheetBinding.actionExportPdf.setOnClickListener {
            dialog.dismiss()
            exportarPdf()
        }
        sheetBinding.actionSendEmail.setOnClickListener {
            dialog.dismiss()
            enviarPorCorreo()
        }
        sheetBinding.btnVerHistorial.setOnClickListener {
            dialog.dismiss()
            mostrarHistorial()
        }

        dialog.setContentView(sheetBinding.root)
        dialog.show()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val isProcessing = state.isGlobalLoading || state.isEmailSending || state.loading
                    binding.progressIndicator.isVisible = isProcessing
                    binding.btnCambiarFechas.isEnabled = !isProcessing
                    binding.btnCambiarFechas.text = state.rangoTexto

                    val seleccionado = state.reporteSeleccionado
                    syncSpinnerSelection(seleccionado)

                    binding.tvResumenTitulo.text = getString(seleccionado.titleRes)
                    binding.tvResumenRango.text = state.rangoTexto

                    // Gráficas Mi Resumen
                    val chartData = state.resumenChartData
                    val mostrarGraficas = seleccionado == ReportType.MI_RESUMEN && chartData != null
                    binding.containerResumenCharts.isVisible = mostrarGraficas
                    if (mostrarGraficas) {
                        renderizarGraficaAverias(chartData.averiasAtendidas, chartData.averiasPendientes)
                        renderizarGraficaLuminarias(chartData.luminariasReparadas, chartData.luminariasPendientes)
                        renderizarGraficaHoras(chartData.horasPorDia, chartData.semanaLabel)
                        renderizarGraficaOrdenes(chartData.ordenesAsignadas, chartData.ordenesEjecutadas)
                    }

                    // KPIs header – para Mi Resumen: Averías | Luminarias | Órdenes
                    actualizarEtiquetasResumen(seleccionado)

                    val resumen = state.resumen
                    val hasResumen = resumen != null
                    binding.resumenCardsContainer.isVisible = hasResumen
                    binding.tvResumenPlaceholder.isVisible = !hasResumen
                    if (resumen != null) {
                        binding.tvResumenTotalAverias.text = resumen.totalAverias.toString()
                        binding.tvResumenTotalMateriales.text = resumen.totalMateriales.toString()
                        binding.tvResumenTotalCodigos.text = resumen.totalMaterialesDistintos.toString()
                    }

                    historyAdapter.submitList(state.historialExports)

                    val section: ReportSectionState<*>
                    val recycler: RecyclerView
                    val emptyView: TextView
                    val emptyRes: Int

                    when (seleccionado) {
                        ReportType.MI_RESUMEN -> {
                            binding.cardMiResumen.isVisible = true
                            binding.cardMisAverias.isVisible = false
                            binding.cardMisLuminarias.isVisible = false
                            binding.cardMiInventario.isVisible = false
                            binding.cardMiBitacora.isVisible = false
                            resumenAdapter.submitList(state.resumenState.items)
                            section = state.resumenState
                            recycler = binding.recyclerMiResumen
                            emptyView = binding.tvMiResumenVacio
                            emptyRes = R.string.reportes_mi_resumen_vacio
                            // Mostrar placeholder mientras carga; las gráficas se gestionan arriba
                            binding.tvMiResumenVacio.isVisible = state.resumenState.isLoading
                        }
                        ReportType.MIS_AVERIAS -> {
                            binding.cardMiResumen.isVisible = false
                            binding.cardMisAverias.isVisible = true
                            binding.cardMisLuminarias.isVisible = false
                            binding.cardMiInventario.isVisible = false
                            binding.cardMiBitacora.isVisible = false
                            misAveriasAdapter.submitList(state.misAveriasState.items)
                            section = state.misAveriasState
                            recycler = binding.recyclerMisAverias
                            emptyView = binding.tvMisAveriasVacio
                            emptyRes = R.string.reportes_mis_averias_vacio
                        }
                        ReportType.MIS_LUMINARIAS -> {
                            binding.cardMiResumen.isVisible = false
                            binding.cardMisAverias.isVisible = false
                            binding.cardMisLuminarias.isVisible = true
                            binding.cardMiInventario.isVisible = false
                            binding.cardMiBitacora.isVisible = false
                            misLuminariasAdapter.submitList(state.misLuminariasState.items)
                            section = state.misLuminariasState
                            recycler = binding.recyclerMisLuminarias
                            emptyView = binding.tvMisLuminariasVacio
                            emptyRes = R.string.reportes_mis_luminarias_vacio
                        }
                        ReportType.MI_INVENTARIO -> {
                            binding.cardMiResumen.isVisible = false
                            binding.cardMisAverias.isVisible = false
                            binding.cardMisLuminarias.isVisible = false
                            binding.cardMiInventario.isVisible = true
                            binding.cardMiBitacora.isVisible = false
                            // Título dinámico "Inventario vehículo XXXX"
                            binding.tvTituloMiInventario.text = if (!state.placaVehiculo.isNullOrBlank()) {
                                getString(R.string.reportes_inventario_vehiculo_titulo, state.placaVehiculo)
                            } else {
                                getString(R.string.reportes_mi_inventario_titulo)
                            }
                            inventarioGeneralAdapter.submitList(state.miInventarioState.items)
                            inventarioCriticoAdapter.submitList(state.miInventarioCriticoState.items)
                            section = state.miInventarioState
                            recycler = binding.recyclerInventarioGeneral
                            emptyView = binding.tvInventarioGeneralVacio
                            emptyRes = R.string.reportes_mi_inventario_vacio
                            val movimientos = state.miInventarioMovimientos
                            inventarioConsumoAdapter.submitList(state.miInventarioConsumos)
                            binding.tvInventarioMovimientosDetalle.text = if (movimientos != null) {
                                getString(
                                    R.string.reportes_inventario_movimientos_resumen,
                                    movimientos.entradas,
                                    movimientos.salidas,
                                    movimientos.neto
                                )
                            } else {
                                getString(R.string.reportes_inventario_movimientos_sin_datos)
                            }
                            binding.recyclerInventarioCritico.isVisible = state.miInventarioCriticoState.items.isNotEmpty()
                            binding.tvInventarioCriticoVacio.isVisible = state.miInventarioCriticoState.items.isEmpty()
                            binding.recyclerInventarioConsumos.isVisible = state.miInventarioConsumos.isNotEmpty()
                            binding.tvInventarioConsumosVacio.isVisible = state.miInventarioConsumos.isEmpty()
                        }
                        ReportType.MI_BITACORA -> {
                            binding.cardMiResumen.isVisible = false
                            binding.cardMisAverias.isVisible = false
                            binding.cardMisLuminarias.isVisible = false
                            binding.cardMiInventario.isVisible = false
                            binding.cardMiBitacora.isVisible = true
                            binding.tvTituloMiBitacora.text = getString(R.string.reportes_mi_bitacora_titulo)
                            binding.bitacoraResumenContainer.isVisible = true
                            bitacoraAdapter.submitList(state.miBitacoraState.items)
                            section = state.miBitacoraState
                            recycler = binding.recyclerBitacora
                            emptyView = binding.tvBitacoraVacio
                            emptyRes = R.string.reportes_mi_bitacora_vacio
                            val resumenBitacora = state.miBitacoraResumen
                            if (resumenBitacora != null) {
                                binding.tvBitacoraHoras.text = getString(
                                    R.string.reportes_bitacora_horas,
                                    resumenBitacora.horasTrabajadas
                                )
                                binding.tvBitacoraKm.text = getString(
                                    R.string.reportes_bitacora_km,
                                    resumenBitacora.kilometros
                                )
                                binding.tvBitacoraAverias.text = getString(
                                    R.string.reportes_bitacora_averias,
                                    resumenBitacora.averiasAtendidas
                                )
                                binding.tvBitacoraLuminarias.text = getString(
                                    R.string.reportes_bitacora_luminarias,
                                    resumenBitacora.luminariasReparadas
                                )
                                binding.tvBitacoraLabores.text = getString(
                                    R.string.reportes_bitacora_labores,
                                    resumenBitacora.laboresEjecutadas
                                )
                                binding.tvBitacoraKmInicial.text = getString(
                                    R.string.reportes_bitacora_km_inicial,
                                    resumenBitacora.kilometrajeInicial
                                )
                                binding.tvBitacoraKmFinal.text = getString(
                                    R.string.reportes_bitacora_km_final,
                                    resumenBitacora.kilometrajeFinal
                                )
                                val topMateriales = resumenBitacora.materialTop.joinToString(", ") { it.descripcion }
                                binding.tvBitacoraMaterial.text = getString(
                                    R.string.reportes_bitacora_material_top,
                                    topMateriales.ifBlank { "-" }
                                )
                            }
                        }
                        ReportType.MI_ETM_CAMION -> {
                            binding.cardMiResumen.isVisible = false
                            binding.cardMisAverias.isVisible = false
                            binding.cardMisLuminarias.isVisible = false
                            binding.cardMiInventario.isVisible = false
                            binding.cardMiBitacora.isVisible = true
                            binding.tvTituloMiBitacora.text = getString(R.string.reportes_mi_etm_camion_titulo)
                            binding.bitacoraResumenContainer.isVisible = false
                            bitacoraAdapter.submitList(state.miEtmState.items)
                            section = state.miEtmState
                            recycler = binding.recyclerBitacora
                            emptyView = binding.tvBitacoraVacio
                            emptyRes = R.string.reportes_mi_etm_camion_vacio
                        }
                    }

                    when {
                        section.isLoading -> {
                            recycler.isVisible = false
                            emptyView.isVisible = true
                            emptyView.setText(R.string.reportes_estado_cargando)
                        }
                        section.hasContent -> {
                            recycler.isVisible = section.items.isNotEmpty()
                            emptyView.isVisible = section.items.isEmpty()
                            emptyView.setText(emptyRes)
                        }
                        else -> {
                            recycler.isVisible = false
                            emptyView.isVisible = true
                            emptyView.setText(R.string.reportes_estado_pendiente)
                        }
                    }

                    binding.fabExportar.isVisible = section.hasContent
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.messages.collect { message ->
                if (!isAdded) return@collect
                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun actualizarEtiquetasResumen(tipo: ReportType) {
        when (tipo) {
            ReportType.MI_RESUMEN -> {
                binding.tvResumenLabelAverias.text = getString(R.string.reportes_resumen_label_averias)
                binding.tvResumenLabelMateriales.text = getString(R.string.reportes_resumen_label_luminarias)
                binding.tvResumenLabelCodigos.text = getString(R.string.reportes_resumen_label_ordenes)
            }
            ReportType.MIS_AVERIAS -> {
                binding.tvResumenLabelAverias.text = getString(R.string.reportes_resumen_label_averias)
                binding.tvResumenLabelMateriales.text = getString(R.string.reportes_resumen_label_material)
                binding.tvResumenLabelCodigos.text = getString(R.string.reportes_resumen_label_materiales_distintos)
            }
            ReportType.MIS_LUMINARIAS -> {
                binding.tvResumenLabelAverias.text = getString(R.string.reportes_resumen_label_luminarias)
                binding.tvResumenLabelMateriales.text = getString(R.string.reportes_resumen_label_material)
                binding.tvResumenLabelCodigos.text = getString(R.string.reportes_resumen_label_pendientes)
            }
            ReportType.MI_INVENTARIO -> {
                binding.tvResumenLabelAverias.text = getString(R.string.reportes_resumen_label_items)
                binding.tvResumenLabelMateriales.text = getString(R.string.reportes_resumen_label_criticos)
                binding.tvResumenLabelCodigos.text = getString(R.string.reportes_resumen_label_disponibles)
            }
            ReportType.MI_BITACORA -> {
                binding.tvResumenLabelAverias.text = getString(R.string.reportes_resumen_label_averias)
                binding.tvResumenLabelMateriales.text = getString(R.string.reportes_resumen_label_material)
                binding.tvResumenLabelCodigos.text = getString(R.string.reportes_resumen_label_luminarias)
            }
            ReportType.MI_ETM_CAMION -> {
                binding.tvResumenLabelAverias.text = getString(R.string.reportes_resumen_label_eventos)
                binding.tvResumenLabelMateriales.text = getString(R.string.reportes_resumen_label_etm_cerrados)
                binding.tvResumenLabelCodigos.text = getString(R.string.reportes_resumen_label_etm_pendientes)
            }
        }
    }

    private fun mostrarSelectorRango() {
        val state = viewModel.uiState.value
        val tag = "reportes_rango"
        if (childFragmentManager.findFragmentByTag(tag) != null) return

        val selection = Pair(
            state.fechaInicio.toUtcStartOfDayMillis(),
            state.fechaFin.toUtcStartOfDayMillis()
        )

        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(getString(R.string.reportes_dialog_rango_titulo))
            .setSelection(selection)
            .build()

        picker.addOnPositiveButtonClickListener { range ->
            val start = range.first
            val end = range.second
            if (start != null && end != null) {
                val inicio = start.toUtcLocalDate()
                val fin = end.toUtcLocalDate()
                viewModel.actualizarRangoFechas(inicio, fin)
            }
        }

        picker.show(childFragmentManager, tag)
    }

    private fun prepararExportacion() {
        val state = viewModel.uiState.value
        val tipo = state.reporteSeleccionado
        val datos = viewModel.obtenerDatosParaExportar(tipo)
        if (datos == null) {
            Snackbar.make(binding.root, R.string.reportes_export_no_data, Snackbar.LENGTH_SHORT).show()
            return
        }
        val payload = ExportPayload(tipo = tipo, data = datos, resumen = state.resumen, rango = state.rangoTexto)
        autoGuardarExcel(tipo, payload)
    }

    private fun autoGuardarExcel(tipo: ReportType, payload: ExportPayload) {
        viewLifecycleOwner.lifecycleScope.launch {
            val fechaDisplay = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
            val subfolder = viewModel.obtenerSubfolder(tipo)
            val nombre = viewModel.generarNombreArchivoProfesional(tipo, "xlsx", fechaDisplay)
            val targetDir = requireContext().getExternalFilesDir("Reportes/$subfolder")
            if (targetDir == null) {
                Snackbar.make(binding.root, R.string.reportes_export_error, Snackbar.LENGTH_LONG).show()
                return@launch
            }
            targetDir.mkdirs()
            val file = File(targetDir, nombre)

            val snack = Snackbar.make(binding.root, R.string.reportes_export_progress_preparar, Snackbar.LENGTH_INDEFINITE)
            snack.show()
            try {
                val workbook = withContext(Dispatchers.Default) {
                    ExcelReportExporter.buildWorkbook(requireContext(), payload) { step ->
                        val msg = when (step) {
                            ExcelReportExporter.ExportStep.PREPARAR -> R.string.reportes_export_progress_preparar
                            ExcelReportExporter.ExportStep.ESCRIBIR -> R.string.reportes_export_progress_escribir
                            ExcelReportExporter.ExportStep.CERRAR -> R.string.reportes_export_progress_cerrar
                            ExcelReportExporter.ExportStep.GUARDAR -> R.string.reportes_export_progress_guardar
                            ExcelReportExporter.ExportStep.INDEXAR -> R.string.reportes_export_progress_indexar
                        }
                        snack.setText(msg)
                    }
                }
                withContext(Dispatchers.IO) {
                    file.outputStream().use { out -> workbook.use { wb -> wb.write(out) } }
                }
                snack.dismiss()
                val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
                Snackbar.make(binding.root, getString(R.string.reportes_export_success_file, nombre), Snackbar.LENGTH_LONG)
                    .setAction(R.string.reportes_export_action_view) { openReportUri(uri) }
                    .show()
                ReportDownloadNotifier.show(requireContext(), nombre, uri, ReportDownloadNotifier.buildLocationUriForFile(requireContext(), file))
                val entidad = ReporteGeneradoEntity(
                    id = UUID.randomUUID().toString(),
                    tipoReporte = getString(tipo.titleRes),
                    formato = "EXCEL",
                    nombreArchivo = nombre,
                    rutaLocal = file.absolutePath,
                    fechaGeneracion = System.currentTimeMillis(),
                    usuarioUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                )
                viewModel.persistirReporte(entidad)
            } catch (t: Throwable) {
                Log.e("ReportesFragment", "Error exportando Excel", t)
                snack.dismiss()
                Snackbar.make(binding.root, R.string.reportes_export_error, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun exportarPdf() {
        val state = viewModel.uiState.value
        val tipo = state.reporteSeleccionado
        val datos = viewModel.obtenerDatosParaExportar(tipo)
        if (datos == null) {
            Snackbar.make(binding.root, R.string.reportes_export_no_data, Snackbar.LENGTH_SHORT).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val fechaDisplay = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
            val subfolder = viewModel.obtenerSubfolder(tipo)
            val fileName = viewModel.generarNombreArchivoProfesional(tipo, "pdf", fechaDisplay)
            val targetDir = requireContext().getExternalFilesDir("Reportes/$subfolder")
            if (targetDir == null) {
                Snackbar.make(binding.root, R.string.reportes_export_error, Snackbar.LENGTH_LONG).show()
                return@launch
            }
            targetDir.mkdirs()
            val file = File(targetDir, fileName)
            val payload = PdfReportExporter.ExportPayload(
                tipo = tipo, data = datos, resumen = state.resumen, rango = state.rangoTexto
            )
            if (file.exists()) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.reportes_export_reemplazar_titulo)
                    .setMessage(R.string.reportes_export_reemplazar_mensaje)
                    .setPositiveButton(R.string.reportes_export_reemplazar_confirmar) { _, _ ->
                        exportarPdfEnArchivo(file, payload)
                    }
                    .setNegativeButton(R.string.reportes_historial_cancelar, null)
                    .show()
            } else {
                exportarPdfEnArchivo(file, payload)
            }
        }
    }

    private fun exportarPdfEnArchivo(file: File, payload: PdfReportExporter.ExportPayload) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    PdfReportExporter.export(context = requireContext(), payload = payload, outputFile = file)
                }
                val uri = PdfReportExporter.buildContentUri(requireContext(), file)
                Snackbar.make(binding.root, getString(R.string.reportes_export_success_file, file.name), Snackbar.LENGTH_LONG)
                    .setAction(R.string.reportes_export_action_view) { openReportUri(uri) }
                    .show()
                ReportDownloadNotifier.show(requireContext(), file.name, uri, ReportDownloadNotifier.buildLocationUriForFile(requireContext(), file))
                val entidad = ReporteGeneradoEntity(
                    id = UUID.randomUUID().toString(),
                    tipoReporte = getString(payload.tipo.titleRes),
                    formato = "PDF",
                    nombreArchivo = file.name,
                    rutaLocal = file.absolutePath,
                    fechaGeneracion = System.currentTimeMillis(),
                    usuarioUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                )
                viewModel.persistirReporte(entidad)
            } catch (t: Throwable) {
                Log.e("ReportesFragment", "Error exportando PDF", t)
                Snackbar.make(binding.root, R.string.reportes_export_error, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun enviarPorCorreo() {
        val state = viewModel.uiState.value
        val tipo = state.reporteSeleccionado
        val emailDestino = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email?.takeIf { it.isNotBlank() }
        if (emailDestino == null) {
            Snackbar.make(binding.root, R.string.reportes_email_sin_correo, Snackbar.LENGTH_LONG).show()
            return
        }
        val datos = viewModel.obtenerDatosParaExportar(tipo)
        if (datos == null) {
            Snackbar.make(binding.root, R.string.reportes_export_no_data, Snackbar.LENGTH_SHORT).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val snack = Snackbar.make(binding.root, R.string.reportes_email_preparando, Snackbar.LENGTH_INDEFINITE)
            snack.show()
            try {
                val fechaDisplay = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                val nombre = viewModel.generarNombreArchivoProfesional(tipo, "xlsx", fechaDisplay)
                val payload = ExportPayload(tipo = tipo, data = datos, resumen = state.resumen, rango = state.rangoTexto)
                val workbook = withContext(Dispatchers.Default) {
                    ExcelReportExporter.buildWorkbook(requireContext(), payload) {}
                }
                val cacheDir = File(requireContext().cacheDir, "reportes").also { it.mkdirs() }
                val file = File(cacheDir, nombre)
                withContext(Dispatchers.IO) {
                    file.outputStream().use { out -> workbook.use { wb -> wb.write(out) } }
                }
                val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
                snack.dismiss()
                val subject = getString(R.string.reportes_email_asunto, getString(tipo.titleRes), state.rangoTexto)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = MIME_TYPE_XLSX
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(emailDestino))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, getString(R.string.reportes_email_chooser_title)))
            } catch (t: Throwable) {
                Log.e("ReportesFragment", "Error preparando correo", t)
                snack.dismiss()
                Snackbar.make(binding.root, R.string.reportes_export_error, Snackbar.LENGTH_LONG).show()
            }
        }
    }


    private fun openReportUri(uri: Uri) {
        val mimeType = requireContext().contentResolver.getType(uri) ?: MIME_TYPE_XLSX
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            startActivity(Intent.createChooser(intent, getString(R.string.reportes_export_open_title)))
        }
    }

    private fun shareReport(uri: Uri) {
        val mimeType = requireContext().contentResolver.getType(uri) ?: "*/*"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.reportes_export_share_title)))
    }

    private fun mostrarHistorial() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetBinding = SheetReportHistoryBinding.inflate(layoutInflater)
        sheetBinding.recyclerHistory.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = historyAdapter
        }
        val items = viewModel.uiState.value.historialExports
        historyAdapter.submitList(items)
        sheetBinding.tvHistoryEmpty.isVisible = items.isEmpty()
        sheetBinding.btnCloseHistory.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(sheetBinding.root)
        dialog.show()
    }

    private fun confirmarEliminarHistorial(item: ExportHistoryItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.reportes_historial_eliminar)
            .setMessage(R.string.reportes_historial_confirmar_eliminar)
            .setPositiveButton(R.string.reportes_historial_eliminar) { _, _ ->
                viewModel.eliminarHistorial(item)
            }
            .setNegativeButton(R.string.reportes_historial_cancelar, null)
            .show()
    }

    private fun mostrarDescargoMaterial() {
        if (childFragmentManager.findFragmentByTag("descargo") != null) return
        DescargoMaterialBottomSheet.newInstance(viewModel)
            .show(childFragmentManager, "descargo")
    }

    private fun confirmarAbrirAveria(caseId: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.reportes_deeplink_averia_titulo)
            .setMessage(getString(R.string.reportes_deeplink_averia_msg, caseId))
            .setPositiveButton(R.string.reportes_deeplink_abrir) { _, _ ->
                AveriaDeepLink.pendingCaseId = caseId
                findNavController().navigate(R.id.nav_averias)
            }
            .setNegativeButton(R.string.reportes_historial_cancelar, null)
            .show()
    }

    private fun confirmarAbrirLuminaria(luminariaId: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.reportes_deeplink_luminaria_titulo)
            .setMessage(R.string.reportes_deeplink_luminaria_msg)
            .setPositiveButton(R.string.reportes_deeplink_abrir) { _, _ ->
                LuminariaDeepLink.pendingLuminariaId = luminariaId
                findNavController().navigate(R.id.nav_luminarias)
            }
            .setNegativeButton(R.string.reportes_historial_cancelar, null)
            .show()
    }

    private fun LocalDate.toUtcStartOfDayMillis(): Long =
        this.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun Long.toUtcLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
