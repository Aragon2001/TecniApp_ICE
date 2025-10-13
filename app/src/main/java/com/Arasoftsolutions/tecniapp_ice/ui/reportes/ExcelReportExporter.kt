package com.Arasoftsolutions.tecniapp_ice.ui.reportes

import android.content.Context
import com.Arasoftsolutions.tecniapp_ice.R
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.util.WorkbookUtil
import org.apache.poi.xssf.usermodel.XSSFWorkbook

object ExcelReportExporter {

    const val MIME_TYPE_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    data class ExportPayload(
        val tipo: ReportType,
        val data: ReportExportData,
        val resumen: ResumenTotales?,
        val rango: String
    )

    fun buildWorkbook(context: Context, payload: ExportPayload): Workbook {
        val workbook = XSSFWorkbook()
        val headerStyle = createHeaderStyle(workbook)

        addResumenSheet(workbook, context, payload.resumen, payload.rango)

        when (payload.data) {
            is ReportExportData.Averias -> addAveriasSheet(context, workbook, headerStyle, payload.data.items)
            is ReportExportData.MaterialesPorAveria ->
                addMaterialesPorAveriaSheet(context, workbook, headerStyle, payload.data.items)
            is ReportExportData.MaterialesTotales ->
                addMaterialesTotalesSheet(context, workbook, headerStyle, payload.data.items)
        }

        return workbook
    }

    private fun createHeaderStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        val font = workbook.createFont()
        font.bold = true
        style.setFont(font)
        style.fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        return style
    }

    private fun addResumenSheet(
        workbook: Workbook,
        context: Context,
        resumen: ResumenTotales?,
        rango: String
    ) {
        val sheet = workbook.createSheet(context.getString(R.string.reportes_excel_resumen_sheet))
        var rowIndex = 0

        val rangoRow = sheet.createRow(rowIndex++)
        rangoRow.createCell(0).setCellValue(context.getString(R.string.reportes_excel_resumen_rango))
        rangoRow.createCell(1).setCellValue(rango)

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

    private fun addAveriasSheet(
        context: Context,
        workbook: Workbook,
        headerStyle: CellStyle,
        items: List<AveriaReportItem>
    ) {
        val sheet = workbook.createSheet(context.getString(R.string.reportes_excel_averias_sheet))
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

    private fun addMaterialesPorAveriaSheet(
        context: Context,
        workbook: Workbook,
        headerStyle: CellStyle,
        items: List<MaterialPorAveriaReportItem>
    ) {
        val headers = listOf(
            context.getString(R.string.reportes_excel_col_case),
            context.getString(R.string.reportes_excel_col_fecha),
            context.getString(R.string.reportes_excel_col_agencia),
            context.getString(R.string.reportes_excel_col_codigo),
            context.getString(R.string.reportes_excel_col_descripcion),
            context.getString(R.string.reportes_excel_col_cantidad)
        )

        if (items.isEmpty()) {
            val sheet = createUniqueSheet(
                workbook,
                context.getString(R.string.reportes_excel_materiales_por_averia_sheet)
            )
            createHeader(sheet, headerStyle, headers)
            autosize(sheet, headers.size)
            return
        }

        val sinVehiculo = context.getString(R.string.reportes_excel_materiales_por_averia_sheet_sin_vehiculo)
        val grupos = items.groupBy { item ->
            item.vehiculo?.takeIf { it.isNotBlank() } ?: sinVehiculo
        }.toSortedMap(String.CASE_INSENSITIVE_ORDER)

        grupos.forEach { (vehiculo, lista) ->
            val titulo = if (vehiculo == sinVehiculo) {
                sinVehiculo
            } else {
                context.getString(R.string.reportes_excel_materiales_por_averia_sheet_vehicle, vehiculo)
            }
            val sheet = createUniqueSheet(workbook, titulo)
            var rowIndex = createHeader(sheet, headerStyle, headers)
            lista.forEach { item ->
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
    }

    private fun createUniqueSheet(workbook: Workbook, desiredName: String): Sheet {
        var attempt = 0
        while (true) {
            val suffix = if (attempt == 0) "" else " (${attempt + 1})"
            val rawName = desiredName + suffix
            val safeName = WorkbookUtil.createSafeSheetName(rawName).take(31)
            if (workbook.getSheet(safeName) == null) {
                return workbook.createSheet(safeName)
            }
            attempt++
        }
    }

    private fun addMaterialesTotalesSheet(
        context: Context,
        workbook: Workbook,
        headerStyle: CellStyle,
        items: List<MaterialTotalItem>
    ) {
        val sheet = workbook.createSheet(context.getString(R.string.reportes_excel_materiales_totales_sheet))
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

    private fun createHeader(sheet: Sheet, headerStyle: CellStyle, headers: List<String>): Int {
        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { index, title ->
            val cell = headerRow.createCell(index)
            cell.setCellValue(title)
            cell.cellStyle = headerStyle
        }
        return 1
    }

    private fun autosize(sheet: Sheet, columnCount: Int) {
        for (index in 0 until columnCount) {
            sheet.autoSizeColumn(index)
        }
    }
}
