package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.Arasoftsolutions.tecniapp_ice.R

object FieldMapsIntents {
    private const val FIELD_MAPS_PACKAGE = "com.esri.fieldmaps"

    private fun buildGeoUri(lat: Double, lng: Double, label: String?): Uri {
        val safeLabel = label?.takeIf { it.isNotBlank() } ?: "$lat,$lng"
        val query = Uri.encode("$lat,$lng ($safeLabel)")
        return Uri.parse("geo:$lat,$lng?q=$query")
    }

    private fun fieldMapsIntent(lat: Double, lng: Double, label: String?): Intent {
        return Intent(Intent.ACTION_VIEW, buildGeoUri(lat, lng, label)).apply {
            setPackage(FIELD_MAPS_PACKAGE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }

    fun launch(context: Context, lat: Double, lng: Double, label: String?) {
        val intent = fieldMapsIntent(lat, lng, label)
        val pm = context.packageManager
        if (intent.resolveActivity(pm) != null) {
            context.startActivity(intent)
            return
        }
        Toast.makeText(
            context,
            context.getString(R.string.averia_field_maps_required),
            Toast.LENGTH_LONG
        ).show()
    }

    fun pendingIntent(
        context: Context,
        lat: Double?,
        lng: Double?,
        label: String?,
        requestCode: Int
    ): PendingIntent? {
        if (lat == null || lng == null) return null
        if (lat == 0.0 && lng == 0.0) return null
        val intent = fieldMapsIntent(lat, lng, label)
        val pm = context.packageManager
        if (intent.resolveActivity(pm) == null) {
            return null
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
    }
}
