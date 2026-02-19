package com.Arasoftsolutions.tecniapp_ice.ui.reportes

import android.content.Context
import com.Arasoftsolutions.tecniapp_ice.R
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook

object ExcelReportExporter {

    const val MIME_TYPE_XLSX =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    enum class ExportStep {
        PREPARAR,
        ESCRIBIR,
        CERRAR,
        GUARDAR,
        INDEXAR
    }

    data class ExportPayload(
        val tipo: ReportType,
        val data: ReportExportData,
        val resumen: ResumenTotales?,
        val rango: String
    )

    fun buildWorkbook(
        context: Context,
        payload: ExportPayload,
        onProgress: ((ExportStep) -> Unit)? = null
    ): Workbook {
        onProgress?.invoke(ExportStep.PREPARAR)
        val workbook = XSSFWorkbook()
        val headerStyle = createHeaderStyle(workbook)

        addResumenSheet(workbook, context, payload.tipo, payload.resumen, payload.rango)

        when (payload.data) {
            is ReportExportData.MiResumen ->
                addMiResumenSheet(context, workbook, headerStyle, payload.data.items)
            is ReportExportData.MisAverias ->
                addMisAveriasSheet(context, workbook, headerStyle, payload.data.items)
            is ReportExportData.MisLuminarias ->
                addMisLuminariasSheet(context, workbook, headerStyle, payload.data.items)
            is ReportExportData.MiInventario ->
                addMiInventarioSheets(context, workbook, headerStyle, payload.data)
            is ReportExportData.MiBitacora ->
                addMiBitacoraSheets(context, workbook, headerStyle, payload.data)
        }

        onProgress?.invoke(ExportStep.ESCRIBIR)
        return workbook
    }

    private fun createHeaderStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        val font = workbook.createFont()
        font.bold = true
        font.color = IndexedColors.WHITE.index
        style.setFont(font)
        style.fillForegroundColor = IndexedColors.BLUE_GREY.index
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        style.alignment = HorizontalAlignment.CENTER
        style.verticalAlignment = VerticalAlignment.CENTER
        style.borderBottom = BorderStyle.THIN
        style.borderTop = BorderStyle.THIN
        style.borderLeft = BorderStyle.THIN
        style.borderRight = BorderStyle.THIN
        return style
    }

    private fun addResumenSheet(
        workbook: Workbook,
        context: Context,
        tipo: ReportType,
        resumen: ResumenTotales?,
        rango: String
    ) {
        val sheet = workbook.createSheet(context.getString(R.string.reportes_excel_resumen_sheet))
        var rowIndex = 0

        sheet.createRow(rowIndex++).apply {
            createCell(0).setCellValue(context.getString(R.string.reportes_excel_resumen_tipo))
            createCell(1).setCellValue(context.getString(tipo.titleRes))
        }
        sheet.createRow(rowIndex++).apply {
            createCell(0).setCellValue(context.getString(R.string.reportes_excel_resumen_rango))
            createCell(1).setCellValue(rango)
        }

        if (resumen != null) {
            sheet.createRow(rowIndex++).apply {
                createCell(0).setCellValue(context.getString(R.string.reportes_excel_resumen_total_averias))
                createCell(1).setCellValue(resumen.totalAverias.toDouble())
            }
            sheet.createRow(rowIndex++).apply {
                createCell(0).setCellValue(context.getString(R.string.reportes_excel_resumen_total_materiales))
                createCell(1).setCellValue(resumen.totalMateriales.toDouble())
            }
            sheet.createRow(rowIndex).apply {
                createCell(0).setCellValue(context.getString(R.string.reportes_excel_resumen_total_codigos))
                createCell(1).setCellValue(resumen.totalMaterialesDistintos.toDouble())
            }
        } else {
            sheet.createRow(rowIndex).apply {
                createCell(0).setCellValue(context.getString(R.string.reportes_excel_resumen_sin_datos))
            }
        }

        autosize(sheet, 2)
    }

    private fun addMiResumenSheet(
        context: Context,
        workbook: Workbook,
        headerStyle: CellStyle,
        items: List<ResumenKpiItem>
    ) {
        val sheet = workbook.createSheet(context.getString(R.string.reportes_excel_mi_resumen_sheet))
        val headers = listOf(
            context.getString(R.string.reportes_excel_col_kpi),
            context.getString(R.string.reportes_excel_col_valor),
            context.getString(R.string.reportes_excel_col_detalle)
        )

        var rowIndex = createHeader(sheet, headerStyle, headers)
        items.forEach { item ->
            val row = sheet.createRow(rowIndex++)
            row.createCell(0).setCellValue(item.titulo)
            row.createCell(1).setCellValue(item.valor)
            row.createCell(2).setCellValue(item.detalle.orEmpty())
        }

        autosize(sheet, headers.size)
    }

    private fun addMisAveriasSheet(
        context: Context,
        workbook: Workbook,
        headerStyle: CellStyle,
        items: List<MisAveriaReportItem>
    ) {
        val sheet = workbook.createSheet(context.getString(R.string.reportes_excel_mis_averias_sheet))
        val headers = listOf(
            context.getString(R.string.reportes_excel_col_case),
            context.getString(R.string.reportes_excel_col_region),
            context.getString(R.string.reportes_excel_col_provincia),
            context.getString(R.string.reportes_excel_col_agencia),
            context.getString(R.string.reportes_excel_col_cliente),
            context.getString(R.string.reportes_excel_col_nise),
            context.getString(R.string.reportes_excel_col_causa),
            context.getString(R.string.reportes_excel_col_observaciones),
            context.getString(R.string.reportes_excel_col_direccion),
            context.getString(R.string.reportes_excel_col_ubicacion),
            context.getString(R.string.reportes_excel_col_tipo_afectacion),
            context.getString(R.string.reportes_excel_col_numero_medidor),
            context.getString(R.string.reportes_excel_col_medidor_referencia),
            context.getString(R.string.reportes_excel_col_clientes_afectados),
            context.getString(R.string.reportes_excel_col_tecnico_asignado),
            context.getString(R.string.reportes_excel_col_atendido),
            context.getString(R.string.reportes_excel_col_vehiculo),
            context.getString(R.string.reportes_excel_col_estado),
            context.getString(R.string.reportes_excel_col_fecha_reporte),
            context.getString(R.string.reportes_excel_col_fecha_llegada),
            context.getString(R.string.reportes_excel_col_fecha_atencion),
            context.getString(R.string.reportes_excel_col_km_inicio),
            context.getString(R.string.reportes_excel_col_km_llegada),
            context.getString(R.string.reportes_excel_col_km_final),
            context.getString(R.string.reportes_excel_col_material_resumen),
            context.getString(R.string.reportes_excel_col_material_total)
        )

        var rowIndex = createHeader(sheet, headerStyle, headers)
        items.forEach { item ->
            val row = sheet.createRow(rowIndex++)
            row.createCell(0).setCellValue(item.caseId)
            row.createCell(1).setCellValue(item.region)
            row.createCell(2).setCellValue(item.provincia)
            row.createCell(3).setCellValue(item.agencia)
            row.createCell(4).setCellValue(item.cliente)
            row.createCell(5).setCellValue(item.nise)
            row.createCell(6).setCellValue(item.causa)
            row.createCell(7).setCellValue(item.observaciones)
            row.createCell(8).setCellValue(item.direccion)
            row.createCell(9).setCellValue(item.ubicacion)
            row.createCell(10).setCellValue(item.tipoAfectacion)
            row.createCell(11).setCellValue(item.numeroMedidor)
            row.createCell(12).setCellValue(item.medidorReferencia)
            row.createCell(13).setCellValue(item.clientesAfectados)
            row.createCell(14).setCellValue(item.tecnicoAsignado)
            row.createCell(15).setCellValue(item.tecnicoAtendio)
            row.createCell(16).setCellValue(item.vehiculoAsignado)
            row.createCell(17).setCellValue(item.estado)
            row.createCell(18).setCellValue(item.fechaReporte)
            row.createCell(19).setCellValue(item.fechaLlegada)
            row.createCell(20).setCellValue(item.fechaAtencion)
            row.createCell(21).setCellValue(item.kilometrajeInicio)
            row.createCell(22).setCellValue(item.kilometrajeLlegada)
            row.createCell(23).setCellValue(item.kilometrajeFinal)
            row.createCell(24).setCellValue(item.materialesResumen)
            row.createCell(25).setCellValue(item.materialesCantidad.toDouble())
        }

        autosize(sheet, headers.size)
    }

    private fun addMisLuminariasSheet(
        context: Context,
        workbook: Workbook,
        headerStyle: CellStyle,
        items: List<MisLuminariaReportItem>
    ) {
        val sheet = workbook.createSheet(context.getString(R.string.reportes_excel_mis_luminarias_sheet))
        val headers = listOf(
            context.getString(R.string.reportes_excel_col_localizacion),
            context.getString(R.string.reportes_excel_col_estado),
            context.getString(R.string.reportes_excel_col_fecha),
            context.getString(R.string.reportes_excel_col_vehiculo),
            context.getString(R.string.reportes_excel_col_comunidad),
            context.getString(R.string.reportes_excel_col_material_resumen)
        )

        var rowIndex = createHeader(sheet, headerStyle, headers)
        items.forEach { item ->
            val row = sheet.createRow(rowIndex++)
            row.createCell(0).setCellValue(item.localizacion)
            row.createCell(1).setCellValue(item.estado)
            row.createCell(2).setCellValue(item.fecha)
            row.createCell(3).setCellValue(item.vehiculo)
            row.createCell(4).setCellValue(item.comunidad)
            row.createCell(5).setCellValue(item.materialesResumen)
        }

        autosize(sheet, headers.size)
    }

    private fun addMiInventarioSheets(
        context: Context,
        workbook: Workbook,
        headerStyle: CellStyle,
        data: ReportExportData.MiInventario
    ) {
        val sheet = workbook.createSheet(context.getString(R.string.reportes_excel_mi_inventario_sheet))
        val headers = listOf(
            context.getString(R.string.reportes_excel_col_codigo),
            context.getString(R.string.reportes_excel_col_descripcion),
            context.getString(R.string.reportes_excel_col_cantidad),
            context.getString(R.string.reportes_excel_col_unidad)
        )

        var rowIndex = createHeader(sheet, headerStyle, headers)
        data.generales.forEach { item ->
            val row = sheet.createRow(rowIndex++)
            row.createCell(0).setCellValue(item.codigo)
            row.createCell(1).setCellValue(item.descripcion)
            row.createCell(2).setCellValue(item.cantidad)
            row.createCell(3).setCellValue(item.unidad)
        }

        autosize(sheet, headers.size)

        val criticosSheet = workbook.createSheet(context.getString(R.string.reportes_excel_mi_inventario_critico_sheet))
        rowIndex = createHeader(criticosSheet, headerStyle, headers)
        data.criticos.forEach { item ->
            val row = criticosSheet.createRow(rowIndex++)
            row.createCell(0).setCellValue(item.codigo)
            row.createCell(1).setCellValue(item.descripcion)
            row.createCell(2).setCellValue(item.cantidad)
            row.createCell(3).setCellValue(item.unidad)
        }
        autosize(criticosSheet, headers.size)

        val movimientosSheet = workbook.createSheet(context.getString(R.string.reportes_excel_mi_inventario_mov_sheet))
        movimientosSheet.createRow(0).apply {
            createCell(0).setCellValue(context.getString(R.string.reportes_excel_col_mov_entradas))
            createCell(1).setCellValue(data.movimientos.entradas)
        }
        movimientosSheet.createRow(1).apply {
            createCell(0).setCellValue(context.getString(R.string.reportes_excel_col_mov_salidas))
            createCell(1).setCellValue(data.movimientos.salidas)
        }
        movimientosSheet.createRow(2).apply {
            createCell(0).setCellValue(context.getString(R.string.reportes_excel_col_mov_neto))
            createCell(1).setCellValue(data.movimientos.neto)
        }
        autosize(movimientosSheet, 2)
    }

    private fun addMiBitacoraSheets(
        context: Context,
        workbook: Workbook,
        headerStyle: CellStyle,
        data: ReportExportData.MiBitacora
    ) {
        val resumenSheet = workbook.createSheet(context.getString(R.string.reportes_excel_mi_bitacora_resumen_sheet))
        resumenSheet.createRow(0).apply {
            createCell(0).setCellValue(context.getString(R.string.reportes_excel_col_horas))
            createCell(1).setCellValue(data.resumen.horasTrabajadas)
        }
        resumenSheet.createRow(1).apply {
            createCell(0).setCellValue(context.getString(R.string.reportes_excel_col_kilometros))
            createCell(1).setCellValue(data.resumen.kilometros)
        }
        resumenSheet.createRow(2).apply {
            createCell(0).setCellValue(context.getString(R.string.reportes_excel_col_averias))
            createCell(1).setCellValue(data.resumen.averiasAtendidas.toDouble())
        }
        resumenSheet.createRow(3).apply {
            createCell(0).setCellValue(context.getString(R.string.reportes_excel_col_luminarias))
            createCell(1).setCellValue(data.resumen.luminariasReparadas.toDouble())
        }
        autosize(resumenSheet, 2)

        val detallesSheet = workbook.createSheet(context.getString(R.string.reportes_excel_mi_bitacora_sheet))
        val headers = listOf(
            context.getString(R.string.reportes_excel_col_tipo),
            context.getString(R.string.reportes_excel_col_referencia),
            context.getString(R.string.reportes_excel_col_fecha),
            context.getString(R.string.reportes_excel_col_descripcion),
            context.getString(R.string.reportes_excel_col_cantidad)
        )
        var rowIndex = createHeader(detallesSheet, headerStyle, headers)
        data.eventos.forEach { item ->
            val row = detallesSheet.createRow(rowIndex++)
            row.createCell(0).setCellValue(item.tipo)
            row.createCell(1).setCellValue(item.referencia)
            row.createCell(2).setCellValue(item.fecha)
            row.createCell(3).setCellValue(item.descripcion)
            row.createCell(4).setCellValue(item.cantidad)
        }
        autosize(detallesSheet, headers.size)
    }

    private fun createHeader(sheet: Sheet, headerStyle: CellStyle, headers: List<String>): Int {
        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { index, title ->
            val cell = headerRow.createCell(index)
            cell.setCellValue(title)
            cell.cellStyle = headerStyle
        }
        return 1
    }

    private fun autosize(sheet: Sheet, numColumns: Int) {
        for (i in 0 until numColumns) {
            try {
                sheet.setColumnWidth(i, 20 * 256)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
