package com.Arasoftsolutions.tecniapp_ice.ui.reportes

import android.content.Context
import com.Arasoftsolutions.tecniapp_ice.R
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook

object ExcelReportExporter {

    const val MIME_TYPE_XLSX =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    data class ExportPayload(
        val tipo: ReportType,
        val data: ReportExportData,
        val resumen: ResumenTotales?,
        val rango: String
    )

    fun buildWorkbook(context: Context, payload: ExportPayload): Workbook {
        val workbook = XSSFWorkbook()
        val headerStyle = createHeaderStyle(workbook)

        // Hoja de resumen
        addResumenSheet(workbook, context, payload.resumen, payload.rango)

        // Hojas según tipo de reporte
        when (payload.data) {
            is ReportExportData.Averias ->
                addAveriasSheet(context, workbook, headerStyle, payload.data.items)

            is ReportExportData.MaterialesPorAveria ->
                addMaterialesPorAveriaSheet(context, workbook, headerStyle, payload.data.items)

            is ReportExportData.MaterialesTotales ->
                addMaterialesTotalesSheet(context, workbook, headerStyle, payload.data.items)
        }

        return workbook
    }

    // ---- Estilo de encabezados ----
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

    // ---- Hoja de resumen ----
    private fun addResumenSheet(
        workbook: Workbook,
        context: Context,
        resumen: ResumenTotales?,
        rango: String
    ) {
        val sheet = workbook.createSheet(
            context.getString(R.string.reportes_excel_resumen_sheet)
        )
        var rowIndex = 0

        val rangoRow = sheet.createRow(rowIndex++)
        rangoRow.createCell(0).setCellValue(
            context.getString(R.string.reportes_excel_resumen_rango)
        )
        rangoRow.createCell(1).setCellValue(rango)

        if (resumen != null) {
            sheet.createRow(rowIndex++).apply {
                createCell(0).setCellValue(
                    context.getString(R.string.reportes_excel_resumen_total_averias)
                )
                createCell(1).setCellValue(resumen.totalAverias.toDouble())
            }
            sheet.createRow(rowIndex++).apply {
                createCell(0).setCellValue(
                    context.getString(R.string.reportes_excel_resumen_total_materiales)
                )
                createCell(1).setCellValue(resumen.totalMateriales.toDouble())
            }
            sheet.createRow(rowIndex).apply {
                createCell(0).setCellValue(
                    context.getString(R.string.reportes_excel_resumen_total_codigos)
                )
                createCell(1).setCellValue(resumen.totalMaterialesDistintos.toDouble())
            }
        } else {
            sheet.createRow(rowIndex).apply {
                createCell(0).setCellValue(
                    context.getString(R.string.reportes_excel_resumen_sin_datos)
                )
            }
        }

        autosize(sheet, 2)
    }

    // ---- Hoja de Averías ----
    private fun addAveriasSheet(
        context: Context,
        workbook: Workbook,
        headerStyle: CellStyle,
        items: List<AveriaReportItem>
    ) {
        val sheet = workbook.createSheet(
            context.getString(R.string.reportes_excel_averias_sheet)
        )
        val headers = listOf(
            context.getString(R.string.reportes_excel_col_case),
            context.getString(R.string.reportes_excel_col_fecha),
            context.getString(R.string.reportes_excel_col_agencia),
            context.getString(R.string.reportes_excel_col_estado),
            context.getString(R.string.reportes_excel_col_atendido),
            context.getString(R.string.reportes_excel_col_vehiculo),
            context.getString(R.string.reportes_excel_col_material_resumen),
            context.getString(R.string.reportes_excel_col_material_total)
        )

        var rowIndex = createHeader(sheet, headerStyle, headers)
        items.forEach { item ->
            val row = sheet.createRow(rowIndex++)
            row.createCell(0).setCellValue(item.caseId)
            row.createCell(1).setCellValue(item.fechaTexto)
            row.createCell(2).setCellValue(item.agencia)
            row.createCell(3).setCellValue(item.estado)
            row.createCell(4).setCellValue(item.atendidoPor)
            row.createCell(5).setCellValue(item.vehiculo.orEmpty())
            row.createCell(6).setCellValue(item.materialesResumen)
            row.createCell(7).setCellValue(item.materialesCantidad.toDouble())
        }

        autosize(sheet, headers.size)
    }

    // ---- Hoja Materiales por Avería ----
    private fun addMaterialesPorAveriaSheet(
        context: Context,
        workbook: Workbook,
        headerStyle: CellStyle,
        items: List<MaterialPorAveriaReportItem>
    ) {
        val sheet = workbook.createSheet(
            context.getString(R.string.reportes_excel_materiales_por_averia_sheet)
        )
        val headers = listOf(
            context.getString(R.string.reportes_excel_col_case),
            context.getString(R.string.reportes_excel_col_fecha),
            context.getString(R.string.reportes_excel_col_agencia),
            context.getString(R.string.reportes_excel_col_codigo),
            context.getString(R.string.reportes_excel_col_descripcion),
            context.getString(R.string.reportes_excel_col_cantidad)
        )

        var rowIndex = createHeader(sheet, headerStyle, headers)
        items.forEach { item ->
            if (item.materiales.isEmpty()) {
                val row = sheet.createRow(rowIndex++)
                row.createCell(0).setCellValue(item.caseId)
                row.createCell(1).setCellValue(item.fechaTexto)
                row.createCell(2).setCellValue(item.agencia)
                row.createCell(3).setCellValue("")
                row.createCell(4).setCellValue("")
                row.createCell(5).setCellValue(0.0)
            } else {
                item.materiales.forEach { material ->
                    val row = sheet.createRow(rowIndex++)
                    row.createCell(0).setCellValue(item.caseId)
                    row.createCell(1).setCellValue(item.fechaTexto)
                    row.createCell(2).setCellValue(item.agencia)
                    row.createCell(3).setCellValue(material.codigo)
                    row.createCell(4).setCellValue(material.descripcion)
                    row.createCell(5).setCellValue(material.cantidad.toDouble())
                }
            }
        }

        autosize(sheet, headers.size)
    }

    // ---- Hoja Materiales Totales ----
    private fun addMaterialesTotalesSheet(
        context: Context,
        workbook: Workbook,
        headerStyle: CellStyle,
        items: List<MaterialTotalItem>
    ) {
        val sheet = workbook.createSheet(
            context.getString(R.string.reportes_excel_materiales_totales_sheet)
        )
        val headers = listOf(
            context.getString(R.string.reportes_excel_col_codigo),
            context.getString(R.string.reportes_excel_col_descripcion),
            context.getString(R.string.reportes_excel_col_material_total),
            context.getString(R.string.reportes_excel_col_averias)
        )

        var rowIndex = createHeader(sheet, headerStyle, headers)
        items.forEach { item ->
            val row = sheet.createRow(rowIndex++)
            row.createCell(0).setCellValue(item.codigo)
            row.createCell(1).setCellValue(item.descripcion)
            row.createCell(2).setCellValue(item.total.toDouble())
            row.createCell(3).setCellValue(item.averias.toDouble())
        }

        autosize(sheet, headers.size)
    }

    // ---- Encabezados ----
    private fun createHeader(sheet: Sheet, headerStyle: CellStyle, headers: List<String>): Int {
        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { index, title ->
            val cell = headerRow.createCell(index)
            cell.setCellValue(title)
            cell.cellStyle = headerStyle
        }
        return 1
    }

    // ---- Ajuste manual de columnas ----
    private fun autosize(sheet: Sheet, numColumns: Int) {
        for (i in 0 until numColumns) {
            try {
                sheet.setColumnWidth(i, 20 * 256) // ancho fijo (20 caracteres aprox.)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
