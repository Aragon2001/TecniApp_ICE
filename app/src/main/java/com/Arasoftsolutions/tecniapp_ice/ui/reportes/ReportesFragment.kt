package com.Arasoftsolutions.tecniapp_ice.ui.reportes

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.util.Pair
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentReportesBinding
import com.Arasoftsolutions.tecniapp_ice.databinding.SheetReportHistoryBinding
import com.Arasoftsolutions.tecniapp_ice.ui.reportes.ExcelReportExporter.ExportPayload
import com.Arasoftsolutions.tecniapp_ice.ui.reportes.ExcelReportExporter.MIME_TYPE_XLSX
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    private val fileNameFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val historyFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm", locale)
    private val zoneId: ZoneId = ZoneId.systemDefault()

    private var pendingExport: ExportPayload? = null
    private var pendingExportFileName: String? = null
    private var speedDialExpanded = false

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument(MIME_TYPE_XLSX)) { uri ->
            val payload = pendingExport
            pendingExport = null
            if (payload == null || uri == null) {
                return@registerForActivityResult
            }
            exportToUri(uri, payload)
        }

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
        setupTabs()
        setupListeners()
        setupSpeedDial()
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

        misAveriasAdapter = MisAveriasAdapter()
        binding.recyclerMisAverias.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = misAveriasAdapter
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }

        misLuminariasAdapter = MisLuminariasAdapter()
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

    private fun setupTabs() {
        binding.tabGroupReportes.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val tab = when (checkedId) {
                R.id.tabResumen -> ReportTab.RESUMEN
                R.id.tabOperacion -> ReportTab.OPERACION
                R.id.tabMateriales -> ReportTab.MATERIALES
                R.id.tabInventario -> ReportTab.INVENTARIO
                R.id.tabBitacora -> ReportTab.BITACORA
                else -> ReportTab.RESUMEN
            }
            viewModel.seleccionarTab(tab)
        }
        syncTabSelection(viewModel.uiState.value.selectedTab)
    }

    private fun syncTabSelection(tab: ReportTab) {
        val targetId = when (tab) {
            ReportTab.RESUMEN -> R.id.tabResumen
            ReportTab.OPERACION -> R.id.tabOperacion
            ReportTab.MATERIALES -> R.id.tabMateriales
            ReportTab.INVENTARIO -> R.id.tabInventario
            ReportTab.BITACORA -> R.id.tabBitacora
        }
        if (binding.tabGroupReportes.checkedButtonId != targetId) {
            binding.tabGroupReportes.check(targetId)
        }
    }

    private fun setupListeners() {
        binding.btnCambiarFechas.setOnClickListener { mostrarSelectorRango() }
        binding.btnGenerarReporte.setOnClickListener { viewModel.generarReporteSeleccionado() }
        binding.btnLimpiarFechas.setOnClickListener { viewModel.restablecerRango() }
        binding.chipFiltroFecha.setOnClickListener { mostrarSelectorRango() }
        binding.chipFiltroTipo.setOnClickListener { viewModel.generarReporteSeleccionado() }
        binding.chipFiltroSync.setOnClickListener {
            Snackbar.make(binding.root, R.string.reportes_sync_info, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun setupSpeedDial() {
        binding.fabSpeedDial.setOnClickListener {
            if (speedDialExpanded) {
                collapseSpeedDial()
            } else {
                expandSpeedDial()
            }
        }
        binding.speedDialScrim.setOnClickListener { collapseSpeedDial() }
        binding.actionExportExcel.setOnClickListener {
            collapseSpeedDial()
            prepararExportacion()
        }
        binding.actionExportPdf.setOnClickListener {
            collapseSpeedDial()
            exportarPdf()
        }
        binding.actionShareLast.setOnClickListener {
            collapseSpeedDial()
            compartirUltimoReporte()
        }
        binding.actionHistory.setOnClickListener {
            collapseSpeedDial()
            mostrarHistorial()
        }
        binding.actionConfig.setOnClickListener {
            collapseSpeedDial()
            Snackbar.make(binding.root, R.string.reportes_config_proximamente, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun expandSpeedDial() {
        speedDialExpanded = true
        binding.speedDialScrim.isVisible = true
        binding.speedDialActions.apply {
            isVisible = true
            alpha = 0f
            translationY = 20f
            animate().alpha(1f).translationY(0f).setDuration(180).start()
        }
    }

    private fun collapseSpeedDial() {
        speedDialExpanded = false
        binding.speedDialScrim.isVisible = false
        binding.speedDialActions.animate()
            .alpha(0f)
            .translationY(20f)
            .setDuration(150)
            .withEndAction { binding.speedDialActions.isVisible = false }
            .start()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val isProcessing = state.isGlobalLoading || state.isEmailSending || state.loading
                    binding.progressIndicator.isVisible = isProcessing
                    binding.tvRangoFechas.text = state.rangoTexto
                    binding.btnLimpiarFechas.isVisible = !state.isDefaultRange
                    binding.btnLimpiarFechas.isEnabled = !isProcessing
                    binding.btnCambiarFechas.isEnabled = !isProcessing

                    val seleccionado = state.reporteSeleccionado
                    syncTabSelection(state.selectedTab)
                    binding.btnGenerarReporte.text = getString(
                        R.string.reportes_btn_generar_tipo,
                        getString(seleccionado.titleRes).replaceFirstChar { char ->
                            char.lowercase(locale)
                        }
                    )
                    binding.btnGenerarReporte.isEnabled = !isProcessing

                    binding.tvReportesSubtitle.text = getString(R.string.reportes_header_rango, state.rangoTexto)
                    binding.tvReportesContext.text = getString(
                        R.string.reportes_header_contexto,
                        state.filtrosSeleccionados.agencia ?: getString(R.string.reportes_filtro_agencia_default),
                        state.filtrosSeleccionados.tecnico ?: getString(R.string.reportes_filtro_tecnico_default)
                    )

                    binding.chipFiltroFecha.text = getString(R.string.reportes_chip_fecha_resumen, state.rangoTexto)
                    binding.chipFiltroAgencia.text = getString(
                        R.string.reportes_chip_agencia_resumen,
                        state.filtrosSeleccionados.agencia ?: getString(R.string.reportes_filtro_agencia_default)
                    )
                    binding.chipFiltroCamion.text = getString(
                        R.string.reportes_chip_camion_resumen,
                        state.filtrosSeleccionados.camion ?: getString(R.string.reportes_filtro_camion_default)
                    )
                    binding.chipFiltroTecnico.text = getString(
                        R.string.reportes_chip_tecnico_resumen,
                        state.filtrosSeleccionados.tecnico ?: getString(R.string.reportes_filtro_tecnico_default)
                    )
                    binding.chipFiltroTipo.text = getString(
                        R.string.reportes_chip_tipo_resumen,
                        getString(seleccionado.titleRes)
                    )
                    binding.chipFiltroSync.text = getString(
                        R.string.reportes_chip_sync_resumen,
                        state.filtrosSeleccionados.estadoSync ?: getString(R.string.reportes_filtro_sync_default)
                    )

                    binding.tvResumenTitulo.text = getString(seleccionado.titleRes)
                    binding.tvResumenRango.text = state.rangoTexto

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

                    binding.tvInsight1.text = getString(
                        R.string.reportes_insight_material,
                        resumen?.totalMateriales ?: 0
                    )
                    binding.tvInsight2.text = getString(
                        R.string.reportes_insight_averias,
                        resumen?.totalAverias ?: 0
                    )
                    binding.tvInsight3.text = getString(
                        R.string.reportes_insight_codigos,
                        resumen?.totalMaterialesDistintos ?: 0
                    )

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

                    val hasContent = section.hasContent
                    binding.fabSpeedDial.isVisible = hasContent
                    if (!hasContent) {
                        collapseSpeedDial()
                    }
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
                binding.tvResumenLabelMateriales.text = getString(R.string.reportes_resumen_label_material)
                binding.tvResumenLabelCodigos.text = getString(R.string.reportes_resumen_label_luminarias)
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
        }
    }

    private fun mostrarSelectorRango() {
        val state = viewModel.uiState.value
        val tag = "reportes_rango"
        if (childFragmentManager.findFragmentByTag(tag) != null) return

        val selection = Pair(
            state.fechaInicio.toStartOfDayMillis(),
            state.fechaFin.toStartOfDayMillis()
        )

        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(getString(R.string.reportes_dialog_rango_titulo))
            .setSelection(selection)
            .build()

        picker.addOnPositiveButtonClickListener { range ->
            val start = range.first
            val end = range.second
            if (start != null && end != null) {
                val inicio = start.toLocalDate()
                val fin = end.toLocalDate()
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
        val payload = ExportPayload(
            tipo = tipo,
            data = datos,
            resumen = state.resumen,
            rango = state.rangoTexto
        )
        pendingExport = payload
        val nombre = generarNombreArchivo(tipo, state.fechaInicio, state.fechaFin)
        pendingExportFileName = nombre
        exportLauncher.launch(nombre)
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
            val fileName = generarNombreArchivoPdf(tipo, state.fechaInicio, state.fechaFin)
            val targetDir = requireContext().getExternalFilesDir("TecniApp/Reportes")
            if (targetDir == null) {
                Snackbar.make(binding.root, R.string.reportes_export_error, Snackbar.LENGTH_LONG).show()
                return@launch
            }
            val file = File(targetDir, fileName)
            val payload = PdfReportExporter.ExportPayload(
                tipo = tipo,
                data = datos,
                resumen = state.resumen,
                rango = state.rangoTexto
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
                    PdfReportExporter.export(
                        context = requireContext(),
                        payload = payload,
                        outputFile = file
                    )
                }
                val uri = PdfReportExporter.buildContentUri(requireContext(), file)
                Snackbar.make(
                    binding.root,
                    getString(R.string.reportes_export_success_file, file.name),
                    Snackbar.LENGTH_LONG
                ).setAction(R.string.reportes_export_action_view) {
                    openReportUri(uri)
                }.show()
                ReportDownloadNotifier.show(
                    context = requireContext(),
                    fileName = file.name,
                    fileUri = uri,
                    locationUri = ReportDownloadNotifier.buildLocationUriForFile(requireContext(), file)
                )
                viewModel.agregarExportHistorial(
                    ExportHistoryItem(
                        id = file.name,
                        fileName = file.name,
                        fileType = ReportFileType.PDF,
                        dateTime = historyFormatter.format(LocalDateTime.now()),
                        fileSize = file.length().toString(),
                        status = ReportExportStatus.OK,
                        uri = uri.toString()
                    )
                )
            } catch (t: Throwable) {
                Log.e("ReportesFragment", "Error exportando PDF", t)
                Snackbar.make(binding.root, R.string.reportes_export_error, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun generarNombreArchivo(tipo: ReportType, inicio: LocalDate, fin: LocalDate): String {
        val inicioTexto = inicio.format(fileNameFormatter)
        val finTexto = fin.format(fileNameFormatter)
        return "Reporte_${tipo.fileNameKey}_${inicioTexto}_${finTexto}.xlsx"
    }

    private fun generarNombreArchivoPdf(tipo: ReportType, inicio: LocalDate, fin: LocalDate): String {
        val inicioTexto = inicio.format(fileNameFormatter)
        val finTexto = fin.format(fileNameFormatter)
        return "Reporte_${tipo.fileNameKey}_${inicioTexto}_${finTexto}.pdf"
    }

    private fun exportToUri(uri: Uri, payload: ExportPayload) {
        viewLifecycleOwner.lifecycleScope.launch {
            val progressSnackbar = Snackbar.make(
                binding.root,
                R.string.reportes_export_progress_preparar,
                Snackbar.LENGTH_INDEFINITE
            )
            progressSnackbar.show()
            try {
                val workbook = withContext(Dispatchers.Default) {
                    ExcelReportExporter.buildWorkbook(
                        context = requireContext(),
                        payload = payload,
                        onProgress = { step ->
                            val message = when (step) {
                                ExcelReportExporter.ExportStep.PREPARAR ->
                                    R.string.reportes_export_progress_preparar
                                ExcelReportExporter.ExportStep.ESCRIBIR ->
                                    R.string.reportes_export_progress_escribir
                                ExcelReportExporter.ExportStep.CERRAR ->
                                    R.string.reportes_export_progress_cerrar
                                ExcelReportExporter.ExportStep.GUARDAR ->
                                    R.string.reportes_export_progress_guardar
                                ExcelReportExporter.ExportStep.INDEXAR ->
                                    R.string.reportes_export_progress_indexar
                            }
                            progressSnackbar.setText(message)
                        }
                    )
                }
                progressSnackbar.setText(R.string.reportes_export_progress_guardar)
                withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openOutputStream(uri)?.use { output ->
                        workbook.use { wb ->
                            wb.write(output)
                            output.flush()
                        }
                    } ?: throw IllegalStateException("Output stream is null")
                }
                progressSnackbar.dismiss()
                val fileName = pendingExportFileName
                    ?: DocumentFile.fromSingleUri(requireContext(), uri)?.name
                    ?: getString(R.string.reportes_export_default_name)
                val size = DocumentFile.fromSingleUri(requireContext(), uri)?.length()
                Snackbar.make(
                    binding.root,
                    getString(R.string.reportes_export_success_file, fileName),
                    Snackbar.LENGTH_LONG
                ).setAction(R.string.reportes_export_action_view) {
                    openReportUri(uri)
                }.show()
                ReportDownloadNotifier.show(
                    context = requireContext(),
                    fileName = fileName,
                    fileUri = uri,
                    locationUri = ReportDownloadNotifier.buildLocationUriForDocument(requireContext(), uri)
                )
                viewModel.agregarExportHistorial(
                    ExportHistoryItem(
                        id = fileName,
                        fileName = fileName,
                        fileType = ReportFileType.EXCEL,
                        dateTime = historyFormatter.format(LocalDateTime.now()),
                        fileSize = size?.toString(),
                        status = ReportExportStatus.OK,
                        uri = uri.toString()
                    )
                )
            } catch (t: Throwable) {
                Log.e("ReportesFragment", "Error exportando Excel", t)
                progressSnackbar.dismiss()
                Snackbar.make(binding.root, R.string.reportes_export_error, Snackbar.LENGTH_LONG).show()
            } finally {
                pendingExportFileName = null
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

    private fun compartirUltimoReporte() {
        val last = viewModel.uiState.value.historialExports.firstOrNull()
        if (last == null) {
            Snackbar.make(binding.root, R.string.reportes_historial_vacio, Snackbar.LENGTH_SHORT).show()
            return
        }
        shareReport(Uri.parse(last.uri))
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

    private fun LocalDate.toStartOfDayMillis(): Long =
        this.atStartOfDay(zoneId).toInstant().toEpochMilli()

    private fun Long.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
