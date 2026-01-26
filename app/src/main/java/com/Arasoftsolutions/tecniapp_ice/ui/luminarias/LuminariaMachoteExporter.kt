package com.Arasoftsolutions.tecniapp_ice.ui.luminarias

import android.content.Context
import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculosEntity
import com.Arasoftsolutions.tecniapp_ice.R
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.util.WorkbookUtil
import org.apache.poi.xssf.usermodel.XSSFWorkbook

object LuminariaMachoteExporter {
    fun buildWorkbook(context: Context, vehiculos: List<VehiculosEntity>): Workbook {
        val workbook = XSSFWorkbook()
        val headerStyle = createHeaderStyle(workbook)
        val headers = listOf(
            context.getString(R.string.reportes_excel_col_localizacion),
            context.getString(R.string.luminarias_excel_col_cliente),
            context.getString(R.string.luminarias_excel_col_contacto),
            context.getString(R.string.luminarias_excel_col_observaciones)
        )
        vehiculos.sortedBy { it.placa }.forEach { vehiculo ->
            val sheetName = WorkbookUtil.createSafeSheetName(vehiculo.placa.toString())
            val sheet = workbook.createSheet(sheetName)
            val headerRow = sheet.createRow(0)
            headers.forEachIndexed { index, title ->
                val cell = headerRow.createCell(index)
                cell.setCellValue(title)
                cell.cellStyle = headerStyle
                sheet.setColumnWidth(index, 20 * 256)
            }
        }
        if (vehiculos.isEmpty()) {
            workbook.createSheet(context.getString(R.string.luminarias_camion_todos))
        }
        return workbook
    }

    private fun createHeaderStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        val font = workbook.createFont()
        font.bold = true
        style.setFont(font)
        style.alignment = HorizontalAlignment.CENTER
        return style
    }
}
