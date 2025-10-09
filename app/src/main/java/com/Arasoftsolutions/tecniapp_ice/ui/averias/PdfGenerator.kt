package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.core.content.ContextCompat
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

            val logoDrawable = ContextCompat.getDrawable(context, R.drawable.logo)
            logoDrawable?.let { drawable ->
                val logoBitmapSize = 256
                val logoBitmap = Bitmap.createBitmap(logoBitmapSize, logoBitmapSize, Bitmap.Config.ARGB_8888)
                val logoCanvas = android.graphics.Canvas(logoBitmap)
                drawable.setBounds(0, 0, logoBitmapSize, logoBitmapSize)
                drawable.draw(logoCanvas)
                val logoSize = 88f
                val logoRect = RectF(
                    headerRect.right - headerPadding - logoSize,
                    headerRect.top + headerPadding / 2f,
                    headerRect.right - headerPadding,
                    headerRect.top + headerPadding / 2f + logoSize
                )
                canvas.drawBitmap(logoBitmap, null, logoRect, null)
            }

            var textY = headerRect.top + headerPadding
            val textX = headerRect.left + headerPadding
            canvas.drawText(headerTitle, textX, textY, headerTitlePaint)
            textY += 36f
            canvas.drawText(headerSubtitle, textX, textY, headerSubtitlePaint)
            textY += 26f
            canvas.drawText(headerTagline, textX, textY, headerTagPaint)

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
            val attended = item.resolvedAtendidoDisplay(emptyValue)
            val vehicle = item.vehiculo ?: emptyValue
            val nise = item.nise.ifBlank { emptyValue }
            val description = item.descripcion.ifBlank { emptyValue }
            val client = item.cliente?.takeIf { it.isNotBlank() } ?: emptyValue
            val textualLocation = item.localizacion?.takeIf { it.isNotBlank() } ?: emptyValue
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
            val affectation = when (item.tipoAfectacion) {
                TipoAfectacion.CLIENTE -> context.getString(R.string.averia_tipo_cliente)
                TipoAfectacion.SECTOR -> context.getString(R.string.averia_tipo_sector)
            }
            val zoneTag = item.zonaTag.ifBlank { emptyValue }
            val medidorNumero = item.numeroMedidor?.takeIf { it.isNotBlank() } ?: emptyValue
            val medidorCalle = item.medidorCalle?.takeIf { it.isNotBlank() } ?: emptyValue
            val medidorPueblo = item.medidorPueblo?.takeIf { it.isNotBlank() } ?: emptyValue
            val medidorMetros = item.medidorMetros?.takeIf { it.isNotBlank() } ?: emptyValue
            val medidorPoste = item.medidorPoste?.takeIf { it.isNotBlank() } ?: emptyValue

            val tableData = listOf(
                context.getString(R.string.averia_pdf_table_label_case) to item.id,
                context.getString(R.string.averia_pdf_table_label_description) to description,
                context.getString(R.string.averia_pdf_table_label_nise) to nise,
                context.getString(R.string.averia_pdf_table_label_status) to item.estado,
                context.getString(R.string.averia_pdf_table_label_assigned) to assigned,
                context.getString(R.string.averia_pdf_table_label_attended_by) to attended,
                context.getString(R.string.averia_pdf_table_label_vehicle) to vehicle,
                context.getString(R.string.averia_pdf_table_label_client) to client,
                context.getString(R.string.averia_pdf_table_label_location_text) to textualLocation,
                context.getString(R.string.averia_pdf_table_label_event_date) to (fechaEvento ?: emptyValue),
                context.getString(R.string.averia_pdf_table_label_start_time) to (inicioAtencion ?: emptyValue),
                context.getString(R.string.averia_pdf_table_label_end_time) to (finAtencion ?: emptyValue),
                context.getString(R.string.averia_pdf_table_label_kilometers) to kilometraje,
                context.getString(R.string.averia_pdf_table_label_region) to region,
                context.getString(R.string.averia_pdf_table_label_agency) to agency,
                context.getString(R.string.averia_pdf_table_label_affectation) to affectation,
                context.getString(R.string.averia_pdf_table_label_zone) to zoneTag,
                context.getString(R.string.averia_pdf_table_label_medidor) to medidorNumero,
                context.getString(R.string.averia_pdf_table_label_street) to medidorCalle,
                context.getString(R.string.averia_pdf_table_label_town) to medidorPueblo,
                context.getString(R.string.averia_pdf_table_label_meters) to medidorMetros,
                context.getString(R.string.averia_pdf_table_label_post) to medidorPoste,
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

            var currentY = tableTop + tableHeight + 28f
            val innerWidth = contentWidth - 40f

            fun wrapParagraphs(text: String, availableWidth: Float): List<String> {
                if (text.isBlank()) return listOf("")
                val result = mutableListOf<String>()
                val paragraphs = text.split('\n')
                paragraphs.forEach { paragraph ->
                    val trimmed = paragraph.trim()
                    if (trimmed.isEmpty()) {
                        result += ""
                    } else {
                        var remaining = trimmed
                        while (remaining.isNotEmpty()) {
                            var count = valuePaint.breakText(remaining, true, availableWidth, null)
                            if (count <= 0) break
                            if (count < remaining.length) {
                                val lastSpace = remaining.substring(0, count).lastIndexOf(' ')
                                if (lastSpace > 0) {
                                    count = lastSpace + 1
                                }
                            }
                            val segment = remaining.substring(0, count).trim()
                            result += segment
                            remaining = remaining.substring(count).trimStart()
                        }
                    }
                }
                return result.ifEmpty { listOf("") }
            }

            fun buildCardLines(entries: List<String>, bullet: Boolean = false): List<String> {
                if (entries.isEmpty()) return if (bullet) listOf("• $emptyValue") else listOf(emptyValue)
                val bulletPrefix = "• "
                val indentPrefix = "  "
                val indentWidth = valuePaint.measureText(bulletPrefix)
                val lines = mutableListOf<String>()
                var hasContent = false
                entries.forEach { entry ->
                    val paragraphs = wrapParagraphs(entry, if (bullet) innerWidth - indentWidth else innerWidth)
                    if (paragraphs.size == 1 && paragraphs.first().isBlank()) {
                        val placeholder = if (bullet) "$bulletPrefix$emptyValue" else emptyValue
                        lines += placeholder
                    } else {
                        paragraphs.forEachIndexed { index, paragraph ->
                            val display = paragraph.ifBlank { emptyValue }
                            if (bullet) {
                                val prefix = if (index == 0) bulletPrefix else indentPrefix
                                lines += prefix + display
                            } else {
                                lines += display
                            }
                            if (paragraph.isNotBlank()) {
                                hasContent = true
                            }
                        }
                    }
                }
                if (!hasContent && lines.isEmpty()) {
                    return if (bullet) listOf("$bulletPrefix$emptyValue") else listOf(emptyValue)
                }
                return lines
            }

            val cardTitlePaint = Paint(labelPaint).apply {
                textSize = 14f
            }

            fun drawCard(title: String, entries: List<String>, bullet: Boolean = false) {
                val lines = buildCardLines(entries, bullet)
                val boxPadding = 20f
                val lineSpacing = valuePaint.textSize + 8f
                val gapAfterTitle = 16f
                val boxHeight = boxPadding * 2 + cardTitlePaint.textSize + gapAfterTitle + (lines.size * lineSpacing)
                val boxRect = RectF(margin, currentY, margin + contentWidth, currentY + boxHeight)
                val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFFFFF") }
                val boxShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#E3E7FF")
                    strokeWidth = 3f
                    style = Paint.Style.STROKE
                }

                canvas.drawRoundRect(boxRect, 26f, 26f, boxPaint)
                canvas.drawRoundRect(boxRect, 26f, 26f, boxShadowPaint)

                val titleBaseline = boxRect.top + boxPadding + cardTitlePaint.textSize
                canvas.drawText(title, boxRect.left + boxPadding, titleBaseline, cardTitlePaint)

                var lineY = titleBaseline + gapAfterTitle
                lines.forEach { line ->
                    canvas.drawText(line, boxRect.left + boxPadding, lineY, valuePaint)
                    lineY += lineSpacing
                }
                currentY += boxHeight + 16f
            }

            drawCard(
                context.getString(R.string.averia_pdf_section_description),
                listOf(description)
            )
            drawCard(
                context.getString(R.string.averia_pdf_section_cause),
                listOf(item.causa.ifBlank { emptyValue })
            )
            drawCard(
                context.getString(R.string.averia_pdf_section_notes),
                listOf(item.observaciones.ifBlank { emptyValue })
            )
            val medidorDetalle = buildList {
                if (item.numeroMedidor?.isNotBlank() == true) {
                    add(context.getString(R.string.averia_medidor_label, item.numeroMedidor))
                }
                if (item.medidorCalle?.isNotBlank() == true) {
                    add("${context.getString(R.string.medidor_calle_label)}: ${item.medidorCalle}")
                }
                if (item.medidorPueblo?.isNotBlank() == true) {
                    add("${context.getString(R.string.medidor_pueblo_label)}: ${item.medidorPueblo}")
                }
                if (item.medidorMetros?.isNotBlank() == true) {
                    add("${context.getString(R.string.medidor_metros_label)}: ${item.medidorMetros}")
                }
                if (item.medidorPoste?.isNotBlank() == true) {
                    add("${context.getString(R.string.medidor_poste_label)}: ${item.medidorPoste}")
                }
            }
            drawCard(
                context.getString(R.string.averia_pdf_section_medidor),
                medidorDetalle,
                bullet = true
            )

            val materialesLines = when {
                item.materialesDetalle.any { it.cantidad > 0 } -> item.materialesDetalle
                    .filter { it.cantidad > 0 }
                    .map { uso ->
                        val descripcionMaterial = uso.descripcion.ifBlank { uso.codigo }
                        context.getString(
                            R.string.averia_pdf_material_line,
                            descripcionMaterial,
                            uso.cantidad,
                            uso.codigo
                        )
                    }
                item.materialesResumen.isNotBlank() -> item.materialesResumen
                    .split("[\\n;,]".toRegex())
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .ifEmpty { listOf(item.materialesResumen.trim()) }
                else -> listOf(context.getString(R.string.averia_pdf_no_materials))
            }
            drawCard(
                context.getString(R.string.averia_pdf_section_materials),
                materialesLines,
                bullet = true
            )

            val tecnicosLinesFromList = item.tecnicosAtendieron.mapNotNull { tecnico ->
                val nombre = tecnico.nombre.trim()
                val cedula = tecnico.cedula.trim()
                when {
                    nombre.isNotBlank() && cedula.isNotBlank() ->
                        context.getString(R.string.averia_pdf_technician_line, nombre, cedula)
                    nombre.isNotBlank() -> nombre
                    cedula.isNotBlank() -> cedula
                    else -> null
                }
            }
            val tecnicosLines = if (tecnicosLinesFromList.isNotEmpty()) {
                tecnicosLinesFromList
            } else {
                val attendedFallback = item.resolvedAtendidoLines(emptyValue)
                    .filter { it.isNotBlank() && it != emptyValue }
                if (attendedFallback.isNotEmpty()) attendedFallback
                else listOf(context.getString(R.string.averia_pdf_no_technicians))
            }
            drawCard(
                context.getString(R.string.averia_pdf_section_technicians),
                tecnicosLines,
                bullet = true
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
