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
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.Arasoftsolutions.tecniapp_ice.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

object AveriaMapLauncher {

    private const val PACKAGE_FIELD_MAPS = "com.esri.fieldmaps"
    private const val PACKAGE_GOOGLE_MAPS = "com.google.android.apps.maps"
    private const val PACKAGE_WAZE = "com.waze"

    /**
     * Muestra un diálogo con las opciones:
     *  - Field Maps
     *  - Google Maps
     *  - Waze
     *  - Navegador web
     *
     * Usa las mismas URIs que tu método mostrarOpcionesDeNavegacion()
     */
    fun show(
        context: Context,
        lat: Double,
        lng: Double,
        label: String? = null,
        onUnavailable: (() -> Unit)? = null
    ) {
        // Por seguridad, si vienen 0,0 no mostramos nada
        if (lat == 0.0 && lng == 0.0) {
            onUnavailable?.invoke()
            return
        }

        val options = buildOptions(context, lat, lng, label)
        if (options.isEmpty()) {
            onUnavailable?.invoke()
            return
        }

        val adapter = MapAppAdapter(context, options)

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.averia_map_select_app)
            .setAdapter(adapter) { _, which ->
                val option = options.getOrNull(which) ?: return@setAdapter
                try {
                    context.startActivity(option.intent)
                } catch (e: Exception) {
                    // Mensaje específico por opción (igual que en tu función del fragment)
                    Toast.makeText(context, option.errorMessage, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Crea un PendingIntent que abre un chooser con las apps de mapas disponibles.
     * Útil si quieres usarlo en una notificación.
     */
    fun pendingIntent(
        context: Context,
        lat: Double?,
        lng: Double?,
        label: String? = null,
        requestCode: Int
    ): PendingIntent? {
        if (lat == null || lng == null) return null
        if (lat == 0.0 && lng == 0.0) return null

        val pm = context.packageManager
        val allOptions = buildOptions(context, lat, lng, label)

        // Para el PendingIntent sí filtramos los que el sistema realmente puede abrir
        val availableOptions = allOptions.filter { option ->
            option.intent.resolveActivity(pm) != null
        }
        if (availableOptions.isEmpty()) return null

        val baseIntent = availableOptions.first().intent
        val extraIntents = availableOptions.drop(1).map { it.intent }.toTypedArray()

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

    // --------------------------------------------------------------------
    //  Construcción de opciones usando TUS mismas URIs que sí funcionan
    // --------------------------------------------------------------------
    private fun buildOptions(
        context: Context,
        lat: Double,
        lng: Double,
        label: String?
    ): List<MapAppOption> {
        val centerParam = String.format(Locale.US, "%f,%f", lat, lng)

        // URIs iguales a las de tu fragmento Localización, pero sin navegador web
        val fieldMapsUri = Uri.parse(
            "arcgis-fieldmaps://?referenceContext=center&itemID=&center=$centerParam"
        )
        val googleMapsNavUri = Uri.parse("google.navigation:q=$centerParam")   // navegación
        val googleMapsMapUri = Uri.parse("geo:$centerParam?q=$centerParam")    // mapa normal
        val wazeUri = Uri.parse("waze://?ll=$centerParam&navigate=yes")

        // Intents dirigidos a cada app
        val fieldMapsIntent = Intent(Intent.ACTION_VIEW, fieldMapsUri).apply {
    setPackage("com.esri.fieldmaps")
}

val googleMapsNavIntent = Intent(Intent.ACTION_VIEW, googleMapsNavUri).apply {
    setPackage("com.google.android.apps.maps")
}

val googleMapsMapIntent = Intent(Intent.ACTION_VIEW, googleMapsMapUri).apply {
    setPackage("com.google.android.apps.maps")
}

val wazeIntent = Intent(Intent.ACTION_VIEW, wazeUri).apply {
    setPackage("com.waze")
}


        // Mensajes de error específicos (igual que en tu código)
        val opciones = listOf(
            MapAppOption(
                label = "Field Maps",
                packageName = PACKAGE_FIELD_MAPS,
                intent = fieldMapsIntent,
                errorMessage = "Field Maps no está instalado o el vínculo es inválido."
            ),
            MapAppOption(
                label = "Google Maps",
                packageName = PACKAGE_GOOGLE_MAPS,
                intent = googleMapsMapIntent,
                errorMessage = "Google Maps no está disponible."
            ),
            MapAppOption(
                label = "Waze",
                packageName = PACKAGE_WAZE,
                intent = wazeIntent,
                errorMessage = "Waze no está instalado."
            ),
            MapAppOption(
                label = "Navegar con Google Maps",
                packageName = null, // no depende de un paquete específico
                intent = googleMapsNavIntent,
                errorMessage = "No se pudo abrir en el navegador."
            )
        )

        return opciones
    }

    // --------------------------------------------------------------------
    //  Clases auxiliares y Adapter para el diálogo con íconos
    // --------------------------------------------------------------------
    private data class MapAppOption(
        val label: String,
        val packageName: String?,
        val intent: Intent,
        val errorMessage: String
    )

  private class MapAppAdapter(
    context: Context,
    private val items: List<MapAppOption>
) : android.widget.ArrayAdapter<MapAppOption>(context, 0, items) {

    private val pm: PackageManager = context.packageManager
    private val fallbackIcon: Drawable =
        ContextCompat.getDrawable(context, R.drawable.ic_map_placeholder)
            ?: context.applicationInfo.loadIcon(pm)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_map_app_option, parent, false)

        val option = getItem(position) ?: return view

        val iconView = view.findViewById<ImageView>(R.id.imgAppIcon)
        val labelView = view.findViewById<TextView>(R.id.tvAppName)

        // 1) Intentamos resolver la activity que va a manejar el intent
        val icon = try {
            val resolved = option.intent.resolveActivity(pm)
            if (resolved != null) {
                pm.getApplicationIcon(resolved.packageName)
            } else {
                // 2) Si no resolvió, probamos con packageName “a mano”
                option.packageName?.let { pkg ->
                    pm.getApplicationIcon(pkg)
                } ?: fallbackIcon
            }
        } catch (e: Exception) {
            fallbackIcon
        }

        iconView.setImageDrawable(icon)
        labelView.text = option.label

        return view
    }

    override fun areAllItemsEnabled(): Boolean = true
    override fun isEnabled(position: Int): Boolean = position in items.indices
}

}
