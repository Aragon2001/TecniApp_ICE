package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.content.Context
import android.content.Intent
import android.graphics.Paint
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
  private const val PAGE_WIDTH = 595
  private const val PAGE_HEIGHT = 842

  suspend fun exportAveria(context: Context, item: AveriaUI) = withContext(Dispatchers.IO) {
    val document = PdfDocument()
    try {
      val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
      val page = document.startPage(pageInfo)
      val canvas = page.canvas

      val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 18f
      }
      val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14f }

      var y = 40f
      fun drawLine(text: String, bold: Boolean = false) {
        val paint = if (bold) titlePaint else bodyPaint
        canvas.drawText(text, 40f, y, paint)
        y += if (bold) 28f else 22f
      }

      val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

      fun formatMillis(millis: Long?): String? {
        if (millis == null) return null
        if (millis <= 0) return null
        return formatter.format(Date(millis))
      }

      val reporteGenerado = formatter.format(Date())
      val fechaEvento = formatMillis(item.fechaMillis)
      val inicioAtencion = formatMillis(item.horaAtencionInicio)
      val finAtencion = formatMillis(item.horaAtencionFinal)

      drawLine(context.getString(R.string.averia_reporte_titulo), true)
      drawLine(context.getString(R.string.averia_caso_format, item.id), true)
      drawLine(context.getString(R.string.averia_nise_format, item.nise))
      drawLine(context.getString(R.string.averia_asignado_a, item.tecnico.ifBlank { context.getString(R.string.averia_sin_asignar) }))
      drawLine(context.getString(R.string.averia_atendido_por_format, item.atendidoPor.ifBlank { "—" }))
      drawLine(context.getString(R.string.averia_vehiculo_format, item.vehiculo ?: "—"))
      drawLine(context.getString(R.string.averia_estado_pdf, item.estado))
      fechaEvento?.let {
        drawLine(context.getString(R.string.averia_reporte_fecha_evento, it))
      }
      inicioAtencion?.let {
        drawLine(context.getString(R.string.averia_reporte_atencion_inicio, it))
      }
      finAtencion?.let {
        drawLine(context.getString(R.string.averia_reporte_atencion_fin, it))
      }
      if (item.kilometrajeInicio != null || item.kilometrajeFinal != null) {
        val inicioKm = item.kilometrajeInicio?.toString() ?: "—"
        val finKm = item.kilometrajeFinal?.toString() ?: "—"
        drawLine(context.getString(R.string.averia_kilometraje_label, inicioKm, finKm))
      }
      drawLine(context.getString(R.string.averia_reporte_region, item.region))
      drawLine(context.getString(R.string.averia_reporte_agencia, item.agencia))
      drawLine(context.getString(R.string.averia_reporte_causa, item.causa.ifBlank { "—" }))
      drawLine(context.getString(R.string.averia_reporte_obs, item.observaciones.ifBlank { "—" }))
      drawLine(context.getString(R.string.averia_reporte_materiales, item.materialesResumen.ifBlank { "—" }))
      if (item.lat == 0.0 && item.lng == 0.0) {
        drawLine(context.getString(R.string.averia_reporte_coordenadas_sin_datos))
      } else {
        drawLine(context.getString(R.string.averia_reporte_coordenadas, item.lat, item.lng))
      }
      drawLine(context.getString(R.string.averia_reporte_generado, reporteGenerado))

      document.finishPage(page)

      val reportsDir = File(context.getExternalFilesDir(null), "TecniApp/Reportes")
      if (!reportsDir.exists()) reportsDir.mkdirs()
      val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
      val fileName = "averia_${item.id}_${formatter.format(Date())}.pdf"
      val file = File(reportsDir, fileName)
      FileOutputStream(file).use { output ->
        document.writeTo(output)
      }

      withContext(Dispatchers.Main) {
        Toast.makeText(context, context.getString(R.string.averia_export_success, fileName), Toast.LENGTH_LONG).show()
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
          type = "application/pdf"
          putExtra(Intent.EXTRA_STREAM, uri)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.averia_export_share_title)))
      }
    } catch (t: Throwable) {
      withContext(Dispatchers.Main) {
        Toast.makeText(context, context.getString(R.string.averia_export_error), Toast.LENGTH_LONG).show()
      }
    } finally {
      document.close()
    }
  }
}
