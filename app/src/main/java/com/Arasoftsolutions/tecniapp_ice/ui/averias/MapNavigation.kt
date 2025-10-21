package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.Arasoftsolutions.tecniapp_ice.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

object MapNavigation {
    private const val FIELD_MAPS_PACKAGE = "com.esri.fieldmaps"
    private const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

    fun open(context: Context, lat: Double, lng: Double, label: String?) {
        if (lat == 0.0 && lng == 0.0) {
            Toast.makeText(context, R.string.averia_map_error_no_location, Toast.LENGTH_SHORT).show()
            return
        }
        val centerParam = String.format(Locale.US, "%f,%f", lat, lng)
        val encodedLabel = Uri.encode(label?.takeIf { it.isNotBlank() } ?: centerParam)

        val fieldMapsUri = Uri.parse("arcgis-fieldmaps://?referenceContext=center&itemID=&center=$centerParam")
        val googleMapsUri = Uri.parse("geo:$centerParam?q=$encodedLabel")
        val browserUri = Uri.parse("https://maps.google.com/?q=$centerParam")

        val fieldMapsIntent = Intent(Intent.ACTION_VIEW, fieldMapsUri).apply {
            setPackage(FIELD_MAPS_PACKAGE)
        }
        val googleMapsIntent = Intent(Intent.ACTION_VIEW, googleMapsUri).apply {
            setPackage(GOOGLE_MAPS_PACKAGE)
        }
        val browserIntent = Intent(Intent.ACTION_VIEW, browserUri)

        val options = arrayOf(
            context.getString(R.string.averia_map_option_field_maps),
            context.getString(R.string.averia_map_option_google_maps),
            context.getString(R.string.averia_map_option_browser)
        )

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.averia_map_dialog_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> launchSafely(context, fieldMapsIntent, R.string.averia_map_error_field_maps)
                    1 -> launchSafely(context, googleMapsIntent, R.string.averia_map_error_google_maps)
                    2 -> launchSafely(context, browserIntent, R.string.averia_map_error_browser)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun launchSafely(context: Context, intent: Intent, errorRes: Int) {
        try {
            context.startActivity(intent)
        } catch (t: Throwable) {
            Toast.makeText(context, errorRes, Toast.LENGTH_LONG).show()
        }
    }
}
