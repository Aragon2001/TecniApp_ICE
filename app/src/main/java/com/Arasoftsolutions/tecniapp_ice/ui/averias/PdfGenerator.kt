package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.Arasoftsolutions.tecniapp_ice.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

object PdfGenerator {
    private const val PAGE_WIDTH = 595 // A4
    private const val PAGE_HEIGHT = 842
    private const val PAGE_MARGIN = 36f
    private const val FOOTER_HEIGHT = 64f

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
            val now = Date()
            val dateTimeFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val yearFormatter = SimpleDateFormat("yyyy", Locale.getDefault())
            val shortTimeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

            fun formatMillis(millis: Long?, fallback: String): String =
                millis?.takeIf { it > 0 }
                    ?.let { dateTimeFormatter.format(Date(it)) }
                    ?: fallback

            val generatedAt = dateTimeFormatter.format(now)
            val currentYear = yearFormatter.format(now)

            val headerLogos = loadLogos(context)
            val style = PdfStyle(context)

            val bottomSpace = PAGE_MARGIN + FOOTER_HEIGHT
            val state = PageState(
                document = document,
                bottomMargin = bottomSpace,
                onPageStarted = { canvas, pageNumber ->
                    drawHeader(
                        context = context,
                        canvas = canvas,
                        style = style,
                        logos = headerLogos,
                        item = item,
                        pageNumber = pageNumber,
                        generatedAt = generatedAt
                    )
                },
                onPageFinished = { canvas, pageNumber ->
                    drawFooter(context, canvas, style, pageNumber, currentYear)
                }
            )

            // region Summary chips
            state.startPage()
            drawSummaryChips(state, style, item)
            state.currentY += 12f
            // endregion

            val emptyValue = context.getString(R.string.averia_pdf_empty_value)
            val assigned = item.tecnico.ifBlank { context.getString(R.string.averia_sin_asignar) }
            val attended = item.resolvedAtendidoDisplay(emptyValue)
            val vehicle = item.vehiculo ?: emptyValue
            val nise = item.nise.ifBlank { emptyValue }
            val description = item.descripcion.ifBlank { emptyValue }
            val observations = item.observaciones.ifBlank { emptyValue }
            val client = item.cliente?.takeIf { it.isNotBlank() } ?: emptyValue
            val textualLocation = item.localizacion?.takeIf { it.isNotBlank() } ?: emptyValue
            val kilometraje = if (item.kilometrajeInicio != null || item.kilometrajeFinal != null) {
                val inicio = item.kilometrajeInicio?.toString() ?: emptyValue
                val fin = item.kilometrajeFinal?.toString() ?: emptyValue
                context.getString(R.string.averia_pdf_kilometers_value, inicio, fin)
            } else {
                emptyValue
            }
            val eventDate = formatMillis(item.fechaMillis, emptyValue)
            val startAttention = formatMillis(item.horaAtencionInicio, emptyValue)
            val endAttention = formatMillis(item.horaAtencionFinal, emptyValue)
            val region = item.region.ifBlank { emptyValue }
            val agency = item.agencia.ifBlank { emptyValue }
            val zoneTag = item.zonaTag.ifBlank { emptyValue }
            val affectation = when (item.tipoAfectacion) {
                TipoAfectacion.CLIENTE -> context.getString(R.string.averia_tipo_cliente)
                TipoAfectacion.SECTOR -> context.getString(R.string.averia_tipo_sector)
            }
            val medidorNumero = item.numeroMedidor?.takeIf { it.isNotBlank() } ?: emptyValue
            val medidorCalle = item.medidorCalle?.takeIf { it.isNotBlank() } ?: emptyValue
            val medidorPueblo = item.medidorPueblo?.takeIf { it.isNotBlank() } ?: emptyValue
            val medidorMetros = item.medidorMetros?.takeIf { it.isNotBlank() } ?: emptyValue
            val medidorPoste = item.medidorPoste?.takeIf { it.isNotBlank() } ?: emptyValue
            val location = if (item.lat == 0.0 && item.lng == 0.0) {
                context.getString(R.string.averia_pdf_location_no_data)
            } else {
                context.getString(R.string.averia_pdf_location_value, item.lat, item.lng)
            }

            val infoRows = listOf(
                InfoRow(context.getString(R.string.averia_pdf_table_label_case), item.id),
                InfoRow(context.getString(R.string.averia_pdf_table_label_status), item.estado),
                InfoRow(context.getString(R.string.averia_pdf_table_label_assigned), assigned),
                InfoRow(context.getString(R.string.averia_pdf_table_label_attended_by), attended),
                InfoRow(context.getString(R.string.averia_pdf_table_label_vehicle), vehicle),
                InfoRow(context.getString(R.string.averia_pdf_table_label_client), client),
                InfoRow(context.getString(R.string.averia_pdf_table_label_nise), nise),
                InfoRow(context.getString(R.string.averia_pdf_table_label_zone), zoneTag),
                InfoRow(context.getString(R.string.averia_pdf_table_label_region), region),
                InfoRow(context.getString(R.string.averia_pdf_table_label_agency), agency),
                InfoRow(context.getString(R.string.averia_pdf_table_label_location_text), textualLocation),
                InfoRow(context.getString(R.string.averia_pdf_table_label_affectation), affectation),
                InfoRow(context.getString(R.string.averia_pdf_table_label_event_date), eventDate),
                InfoRow(context.getString(R.string.averia_pdf_table_label_start_time), startAttention),
                InfoRow(context.getString(R.string.averia_pdf_table_label_end_time), endAttention),
                InfoRow(context.getString(R.string.averia_pdf_table_label_kilometers), kilometraje),
                InfoRow(context.getString(R.string.averia_pdf_table_label_location), location),
                InfoRow(context.getString(R.string.averia_pdf_table_label_generated), generatedAt),
                InfoRow(context.getString(R.string.averia_pdf_table_label_medidor), medidorNumero),
                InfoRow(context.getString(R.string.averia_pdf_table_label_street), medidorCalle),
                InfoRow(context.getString(R.string.averia_pdf_table_label_town), medidorPueblo),
                InfoRow(context.getString(R.string.averia_pdf_table_label_meters), medidorMetros),
                InfoRow(context.getString(R.string.averia_pdf_table_label_post), medidorPoste)
            )

            drawInfoGrid(state, style, infoRows)
            state.currentY += 12f

            drawTimeline(
                context = context,
                state = state,
                style = style,
                items = listOf(
                    TimelineEntry(
                        title = context.getString(R.string.averia_pdf_table_label_event_date),
                        date = eventDate,
                        time = item.fechaMillis.takeIf { it > 0 }?.let { shortTimeFormatter.format(Date(it)) } ?: "--"
                    ),
                    TimelineEntry(
                        title = context.getString(R.string.averia_pdf_table_label_start_time),
                        date = startAttention,
                        time = item.horaAtencionInicio?.takeIf { it > 0 }
                            ?.let { shortTimeFormatter.format(Date(it)) }
                            ?: "--"
                    ),
                    TimelineEntry(
                        title = context.getString(R.string.averia_pdf_table_label_end_time),
                        date = endAttention,
                        time = item.horaAtencionFinal?.takeIf { it > 0 }
                            ?.let { shortTimeFormatter.format(Date(it)) }
                            ?: "--"
                    )
                )
            )

            val cards = listOf(
                DetailCard(
                    title = context.getString(R.string.averia_pdf_section_description),
                    content = listOf(description)
                ),
                DetailCard(
                    title = context.getString(R.string.averia_pdf_section_cause),
                    content = listOf(item.causa.ifBlank { emptyValue })
                ),
                DetailCard(
                    title = context.getString(R.string.averia_pdf_section_notes),
                    content = listOf(observations)
                )
            )

            cards.forEach { card ->
                drawDetailCard(context, state, style, card)
            }

            val medidorLines = buildList {
                if (item.numeroMedidor?.isNotBlank() == true) {
                    add(context.getString(R.string.averia_medidor_label, item.numeroMedidor))
                }
                if (item.medidorCalle?.isNotBlank() == true) {
                    add(context.getString(R.string.averia_medidor_calle, item.medidorCalle))
                }
                if (item.medidorPueblo?.isNotBlank() == true) {
                    add(context.getString(R.string.averia_medidor_pueblo, item.medidorPueblo))
                }
                if (item.medidorMetros?.isNotBlank() == true) {
                    add(context.getString(R.string.averia_medidor_metros, item.medidorMetros))
                }
                if (item.medidorPoste?.isNotBlank() == true) {
                    add(context.getString(R.string.averia_medidor_poste, item.medidorPoste))
                }
            }.ifEmpty {
                listOf(context.getString(R.string.averia_pdf_no_meter_details))
            }

            drawDetailCard(
                context,
                state,
                style,
                DetailCard(
                    title = context.getString(R.string.averia_pdf_section_medidor),
                    content = medidorLines,
                    bullet = true
                )
            )

            val materialLines = when {
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

            drawDetailCard(
                context,
                state,
                style,
                DetailCard(
                    title = context.getString(R.string.averia_pdf_section_materials),
                    content = materialLines,
                    bullet = true
                )
            )

            val techniciansLines = item.tecnicosAtendieron.mapNotNull { tecnico ->
                val nombre = tecnico.nombre.trim()
                val cedula = tecnico.cedula.trim()
                when {
                    nombre.isNotBlank() && cedula.isNotBlank() ->
                        "${nombre} - ${cedula}"
                    nombre.isNotBlank() -> nombre
                    cedula.isNotBlank() -> cedula
                    else -> null
                }
            }.ifEmpty {
                val fallback = item.resolvedAtendidoLines(emptyValue)
                    .filter { it.isNotBlank() && it != emptyValue }
                if (fallback.isNotEmpty()) fallback else listOf(context.getString(R.string.averia_pdf_no_technicians))
            }

            drawDetailCard(
                context,
                state,
                style,
                DetailCard(
                    title = context.getString(R.string.averia_pdf_section_technicians),
                    content = techniciansLines,
                    bullet = true
                )
            )
            canvas.drawText(
                context.getString(R.string.averia_pdf_footer_copyright, currentYear),
                footerRect.left + 24f,
                footerRect.top + 60f,
                footerTextPaint
            )

            state.finish()

            val parentDir = context.getExternalFilesDir(null) ?: context.filesDir
            val reportsDir = File(parentDir, "TecniApp/Reportes")
            if (!reportsDir.exists()) reportsDir.mkdirs()
            val fileNameFormatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val fileName = "averia_${item.id}_${fileNameFormatter.format(now)}.pdf"
            val file = File(reportsDir, fileName)
            FileOutputStream(file).use { output -> document.writeTo(output) }

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.getString(R.string.averia_export_success, fileName),
                    Toast.LENGTH_LONG
                ).show()
                val uri: Uri = FileProvider.getUriForFile(
                    context,
                    context.packageName + ".fileprovider",
                    file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    Intent.createChooser(
                        shareIntent,
                        context.getString(R.string.averia_export_share_title)
                    )
                )
            }
        } catch (t: Throwable) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.getString(R.string.averia_export_error),
                    Toast.LENGTH_LONG
                ).show()
            }
        } finally {
            document.close()
        }
    }

    private fun loadLogos(context: Context): List<Bitmap> {
        val desiredSize = 120
        val resIds = listOfNotNull(
            R.drawable.logo,
            R.drawable.ice
        )
        return resIds.mapNotNull { resId ->
            val drawable = ContextCompat.getDrawable(context, resId) ?: return@mapNotNull null
            val bitmap = Bitmap.createBitmap(desiredSize, desiredSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, desiredSize, desiredSize)
            drawable.draw(canvas)
            bitmap
        }
    }

    private data class PdfStyle(
        val titlePaint: Paint,
        val bodyPaint: Paint,
        val labelPaint: Paint,
        val chipTextPaint: Paint,
        val chipBackgroundPaint: Paint,
        val chipAccentPaint: Paint,
        val cardBackgroundPaint: Paint,
        val cardBorderPaint: Paint,
        val timelinePaint: Paint,
        val timelineLabelPaint: Paint,
        val footerTextPaint: Paint
    )

    private fun PdfStyle(context: Context): PdfStyle {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 16f
            color = Color.parseColor("#1D2A44")
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f
            color = Color.parseColor("#1D2A44")
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 10.5f
            color = Color.parseColor("#536DFE")
        }
        val chipText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 11.5f
            color = Color.WHITE
        }
        val chipBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#5C6CFF")
        }
        val chipAccent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#36D1DC")
        }
        val cardBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }
        val cardBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0E7FF")
            strokeWidth = 2.5f
            style = Paint.Style.STROKE
        }
        val timeline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#5C6CFF")
            strokeWidth = 3f
        }
        val timelineLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            color = Color.parseColor("#1D2A44")
        }
        val footerText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            color = Color.WHITE
        }
        return PdfStyle(
            titlePaint = titlePaint,
            bodyPaint = bodyPaint,
            labelPaint = labelPaint,
            chipTextPaint = chipText,
            chipBackgroundPaint = chipBackground,
            chipAccentPaint = chipAccent,
            cardBackgroundPaint = cardBackground,
            cardBorderPaint = cardBorder,
            timelinePaint = timeline,
            timelineLabelPaint = timelineLabel,
            footerTextPaint = footerText
        )
    }

    private class PageState(
        private val document: PdfDocument,
        private val bottomMargin: Float,
        private val onPageStarted: (Canvas, Int) -> Float,
        private val onPageFinished: (Canvas, Int) -> Unit
    ) {
        var currentY: Float = 0f
            internal set
        private var pageNumber = 0
        private lateinit var page: PdfDocument.Page
        lateinit var canvas: Canvas
            private set

        fun startPage() {
            finishCurrentPage()
            pageNumber++
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            currentY = onPageStarted(canvas, pageNumber)
        }

        fun ensureSpace(requiredHeight: Float, onPageBreak: (() -> Unit)? = null) {
            val usableBottom = PAGE_HEIGHT - bottomMargin
            if (!::page.isInitialized) {
                startPage()
            }
            if (currentY + requiredHeight > usableBottom) {
                startPage()
                onPageBreak?.invoke()
            }
        }

        private fun ensurePage() {
            if (!::page.isInitialized) {
                startPage()
            }
        }

        fun finish() {
            finishCurrentPage()
        }

        private fun finishCurrentPage() {
            if (::page.isInitialized) {
                onPageFinished(canvas, pageNumber)
                document.finishPage(page)
            }
        }
    }

    private fun drawHeader(
        context: Context,
        canvas: Canvas,
        style: PdfStyle,
        logos: List<Bitmap>,
        item: AveriaUI,
        pageNumber: Int,
        generatedAt: String
    ): Float {
        val headerHeight = 150f
        val rect = RectF(
            PAGE_MARGIN,
            PAGE_MARGIN,
            PAGE_WIDTH - PAGE_MARGIN,
            PAGE_MARGIN + headerHeight
        )
        val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                Color.parseColor("#2E3192"),
                Color.parseColor("#36D1DC"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, 24f, 24f, gradientPaint)

        val headerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 30f
            color = Color.WHITE
        }
        val headerSubtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14f
            color = Color.WHITE
        }
        val headerMetaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            color = Color.WHITE
            alpha = 220
        }

        val headerTitle = context.getString(R.string.averia_pdf_header_title)
        val headerSubtitle = context.getString(R.string.averia_pdf_header_subtitle)
        val headerTagline = context.getString(R.string.averia_pdf_header_tagline)

        var textY = rect.top + 42f
        val textX = rect.left + 32f
        canvas.drawText(headerTitle, textX, textY, headerTitlePaint)
        textY += 30f
        canvas.drawText(headerSubtitle, textX, textY, headerSubtitlePaint)
        textY += 22f
        canvas.drawText(headerTagline, textX, textY, headerMetaPaint)
        textY += 18f
        val caseLabel = context.getString(R.string.averia_pdf_table_label_case)
        canvas.drawText("$caseLabel: ${item.id}", textX, textY, headerMetaPaint)
        textY += 16f
        canvas.drawText(
            context.getString(R.string.averia_pdf_table_label_generated) + ": " + generatedAt,
            textX,
            textY,
            headerMetaPaint
        )

        if (logos.isNotEmpty()) {
            val logoSize = 84f
            val spacing = 12f
            val totalWidth = (logoSize * logos.size) + spacing * (logos.size - 1)
            var left = rect.right - 32f - totalWidth
            val top = rect.top + 32f
            logos.forEach { bitmap ->
                val dest = RectF(left, top, left + logoSize, top + logoSize)
                canvas.drawBitmap(bitmap, null, dest, null)
                left += logoSize + spacing
            }
        }

        canvas.drawText("Página $pageNumber", rect.right - 120f, rect.bottom - 18f, headerMetaPaint)

        return rect.bottom + 28f
    }

    private fun drawFooter(
        context: Context,
        canvas: Canvas,
        style: PdfStyle,
        pageNumber: Int,
        currentYear: String
    ) {
        val footerRect = RectF(
            PAGE_MARGIN,
            PAGE_HEIGHT - FOOTER_HEIGHT - PAGE_MARGIN / 2f,
            PAGE_WIDTH - PAGE_MARGIN,
            PAGE_HEIGHT - PAGE_MARGIN / 2f
        )
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                footerRect.left,
                footerRect.top,
                footerRect.right,
                footerRect.bottom,
                Color.parseColor("#2E3192"),
                Color.parseColor("#0B8E9A"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(footerRect, 18f, 18f, footerPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 13f
            color = Color.WHITE
        }
        val bodyPaint = style.footerTextPaint
        val line1 = context.getString(R.string.averia_pdf_footer_title)
        val line2 = context.getString(R.string.averia_pdf_footer_content)
        val line3 = context.getString(R.string.averia_pdf_footer_copyright, currentYear)
        var textY = footerRect.top + 20f
        val textX = footerRect.left + 24f
        canvas.drawText(line1, textX, textY, titlePaint)
        textY += 16f
        canvas.drawText(line2, textX, textY, bodyPaint)
        textY += 16f
        canvas.drawText(line3, textX, textY, bodyPaint)

        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            color = Color.WHITE
            alpha = 200
        }
        val metaText = "TecniApp ICE · Página $pageNumber"
        canvas.drawText(metaText, footerRect.right - metaPaint.measureText(metaText) - 24f, footerRect.bottom - 14f, metaPaint)
    }

    private fun drawSummaryChips(state: PageState, style: PdfStyle, item: AveriaUI) {
        val chips = listOf(
            Chip(text = "${item.estado}", isPrimary = true),
            Chip(text = item.zonaTag.ifBlank { "—" }),
            Chip(text = item.region.ifBlank { "—" }),
            Chip(text = item.agencia.ifBlank { "—" }),
            Chip(text = item.numeroMedidor?.takeIf { it.isNotBlank() } ?: "Medidor —")
        )
        val chipHeight = 28f
        val horizontalSpacing = 12f
        val verticalSpacing = 10f
        val maxWidth = PAGE_WIDTH - PAGE_MARGIN * 2
        var x = PAGE_MARGIN
        var y = state.currentY

        chips.forEach { chip ->
            val textPaint = style.chipTextPaint
            val textWidth = textPaint.measureText(chip.text)
            val chipWidth = textWidth + 34f
            if (x + chipWidth > PAGE_MARGIN + maxWidth) {
                x = PAGE_MARGIN
                y += chipHeight + verticalSpacing
            }
            val rect = RectF(x, y, x + chipWidth, y + chipHeight)
            val paint = if (chip.isPrimary) style.chipBackgroundPaint else style.chipAccentPaint
            state.canvas.drawRoundRect(rect, chipHeight / 2f, chipHeight / 2f, paint)
            val textY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
            state.canvas.drawText(chip.text, rect.left + 17f, textY, textPaint)
            x += chipWidth + horizontalSpacing
        }

        state.currentY = y + chipHeight + verticalSpacing
    }

    private data class Chip(val text: String, val isPrimary: Boolean = false)

    private data class InfoRow(val label: String, val value: String)

    private fun drawInfoGrid(state: PageState, style: PdfStyle, rows: List<InfoRow>) {
        val columns = 2
        val horizontalGap = 16f
        val verticalGap = 12f
        val cardRadius = 18f
        val columnWidth = (PAGE_WIDTH - PAGE_MARGIN * 2 - horizontalGap) / columns
        val labelGap = 8f
        val basePadding = 18f

        rows.chunked(columns).forEach { chunk ->
            val rowHeight = chunk.maxOf { row ->
                val lines = wrapText(style.bodyPaint, row.value, columnWidth - basePadding * 2)
                basePadding * 2 + style.labelPaint.textSize + labelGap + lines.size * (style.bodyPaint.textSize + 6f)
            }
            state.ensureSpace(rowHeight + verticalGap)

            chunk.forEachIndexed { index, row ->
                val left = PAGE_MARGIN + (columnWidth + horizontalGap) * index
                val top = state.currentY
                val rect = RectF(left, top, left + columnWidth, top + rowHeight)
                state.canvas.drawRoundRect(rect, cardRadius, cardRadius, style.cardBackgroundPaint)
                state.canvas.drawRoundRect(rect, cardRadius, cardRadius, style.cardBorderPaint)
                val labelBaseline = rect.top + basePadding + style.labelPaint.textSize
                state.canvas.drawText(row.label.uppercase(Locale.getDefault()), rect.left + basePadding, labelBaseline, style.labelPaint)
                val bodyLines = wrapText(style.bodyPaint, row.value.ifBlank { "—" }, rect.width() - basePadding * 2)
                var lineY = labelBaseline + labelGap + style.bodyPaint.textSize
                bodyLines.forEach { line ->
                    state.canvas.drawText(line, rect.left + basePadding, lineY, style.bodyPaint)
                    lineY += style.bodyPaint.textSize + 6f
                }
            }
            state.currentY += rowHeight + verticalGap
        }
    }

    private data class TimelineEntry(val title: String, val date: String, val time: String)

    private fun drawTimeline(context: Context, state: PageState, style: PdfStyle, items: List<TimelineEntry>) {
        if (items.isEmpty()) return
        val cardPadding = 20f
        val cardHeight = 120f
        val requiredHeight = cardHeight
        state.ensureSpace(requiredHeight + 16f)

        val rect = RectF(
            PAGE_MARGIN,
            state.currentY,
            PAGE_WIDTH - PAGE_MARGIN,
            state.currentY + cardHeight
        )
        state.canvas.drawRoundRect(rect, 20f, 20f, style.cardBackgroundPaint)
        state.canvas.drawRoundRect(rect, 20f, 20f, style.cardBorderPaint)

        val timelineTop = rect.top + cardPadding
        val timelineBottom = rect.bottom - cardPadding
        val availableWidth = rect.width() - cardPadding * 2
        val segmentWidth = if (items.size > 1) availableWidth / (items.size - 1) else 0f
        val lineY = (timelineTop + timelineBottom) / 2f

        if (items.size > 1) {
            state.canvas.drawLine(
                rect.left + cardPadding,
                lineY,
                rect.right - cardPadding,
                lineY,
                style.timelinePaint
            )
        }

        val centers = if (items.size == 1) {
            listOf(rect.centerX())
        } else {
            items.indices.map { index -> rect.left + cardPadding + segmentWidth * index }
        }

        items.forEachIndexed { index, entry ->
            val centerX = centers[index]
            val circleRadius = 8f
            state.canvas.drawCircle(centerX, lineY, circleRadius, style.timelinePaint)
            val titleY = lineY - 18f
            val dateY = lineY + 24f
            val timeY = dateY + 14f
            val titlePaint = style.labelPaint
            val datePaint = style.timelineLabelPaint
            val timePaint = Paint(style.timelineLabelPaint).apply { alpha = 180 }
            val titleText = entry.title.uppercase(Locale.getDefault())
            drawCenteredText(state.canvas, titleText, centerX, titleY, titlePaint)
            drawCenteredText(state.canvas, entry.date.ifBlank { context.getString(R.string.averia_pdf_empty_value) }, centerX, dateY, datePaint)
            drawCenteredText(state.canvas, entry.time.ifBlank { "--" }, centerX, timeY, timePaint)
        }

        state.currentY = rect.bottom + 16f
    }

    private fun drawCenteredText(canvas: Canvas, text: String, centerX: Float, baseline: Float, paint: Paint) {
        val textWidth = paint.measureText(text)
        canvas.drawText(text, centerX - textWidth / 2f, baseline, paint)
    }

    private data class DetailCard(
        val title: String,
        val content: List<String>,
        val bullet: Boolean = false
    )

    private fun drawDetailCard(context: Context, state: PageState, style: PdfStyle, card: DetailCard) {
        val innerPadding = 22f
        val lineSpacing = style.bodyPaint.textSize + 8f
        val titleGap = 16f
        val bulletPrefix = "• "
        val indentWidth = style.bodyPaint.measureText(bulletPrefix)
        val availableWidth = PAGE_WIDTH - PAGE_MARGIN * 2 - innerPadding * 2
        val lines = buildList {
            card.content.forEach { raw ->
                val text = raw.ifBlank { "—" }
                val paragraphs = wrapText(
                    paint = style.bodyPaint,
                    text = if (card.bullet) text.removePrefix(bulletPrefix) else text,
                    maxWidth = if (card.bullet) availableWidth - indentWidth else availableWidth
                )
                if (card.bullet) {
                    paragraphs.forEachIndexed { index, paragraph ->
                        val prefix = if (index == 0) bulletPrefix else "   "
                        add(prefix + paragraph)
                    }
                } else if (paragraphs.isEmpty()) {
                    add("—")
                } else {
                    addAll(paragraphs)
                }
            }
            if (isEmpty()) add("—")
        }

        val usableBottom = PAGE_HEIGHT - (FOOTER_HEIGHT + PAGE_MARGIN)
        val baseHeight = innerPadding * 2 + style.titlePaint.textSize + titleGap
        var startIndex = 0
        var segment = 0
        while (startIndex < lines.size) {
            var pageBreakTriggered = false
            state.ensureSpace(baseHeight + lineSpacing) {
                pageBreakTriggered = true
            }
            if (pageBreakTriggered) {
                segment++
            }

            val availableHeight = usableBottom - state.currentY
            val maxLinesHere = ((availableHeight - baseHeight) / lineSpacing).toInt().coerceAtLeast(1)
            val endIndex = (startIndex + maxLinesHere).coerceAtMost(lines.size)
            val linesForPage = lines.subList(startIndex, endIndex)
            val cardHeight = baseHeight + linesForPage.size * lineSpacing
            if (state.currentY + cardHeight > usableBottom) {
                state.startPage()
                segment++
                continue
            }
            val rect = RectF(
                PAGE_MARGIN,
                state.currentY,
                PAGE_WIDTH - PAGE_MARGIN,
                state.currentY + cardHeight
            )
            state.canvas.drawRoundRect(rect, 22f, 22f, style.cardBackgroundPaint)
            state.canvas.drawRoundRect(rect, 22f, 22f, style.cardBorderPaint)
            val titleBaseline = rect.top + innerPadding + style.titlePaint.textSize
            val displayTitle = if (segment == 0) {
                card.title
            } else {
                context.getString(R.string.averia_pdf_card_continuation_title, card.title)
            }
            state.canvas.drawText(displayTitle, rect.left + innerPadding, titleBaseline, style.titlePaint)
            var lineY = titleBaseline + titleGap
            linesForPage.forEach { line ->
                state.canvas.drawText(line, rect.left + innerPadding, lineY, style.bodyPaint)
                lineY += lineSpacing
            }
            state.currentY += cardHeight + 12f
            startIndex = endIndex
            if (startIndex < lines.size) {
                segment++
            }
        }
    }

    private fun wrapText(paint: Paint, text: String, maxWidth: Float): List<String> {
        val cleaned = text.replace("\r", "\n").trimEnd()
        if (cleaned.isEmpty()) return emptyList()
        val paragraphs = cleaned.split('\n')
        val lines = mutableListOf<String>()
        paragraphs.forEach { paragraph ->
            val trimmed = paragraph.trim()
            if (trimmed.isEmpty()) {
                lines += ""
            } else {
                var remaining = trimmed
                while (remaining.isNotEmpty()) {
                    var count = paint.breakText(remaining, true, maxWidth, null)
                    if (count <= 0) break
                    if (count < remaining.length) {
                        val lastSpace = remaining.substring(0, count).lastIndexOf(' ')
                        if (lastSpace > 0) {
                            count = lastSpace + 1
                        }
                    }
                    val piece = remaining.substring(0, count).trimEnd()
                    lines += piece
                    remaining = remaining.substring(count).trimStart()
                }
            }
        }
        return lines
    }
}
