package com.Arasoftsolutions.tecniapp_ice.ui.reportes

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.Arasoftsolutions.tecniapp_ice.R
import java.io.File

object PdfReportExporter {

    data class ExportPayload(
        val tipo: ReportType,
        val data: ReportExportData,
        val resumen: ResumenTotales?,
        val rango: String
    )

    fun export(context: Context, payload: ExportPayload, outputFile: File) {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val paint = Paint().apply { textSize = 12f }
        val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true }

        val lines = buildLines(context, payload)
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var y = 40f

        fun newPage() {
            document.finishPage(page)
            pageNumber += 1
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            y = 40f
        }

        canvas.drawText(context.getString(R.string.reportes_pdf_title), 40f, y, titlePaint)
        y += 24f
        canvas.drawText(context.getString(payload.tipo.titleRes), 40f, y, paint)
        y += 18f
        canvas.drawText(context.getString(R.string.reportes_pdf_rango, payload.rango), 40f, y, paint)
        y += 24f

        val contentWidth = pageWidth - 80f
        for (line in lines) {
            val wrapped = wrapLine(line, paint, contentWidth)
            for (row in wrapped) {
                if (y > pageHeight - 60f) {
                    newPage()
                }
                canvas.drawText(row, 40f, y, paint)
                y += 18f
            }
        }

        document.finishPage(page)
        outputFile.outputStream().use { output ->
            document.writeTo(output)
        }
        document.close()
    }

    fun buildContentUri(context: Context, file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    private fun buildLines(context: Context, payload: ExportPayload): List<String> {
        val lines = mutableListOf<String>()
        payload.resumen?.let {
            lines += context.getString(R.string.reportes_pdf_resumen_averias, it.totalAverias)
            lines += context.getString(R.string.reportes_pdf_resumen_materiales, it.totalMateriales)
            lines += context.getString(R.string.reportes_pdf_resumen_codigos, it.totalMaterialesDistintos)
            lines += ""
        }
        when (val data = payload.data) {
            is ReportExportData.MiResumen -> {
                lines += context.getString(R.string.reportes_pdf_seccion_resumen)
                data.items.forEach { item ->
                    lines += "• ${item.titulo}: ${item.valor}"
                }
            }
            is ReportExportData.MisAverias -> {
                lines += context.getString(R.string.reportes_pdf_seccion_averias)
                data.items.forEachIndexed { index, item ->
                    lines += ""
                    lines += "${index + 1}. Caso ${item.caseId} · ${item.estado}"
                    lines += "Región/Provincia: ${item.region.ifBlank { "-" }} / ${item.provincia.ifBlank { "-" }}"
                    lines += "Agencia: ${item.agencia.ifBlank { "-" }}"
                    lines += "Cliente/NISE: ${item.cliente.ifBlank { "-" }} / ${item.nise.ifBlank { "-" }}"
                    lines += "Ubicación: ${item.ubicacion.ifBlank { "-" }}"
                    lines += "Dirección: ${item.direccion.ifBlank { "-" }}"
                    lines += "Causa: ${item.causa.ifBlank { "-" }}"
                    lines += "Observaciones: ${item.observaciones.ifBlank { "-" }}"
                    lines += "Afectación: ${item.tipoAfectacion.ifBlank { "-" }}"
                    lines += "Medidor: ${item.numeroMedidor.ifBlank { "-" }}"
                    lines += "Referencia medidor: ${item.medidorReferencia.ifBlank { "-" }}"
                    lines += "Clientes afectados: ${item.clientesAfectados.ifBlank { "-" }}"
                    lines += "Técnico asignado/atendió: ${item.tecnicoAsignado.ifBlank { "-" }} / ${item.tecnicoAtendio.ifBlank { "-" }}"
                    lines += "Vehículo: ${item.vehiculoAsignado.ifBlank { "-" }}"
                    lines += "Fechas (reporte/llegada/atención): ${item.fechaReporte.ifBlank { "-" }} / ${item.fechaLlegada.ifBlank { "-" }} / ${item.fechaAtencion.ifBlank { "-" }}"
                    lines += "Kilometraje (inicio/llegada/final): ${item.kilometrajeInicio.ifBlank { "-" }} / ${item.kilometrajeLlegada.ifBlank { "-" }} / ${item.kilometrajeFinal.ifBlank { "-" }}"
                    lines += "Materiales (${item.materialesCantidad}): ${item.materialesResumen.ifBlank { "-" }}"
                }
            }
            is ReportExportData.MisLuminarias -> {
                lines += context.getString(R.string.reportes_pdf_seccion_luminarias)
                data.items.forEach { item ->
                    lines += "• ${item.localizacion} | ${item.estado} | ${item.fecha}"
                }
            }
            is ReportExportData.MiInventario -> {
                lines += context.getString(R.string.reportes_pdf_seccion_inventario)
                data.generales.forEach { item ->
                    lines += "• ${item.descripcion} (${item.cantidad} ${item.unidad})"
                }
            }
            is ReportExportData.MiBitacora -> {
                lines += context.getString(R.string.reportes_pdf_seccion_bitacora)
                lines += context.getString(R.string.reportes_bitacora_horas, data.resumen.horasTrabajadas)
                lines += context.getString(R.string.reportes_bitacora_km, data.resumen.kilometros)
                lines += context.getString(R.string.reportes_bitacora_km_inicial, data.resumen.kilometrajeInicial)
                lines += context.getString(R.string.reportes_bitacora_km_final, data.resumen.kilometrajeFinal)
                lines += context.getString(R.string.reportes_bitacora_averias, data.resumen.averiasAtendidas)
                lines += context.getString(R.string.reportes_bitacora_luminarias, data.resumen.luminariasReparadas)
                lines += context.getString(R.string.reportes_bitacora_labores, data.resumen.laboresEjecutadas)
                lines += ""
                data.eventos.forEach { item ->
                    lines += "• ${item.tipo}: ${item.descripcion}"
                }
            }
        }
        lines += ""
        lines += context.getString(R.string.reportes_pdf_footer)
        return lines
    }

    private fun wrapLine(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return listOf("")
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = ""
        words.forEach { word ->
            val candidate = if (current.isBlank()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotBlank()) {
                    lines += current
                }
                current = word
            }
        }
        if (current.isNotBlank()) lines += current
        return if (lines.isEmpty()) listOf("") else lines
    }

}
