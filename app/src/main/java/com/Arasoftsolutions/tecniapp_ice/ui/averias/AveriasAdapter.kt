package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import java.text.DateFormat
import java.util.Date
import java.text.Normalizer
import java.util.Locale

class AveriasAdapter(
    private val onVerDetalle: (AveriaUI) -> Unit,
    private val onVerMapa: (AveriaUI) -> Unit,
    private val onAsignar: (AveriaUI) -> Unit,
    private val onAtender: (AveriaUI) -> Unit,
    private val onResolver: (AveriaUI) -> Unit,
    private val onRevertir: (AveriaUI) -> Unit,
) : RecyclerView.Adapter<AveriasAdapter.VH>() {

    private val items = mutableListOf<AveriaUI>()

    /**
     * ✅ UID del técnico logueado (lo setea el Fragment/ViewModel).
     * Se usa para:
     * - permitir acciones solo al dueño
     * - bloquear si está asignada a otro técnico
     */
    var currentUserUid: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var currentUserRegion: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var currentUserVehiculo: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    private fun normalizeRegion(value: String): String {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
        return normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase(Locale.getDefault())
            .replace("[^a-z0-9]".toRegex(), "")
    }

    fun submitList(newItems: List<AveriaUI>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = items.size
            override fun getNewListSize(): Int = newItems.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return items[oldItemPosition].id == newItems[newItemPosition].id
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return items[oldItemPosition] == newItems[newItemPosition]
            }
        })
        items.clear()
        items.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_averia, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTitulo: TextView = view.findViewById(R.id.tvTitulo)
        private val chipEstado: Chip = view.findViewById(R.id.chipEstado)
        private val tvCausa: TextView = view.findViewById(R.id.tvCausa)
        private val tvObs: TextView = view.findViewById(R.id.tvObs)
        private val tvAsignado: TextView = view.findViewById(R.id.tvAsignado)
        private val tvAtendido: TextView = view.findViewById(R.id.tvAtendido)
        private val tvVehiculo: TextView = view.findViewById(R.id.tvVehiculo)
        private val tvNise: TextView = view.findViewById(R.id.tvNise)
        private val tvRegion: TextView = view.findViewById(R.id.tvRegion)
        private val tvAgencia: TextView = view.findViewById(R.id.tvAgencia)
        private val tvKilometraje: TextView = view.findViewById(R.id.tvKilometraje)
        private val tvCliente: TextView? = view.findViewById(R.id.tvCliente)
        private val tvLocalizacion: TextView? = view.findViewById(R.id.tvLocalizacion)
        private val tvDireccion: TextView? = view.findViewById(R.id.tvDireccion)
        private val tvFecha: TextView = view.findViewById(R.id.tvFecha)
        private val mapContainer: View = view.findViewById(R.id.cardMapa)
        private val imgMapa: ImageView = view.findViewById(R.id.imgMapa)
        private val btnVerMapa: MaterialButton = view.findViewById(R.id.btnVerMapa)
        private val btnAsignar: MaterialButton = view.findViewById(R.id.btnAsignar)
        private val btnAtender: MaterialButton = view.findViewById(R.id.btnAtender)
        private val btnResolver: MaterialButton = view.findViewById(R.id.btnResolver)

        fun bind(item: AveriaUI) {
            val context = itemView.context

            // ===============================
            // Reglas globales de bloqueo
            // - Resuelta por CLOR: solo lectura (bloqueo total)
            // - Asignada a otro técnico: se ve, pero sin acciones
            // ===============================
            val currentUid = currentUserUid
            val bloqueadaPorClor = item.estadoClor.equals("RESUELTA", ignoreCase = true)
            val estadoEnum = Estado.fromLabel(item.estado)
            val ownerUid = item.ownerUidFor(estadoEnum)
            val placaUsuario = currentUserVehiculo?.trim()
            val placaAveria = item.vehiculo?.trim().orEmpty()
            val pertenecePorVehiculo =
                placaUsuario != null &&
                placaAveria.isNotBlank() &&
                placaUsuario.equals(placaAveria, ignoreCase = true)
            val asignadaAOtro =
                if (!ownerUid.isNullOrBlank()) {
                    currentUid == null || (ownerUid != currentUid && !pertenecePorVehiculo)
                } else {
                    placaAveria.isNotBlank() && !pertenecePorVehiculo
                }
            val pertenece = if (estadoEnum == Estado.ANULADA && ownerUid.isNullOrBlank()) {
                true
            } else {
                !asignadaAOtro && (!ownerUid.isNullOrBlank() || pertenecePorVehiculo)
            }
            val regionMismatch = currentUserRegion?.let { regionUsuario ->
                val regionAveria = item.region.trim()
                if (regionAveria.isBlank()) {
                    false
                } else {
                    val userKey = normalizeRegion(regionUsuario)
                    val averiaKey = normalizeRegion(regionAveria)
                    userKey.isNotBlank() &&
                        averiaKey.isNotBlank() &&
                        !userKey.contains(averiaKey) &&
                        !averiaKey.contains(userKey)
                }
            } ?: false
            val readOnly = bloqueadaPorClor || asignadaAOtro || regionMismatch

            tvTitulo.text = item.descripcion

            // Chip: color por estado visible, texto informativo
            val estadoParaColor = if (bloqueadaPorClor) Estado.RESUELTA else estadoEnum

            chipEstado.text = when {
                bloqueadaPorClor -> "Resuelta (CLOR)"
                asignadaAOtro && estadoEnum == Estado.ASIGNADA -> "Asignada (Otro técnico)"
                else -> item.estado
            }
            chipEstado.isCheckable = false
            chipEstado.isClickable = false

            val emptyValue = context.getString(R.string.averia_pdf_empty_value)

            fun TextView.renderLabel(labelRes: Int, raw: CharSequence?, hideWhenEmpty: Boolean = false) {
                val normalized = raw?.toString()?.takeIf { it.isNotBlank() }
                if (hideWhenEmpty && normalized == null) {
                    isVisible = false
                    return
                }
                val value = normalized ?: emptyValue
                text = buildSpannedString {
                    bold { append(context.getString(labelRes)) }
                    append(' ')
                    append(value)
                }
                isVisible = true
            }

            val causaApp = item.causa?.takeIf { it.isNotBlank() }
            val causaClor = item.causaClor?.takeIf { it.isNotBlank() }
            val obsApp = item.observaciones?.takeIf { it.isNotBlank() }
            val obsClor = item.observacionesClor?.takeIf { it.isNotBlank() }
            val clorDisplay = listOf(
                context.getString(R.string.averia_label_causa_clor) to (causaClor ?: emptyValue),
                context.getString(R.string.averia_label_observaciones_clor) to (obsClor ?: emptyValue)
            ).joinToString("\n") { (label, value) -> "$label $value" }
            val tecnicoDisplay = listOf(
                context.getString(R.string.averia_label_causa) to (causaApp ?: emptyValue),
                context.getString(R.string.averia_label_observaciones) to (obsApp ?: emptyValue)
            ).joinToString("\n") { (label, value) -> "$label $value" }
            tvCausa.text = clorDisplay
            tvCausa.isVisible = true
            tvObs.text = tecnicoDisplay
            tvObs.isVisible = true

            val asignado = if (item.tecnico.isBlank()) {
                context.getString(R.string.averia_sin_asignar)
            } else {
                item.tecnico
            }
            tvAsignado.renderLabel(R.string.averia_label_asignado, asignado)

            val atendidoDisplay = item.resolvedAtendidoDisplay(emptyValue)
            tvAtendido.renderLabel(R.string.averia_label_atendido, atendidoDisplay)

            tvVehiculo.renderLabel(R.string.averia_label_vehiculo, item.vehiculo)
            tvNise.renderLabel(R.string.averia_label_nise, item.nise)
            tvRegion.renderLabel(R.string.averia_label_region, item.region)
            tvAgencia.renderLabel(R.string.averia_label_agencia, item.agencia)

            tvCliente?.let { label ->
                val cliente = item.cliente?.takeIf { it.isNotBlank() } ?: emptyValue
                val tipo = when (item.tipoAfectacion) {
                    TipoAfectacion.CLIENTE -> context.getString(R.string.averia_tipo_cliente)
                    TipoAfectacion.SECTOR -> context.getString(R.string.averia_tipo_sector)
                }
                val medidor = item.numeroMedidor?.takeIf { it.isNotBlank() }
                    ?.let { context.getString(R.string.averia_medidor_label, it) }
                val content = buildList {
                    add(tipo)
                    if (item.tipoAfectacion == TipoAfectacion.CLIENTE) {
                        add(cliente)
                        medidor?.let { add(it) }
                    }
                }.joinToString(" • ")
                label.renderLabel(R.string.averia_label_cliente, content)
            }

            tvLocalizacion?.let { label ->
                val detalles = buildList {
                    item.medidorPueblo?.takeIf { it.isNotBlank() }?.let {
                        add(context.getString(R.string.averia_medidor_pueblo, it))
                    }
                    item.medidorCalle?.takeIf { it.isNotBlank() }?.let {
                        add(context.getString(R.string.averia_medidor_calle, it))
                    }
                    item.medidorPoste?.takeIf { it.isNotBlank() }?.let {
                        add(context.getString(R.string.averia_medidor_poste, it))
                    }
                    item.medidorMetros?.takeIf { it.isNotBlank() }?.let {
                        add(context.getString(R.string.averia_medidor_metros, it))
                    }
                }
                val base = item.localizacion?.takeIf { it.isNotBlank() }
                val content = when {
                    base != null && detalles.isNotEmpty() -> buildString {
                        append(base)
                        append(" • ")
                        append(detalles.joinToString(" • "))
                    }
                    base != null -> base
                    detalles.isNotEmpty() -> detalles.joinToString(" • ")
                    else -> null
                }
                label.renderLabel(R.string.averia_label_localizacion, content)
            }

            tvDireccion?.renderLabel(R.string.averia_label_direccion, item.direccion, hideWhenEmpty = true)

            val formatDateTime = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            val fechaEvento = formatDateTime.format(Date(item.fechaMillis))
            val inicioEvento = item.horaInicio?.let { formatDateTime.format(Date(it)) }
                ?: item.horaAtencionInicio?.let { formatDateTime.format(Date(it)) }
            val llegadaEvento = item.horaLlegada?.let { formatDateTime.format(Date(it)) }
            val finEvento = item.horaFinal?.let { formatDateTime.format(Date(it)) }
                ?: item.horaAtencionFinal?.let { formatDateTime.format(Date(it)) }

            tvFecha.text = buildSpannedString {
                bold { append(context.getString(R.string.averia_label_evento)) }
                append(' ')
                append(fechaEvento)
                inicioEvento?.let {
                    append('\n')
                    bold { append(context.getString(R.string.averia_label_inicio)) }
                    append(' ')
                    append(it)
                }
                llegadaEvento?.let {
                    append('\n')
                    bold { append(context.getString(R.string.averia_hora_llegada_hint)) }
                    append(' ')
                    append(it)
                }
                finEvento?.let {
                    append('\n')
                    bold { append(context.getString(R.string.averia_label_fin)) }
                    append(' ')
                    append(it)
                }
            }

            val kilometrajeInicio = item.kilometrajeInicio?.toString()
            val kilometrajeLlegada = item.kilometrajeLlegada?.toString()
            val kilometrajeFinal = item.kilometrajeFinal?.toString()
            val kilometrajes = listOfNotNull(
                kilometrajeInicio?.let {
                    context.getString(R.string.averia_km_inicio_hint) to it
                },
                kilometrajeLlegada?.let {
                    context.getString(R.string.averia_km_llegada_hint) to it
                },
                kilometrajeFinal?.let {
                    context.getString(R.string.averia_km_fin_hint) to it
                }
            )
            if (kilometrajes.isNotEmpty()) {
                tvKilometraje.text = buildSpannedString {
                    bold { append(context.getString(R.string.averia_label_kilometraje)) }
                    kilometrajes.forEach { (label, value) ->
                        append('\n')
                        bold { append(label) }
                        append(' ')
                        append(value)
                    }
                }
                tvKilometraje.isVisible = true
            } else {
                tvKilometraje.isVisible = false
            }

            // ✅ Colores: si CLOR resolvió, se pinta como RESUELTA (verde)
            val chipColor = when (estadoParaColor) {
                Estado.PENDIENTE -> ContextCompat.getColor(context, R.color.chip_pendiente)
                Estado.ASIGNADA -> ContextCompat.getColor(context, R.color.chip_asignada)
                Estado.EN_ATENCION -> ContextCompat.getColor(context, R.color.chip_en_atencion)
                Estado.RESUELTA -> ContextCompat.getColor(context, R.color.chip_resuelta)
                Estado.ANULADA -> ContextCompat.getColor(context, R.color.chip_anulada)
            }
            chipEstado.chipBackgroundColor = ColorStateList.valueOf(chipColor)
            chipEstado.setTextColor(ContextCompat.getColor(context, android.R.color.white))

            // ✅ Coordenadas (evita warning “always true”)
            val lat = item.lat
            val lng = item.lng
            val hasCoords = !(lat == 0.0 && lng == 0.0)

            mapContainer.isVisible = hasCoords
            btnVerMapa.isVisible = hasCoords
            btnVerMapa.isEnabled = hasCoords

            if (hasCoords) {
                val mapLabel = item.direccion?.takeIf { it.isNotBlank() } ?: item.agencia
                val mapUrl = AveriaStaticMapProvider.buildUrl(context, lat, lng, mapLabel)
                if (mapUrl != null) {
                    AveriaStaticMapProvider.loadInto(context, imgMapa, mapUrl)
                } else {
                    imgMapa.setImageResource(R.drawable.ic_map_placeholder)
                }
            } else {
                imgMapa.setImageResource(R.drawable.ic_map_placeholder)
            }

            if (hasCoords) {
                mapContainer.isClickable = true
                mapContainer.isFocusable = true
                mapContainer.setOnClickListener { onVerMapa(item) }
            } else {
                mapContainer.isClickable = false
                mapContainer.isFocusable = false
                mapContainer.setOnClickListener(null)
            }

            btnVerMapa.setOnClickListener { onVerMapa(item) }

            if (regionMismatch) {
                itemView.setOnClickListener(null)
                itemView.isClickable = false
            } else {
                // ✅ Ver detalle SIEMPRE (aunque sea readOnly)
                itemView.setOnClickListener { onVerDetalle(item) }
                itemView.isClickable = true
            }

            // ✅ Los callbacks se mantienen, pero los botones se bloquean abajo si hace falta
            btnAsignar.setOnClickListener { onAsignar(item) }
            btnAtender.setOnClickListener { onAtender(item) }
            btnResolver.setOnClickListener { onResolver(item) }

            // ===============================
            // Botones por estado (flujo normal)
            // ===============================
            when (estadoEnum) {
               Estado.PENDIENTE -> {
                btnAsignar.apply {
                    text = context.getString(R.string.averia_asignar)
                    isEnabled = true
                    isVisible = true
                    alpha = 1f
                }
                btnAtender.apply {
                    text = context.getString(R.string.averia_atender)
                    isEnabled = true
                    isVisible = true
                    alpha = 1f
                }
                btnResolver.isVisible = false
            }

                Estado.ASIGNADA -> {
                    btnAsignar.apply {
                        text = context.getString(R.string.averia_eliminar_asignacion)
                        isEnabled = pertenece
                        isVisible = true
                    }
                    btnAtender.apply {
                        text = context.getString(R.string.averia_atender)
                        isEnabled = pertenece
                        isVisible = true
                    }
                    btnResolver.isVisible = false
                }
                Estado.EN_ATENCION -> {
                    btnAsignar.apply {
                        isEnabled = false
                        isVisible = false
                    }
                    btnAtender.apply {
                        text = context.getString(R.string.averia_cancelar_atencion)
                        isEnabled = pertenece
                        isVisible = true
                    }
                    btnResolver.apply {
                        text = context.getString(R.string.averia_resolver)
                        isEnabled = pertenece
                        isVisible = true
                    }
                }
                Estado.RESUELTA -> {
                    btnAsignar.isVisible = false
                    btnAtender.isVisible = false
                    btnResolver.apply {
                        text = context.getString(R.string.averia_exportar_pdf)
                        isEnabled = pertenece
                        isVisible = pertenece   // <- si querés que ni aparezca cuando no pertenece
                        alpha = if (pertenece) 1f else 0.45f
                    }
                }

                Estado.ANULADA -> {
                    btnAsignar.apply {
                        text = context.getString(R.string.averia_revertir_pendiente)
                        isEnabled = pertenece
                        isVisible = true
                        alpha = if (pertenece) 1f else 0.45f
                        setOnClickListener { onRevertir(item) }
                    }
                    btnAtender.isVisible = false
                    btnResolver.isVisible = false
                }
            }

            // ==========================================================
            // Bloqueo final (NO ocultamos la avería, solo deshabilitamos)
            // - CLOR resuelta: oculta acciones para evitar confusión
            // - Asignada a otro: mantiene botones visibles según estado, pero disabled
            // ==========================================================
            if (regionMismatch) {
                btnAsignar.isVisible = false
                btnAtender.isVisible = false
                btnResolver.isVisible = false
            } else if (bloqueadaPorClor) {
                // Caso cerrado por CLOR: en lista no se permite ninguna acción.
                btnAsignar.isVisible = false
                btnAtender.isVisible = false
                btnResolver.isVisible = false
            } else if (asignadaAOtro) {
                // Asignada a otro técnico: se ve, pero sin acciones.
                btnAsignar.isEnabled = false
                btnAtender.isEnabled = false
                btnResolver.isEnabled = false

                // Señal visual suave
                btnAsignar.alpha = if (btnAsignar.isVisible) 0.45f else 1f
                btnAtender.alpha = if (btnAtender.isVisible) 0.45f else 1f
                btnResolver.alpha = if (btnResolver.isVisible) 0.45f else 1f
            } else {
                // Normal
                btnAsignar.alpha = 1f
                btnAtender.alpha = 1f
                btnResolver.alpha = 1f
            }

            // readOnly está calculado arriba por claridad; no se usa directo aquí,
            // pero sirve para debug y para futuras decisiones UI.
            @Suppress("UNUSED_VARIABLE")
            val _readOnly = readOnly
        }
    }
}
