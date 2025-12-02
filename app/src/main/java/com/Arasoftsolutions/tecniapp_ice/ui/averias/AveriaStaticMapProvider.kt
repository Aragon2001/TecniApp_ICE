package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.Arasoftsolutions.tecniapp_ice.R
import com.bumptech.glide.Glide
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object AveriaStaticMapProvider {
    private const val DEFAULT_WIDTH = 640
    private const val DEFAULT_HEIGHT = 360

    fun buildUrl(context: Context, lat: Double?, lng: Double?, label: String?): String? {
        if (lat == null || lng == null) return null
        val key = context.getString(R.string.google_maps_key)
        val labelChar = label?.takeIf { it.isNotBlank() }
            ?.trim()?.firstOrNull()
            ?.let { URLEncoder.encode(it.uppercaseChar().toString(), StandardCharsets.UTF_8.toString()) }
        val marker = labelChar?.let { "&markers=color:red%7Clabel:$it%7C$lat,$lng" }
            ?: "&markers=color:red%7C$lat,$lng"
        return "https://maps.googleapis.com/maps/api/staticmap?center=$lat,$lng&zoom=17&size=${DEFAULT_WIDTH}x${DEFAULT_HEIGHT}&scale=2&maptype=roadmap$marker&key=$key"
    }

    fun loadInto(
        context: Context,
        imageView: android.widget.ImageView,
        url: String
    ) {
        Glide.with(context)
            .load(url)
            .placeholder(R.drawable.ic_map_placeholder)
            .error(R.drawable.ic_map_placeholder)
            .into(imageView)
    }

    fun bitmapOrPlaceholder(context: Context, url: String?): Bitmap {
        if (url != null) {
            val preview = runCatching {
                Glide.with(context)
                    .asBitmap()
                    .load(url)
                    .submit(DEFAULT_WIDTH, DEFAULT_HEIGHT)
                    .get()
            }.getOrNull()
            if (preview != null) return preview
        }
        return BitmapFactory.decodeResource(context.resources, R.drawable.ic_map_placeholder)
    }
}
