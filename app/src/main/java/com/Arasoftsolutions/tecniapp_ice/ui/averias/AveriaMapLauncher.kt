package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.app.PendingIntent
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
    private const val PACKAGE_FIELD_MAPS = "com.esri.fieldmaps"
    private const val PACKAGE_GOOGLE_MAPS = "com.google.android.apps.maps"
    private const val PACKAGE_WAZE = "com.waze"

    private val PREFERRED_PACKAGES = listOf(
        PACKAGE_FIELD_MAPS,
        PACKAGE_GOOGLE_MAPS,
        PACKAGE_WAZE
    )

    fun show(
        context: Context,
        lat: Double,
        lng: Double,
        label: String?,
        onUnavailable: (() -> Unit)? = null
    ) {
        val options = resolveOptions(context, lat, lng, label)
        if (options.isEmpty()) {
            onUnavailable?.invoke()
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

    fun pendingIntent(
        context: Context,
        lat: Double?,
        lng: Double?,
        label: String?,
        requestCode: Int
    ): PendingIntent? {
        if (lat == null || lng == null) return null
        if (lat == 0.0 && lng == 0.0) return null
        val options = resolveOptions(context, lat, lng, label)
        if (options.isEmpty()) return null
        val baseIntent = options.first().intent
        val extraIntents = options.drop(1).map { it.intent }.toTypedArray()
        val chooser = Intent.createChooser(
            baseIntent,
            context.getString(R.string.averia_map_select_app)
        ).apply {
            if (extraIntents.isNotEmpty()) {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntents)
            }
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            chooser,
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun resolveOptions(
        context: Context,
        lat: Double,
        lng: Double,
        label: String?
    ): List<MapAppOption> {
        if (lat == 0.0 && lng == 0.0) return emptyList()
        val pm = context.packageManager
        val geoUri = buildGeoUri(lat, lng, label)
        val preferred = buildPreferredOptions(pm, geoUri, lat, lng)

        val baseIntent = Intent(Intent.ACTION_VIEW, geoUri)
        val resolved = pm.queryIntentActivities(baseIntent, PackageManager.MATCH_DEFAULT_ONLY)
        if (resolved.isEmpty() && preferred.isEmpty()) return emptyList()

        val resolvedOptions = resolved.mapNotNull { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            val intent = Intent(baseIntent).setPackage(packageName)
            val priorityIndex = PREFERRED_PACKAGES.indexOf(packageName)
            val priority = if (priorityIndex >= 0) priorityIndex else Int.MAX_VALUE / 2
            MapAppOption(
                label = resolveInfo.loadLabel(pm).toString(),
                icon = resolveInfo.loadIcon(pm),
                intent = intent,
                priority = priority,
                packageName = packageName
            )
        }.filterNot { option -> preferred.any { it.packageName == option.packageName } }

        return (preferred + resolvedOptions)
            .sortedWith(compareBy<MapAppOption> { it.priority }.thenBy { it.label.lowercase() })
    }

    private fun buildPreferredOptions(
        pm: PackageManager,
        geoUri: Uri,
        lat: Double,
        lng: Double
    ): List<MapAppOption> {
        val preferredIntents = listOf(
            PACKAGE_FIELD_MAPS to Intent(Intent.ACTION_VIEW, geoUri).setPackage(PACKAGE_FIELD_MAPS),
            PACKAGE_GOOGLE_MAPS to Intent(Intent.ACTION_VIEW, geoUri).setPackage(PACKAGE_GOOGLE_MAPS),
            PACKAGE_WAZE to Intent(
                Intent.ACTION_VIEW,
                Uri.parse("waze://?ll=$lat,$lng&navigate=yes")
            ).setPackage(PACKAGE_WAZE)
        )

        return preferredIntents.mapIndexedNotNull { index, (packageName, intent) ->
            val activityResolved = intent.resolveActivity(pm) ?: return@mapIndexedNotNull null
            val appInfo = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull()
                ?: return@mapIndexedNotNull null
            val labelText = pm.getApplicationLabel(appInfo).toString()
            val icon = pm.getApplicationIcon(packageName)
            MapAppOption(
                label = labelText,
                icon = icon,
                intent = Intent(intent).apply { setPackage(packageName) },
                priority = index,
                packageName = activityResolved.packageName
            )
        }
    }

    private fun buildGeoUri(lat: Double, lng: Double, label: String?): Uri {
        val encodedLabel = label?.takeIf { it.isNotBlank() }?.let { Uri.encode(it) }
        return if (encodedLabel != null) {
            Uri.parse("geo:$lat,$lng?q=$lat,$lng($encodedLabel)")
        } else {
            Uri.parse("geo:$lat,$lng")
        }
    }

    private data class MapAppOption(
        val label: String,
        val icon: Drawable,
        val intent: Intent,
        val priority: Int,
        val packageName: String
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
