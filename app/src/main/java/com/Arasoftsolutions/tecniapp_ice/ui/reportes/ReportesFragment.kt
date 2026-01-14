package com.Arasoftsolutions.tecniapp_ice.ui.reportes

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentReportesBinding
import com.Arasoftsolutions.tecniapp_ice.ui.reportes.ExcelReportExporter.ExportPayload
import com.Arasoftsolutions.tecniapp_ice.ui.reportes.ExcelReportExporter.MIME_TYPE_XLSX
import com.google.android.material.datepicker.MaterialDatePicker
import androidx.core.util.Pair
import com.google.android.material.snackbar.Snackbar
import com.Arasoftsolutions.tecniapp_ice.ui.inventario.InventarioAdapter
import java.time.Instant
import java.time.LocalDate
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
    private val inventarioReportViewModel: InventarioReportViewModel by viewModels()

    private lateinit var averiasAdapter: AveriasReportAdapter
    private lateinit var materialesPorAveriaAdapter: MaterialesPorAveriaAdapter
    private lateinit var materialTotalAdapter: MaterialTotalAdapter
    private lateinit var luminariasAdapter: LuminariasReparadasAdapter
    private lateinit var inventarioAdapter: InventarioAdapter
    private lateinit var tiposAdapter: ArrayAdapter<String>
    private var isUpdatingTipoReporte = false
    private val locale = Locale.getDefault()
    private val fileNameFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val zoneId: ZoneId = ZoneId.systemDefault()

    private var pendingExport: ExportPayload? = null

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
        setupReportTypeSelector()
        setupListeners()
        observeState()
        observeInventario()
    }

    private fun setupAdapters() {
        averiasAdapter = AveriasReportAdapter()
        binding.recyclerAverias.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = averiasAdapter
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }

        materialesPorAveriaAdapter = MaterialesPorAveriaAdapter()
        binding.recyclerMaterialPorAveria.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = materialesPorAveriaAdapter
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }

        materialTotalAdapter = MaterialTotalAdapter()
        binding.recyclerMaterialTotal.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = materialTotalAdapter
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }

        luminariasAdapter = LuminariasReparadasAdapter()
        binding.recyclerLuminarias.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = luminariasAdapter
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }

        inventarioAdapter = InventarioAdapter()
        binding.recyclerInventario.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = inventarioAdapter
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }
    }

    private fun setupReportTypeSelector() {
        val labels = ReportType.values().map { getString(it.titleRes) }
        tiposAdapter = ArrayAdapter(requireContext(), R.layout.item_dropdown_report_type, labels)
        tiposAdapter.setDropDownViewResource(R.layout.item_dropdown_report_type)
        binding.spinnerTipoReporte.adapter = tiposAdapter
        binding.spinnerTipoReporte.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isUpdatingTipoReporte) return
                val tipo = ReportType.values().getOrNull(position) ?: return
                viewModel.seleccionarTipo(tipo)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        binding.selectorContainer.setOnClickListener { binding.spinnerTipoReporte.performClick() }
        syncReportTypeSelection(viewModel.uiState.value.reporteSeleccionado)
    }

    private fun syncReportTypeSelection(tipo: ReportType) {
        if (!::tiposAdapter.isInitialized) return
        val index = ReportType.values().indexOf(tipo)
        if (index >= 0 && binding.spinnerTipoReporte.selectedItemPosition != index) {
            isUpdatingTipoReporte = true
            binding.spinnerTipoReporte.setSelection(index, false)
            isUpdatingTipoReporte = false
        }
    }

    private fun setupListeners() {
        binding.btnCambiarFechas.setOnClickListener { mostrarSelectorRango() }
        binding.btnGenerarReporte.setOnClickListener { viewModel.generarReporteSeleccionado() }
        binding.btnExportarExcel.setOnClickListener { prepararExportacion() }
        binding.btnLimpiarFechas.setOnClickListener { viewModel.restablecerRango() }
        binding.fabEnviarCorreo.setOnClickListener { viewModel.enviarReportePorCorreo() }
    }

    private fun observeInventario() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                inventarioReportViewModel.inventario.collect { items ->
                    inventarioAdapter.submitList(items)
                    binding.cardInventario.isVisible = true
                    binding.tvInventarioVacio.isVisible = items.isEmpty()
                }
            }
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val isProcessing = state.isGlobalLoading || state.isEmailSending
                    binding.progressIndicator.isVisible = isProcessing
                    binding.tvRangoFechas.text = state.rangoTexto
                    binding.btnLimpiarFechas.isVisible = !state.isDefaultRange
                    binding.btnLimpiarFechas.isEnabled = !isProcessing
                    binding.btnCambiarFechas.isEnabled = !isProcessing

                    val seleccionado = state.reporteSeleccionado
                    syncReportTypeSelection(seleccionado)
                    binding.btnGenerarReporte.text = getString(
                        R.string.reportes_btn_generar_tipo,
                        getString(seleccionado.titleRes).replaceFirstChar { char ->
                            char.lowercase(locale)
                        }
                    )
                    binding.btnGenerarReporte.isEnabled = !isProcessing
                    binding.spinnerTipoReporte.isEnabled = !isProcessing
                    binding.selectorContainer.isEnabled = !isProcessing

                    binding.tvResumenTitulo.text = getString(seleccionado.titleRes)
                    binding.tvResumenRango.text = state.rangoTexto

                    val resumen = state.resumen
                    val hasResumen = resumen != null
                    binding.resumenCardsContainer.isVisible = hasResumen
                    binding.tvResumenPlaceholder.isVisible = !hasResumen
                    if (resumen != null) {
                        binding.tvResumenTotalAverias.text = resumen.totalAverias.toString()
                        binding.tvResumenTotalMateriales.text = resumen.totalMateriales.toString()
                        binding.tvResumenTotalCodigos.text = resumen.totalMaterialesDistintos.toString()
                    }

                    val section: ReportSectionState<*>
                    val recycler: RecyclerView
                    val emptyView: TextView
                    val emptyRes: Int

                    when (seleccionado) {
                        ReportType.AVERIAS -> {
                            binding.cardAverias.isVisible = true
                            binding.cardMaterialPorAveria.isVisible = false
                            binding.cardMaterialTotal.isVisible = false
                            binding.cardLuminariasReparadas.isVisible = false
                            averiasAdapter.submitList(state.averiasState.items)
                            section = state.averiasState
                            recycler = binding.recyclerAverias
                            emptyView = binding.tvAveriasVacio
                            emptyRes = R.string.reportes_averias_vacio
                        }
                        ReportType.MATERIALES_POR_AVERIA -> {
                            binding.cardAverias.isVisible = false
                            binding.cardMaterialPorAveria.isVisible = true
                            binding.cardMaterialTotal.isVisible = false
                            binding.cardLuminariasReparadas.isVisible = false
                            materialesPorAveriaAdapter.submitList(state.materialesPorAveriaState.items)
                            section = state.materialesPorAveriaState
                            recycler = binding.recyclerMaterialPorAveria
                            emptyView = binding.tvMaterialPorAveriaVacio
                            emptyRes = R.string.reportes_material_por_averia_vacio
                        }
                        ReportType.MATERIALES_TOTALES -> {
                            binding.cardAverias.isVisible = false
                            binding.cardMaterialPorAveria.isVisible = false
                            binding.cardMaterialTotal.isVisible = true
                            binding.cardLuminariasReparadas.isVisible = false
                            materialTotalAdapter.submitList(state.materialesTotalesState.items)
                            section = state.materialesTotalesState
                            recycler = binding.recyclerMaterialTotal
                            emptyView = binding.tvMaterialTotalVacio
                            emptyRes = R.string.reportes_material_total_vacio
                        }
                        ReportType.LUMINARIAS_REPARADAS -> {
                            binding.cardAverias.isVisible = false
                            binding.cardMaterialPorAveria.isVisible = false
                            binding.cardMaterialTotal.isVisible = false
                            binding.cardLuminariasReparadas.isVisible = true
                            luminariasAdapter.submitList(state.luminariasState.items)
                            section = state.luminariasState
                            recycler = binding.recyclerLuminarias
                            emptyView = binding.tvLuminariasVacio
                            emptyRes = R.string.reportes_luminarias_vacio
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
                    binding.fabActionsGroup.isVisible = hasContent
                    binding.btnExportarExcel.isVisible = hasContent
                    binding.fabEnviarCorreo.isVisible = hasContent
                    binding.btnExportarExcel.isEnabled = hasContent && !isProcessing
                    binding.fabEnviarCorreo.isEnabled = hasContent && !isProcessing

                    if (hasContent) {
                        binding.btnExportarExcel.extend()
                        binding.fabEnviarCorreo.extend()
                    } else {
                        binding.btnExportarExcel.shrink()
                        binding.fabEnviarCorreo.shrink()
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
        exportLauncher.launch(nombre)
    }

    private fun generarNombreArchivo(tipo: ReportType, inicio: LocalDate, fin: LocalDate): String {
        val inicioTexto = inicio.format(fileNameFormatter)
        val finTexto = fin.format(fileNameFormatter)
        return "Reporte_${tipo.fileNameKey}_${inicioTexto}_${finTexto}.xlsx"
    }

    private fun exportToUri(uri: Uri, payload: ExportPayload) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val workbook = withContext(Dispatchers.Default) {
                    ExcelReportExporter.buildWorkbook(
                        context = requireContext(),
                        payload = payload
                    )
                }
                withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openOutputStream(uri)?.use { output ->
                        workbook.use { wb ->
                            wb.write(output)
                            output.flush()
                        }
                    } ?: throw IllegalStateException("Output stream is null")
                }
                Snackbar.make(binding.root, R.string.reportes_export_success, Snackbar.LENGTH_LONG).show()
            } catch (t: Throwable) {
                Log.e("ReportesFragment", "Error exportando Excel", t)
                Snackbar.make(binding.root, R.string.reportes_export_error, Snackbar.LENGTH_LONG).show()
            }
        }
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
