package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.content.Context
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfGenerator {

    fun generarPDF(context: Context, averia: AveriaUI) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val doc = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
                val page = doc.startPage(pageInfo)
                val canvas = page.canvas

                // 🎨 Estilos
                val titlePaint = android.graphics.Paint().apply {
                    color = Color.rgb(33, 150, 243) // Azul ICE
                    textSize = 22f
                    isFakeBoldText = true
                }

                val subTitlePaint = android.graphics.Paint().apply {
                    color = Color.DKGRAY
                    textSize = 16f
                    isFakeBoldText = true
                }

                val textPaint = android.graphics.Paint().apply {
                    color = Color.BLACK
                    textSize = 14f
                }

                val linePaint = android.graphics.Paint().apply {
                    color = Color.LTGRAY
                    strokeWidth = 2f
                }

                var y = 60

                // 🟦 Encabezado
                canvas.drawText("TecniApp ICE", 40f, y.toFloat(), titlePaint)
                y += 30
                canvas.drawText("Reporte de Avería", 40f, y.toFloat(), subTitlePaint)
                y += 20

                canvas.drawLine(40f, y.toFloat(), 555f, y.toFloat(), linePaint)
                y += 30

                // 📝 Información general
                val formatterEvento = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                val fecha = formatterEvento.format(Date(averia.fechaMillis))

                canvas.drawText("Caso: ${averia.id}", 40f, y.toFloat(), textPaint); y += 20
                canvas.drawText("Fecha: $fecha", 40f, y.toFloat(), textPaint); y += 20
                canvas.drawText("Estado: ${averia.estado}", 40f, y.toFloat(), textPaint); y += 20
                canvas.drawText("Descripción: ${averia.descripcion}", 40f, y.toFloat(), textPaint); y += 20
                canvas.drawText("Causa: ${averia.causa}", 40f, y.toFloat(), textPaint); y += 20

                canvas.drawLine(40f, y.toFloat(), 555f, y.toFloat(), linePaint); y += 30

                // 👷 Información de atención
                canvas.drawText("Asignado a: ${averia.tecnico}", 40f, y.toFloat(), textPaint); y += 20
                canvas.drawText("Agencia: ${averia.agencia}", 40f, y.toFloat(), textPaint); y += 20
                canvas.drawText("Región: ${averia.region}", 40f, y.toFloat(), textPaint); y += 20
                canvas.drawText("Zona: ${averia.zonaTag}", 40f, y.toFloat(), textPaint); y += 20

                canvas.drawLine(40f, y.toFloat(), 555f, y.toFloat(), linePaint); y += 30

                // 🌎 Localización
                canvas.drawText("NISE: ${averia.nise}", 40f, y.toFloat(), textPaint); y += 20
                canvas.drawText("Coordenadas: ${averia.lat}, ${averia.lng}", 40f, y.toFloat(), textPaint); y += 20

                canvas.drawLine(40f, y.toFloat(), 555f, y.toFloat(), linePaint); y += 30

                // 🗒️ Observaciones
                canvas.drawText("Observaciones:", 40f, y.toFloat(), subTitlePaint); y += 20
                val obsLines = dividirTexto(averia.observaciones, 80)
                for (line in obsLines) {
                    canvas.drawText(line, 60f, y.toFloat(), textPaint)
                    y += 18
                }

                // 📌 Pie de página
                y = 800
                canvas.drawLine(40f, y.toFloat(), 555f, y.toFloat(), linePaint)
                y += 20
                canvas.drawText("Generado automáticamente por TecniApp ICE", 40f, y.toFloat(), textPaint)

                doc.finishPage(page)

                // 📂 Guardar archivo
                val formatterFile = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                val fileName = "averia_${averia.id}_${formatterFile.format(Date())}.pdf"
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Averias")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)

                FileOutputStream(file).use { out ->
                    doc.writeTo(out)
                }
                doc.close()

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "PDF generado: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error generando PDF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun dividirTexto(texto: String, maxLength: Int): List<String> {
        if (texto.isBlank()) return listOf("—")
        val words = texto.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""
        for (word in words) {
            if ((currentLine + word).length > maxLength) {
                lines.add(currentLine.trim())
                currentLine = ""
            }
            currentLine += "$word "
        }
        if (currentLine.isNotBlank()) lines.add(currentLine.trim())
        return lines
    }
}
