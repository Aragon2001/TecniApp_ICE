package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.Arasoftsolutions.tecniapp_ice.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object AveriaMapLauncher {
    private val PREFERRED_PACKAGES = listOf(
        "com.google.android.apps.maps",
        "com.esri.fieldmaps"
    )

    fun show(
        context: Context,
        lat: Double,
        lng: Double,
        label: String?,
        onUnavailable: (() -> Unit)? = null
    ) {
        if (lat == 0.0 && lng == 0.0) {
            onUnavailable?.invoke()
            return
        }
        val pm = context.packageManager
        val encodedLabel = label?.takeIf { it.isNotBlank() }?.let { Uri.encode(it) }
        val geoUri = if (encodedLabel != null) {
            Uri.parse("geo:$lat,$lng?q=$lat,$lng($encodedLabel)")
        } else {
            Uri.parse("geo:$lat,$lng")
        }
        val baseIntent = Intent(Intent.ACTION_VIEW, geoUri)
        val resolved = pm.queryIntentActivities(baseIntent, PackageManager.MATCH_DEFAULT_ONLY)
        if (resolved.isEmpty()) {
            onUnavailable?.invoke()
            return
        }
        val options = resolved.map { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            val intent = Intent(baseIntent).setPackage(packageName)
            MapAppOption(
                label = resolveInfo.loadLabel(pm).toString(),
                icon = resolveInfo.loadIcon(pm),
                intent = intent,
                priority = PREFERRED_PACKAGES.indexOf(packageName).takeIf { it >= 0 }
                    ?: PREFERRED_PACKAGES.size
            )
        }.sortedWith(compareBy<MapAppOption> { it.priority }.thenBy { it.label.lowercase() })

        if (options.size == 1) {
            context.startActivity(options.first().intent)
            return
        }

        val adapter = MapAppAdapter(context, options)
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.averia_map_select_app)
            .setAdapter(adapter) { _, which ->
                val option = options.getOrNull(which)
                if (option != null) {
                    context.startActivity(option.intent)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private data class MapAppOption(
        val label: String,
        val icon: Drawable,
        val intent: Intent,
        val priority: Int
    )

    private class MapAppAdapter(
        context: Context,
        private val items: List<MapAppOption>
    ) : android.widget.ArrayAdapter<MapAppOption>(context, 0, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_map_app_option, parent, false)
            val option = getItem(position) ?: return view
            val iconView = view.findViewById<ImageView>(R.id.imgAppIcon)
            val labelView = view.findViewById<TextView>(R.id.tvAppName)
            iconView.setImageDrawable(option.icon)
            labelView.text = option.label
            return view
        }

        override fun areAllItemsEnabled(): Boolean = true

        override fun isEnabled(position: Int): Boolean = position in items.indices
    }
}
