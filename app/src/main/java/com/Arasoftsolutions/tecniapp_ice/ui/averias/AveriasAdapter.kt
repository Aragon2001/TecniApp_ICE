package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.sun.mail.imap.protocol.FetchResponse.getItem
import java.text.DateFormat
import java.util.Date

class AveriasAdapter(
    private val onVerDetalle: (AveriaUI) -> Unit,
    private val onAsignar: (AveriaUI) -> Unit,
    private val onAtender: (AveriaUI) -> Unit,
    private val onResolver: (AveriaUI) -> Unit,
) : RecyclerView.Adapter<AveriasAdapter.VH>() {

    private val items = mutableListOf<AveriaUI>()

    var currentUserUid: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
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
        private val btnAsignar: MaterialButton = view.findViewById(R.id.btnAsignar)
        private val btnAtender: MaterialButton = view.findViewById(R.id.btnAtender)
        private val btnResolver: MaterialButton = view.findViewById(R.id.btnResolver)

        fun bind(item: AveriaUI) {
            val context = itemView.context
            tvTitulo.text = item.descripcion
            chipEstado.text = item.estado
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

            tvCausa.renderLabel(R.string.averia_label_causa, item.causa)
            tvObs.renderLabel(R.string.averia_label_observaciones, item.observaciones)

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

            if (item.kilometrajeInicio != null || item.kilometrajeFinal != null) {
                val inicio = item.kilometrajeInicio?.toString() ?: emptyValue
                val fin = item.kilometrajeFinal?.toString() ?: emptyValue
                tvKilometraje.renderLabel(
                    R.string.averia_label_kilometraje,
                    "$inicio → $fin",
                )
            } else {
                tvKilometraje.isVisible = false
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
                val extras = buildList {
                    if (tipo.isNotBlank()) add(tipo)
                    if (medidor != null) add(medidor)
                }
                val content = if (extras.isNotEmpty()) {
                    buildString {
                        append(cliente)
                        append(" • ")
                        append(extras.joinToString(" • "))
                    }
                } else cliente
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

            tvDireccion?.let { label ->
                label.renderLabel(R.string.averia_label_direccion, item.direccion, hideWhenEmpty = true)
            }

            val fechaEvento = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(item.fechaMillis))
            val inicioAtencion = item.horaAtencionInicio?.let {
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
            }
            val finAtencion = item.horaAtencionFinal?.let {
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
            }

            tvFecha.text = buildSpannedString {
                bold { append(context.getString(R.string.averia_label_evento)) }
                append(' ')
                append(fechaEvento)
                inicioAtencion?.let {
                    append('\n')
                    bold { append(context.getString(R.string.averia_label_inicio)) }
                    append(' ')
                    append(it)
                }
                finAtencion?.let {
                    append('\n')
                    bold { append(context.getString(R.string.averia_label_fin)) }
                    append(' ')
                    append(it)
                }
            }

            val estadoEnum = Estado.fromLabel(item.estado)
            val chipColor = when (estadoEnum) {
                Estado.PENDIENTE -> ContextCompat.getColor(context, R.color.chip_pendiente)
                Estado.ASIGNADA -> ContextCompat.getColor(context, R.color.chip_asignada)
                Estado.EN_ATENCION -> ContextCompat.getColor(context, R.color.chip_en_atencion)
                Estado.RESUELTA -> ContextCompat.getColor(context, R.color.chip_resuelta)
                Estado.ANULADA -> ContextCompat.getColor(context, R.color.chip_anulada)
            }
            chipEstado.chipBackgroundColor = ColorStateList.valueOf(chipColor)
            chipEstado.setTextColor(ContextCompat.getColor(context, android.R.color.white))

            itemView.setOnClickListener { onVerDetalle(item) }
            btnAsignar.setOnClickListener { onAsignar(item) }
            btnAtender.setOnClickListener { onAtender(item) }
            btnResolver.setOnClickListener { onResolver(item) }

            val currentUid = currentUserUid
            val pertenece = currentUid != null && item.tecnicoUid == currentUid

            when (estadoEnum) {
                Estado.PENDIENTE -> {
                    btnAsignar.apply {
                        text = context.getString(R.string.averia_asignar)
                        isEnabled = true
                        isVisible = true
                    }
                    btnAtender.apply {
                        text = context.getString(R.string.averia_atender)
                        isEnabled = true
                        isVisible = true
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
                        isEnabled = true
                        isVisible = true
                    }
                }
                Estado.ANULADA -> {
                    btnAsignar.isVisible = false
                    btnAtender.isVisible = false
                    btnResolver.isVisible = false
                }
            }
        }
    }
}
