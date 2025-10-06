package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.Arasoftsolutions.tecniapp_ice.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfGenerator {
    private const val PAGE_WIDTH = 595  // A4 px
    private const val PAGE_HEIGHT = 842

    suspend fun exportAveria(context: Context, item: AveriaUI) = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            fun formatMillis(millis: Long?): String? =
                millis?.takeIf { it > 0 }?.let { formatter.format(Date(it)) }

            val reporteGenerado = formatter.format(Date())
            val fechaEvento = formatMillis(item.fechaMillis)
            val inicioAtencion = formatMillis(item.horaAtencionInicio)
            val finAtencion = formatMillis(item.horaAtencionFinal)

            val headerPadding = 32f
            val margin = 40f
            val contentWidth = PAGE_WIDTH - (margin * 2)

            // Encabezado con degradado
            val headerHeight = 150f
            val headerRect = RectF(margin, margin, PAGE_WIDTH - margin, margin + headerHeight)
            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    headerRect.left,
                    headerRect.top,
                    headerRect.right,
                    headerRect.bottom,
                    Color.parseColor("#2E3192"),
                    Color.parseColor("#1BFFFF"),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(headerRect, 28f, 28f, headerPaint)

            val headerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 32f
                color = Color.WHITE
            }
            val headerSubtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 16f
                color = Color.WHITE
            }
            val headerTagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 14f
                color = Color.WHITE
                alpha = 220
            }

            val headerTitle = context.getString(R.string.averia_pdf_header_title)
            val headerSubtitle = context.getString(R.string.averia_pdf_header_subtitle)
            val headerTagline = context.getString(R.string.averia_pdf_header_tagline)

            var textY = headerRect.top + headerPadding
            canvas.drawText(headerTitle, headerRect.left + headerPadding, textY, headerTitlePaint)
            textY += 36f
            canvas.drawText(headerSubtitle, headerRect.left + headerPadding, textY, headerSubtitlePaint)
            textY += 26f
            canvas.drawText(headerTagline, headerRect.left + headerPadding, textY, headerTagPaint)

            // Sección tabla principal
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 18f
                color = Color.parseColor("#233041")
            }
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 13f
                color = Color.parseColor("#2E3192")
            }
            val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 13f
                color = Color.parseColor("#233041")
            }

            val sectionTitleY = headerRect.bottom + 36f
            canvas.drawText(
                context.getString(R.string.averia_pdf_section_title),
                margin,
                sectionTitleY,
                titlePaint
            )

            val emptyValue = context.getString(R.string.averia_pdf_empty_value)
            val assigned = item.tecnico.ifBlank { context.getString(R.string.averia_sin_asignar) }
            val attended = item.atendidoPor.ifBlank { emptyValue }
            val vehicle = item.vehiculo ?: emptyValue
            val nise = item.nise.ifBlank { emptyValue }
            val kilometraje = if (item.kilometrajeInicio != null || item.kilometrajeFinal != null) {
                val inicioKm = item.kilometrajeInicio?.toString() ?: emptyValue
                val finKm = item.kilometrajeFinal?.toString() ?: emptyValue
                context.getString(R.string.averia_pdf_kilometers_value, inicioKm, finKm)
            } else {
                emptyValue
            }
            val location = if (item.lat == 0.0 && item.lng == 0.0) {
                context.getString(R.string.averia_pdf_location_no_data)
            } else {
                context.getString(R.string.averia_pdf_location_value, item.lat, item.lng)
            }
            val region = item.region.ifBlank { emptyValue }
            val agency = item.agencia.ifBlank { emptyValue }

            val tableData = listOf(
                context.getString(R.string.averia_pdf_table_label_case) to item.id,
                context.getString(R.string.averia_pdf_table_label_nise) to nise,
                context.getString(R.string.averia_pdf_table_label_status) to item.estado,
                context.getString(R.string.averia_pdf_table_label_assigned) to assigned,
                context.getString(R.string.averia_pdf_table_label_attended_by) to attended,
                context.getString(R.string.averia_pdf_table_label_vehicle) to vehicle,
                context.getString(R.string.averia_pdf_table_label_event_date) to (fechaEvento ?: emptyValue),
                context.getString(R.string.averia_pdf_table_label_start_time) to (inicioAtencion ?: emptyValue),
                context.getString(R.string.averia_pdf_table_label_end_time) to (finAtencion ?: emptyValue),
                context.getString(R.string.averia_pdf_table_label_kilometers) to kilometraje,
                context.getString(R.string.averia_pdf_table_label_region) to region,
                context.getString(R.string.averia_pdf_table_label_agency) to agency,
                context.getString(R.string.averia_pdf_table_label_location) to location,
                context.getString(R.string.averia_pdf_table_label_generated) to reporteGenerado
            )

            val tableRows = tableData.chunked(2)
            val tableTop = sectionTitleY + 18f
            val tableLeft = margin
            val tableRight = margin + contentWidth
            val tableRowHeight = 52f
            val tableHeight = tableRowHeight * tableRows.size
            val tableRect = RectF(tableLeft, tableTop, tableRight, tableTop + tableHeight)

            val tableBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#F6F7FB")
            }
            val tableBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = Color.parseColor("#D0D8FF")
            }
            val rowEvenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFFFFF") }
            val rowOddPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EEF2FF") }
            val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#D0D8FF")
                strokeWidth = 1.5f
            }

            canvas.drawRoundRect(tableRect, 24f, 24f, tableBackgroundPaint)
            canvas.drawRoundRect(tableRect, 24f, 24f, tableBorderPaint)

            val columnDivider = tableLeft + (contentWidth / 2f)
            var rowTop = tableTop
            tableRows.forEachIndexed { index, rowItems ->
                val rowBottom = rowTop + tableRowHeight
                val rowRect = RectF(
                    tableLeft + 4f,
                    rowTop + if (index == 0) 4f else 0f,
                    tableRight - 4f,
                    rowBottom - if (index == tableRows.lastIndex) 4f else 0f
                )
                val rowPaint = if (index % 2 == 0) rowEvenPaint else rowOddPaint
                canvas.drawRoundRect(rowRect, 18f, 18f, rowPaint)

                rowItems.forEachIndexed { columnIndex, (label, value) ->
                    val cellLeft = if (columnIndex == 0) tableLeft else columnDivider
                    val labelY = rowTop + 22f
                    val valueY = labelY + 20f
                    canvas.drawText(
                        label.uppercase(Locale.getDefault()),
                        cellLeft + 14f,
                        labelY,
                        labelPaint
                    )
                    canvas.drawText(
                        value,
                        cellLeft + 14f,
                        valueY,
                        valuePaint
                    )
                }

                canvas.drawLine(tableLeft, rowBottom, tableRight, rowBottom, dividerPaint)
                rowTop = rowBottom
            }

            canvas.drawLine(columnDivider, tableTop, columnDivider, tableTop + tableHeight, dividerPaint)

            var currentY = tableTop + tableHeight + 22f

            fun wrapText(text: String): List<String> {
                if (text.isBlank()) return listOf(emptyValue)
                val paragraphs = text.split('\n')
                val lines = mutableListOf<String>()
                val maxWidth = contentWidth - 48f
                paragraphs.forEach { paragraph ->
                    if (paragraph.isBlank()) {
                        lines.add("")
                    } else {
                        val words = paragraph.split(" ").filter { it.isNotEmpty() }
                        var currentLine = ""
                        words.forEach { word ->
                            val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
                            if (valuePaint.measureText(candidate) <= maxWidth) {
                                currentLine = candidate
                            } else {
                                if (currentLine.isNotEmpty()) lines.add(currentLine)
                                currentLine = word
                            }
                        }
                        if (currentLine.isNotEmpty()) lines.add(currentLine)
                    }
                }
                return lines.ifEmpty { listOf(emptyValue) }
            }

            fun drawHighlightSection(title: String, content: String) {
                val lines = wrapText(content)
                val lineHeight = 18f
                val boxPadding = 20f
                val boxHeight = boxPadding * 2 + (lines.size * lineHeight)
                val boxRect = RectF(margin, currentY, margin + contentWidth, currentY + boxHeight)
                val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFFFFF") }
                val boxShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#E3E7FF")
                    strokeWidth = 3f
                    style = Paint.Style.STROKE
                }

                canvas.drawRoundRect(boxRect, 26f, 26f, boxPaint)
                canvas.drawRoundRect(boxRect, 26f, 26f, boxShadowPaint)

                canvas.drawText(title, boxRect.left + boxPadding, boxRect.top + boxPadding - 2f, labelPaint)
                var lineY = boxRect.top + boxPadding + 14f
                lines.forEach { line ->
                    canvas.drawText(line, boxRect.left + boxPadding, lineY, valuePaint)
                    lineY += lineHeight
                }
                currentY += boxHeight + 12f
            }

            drawHighlightSection(
                context.getString(R.string.averia_pdf_section_notes),
                item.observaciones.ifBlank { emptyValue }
            )
            drawHighlightSection(
                context.getString(R.string.averia_pdf_section_materials),
                item.materialesResumen.ifBlank { emptyValue }
            )

            val footerRect = RectF(margin, currentY, margin + contentWidth, currentY + 62f)
            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2E3192") }
            canvas.drawRoundRect(footerRect, 24f, 24f, footerPaint)

            val footerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 16f
            }
            val footerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 12f
            }

            canvas.drawText(
                context.getString(R.string.averia_pdf_footer_title),
                footerRect.left + 24f,
                footerRect.top + 24f,
                footerTitlePaint
            )
            canvas.drawText(
                context.getString(R.string.averia_pdf_footer_content),
                footerRect.left + 24f,
                footerRect.top + 42f,
                footerTextPaint
            )

            document.finishPage(page)

            // Guardar PDF
            val reportsDir = File(context.getExternalFilesDir(null), "TecniApp/Reportes")
            if (!reportsDir.exists()) reportsDir.mkdirs()
            val fileNameFormatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val fileName = "averia_${item.id}_${fileNameFormatter.format(Date())}.pdf"
            val file = File(reportsDir, fileName)
            FileOutputStream(file).use { output ->
                document.writeTo(output)
            }

            // Compartir PDF
            withContext(Dispatchers.Main) {
                Toast.makeText(context,
                    context.getString(R.string.averia_export_success, fileName),
                    Toast.LENGTH_LONG).show()
                val uri: Uri = FileProvider.getUriForFile(
                    context, context.packageName + ".fileprovider", file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    Intent.createChooser(shareIntent,
                        context.getString(R.string.averia_export_share_title))
                )
            }
        } catch (t: Throwable) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context,
                    context.getString(R.string.averia_export_error),
                    Toast.LENGTH_LONG).show()
            }
        } finally {
            document.close()
        }
    }
}
