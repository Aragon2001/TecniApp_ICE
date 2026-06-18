package com.Arasoftsolutions.tecniapp_ice.ui.reportes

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.Arasoftsolutions.tecniapp_ice.Database.entities.ReporteGeneradoEntity
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.BottomSheetDescargoMaterialBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

class DescargoMaterialBottomSheet : BottomSheetDialogFragment() {

    private var _b: BottomSheetDescargoMaterialBinding? = null
    private val b get() = _b!!

    private lateinit var adapter: DescargoMaterialAdapter
    private var todosLosItems: List<DescargoMaterialItem> = emptyList()
    private lateinit var viewModel: ReportesViewModel

    companion object {
        private const val TAG = "DescargoBottomSheet"

        fun newInstance(viewModel: ReportesViewModel): DescargoMaterialBottomSheet {
            return DescargoMaterialBottomSheet().also { it.viewModel = viewModel }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = BottomSheetDescargoMaterialBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (dialog as? com.google.android.material.bottomsheet.BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        adapter = DescargoMaterialAdapter()
        b.recyclerDescargo.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@DescargoMaterialBottomSheet.adapter
            setHasFixedSize(false)
        }

        b.btnCerrarDescargo.setOnClickListener { dismiss() }

        b.chipGroupFiltroOrigen.setOnCheckedStateChangeListener { _, checkedIds ->
            val chipId = checkedIds.firstOrNull()
            aplicarFiltro(chipId)
        }

        b.btnDescargoExportarExcel.setOnClickListener { exportarDescargo(formato = "xlsx") }
        b.btnDescargoExportarPdf.setOnClickListener { exportarDescargo(formato = "pdf") }

        cargarDatos()
    }

    private fun cargarDatos() {
        mostrarCargando(true)
        val state = viewModel.uiState.value
        val subtitulo = buildString {
            state.placaVehiculo?.let { append(getString(R.string.descargo_subtitulo_vehiculo, it)).append(" · ") }
            append(state.rangoTexto)
        }
        b.tvDescargoSubtitulo.text = subtitulo

        lifecycleScope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    viewModel.generarDescargoDeMaterial(state.fechaInicio, state.fechaFin)
                }
                todosLosItems = items
                actualizarContadores(items)
                adapter.submitList(items)
                mostrarCargando(false)
                mostrarVacio(items.isEmpty())
            } catch (t: Throwable) {
                Log.e(TAG, "Error cargando descargo", t)
                mostrarCargando(false)
                mostrarVacio(true)
            }
        }
    }

    private fun aplicarFiltro(chipId: Int?) {
        val filtrado = when (chipId) {
            R.id.chipFiltroLuminarias -> todosLosItems.filter { it.origenTipo == DescargoOrigen.LUMINARIA }
            R.id.chipFiltroAverias    -> todosLosItems.filter { it.origenTipo == DescargoOrigen.AVERIA }
            R.id.chipFiltroProgramaciones -> todosLosItems.filter { it.origenTipo == DescargoOrigen.PROGRAMACION }
            else -> todosLosItems
        }
        adapter.submitList(filtrado)
        mostrarVacio(filtrado.isEmpty() && !b.layoutDescargoCargando.isVisible)
    }

    private fun actualizarContadores(items: List<DescargoMaterialItem>) {
        b.tvCountLuminarias.text = items.count { it.origenTipo == DescargoOrigen.LUMINARIA }.toString()
        b.tvCountAverias.text = items.count { it.origenTipo == DescargoOrigen.AVERIA }.toString()
        b.tvCountProgramaciones.text = items.count { it.origenTipo == DescargoOrigen.PROGRAMACION }.toString()
    }

    private fun mostrarCargando(cargando: Boolean) {
        b.layoutDescargoCargando.isVisible = cargando
        b.recyclerDescargo.isVisible = !cargando
    }

    private fun mostrarVacio(vacio: Boolean) {
        b.layoutDescargoVacio.isVisible = vacio
        b.recyclerDescargo.isVisible = !vacio && !b.layoutDescargoCargando.isVisible
    }

    private fun exportarDescargo(formato: String) {
        if (todosLosItems.isEmpty()) {
            Snackbar.make(b.root, R.string.reportes_export_no_data, Snackbar.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val state = viewModel.uiState.value
            val fechaDisplay = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
            val placa = state.placaVehiculo ?: "vehiculo"
            val nombreArchivo = "Descargo_Material_Vehiculo_${placa}_$fechaDisplay.$formato"
            val subfolder = "Reportes/DescargoMaterial"
            val targetDir = requireContext().getExternalFilesDir(subfolder)
            if (targetDir == null) {
                Snackbar.make(b.root, R.string.reportes_export_error, Snackbar.LENGTH_LONG).show()
                return@launch
            }
            targetDir.mkdirs()
            val file = File(targetDir, nombreArchivo)

            val snack = Snackbar.make(b.root, R.string.reportes_export_progress_preparar, Snackbar.LENGTH_INDEFINITE)
            snack.show()
            try {
                withContext(Dispatchers.IO) {
                    if (formato == "xlsx") {
                        exportarExcelDescargo(file, todosLosItems, state.rangoTexto, placa)
                    } else {
                        exportarPdfDescargo(file, todosLosItems, state.rangoTexto, placa)
                    }
                }
                snack.dismiss()
                val uri = FileProvider.getUriForFile(
                    requireContext(), "${requireContext().packageName}.fileprovider", file
                )
                Snackbar.make(b.root, getString(R.string.reportes_export_success_file, nombreArchivo), Snackbar.LENGTH_LONG)
                    .setAction(R.string.reportes_export_action_view) {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, requireContext().contentResolver.getType(uri) ?: "*/*")
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { startActivity(intent) }
                    }.show()
                // Persiste en Room
                val entidad = ReporteGeneradoEntity(
                    id = UUID.randomUUID().toString(),
                    tipoReporte = getString(R.string.descargo_titulo),
                    formato = formato.uppercase(),
                    nombreArchivo = nombreArchivo,
                    rutaLocal = file.absolutePath,
                    fechaGeneracion = System.currentTimeMillis(),
                    usuarioUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                )
                viewModel.persistirReporte(entidad)
            } catch (t: Throwable) {
                Log.e(TAG, "Error exportando descargo", t)
                snack.dismiss()
                Snackbar.make(b.root, R.string.reportes_export_error, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun exportarExcelDescargo(
        file: File,
        items: List<DescargoMaterialItem>,
        rango: String,
        placa: String
    ) {
        val workbook = org.apache.poi.xssf.usermodel.XSSFWorkbook()
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = org.apache.poi.ss.usermodel.IndexedColors.DARK_BLUE.index
            fillPattern = org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND
            val font = workbook.createFont().apply {
                bold = true; color = org.apache.poi.ss.usermodel.IndexedColors.WHITE.index
            }
            setFont(font)
        }

        // Hoja resumen
        val sheetResumen = workbook.createSheet(getString(R.string.descargo_excel_hoja_resumen))
        val rResumen = sheetResumen.createRow(0)
        listOf(
            getString(R.string.descargo_titulo),
            "${getString(R.string.reportes_pdf_rango, rango)}",
            "Vehículo: $placa"
        ).forEachIndexed { i, txt ->
            rResumen.createCell(i).setCellValue(txt)
        }

        // Hoja detalle
        val sheet = workbook.createSheet(getString(R.string.descargo_excel_hoja_detalle))
        val headers = listOf(
            getString(R.string.descargo_col_origen),
            getString(R.string.descargo_col_id),
            getString(R.string.descargo_col_localizacion),
            getString(R.string.descargo_col_pueblo),
            getString(R.string.descargo_col_cliente),
            getString(R.string.descargo_col_agencia),
            getString(R.string.descargo_col_fecha_atencion),
            getString(R.string.descargo_col_fecha_resolucion),
            getString(R.string.descargo_col_material),
            getString(R.string.descargo_col_cantidad),
            getString(R.string.descargo_col_tecnico),
            getString(R.string.descargo_col_observaciones)
        )
        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { i, h ->
            headerRow.createCell(i).apply {
                setCellValue(h); cellStyle = headerStyle
            }
        }
        items.forEachIndexed { idx, item ->
            val row = sheet.createRow(idx + 1)
            val origenLabel = when (item.origenTipo) {
                DescargoOrigen.LUMINARIA    -> getString(R.string.descargo_origen_luminaria)
                DescargoOrigen.AVERIA       -> getString(R.string.descargo_origen_averia)
                DescargoOrigen.PROGRAMACION -> getString(R.string.descargo_origen_programacion)
            }
            listOf(
                origenLabel, item.origenId, item.localizacion, item.pueblo,
                item.cliente, item.agencia, item.fechaAtencion, item.fechaResolucion,
                item.material, item.cantidad.toString(), item.tecnico, item.observaciones
            ).forEachIndexed { col, valor ->
                row.createCell(col).setCellValue(valor.toString())
            }
        }
        headers.indices.forEach { sheet.autoSizeColumn(it) }
        file.outputStream().use { workbook.write(it) }
        workbook.close()
    }

    private fun exportarPdfDescargo(
        file: File,
        items: List<DescargoMaterialItem>,
        rango: String,
        placa: String
    ) {
        val document = android.graphics.pdf.PdfDocument()
        val pageW = 595; val pageH = 842
        val paint = android.graphics.Paint().apply { textSize = 10f }
        val boldPaint = android.graphics.Paint().apply { textSize = 12f; isFakeBoldText = true }
        val smallPaint = android.graphics.Paint().apply { textSize = 9f; color = android.graphics.Color.GRAY }

        var pageNum = 1
        var pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var y = 40f

        fun finalizarPagina() {
            document.finishPage(page)
        }
        fun nuevaPagina() {
            finalizarPagina()
            pageNum++
            pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            y = 40f
        }
        fun checkY(needed: Float = 60f) { if (y + needed > pageH - 40) nuevaPagina() }

        // Título
        canvas.drawText(getString(R.string.descargo_titulo), 40f, y, boldPaint); y += 20f
        canvas.drawText("Vehículo: $placa | Rango: $rango", 40f, y, smallPaint); y += 20f
        canvas.drawLine(40f, y, (pageW - 40).toFloat(), y, paint); y += 16f

        items.forEach { item ->
            checkY(80f)
            val origenLabel = when (item.origenTipo) {
                DescargoOrigen.LUMINARIA    -> getString(R.string.descargo_origen_luminaria)
                DescargoOrigen.AVERIA       -> getString(R.string.descargo_origen_averia)
                DescargoOrigen.PROGRAMACION -> getString(R.string.descargo_origen_programacion)
            }
            canvas.drawText("[$origenLabel] ${item.origenId} — ${item.material}", 40f, y, boldPaint); y += 14f
            canvas.drawText("${item.localizacion} · ${item.pueblo} · ${item.agencia}", 48f, y, smallPaint); y += 12f
            canvas.drawText("Cantidad: ${item.cantidad} | Técnico: ${item.tecnico} | ${item.fechaAtencion}", 48f, y, paint); y += 12f
            if (item.observaciones.isNotBlank()) {
                canvas.drawText("Obs: ${item.observaciones}", 48f, y, smallPaint); y += 12f
            }
            y += 6f
            canvas.drawLine(40f, y, (pageW - 40).toFloat(), y, smallPaint); y += 10f
        }

        finalizarPagina()
        file.outputStream().use { document.writeTo(it) }
        document.close()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
