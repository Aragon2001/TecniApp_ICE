package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.Arasoftsolutions.tecniapp_ice.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfGenerator {
    private const val PAGE_WIDTH = 595   // A4
    private const val PAGE_HEIGHT = 842

    // 🔹 Variables globales (para multipágina)
    private lateinit var document: PdfDocument
    private lateinit var page: PdfDocument.Page
    private lateinit var canvas: Canvas
    private var pageNumber = 0
    private var currentY = 0f




    // -------------------------------------------------------------
    //  🔧 Helper: Crear nueva página con encabezado
    // -------------------------------------------------------------
    private fun startPage(context: Context) {
        if (this::page.isInitialized) document.finishPage(page)

        pageNumber++
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        page = document.startPage(pageInfo)
        canvas = page.canvas
        currentY = drawHeader(context, canvas, 40f)
    }

    // -------------------------------------------------------------
    //  🔧 Helper: Asegurar espacio disponible
    // -------------------------------------------------------------
    private fun ensureSpace(context: Context, neededHeight: Float) {
        val bottomMargin = 80f
        val maxY = PAGE_HEIGHT - bottomMargin - 60f
        if (currentY + neededHeight > maxY) {
            startPage(context)
        }
    }

    // -------------------------------------------------------------
    //  🧾 Dibujar encabezado
    // -------------------------------------------------------------
    private fun drawHeader(context: Context, canvas: Canvas, margin: Float): Float {
        val headerHeight = 150f
        val headerRect = RectF(margin, margin, PAGE_WIDTH - margin, margin + headerHeight)
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                headerRect.left, headerRect.top,
                headerRect.right, headerRect.bottom,
                Color.parseColor("#2E3192"),
                Color.parseColor("#1BFFFF"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(headerRect, 28f, 28f, headerPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 32f
            color = Color.WHITE
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16f
            color = Color.WHITE
        }

        val title = context.getString(R.string.averia_pdf_header_title)
        val subtitle = context.getString(R.string.averia_pdf_header_subtitle)

        val textX = headerRect.left + 32f
        var textY = headerRect.top + 48f
        canvas.drawText(title, textX, textY, titlePaint)
        textY += 28f
        canvas.drawText(subtitle, textX, textY, subtitlePaint)

        val logoDrawable = ContextCompat.getDrawable(context, R.drawable.logo)
        logoDrawable?.let { drawable ->
            val logoBitmapSize = 256
            val logoBitmap = Bitmap.createBitmap(logoBitmapSize, logoBitmapSize, Bitmap.Config.ARGB_8888)
            val logoCanvas = Canvas(logoBitmap)
            drawable.setBounds(0, 0, logoBitmapSize, logoBitmapSize)
            drawable.draw(logoCanvas)
            val logoSize = 88f
            val logoRect = RectF(
                headerRect.right - 32f - logoSize,
                headerRect.top + 16f,
                headerRect.right - 32f,
                headerRect.top + 16f + logoSize
            )
            canvas.drawBitmap(logoBitmap, null, logoRect, null)
        }

        return headerRect.bottom + 40f
    }
    suspend fun exportAveria(context: Context, item: AveriaUI) = withContext(Dispatchers.IO) {
        document = PdfDocument()
        try {
            val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            fun formatMillis(millis: Long?) =
                millis?.takeIf { it > 0 }?.let { formatter.format(Date(it)) }

            val reporteGenerado = formatter.format(Date())
            val fechaEvento = formatMillis(item.fechaMillis)
            val inicioAtencion = formatMillis(item.horaAtencionInicio)
            val finAtencion = formatMillis(item.horaAtencionFinal)

            val margin = 40f
            val contentWidth = PAGE_WIDTH - (margin * 2)

            // 🔹 Inicia primera página
            startPage(context)

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

            val emptyValue = context.getString(R.string.averia_pdf_empty_value)
            val observaciones = item.observaciones.ifBlank { emptyValue }
            val attended = item.resolvedAtendidoDisplay(emptyValue)
            val client = item.cliente?.takeIf { it.isNotBlank() } ?: emptyValue
            val textualLocation = item.localizacion?.takeIf { it.isNotBlank() } ?: emptyValue
            val location = if (item.lat == 0.0 && item.lng == 0.0)
                context.getString(R.string.averia_pdf_location_no_data)
            else
                context.getString(R.string.averia_pdf_location_value, item.lat, item.lng)

            // ---------- Tabla general ----------
            val tableData = listOf(
                context.getString(R.string.averia_pdf_table_label_description) to observaciones,
                context.getString(R.string.averia_pdf_table_label_client) to client,
                context.getString(R.string.averia_pdf_table_label_location_text) to textualLocation,
                context.getString(R.string.averia_pdf_table_label_location) to location,
                context.getString(R.string.averia_pdf_table_label_attended_by) to attended,
                context.getString(R.string.averia_pdf_table_label_generated) to reporteGenerado
            )

            val tableTop = currentY + 18f
            val tableRowHeight = 52f
            val contentHeight = tableRowHeight * tableData.size + 60f
            ensureSpace(context, contentHeight)

            drawTable(canvas, context, tableData, margin, contentWidth, tableTop, labelPaint, valuePaint)
            currentY = tableTop + contentHeight

            // ---------- Sección materiales ----------
            ensureSpace(context, 200f)
            val materialesLines = when {
                item.materialesDetalle.any { it.cantidad > 0 } -> item.materialesDetalle
                    .filter { it.cantidad > 0 }
                    .map { m ->
                        context.getString(
                            R.string.averia_pdf_material_line,
                            m.descripcion.ifBlank { m.codigo },
                            m.cantidad,
                            m.codigo
                        )
                    }
                item.materialesResumen.isNotBlank() -> item.materialesResumen.split("[\\n;,]".toRegex())
                    .map { it.trim() }.filter { it.isNotEmpty() }
                else -> listOf(context.getString(R.string.averia_pdf_no_materials))
            }

            drawCard(context, canvas, "Materiales usados", materialesLines, margin, contentWidth, valuePaint)

            // ---------- Sección técnicos ----------
            ensureSpace(context, 200f)
            val tecnicosLines = item.tecnicosAtendieron.mapNotNull {
                val nombre = it.nombre.trim()
                val cedula = it.cedula.trim()
                when {
                    nombre.isNotBlank() && cedula.isNotBlank() ->
                        "${nombre} - ${cedula}"
                    nombre.isNotBlank() -> nombre
                    cedula.isNotBlank() -> cedula
                    else -> null
                }
            }.ifEmpty {
                listOf(context.getString(R.string.averia_pdf_no_technicians))
            }

            drawCard(context, canvas, "Técnicos que atendieron", tecnicosLines, margin, contentWidth, valuePaint)

            // ---------- Footer ----------
            ensureSpace(context, 100f)
            drawFooter(canvas, context, margin, currentY)
            currentY += 100f

            // 🔹 Finaliza última página
            if (this::page.isInitialized) {
                document.finishPage(page)
            }


            // Guardar
            val reportsDir = File(context.getExternalFilesDir(null), "TecniApp/Reportes")
            if (!reportsDir.exists()) reportsDir.mkdirs()
            val fileNameFormatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val fileName = "averia_${item.id}_${fileNameFormatter.format(Date())}.pdf"
            val file = File(reportsDir, fileName)
            FileOutputStream(file).use { output -> document.writeTo(output) }

            // Compartir
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "PDF generado correctamente: $fileName", Toast.LENGTH_LONG).show()
                val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Compartir PDF"))
            }
        } catch (t: Throwable) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.averia_export_error), Toast.LENGTH_LONG).show()
            }
        } finally {
            document.close()
        }
    }
    // ---------- TABLA ----------
    private fun drawTable(
        canvas: Canvas,
        context: Context,
        tableData: List<Pair<String, String>>,
        margin: Float,
        contentWidth: Float,
        tableTop: Float,
        labelPaint: Paint,
        valuePaint: Paint
    ) {
        val tableLeft = margin
        val tableRight = margin + contentWidth
        val rowHeight = 52f
        val columnDivider = tableLeft + (contentWidth / 2f)
        var rowTop = tableTop

        val rowEvenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFFFFF") }
        val rowOddPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EEF2FF") }

        tableData.chunked(2).forEachIndexed { index, rowItems ->
            val rowBottom = rowTop + rowHeight
            val rowPaint = if (index % 2 == 0) rowEvenPaint else rowOddPaint
            val rowRect = RectF(tableLeft, rowTop, tableRight, rowBottom)
            canvas.drawRect(rowRect, rowPaint)

            rowItems.forEachIndexed { col, (label, value) ->
                val cellLeft = if (col == 0) tableLeft else columnDivider
                val labelY = rowTop + 18f
                val valueY = labelY + 20f
                canvas.drawText(label.uppercase(Locale.getDefault()), cellLeft + 10f, labelY, labelPaint)
                canvas.drawText(value, cellLeft + 10f, valueY, valuePaint)
            }
            rowTop = rowBottom
        }
    }

    // ---------- CARD ----------
    private fun drawCard(
        context: Context,
        canvas: Canvas,
        title: String,
        lines: List<String>,
        margin: Float,
        contentWidth: Float,
        valuePaint: Paint
    ) {
        val boxPadding = 20f
        val lineSpacing = valuePaint.textSize + 6f
        val boxHeight = boxPadding * 2 + 18f + (lines.size * lineSpacing)
        val boxRect = RectF(margin, currentY, margin + contentWidth, currentY + boxHeight)
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E3E7FF")
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }

        canvas.drawRoundRect(boxRect, 26f, 26f, boxPaint)
        canvas.drawRoundRect(boxRect, 26f, 26f, borderPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2E3192")
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 14f
        }
        var lineY = boxRect.top + boxPadding + 18f
        canvas.drawText(title, boxRect.left + boxPadding, lineY, titlePaint)
        lineY += 12f
        lines.forEach {
            lineY += lineSpacing
            canvas.drawText(it, boxRect.left + boxPadding, lineY, valuePaint)
        }
        currentY += boxHeight + 20f
    }

    // ---------- FOOTER ----------
    private fun drawFooter(canvas: Canvas, context: Context, margin: Float, currentY: Float) {
        val footerRect = RectF(margin, currentY, PAGE_WIDTH - margin, currentY + 62f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2E3192") }
        canvas.drawRoundRect(footerRect, 24f, 24f, paint)

        val textBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 16f
        }
        val textSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 12f
        }

        canvas.drawText(context.getString(R.string.averia_pdf_footer_title), footerRect.left + 24f, footerRect.top + 24f, textBold)
        canvas.drawText(context.getString(R.string.averia_pdf_footer_content), footerRect.left + 24f, footerRect.top + 42f, textSmall)
    }
}
