package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.content.ContextCompat
import com.Arasoftsolutions.tecniapp_ice.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object AveriaMapPreviewRenderer {
    fun render(
        context: Context,
        lat: Double?,
        lng: Double?,
        label: String
    ): Bitmap {
        val width = context.resources.getDimensionPixelSize(R.dimen.notification_large_icon_width)
            .takeIf { it > 0 } ?: 512
        val height = context.resources.getDimensionPixelSize(R.dimen.notification_large_icon_height)
            .takeIf { it > 0 } ?: 280
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val startColor = ContextCompat.getColor(context, R.color.averia_notification_glow)
        val endColor = ContextCompat.getColor(context, R.color.averia_notification_surface)
        val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                0f,
                height.toFloat(),
                startColor,
                endColor,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), gradientPaint)

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.outline_variant_light)
            strokeWidth = 1.5f
        }
        val steps = 6
        val stepX = width.toFloat() / steps
        val stepY = height.toFloat() / steps
        repeat(steps + 1) { i ->
            val x = i * stepX
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            val y = i * stepY
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
        }

        val accent = ContextCompat.getColor(context, R.color.averia_notification_accent)
        val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
        }
        val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.averia_notification_chip_bg)
        }

        val normalizedLat = lat?.let { (abs(it % 90.0) / 90.0).toFloat() } ?: 0.35f
        val normalizedLng = lng?.let { (abs(it % 180.0) / 180.0).toFloat() } ?: 0.65f
        val markerX = max(0.1f, min(0.9f, normalizedLng)) * width
        val markerY = max(0.1f, min(0.9f, 1 - normalizedLat)) * height

        canvas.drawCircle(markerX, markerY, 28f, pulsePaint)
        canvas.drawCircle(markerX, markerY, 14f, markerPaint)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.averia_notification_on_surface)
            textSize = 34f
        }
        val title = label.takeIf { it.isNotBlank() } ?: context.getString(R.string.averia_notificacion_map_placeholder)
        val textWidth = labelPaint.measureText(title)
        val textPadding = 24f
        val textLeft = max(16f, (width - textWidth) / 2f - textPadding / 2f)
        val textTop = height - 40f
        val backgroundRect = RectF(
            textLeft - textPadding,
            textTop - 52f,
            textLeft + textWidth + textPadding,
            textTop + 24f
        )
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.white_translucent)
        }
        canvas.drawRoundRect(backgroundRect, 16f, 16f, bgPaint)
        canvas.drawText(title, textLeft, textTop, labelPaint)

        return bitmap
    }
}
