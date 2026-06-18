package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Path
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.icu.util.Calendar
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.ui.reportes.ReportDownloadNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

object
PdfGenerator {
    private const val PAGE_WIDTH = 595 // A4
    private const val PAGE_HEIGHT = 842
    private const val PAGE_MARGIN = 36f
    private const val FOOTER_HEIGHT = 64f

    private fun parseMedidorLecturas(raw: String?): Pair<String?, String?> {
        if (raw.isNullOrBlank()) return null to null
        val parts = raw.split("|", limit = 2)
        val nueva = parts.getOrNull(0)?.takeIf { it.isNotBlank() }
        val anterior = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
        return nueva to anterior
        // TODO(Codex): Compartir formato de lecturas entre exportador y UI
    }

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
        val taglinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f
            color = Color.WHITE
        }

        val title = context.getString(R.string.averia_pdf_header_title)
        val subtitle = context.getString(R.string.averia_pdf_header_subtitle)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val tagline = context.getString(R.string.averia_pdf_header_tagline, currentYear)

        val textX = headerRect.left + 32f
        var textY = headerRect.top + 48f
        canvas.drawText(title, textX, textY, titlePaint)
        textY += 28f
        canvas.drawText(subtitle, textX, textY, subtitlePaint)
        textY += 22f
        canvas.drawText(tagline, textX, textY, taglinePaint)

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
            val dateOnlyFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val yearFormatter = SimpleDateFormat("yyyy", Locale.getDefault())
            val shortTimeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

            fun formatMillis(millis: Long?, fallback: String): String =
                millis?.takeIf { it > 0 }
                    ?.let { dateTimeFormatter.format(Date(it)) }
                    ?: fallback

            fun formatDateOnly(millis: Long?, fallback: String): String =
                millis?.takeIf { it > 0 }
                    ?.let { dateOnlyFormatter.format(Date(it)) }
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
                    if (pageNumber == 1) {
                        drawHeader(
                            context = context,
                            canvas = canvas,
                            logos = headerLogos,
                            item = item,
                            generatedAt = generatedAt
                        )
                    } else {
                        PAGE_MARGIN
                    }
                },
                onPageFinished = { canvas, pageNumber ->
                    drawFooter(context, canvas, style, pageNumber, currentYear)
                }
            )

            state.startPage()

            val emptyValue = context.getString(R.string.averia_pdf_empty_value)
            val assigned = item.tecnico.ifBlank { context.getString(R.string.averia_sin_asignar) }
            val attended = item.resolvedAtendidoDisplay(emptyValue)
            val vehicle = item.vehiculo ?: emptyValue
            val nise = item.nise.ifBlank { emptyValue }
            val observations = item.observaciones.ifBlank { emptyValue }
            val client = item.cliente?.takeIf { it.isNotBlank() } ?: emptyValue
            val textualLocation = item.localizacion?.takeIf { it.isNotBlank() } ?: emptyValue
            val geocodedAddress = item.direccion?.takeIf { it.isNotBlank() } ?: emptyValue
            val kilometrajeLlegada = item.kilometrajeLlegada?.toString() ?: emptyValue
            val kilometrajeInicio = item.kilometrajeInicio?.toString() ?: emptyValue
            val kilometrajeFinal = item.kilometrajeFinal?.toString() ?: emptyValue
            val eventDate = formatDateOnly(item.fechaMillis, emptyValue)
            val startAttentionDate = formatDateOnly(item.horaAtencionInicio, emptyValue)
            val arrivalAttentionDate = formatDateOnly(item.horaLlegada, emptyValue)
            val endAttentionDate = formatDateOnly(item.horaAtencionFinal, emptyValue)
            val startAttentionTime = item.horaAtencionInicio?.takeIf { it > 0 }
                ?.let { shortTimeFormatter.format(Date(it)) }
                ?: "--"
            val arrivalAttentionTime = item.horaLlegada?.takeIf { it > 0 }
                ?.let { shortTimeFormatter.format(Date(it)) }
                ?: "--"
            val endAttentionTime = item.horaAtencionFinal?.takeIf { it > 0 }
                ?.let { shortTimeFormatter.format(Date(it)) }
                ?: "--"
            val region = item.region.ifBlank { emptyValue }
            val agency = item.agencia.ifBlank { emptyValue }

            val affectation = when (item.tipoAfectacion) {
                TipoAfectacion.CLIENTE -> context.getString(R.string.averia_tipo_cliente)
                TipoAfectacion.SECTOR -> context.getString(R.string.averia_tipo_sector)
            }
            val medidorNumero = item.numeroMedidor?.takeIf { it.isNotBlank() } ?: emptyValue


            val infoRows = listOfNotNull(
                InfoRow(context.getString(R.string.averia_pdf_table_label_case), item.id),
                InfoRow(context.getString(R.string.averia_pdf_table_label_status), item.estado),
                InfoRow(context.getString(R.string.averia_pdf_table_label_assigned), assigned),
                InfoRow(context.getString(R.string.averia_pdf_table_label_attended_by), attended),
                InfoRow(context.getString(R.string.averia_pdf_table_label_vehicle), vehicle),
                if (item.tipoAfectacion == TipoAfectacion.CLIENTE) {
                    InfoRow(context.getString(R.string.averia_pdf_table_label_client), client)
                } else {
                    null
                },
                InfoRow(context.getString(R.string.averia_pdf_table_label_nise), nise),

                InfoRow(context.getString(R.string.averia_pdf_table_label_region), region),
                InfoRow(context.getString(R.string.averia_pdf_table_label_agency), agency),
                InfoRow(context.getString(R.string.averia_pdf_table_label_location_text), textualLocation),
                InfoRow(context.getString(R.string.averia_pdf_table_label_address), geocodedAddress),
                InfoRow(context.getString(R.string.averia_pdf_table_label_affectation), affectation),
                InfoRow(context.getString(R.string.averia_pdf_table_label_event_date), eventDate),

                if (item.tipoAfectacion == TipoAfectacion.CLIENTE) {
                    InfoRow(context.getString(R.string.averia_pdf_table_label_medidor), medidorNumero)
                } else {
                    null
                }
            )

            drawInfoGrid(state, style, infoRows)
            state.currentY += 16f

            drawSectionHeader(state, style, context.getString(R.string.averia_pdf_section_timeline_dates), contentHeight = 152f)
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
                        date = startAttentionDate,
                        time = startAttentionTime
                    ),
                    TimelineEntry(
                        title = context.getString(R.string.averia_pdf_table_label_arrival_time),
                        date = arrivalAttentionDate,
                        time = arrivalAttentionTime
                    ),
                    TimelineEntry(
                        title = context.getString(R.string.averia_pdf_table_label_end_time),
                        date = endAttentionDate,
                        time = endAttentionTime
                    ),
                )
            )

            drawSectionHeader(state, style, context.getString(R.string.averia_pdf_section_timeline_km), contentHeight = 152f)
            drawTimeline(
                context = context,
                state = state,
                style = style,
                items = listOf(
                    TimelineEntry(
                        title = context.getString(R.string.averia_pdf_table_label_kilometers_start),
                        date = kilometrajeInicio,
                        time = ""
                    ),
                    TimelineEntry(
                        title = context.getString(R.string.averia_pdf_table_label_kilometers_arrival),
                        date = kilometrajeLlegada,
                        time = ""
                    ),
                    TimelineEntry(
                        title = context.getString(R.string.averia_pdf_table_label_kilometers_end),
                        date = kilometrajeFinal,
                        time = ""
                    )
                )
            )

            val cards = listOf(
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

            val materialLines = when {
                item.materialesDetalle.any { it.cantidad > 0 } -> item.materialesDetalle
                    .filter { it.cantidad > 0 }
                    .map { m ->
                        val base = m.descripcion.ifBlank { m.codigo }
                        val detalle = buildList {
                            m.selloNumero?.takeIf { it.isNotBlank() }?.let {
                                add(context.getString(R.string.material_sello_detalle, it))
                            }
                            m.medidorInstalado?.let { meta ->
                                meta.numero?.takeIf { it.isNotBlank() }?.let {
                                    add(context.getString(R.string.averia_medidor_detalle_numero, it))
                                }
                                val (lecturaNuevaPdf, lecturaAnteriorPdf) = parseMedidorLecturas(meta.lectura)
                                lecturaNuevaPdf?.let {
                                    add(context.getString(R.string.averia_medidor_detalle_lectura, it))
                                }
                                lecturaAnteriorPdf?.let {
                                    add(context.getString(R.string.averia_medidor_detalle_lectura_anterior, it))
                                }
                            }
                        }.takeIf { it.isNotEmpty() }
                        val nombre = if (!detalle.isNullOrEmpty()) {
                            "$base (${detalle.joinToString(" • ")})"
                        } else {
                            base
                        }
                        context.getString(
                            R.string.averia_pdf_material_line,
                            nombre,
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

            val principalAtendioLine = item.atendidoPor.takeIf { it.isNotBlank() }?.let { nombre ->
                val tecnicoPrincipal = item.tecnicosAtendieron.firstOrNull { tecnico ->
                    tecnico.uid == item.atendidoPorUid || tecnico.nombre.trim().equals(nombre.trim(), ignoreCase = true)
                }
                val cedulaPrincipal = tecnicoPrincipal?.cedula?.trim().orEmpty()
                if (cedulaPrincipal.isNotBlank()) "$nombre - $cedulaPrincipal" else nombre
            }

            val techniciansLines = item.tecnicosAtendieron
                .mapNotNull { tecnico ->
                    val nombre = tecnico.nombre.trim()
                    val cedula = tecnico.cedula?.trim().orEmpty()
                    val key = cedula.ifBlank { nombre.lowercase(Locale.getDefault()) }
                    when {
                        key.isBlank() -> null
                        nombre.isNotBlank() && cedula.isNotBlank() -> key to "${nombre} - ${cedula}"
                        nombre.isNotBlank() -> key to nombre
                        else -> key to cedula
                    }
                }
                .distinctBy { it.first }
                .map { it.second }
                .let { lines ->
                    val withPrincipal = principalAtendioLine?.let { listOf(it) + lines } ?: lines
                    withPrincipal.distinct()
                }
                .ifEmpty {
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

            drawEvidenciasCard(context, state, style, item.evidencias)

            state.finish()

            val parentDir = context.getExternalFilesDir(null) ?: context.filesDir
            val reportsDir = File(parentDir, "TecniApp/Reportes")
            if (!reportsDir.exists()) reportsDir.mkdirs()
            val calendar = Calendar.getInstance(Locale.getDefault())
            val yearComponent = calendar.get(Calendar.YEAR)
            val monthFormatter = SimpleDateFormat("LLLL", Locale("es", "ES"))
            val monthComponent = monthFormatter.format(now).lowercase(Locale.getDefault())
            val dayComponent = String.format(Locale.getDefault(), "%02d", calendar.get(Calendar.DAY_OF_MONTH))
            val sanitizedId = item.id.replace("[^A-Za-z0-9_-]".toRegex(), "").ifBlank { item.id }
            val fileName = "Averia_IM_${sanitizedId}_${yearComponent}_${monthComponent}_${dayComponent}.pdf"
            // TODO(Codex): Ajustar nombre del PDF al formato solicitado por ICE
            val file = File(reportsDir, fileName)
            FileOutputStream(file).use { output -> document.writeTo(output) }

            withContext(Dispatchers.Main) {
                val fileUri: Uri = FileProvider.getUriForFile(
                    context,
                    context.packageName + ".fileprovider",
                    file
                )
                ReportDownloadNotifier.show(
                    context = context,
                    fileName = fileName,
                    fileUri = fileUri,
                    locationUri = ReportDownloadNotifier.buildLocationUriForFile(context, file)
                )
                Toast.makeText(
                    context,
                    context.getString(R.string.averia_export_success, fileName),
                    Toast.LENGTH_LONG
                ).show()
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, fileUri)
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
        val desiredSize = 96
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
        val cardBackgroundPaint: Paint,
        val cardBorderPaint: Paint,
        val cardAccentPaint: Paint,
        val timelinePaint: Paint,
        val timelineDotFillPaint: Paint,
        val timelineLabelPaint: Paint,
        val timelineLabelBoldPaint: Paint,
        val sectionTitlePaint: Paint,
        val sectionLinePaint: Paint,
        val footerTextPaint: Paint,
        val tagPaint: Paint,
        val tagTextPaint: Paint
    )

    private fun PdfStyle(context: Context): PdfStyle {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 14f
            color = Color.parseColor("#1D2A44")
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 12f
            color = Color.parseColor("#3A4A6B")
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 9f
            color = Color.parseColor("#8FA0C0")
            letterSpacing = 0.08f
        }
        val cardBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F7F9FF")
        }
        val cardBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0E8FF")
            strokeWidth = 1.2f
            style = Paint.Style.STROKE
        }
        val cardAccent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3D5AFE")
            style = Paint.Style.FILL
        }
        val timeline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C5CCFF")
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        val timelineDotFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3D5AFE")
            style = Paint.Style.FILL
        }
        val timelineLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 10f
            color = Color.parseColor("#5C6B8A")
        }
        val timelineLabelBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 9f
            color = Color.parseColor("#8FA0C0")
            letterSpacing = 0.06f
        }
        val sectionTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 11f
            color = Color.parseColor("#3D5AFE")
            letterSpacing = 0.06f
        }
        val sectionLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0E8FF")
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        val footerText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 9.5f
            color = Color.WHITE
        }
        val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EEF1FF")
            style = Paint.Style.FILL
        }
        val tagText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 10f
            color = Color.parseColor("#3D5AFE")
        }
        return PdfStyle(
            titlePaint = titlePaint,
            bodyPaint = bodyPaint,
            labelPaint = labelPaint,
            cardBackgroundPaint = cardBackground,
            cardBorderPaint = cardBorder,
            cardAccentPaint = cardAccent,
            timelinePaint = timeline,
            timelineDotFillPaint = timelineDotFill,
            timelineLabelPaint = timelineLabel,
            timelineLabelBoldPaint = timelineLabelBold,
            sectionTitlePaint = sectionTitle,
            sectionLinePaint = sectionLine,
            footerTextPaint = footerText,
            tagPaint = tagPaint,
            tagTextPaint = tagText
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
        logos: List<Bitmap>,
        item: AveriaUI,
        generatedAt: String
    ): Float {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 22f
            color = Color.parseColor("#1D2A44")
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 13f
            color = Color.parseColor("#1D2A44")
            alpha = 210
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 11f
            color = Color.parseColor("#3A4A6B")
            alpha = 190
        }

        val top = PAGE_MARGIN
        val left = PAGE_MARGIN
        val availableWidth = PAGE_WIDTH - PAGE_MARGIN * 2
        val logoSize = 60f
        val logoSpacing = 10f

        val logosWidth = if (logos.isNotEmpty()) {
            logos.size * logoSize + (logos.size - 1) * logoSpacing
        } else {
            0f
        }
        val textWidth = (availableWidth - logosWidth - if (logosWidth > 0) 24f else 0f).coerceAtLeast(0f)
        val textX = left
        var textY = top + titlePaint.textSize

        val title = context.getString(R.string.averia_pdf_header_title)
        val subtitle = context.getString(R.string.averia_pdf_header_subtitle)
        canvas.drawText(title, textX, textY, titlePaint)
        textY += subtitlePaint.textSize + 4f
        canvas.drawText(subtitle, textX, textY, subtitlePaint)
        textY += metaPaint.textSize + 8f

        val caseLabel = context.getString(R.string.averia_pdf_table_label_case)
        // Case number as pill tag
        val tagText = "$caseLabel: ${item.id}"
        val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EEF1FF"); style = Paint.Style.FILL }
        val tagTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 11f
            color = Color.parseColor("#3D5AFE")
        }
        val tagW = tagTextPaint.measureText(tagText) + 16f
        val tagH = tagTextPaint.textSize + 8f
        val tagRect = RectF(textX, textY - tagTextPaint.textSize, textX + tagW, textY - tagTextPaint.textSize + tagH)
        canvas.drawRoundRect(tagRect, 8f, 8f, tagPaint)
        canvas.drawText(tagText, textX + 8f, tagRect.bottom - 5f, tagTextPaint)

        textY = tagRect.bottom + 6f
        canvas.drawText(
            context.getString(R.string.averia_pdf_table_label_generated) + ": " + generatedAt,
            textX,
            textY + metaPaint.textSize,
            metaPaint
        )
        textY += metaPaint.textSize + 4f

        if (logos.isNotEmpty()) {
            var currentLeft = left + textWidth + 24f
            val topLogo = top
            logos.forEach { bitmap ->
                val dest = RectF(currentLeft, topLogo, currentLeft + logoSize, topLogo + logoSize)
                canvas.drawBitmap(bitmap, null, dest, null)
                currentLeft += logoSize + logoSpacing
            }
        }

        val contentBottom = max(textY + 16f, top + logoSize + 12f)

        // Accent separator line
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3D5AFE")
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(left, contentBottom + 4f, left + PAGE_WIDTH * 0.35f, contentBottom + 4f, accentPaint)
        val faintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0E8FF")
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(left + PAGE_WIDTH * 0.35f + 6f, contentBottom + 4f, PAGE_WIDTH - PAGE_MARGIN, contentBottom + 4f, faintPaint)

        return contentBottom + 20f
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

    private fun drawEvidenciasCard(
        context: Context,
        state: PageState,
        style: PdfStyle,
        evidencias: List<EvidenciaFoto>
    ) {
        if (evidencias.isEmpty()) {
            drawDetailCard(
                context,
                state,
                style,
                DetailCard(
                    title = context.getString(R.string.averia_pdf_section_evidencias),
                    content = listOf(context.getString(R.string.averia_pdf_no_evidencias))
                )
            )
            return
        }

        state.currentY += 8f
        // Section header reserva espacio para header + primera fila de fotos
        drawSectionHeader(state, style, context.getString(R.string.averia_pdf_section_evidencias), contentHeight = 148f)

        val thumb = 120f
        val gap = 10f
        val labelTagHeight = 18f
        var x = PAGE_MARGIN
        var y = state.currentY
        val maxX = PAGE_WIDTH - PAGE_MARGIN

        evidencias.forEachIndexed { idx, evidencia ->
            if (x + thumb > maxX) {
                x = PAGE_MARGIN
                y += thumb + labelTagHeight + gap + 6f
                state.ensureSpace(thumb + labelTagHeight + 24f)
            }

            val imgRect = RectF(x, y, x + thumb, y + thumb)
            val bmp = runCatching {
                context.contentResolver.openInputStream(Uri.parse(evidencia.uri))?.use { ins ->
                    BitmapFactory.decodeStream(ins)
                }
            }.getOrNull()

            if (bmp != null) {
                // Clip to rounded rect for photo
                state.canvas.save()
                val clipPath = Path().apply {
                    addRoundRect(imgRect, 10f, 10f, Path.Direction.CW)
                }
                state.canvas.clipPath(clipPath)
                state.canvas.drawBitmap(bmp, null, imgRect, null)
                state.canvas.restore()
            } else {
                // Placeholder when image not available
                val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#E8ECFF")
                }
                state.canvas.drawRoundRect(imgRect, 10f, 10f, placeholderPaint)
            }

            // Border over image
            state.canvas.drawRoundRect(imgRect, 10f, 10f, style.cardBorderPaint)

            // Tag label below image
            val tagText = "#${idx + 1}"
            val tagW = style.tagTextPaint.measureText(tagText) + 14f
            val tagRect = RectF(x, y + thumb + 4f, x + tagW, y + thumb + 4f + labelTagHeight)
            state.canvas.drawRoundRect(tagRect, 6f, 6f, style.tagPaint)
            state.canvas.drawText(tagText, tagRect.left + 7f, tagRect.top + style.tagTextPaint.textSize + 2f, style.tagTextPaint)

            x += thumb + gap
        }
        state.currentY = y + thumb + labelTagHeight + 18f
    }

    private data class InfoRow(val label: String, val value: String)

    private fun drawInfoGrid(state: PageState, style: PdfStyle, rows: List<InfoRow>) {
        val columns = 2
        val horizontalGap = 10f
        val verticalGap = 8f
        val cardRadius = 12f
        val columnWidth = (PAGE_WIDTH - PAGE_MARGIN * 2 - horizontalGap) / columns
        val labelGap = 5f
        val basePadding = 14f
        val accentBarWidth = 4f

        rows.chunked(columns).forEach { chunk ->
            val rowHeight = chunk.maxOf { row ->
                val lines = wrapText(style.bodyPaint, row.value, columnWidth - basePadding * 2 - accentBarWidth)
                basePadding * 2 + style.labelPaint.textSize + labelGap + lines.size * (style.bodyPaint.textSize + 4f)
            }
            state.ensureSpace(rowHeight + verticalGap)

            chunk.forEachIndexed { index, row ->
                val left = PAGE_MARGIN + (columnWidth + horizontalGap) * index
                val top = state.currentY
                val rect = RectF(left, top, left + columnWidth, top + rowHeight)

                // Card background
                state.canvas.drawRoundRect(rect, cardRadius, cardRadius, style.cardBackgroundPaint)
                state.canvas.drawRoundRect(rect, cardRadius, cardRadius, style.cardBorderPaint)

                // Accent bar on left edge (clipped to card shape)
                val accentRect = RectF(rect.left, rect.top, rect.left + accentBarWidth + cardRadius, rect.bottom)
                state.canvas.save()
                state.canvas.clipRect(rect.left, rect.top, rect.left + accentBarWidth, rect.bottom)
                state.canvas.drawRoundRect(
                    RectF(rect.left, rect.top, rect.left + accentBarWidth * 2 + cardRadius, rect.bottom),
                    cardRadius, cardRadius,
                    style.cardAccentPaint
                )
                state.canvas.restore()

                val textLeft = rect.left + accentBarWidth + basePadding
                val labelBaseline = rect.top + basePadding + style.labelPaint.textSize
                state.canvas.drawText(row.label.uppercase(Locale.getDefault()), textLeft, labelBaseline, style.labelPaint)
                val bodyLines = wrapText(style.bodyPaint, row.value.ifBlank { "—" }, rect.width() - basePadding * 2 - accentBarWidth)
                var lineY = labelBaseline + labelGap + style.bodyPaint.textSize
                bodyLines.forEach { line ->
                    state.canvas.drawText(line, textLeft, lineY, style.bodyPaint)
                    lineY += style.bodyPaint.textSize + 4f
                }
            }
            state.currentY += rowHeight + verticalGap
        }
    }

    private fun drawSectionHeader(state: PageState, style: PdfStyle, title: String, contentHeight: Float = 0f) {
        // Reservar espacio para el título + el contenido que vendrá justo después,
        // así nunca queda el título solo al final de una página.
        val totalNeeded = style.sectionTitlePaint.textSize + 12f + contentHeight
        state.ensureSpace(totalNeeded)
        val x = PAGE_MARGIN
        val y = state.currentY + style.sectionTitlePaint.textSize
        state.canvas.drawText(title.uppercase(Locale.getDefault()), x, y, style.sectionTitlePaint)
        val lineY = y + 5f
        state.canvas.drawLine(
            x + style.sectionTitlePaint.measureText(title.uppercase(Locale.getDefault())) + 8f,
            lineY,
            PAGE_WIDTH - PAGE_MARGIN,
            lineY,
            style.sectionLinePaint
        )
        state.currentY += style.sectionTitlePaint.textSize + 12f
    }

    private data class TimelineEntry(val title: String, val date: String, val time: String)

    private fun drawTimeline(context: Context, state: PageState, style: PdfStyle, items: List<TimelineEntry>) {
        if (items.isEmpty()) return

        // Layout constants
        val outerPadH = 20f
        val outerPadV = 16f
        val labelAreaHeight = 44f
        val lineSection = 20f
        val belowAreaHeight = 44f
        val cardHeight = outerPadV + labelAreaHeight + lineSection + belowAreaHeight + outerPadV

        // No llamamos ensureSpace aquí: el drawSectionHeader que antecede a esta función
        // ya reservó espacio suficiente para header + card juntos en la misma página.

        val rect = RectF(
            PAGE_MARGIN,
            state.currentY,
            PAGE_WIDTH - PAGE_MARGIN,
            state.currentY + cardHeight
        )

        // Card with very subtle background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F0F3FF")
        }
        state.canvas.drawRoundRect(rect, 16f, 16f, bgPaint)
        state.canvas.drawRoundRect(rect, 16f, 16f, style.cardBorderPaint)

        val lineY = rect.top + outerPadV + labelAreaHeight + lineSection / 2f
        val lineLeft = rect.left + outerPadH
        val lineRight = rect.right - outerPadH

        // Draw the connector line
        if (items.size > 1) {
            state.canvas.drawLine(lineLeft, lineY, lineRight, lineY, style.timelinePaint)
        }

        val segmentWidth = if (items.size > 1) (lineRight - lineLeft) / (items.size - 1) else 0f
        val centers = if (items.size == 1) {
            listOf((lineLeft + lineRight) / 2f)
        } else {
            items.indices.map { i -> lineLeft + segmentWidth * i }
        }

        items.forEachIndexed { index, entry ->
            val cx = centers[index]

            // Outer ring
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#3D5AFE")
                this.style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            // White fill for inner dot
            val whiteFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                this.style = Paint.Style.FILL
            }
            state.canvas.drawCircle(cx, lineY, 8f, style.timelineDotFillPaint)
            state.canvas.drawCircle(cx, lineY, 4f, whiteFill)
            state.canvas.drawCircle(cx, lineY, 8f, ringPaint)

            // Alternate: even indices → label above line, odd → below
            val isAbove = (index % 2 == 0)

            val titleText = entry.title.uppercase(Locale.getDefault())
            val dateText = entry.date.ifBlank { context.getString(R.string.averia_pdf_empty_value) }
            val timeText = entry.time.ifBlank { "" }

            if (isAbove) {
                // Title at top of upper area, date+time just above the line
                val titleY = rect.top + outerPadV + style.timelineLabelBoldPaint.textSize
                val dateY = lineY - 14f
                val timeY = lineY - 3f
                drawCenteredText(state.canvas, titleText, cx, titleY, style.timelineLabelBoldPaint)
                drawCenteredText(state.canvas, dateText, cx, dateY, style.timelineLabelPaint)
                if (timeText.isNotBlank()) {
                    drawCenteredText(state.canvas, timeText, cx, timeY, style.timelineLabelPaint)
                }
            } else {
                // Title just below the line, date+time below it
                val titleY = lineY + 16f
                val dateY = lineY + 16f + style.timelineLabelBoldPaint.textSize + 4f
                val timeY = dateY + style.timelineLabelPaint.textSize + 2f
                drawCenteredText(state.canvas, titleText, cx, titleY, style.timelineLabelBoldPaint)
                drawCenteredText(state.canvas, dateText, cx, dateY, style.timelineLabelPaint)
                if (timeText.isNotBlank()) {
                    drawCenteredText(state.canvas, timeText, cx, timeY, style.timelineLabelPaint)
                }
            }
        }

        state.currentY = rect.bottom + 10f
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
        val innerPadding = 16f
        val lineSpacing = style.bodyPaint.textSize + 7f
        val titleGap = 10f
        val accentBarWidth = 4f
        val bulletPrefix = "• "
        val indentWidth = style.bodyPaint.measureText(bulletPrefix)
        val availableWidth = PAGE_WIDTH - PAGE_MARGIN * 2 - innerPadding * 2 - accentBarWidth
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
            state.canvas.drawRoundRect(rect, 12f, 12f, style.cardBackgroundPaint)
            state.canvas.drawRoundRect(rect, 12f, 12f, style.cardBorderPaint)

            // Accent bar left edge
            state.canvas.save()
            state.canvas.clipRect(rect.left, rect.top, rect.left + accentBarWidth, rect.bottom)
            state.canvas.drawRoundRect(
                RectF(rect.left, rect.top, rect.left + accentBarWidth * 2 + 12f, rect.bottom),
                12f, 12f,
                style.cardAccentPaint
            )
            state.canvas.restore()

            val textLeft = rect.left + accentBarWidth + innerPadding
            val titleBaseline = rect.top + innerPadding + style.titlePaint.textSize
            val displayTitle = if (segment == 0) {
                card.title
            } else {
                context.getString(R.string.averia_pdf_card_continuation_title, card.title)
            }
            state.canvas.drawText(displayTitle, textLeft, titleBaseline, style.titlePaint)

            // Thin divider line under title
            val dividerY = titleBaseline + titleGap / 2f
            state.canvas.drawLine(textLeft, dividerY, rect.right - innerPadding, dividerY, style.sectionLinePaint)

            var lineY = dividerY + titleGap / 2f + style.bodyPaint.textSize
            linesForPage.forEach { line ->
                state.canvas.drawText(line, textLeft, lineY, style.bodyPaint)
                lineY += lineSpacing
            }
            state.currentY += cardHeight + 10f
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