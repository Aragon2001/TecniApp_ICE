package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.R
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import java.text.DateFormat
import java.util.Date
import java.io.Serializable

data class AveriaUI(
    val id: String,
    val descripcion: String,
    val fechaMillis: Long,
    val causa: String,
    val estado: String,
    val tecnico: String,
    val tecnicoUid: String?,
    val atendidoPor: String,
    val atendidoPorUid: String?,
    val observaciones: String,
    val nise: String,
    val agencia: String,
    val region: String,
    val zonaTag: String,
    val lat: Double,
    val lng: Double,
    val vehiculo: String?,
    val materialesResumen: String,
    val materialesDetalle: List<MaterialUso>,
    val horaAtencionInicio: Long?,
    val horaAtencionFinal: Long?,
    val kilometrajeInicio: Double?,
    val kilometrajeFinal: Double?,
    val horaInicio: Long?,
    val horaFinal: Long?
) : Serializable

class AveriasAdapter(
    private val onVerDetalle: (AveriaUI) -> Unit,
    private val onAsignar: (AveriaUI) -> Unit,
    private val onAtender: (AveriaUI) -> Unit,
    private val onResolver: (AveriaUI) -> Unit
) : ListAdapter<AveriaUI, AveriasAdapter.VH>(Diff()) {

    var currentUserUid: String? = null

    class Diff : DiffUtil.ItemCallback<AveriaUI>() {
        override fun areItemsTheSame(old: AveriaUI, new: AveriaUI) = old.id == new.id
        override fun areContentsTheSame(old: AveriaUI, new: AveriaUI) = old == new
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        // UI
        private val tvTitulo: TextView = view.findViewById(R.id.tvTitulo)
        private val chipEstado: Chip = view.findViewById(R.id.chipEstado)

        private val tvCausa: TextView = view.findViewById(R.id.tvCausa)
        private val tvObs: TextView = view.findViewById(R.id.tvObs)

        private val tvCaso: TextView = view.findViewById(R.id.tvCaso)
        private val tvAsignado: TextView = view.findViewById(R.id.tvAsignado)
        private val tvAtendido: TextView = view.findViewById(R.id.tvAtendido)
        private val tvVehiculo: TextView = view.findViewById(R.id.tvVehiculo)
        private val tvNise: TextView = view.findViewById(R.id.tvNise)
        private val tvRegion: TextView = view.findViewById(R.id.tvRegion)
        private val tvAgencia: TextView = view.findViewById(R.id.tvAgencia)
        private val tvMateriales: TextView = view.findViewById(R.id.tvMateriales)
        private val tvKilometraje: TextView = view.findViewById(R.id.tvKilometraje)

        private val tvCoords: TextView = view.findViewById(R.id.tvCoords)
        private val tvFecha: TextView = view.findViewById(R.id.tvFecha)

        private val btnAsignar: MaterialButton = view.findViewById(R.id.btnAsignar)
        private val btnAtender: MaterialButton = view.findViewById(R.id.btnAtender)
        private val btnResolver: MaterialButton = view.findViewById(R.id.btnResolver)
        private val btnVer: MaterialButton = view.findViewById(R.id.btnVer)

        private val imgMapa: ImageView = view.findViewById(R.id.imgMapa)

        fun bind(item: AveriaUI) {
            // Título y estado
            tvTitulo.text = item.descripcion
            chipEstado.text = item.estado

            // Miniatura de mapa
            val lat = item.lat
            val lng = item.lng
            if (lat != 0.0 && lng != 0.0) {
                val url = "https://maps.googleapis.com/maps/api/staticmap?" +
                        "center=$lat,$lng&zoom=16&size=300x300&maptype=roadmap" +
                        "&markers=color:red|$lat,$lng&key=AIzaSyBxgf6oA-rRK1-OlNft4oDgzF3gokLl1FU"

                Glide.with(itemView.context)
                    .load(url)
                    .placeholder(R.drawable.placeholder_mapa)
                    .into(imgMapa)
            } else {
                imgMapa.setImageResource(R.drawable.placeholder_mapa)
            }

            // Textos detallados
            tvCausa.text = item.causa
            tvObs.text = item.observaciones
            tvCaso.text = "Caso: ${item.id}"
            tvAsignado.text = "Asignado a: ${if (item.tecnico.isBlank()) itemView.context.getString(R.string.averia_sin_asignar) else item.tecnico}"
            tvAtendido.text = "Atendido por: ${if (item.atendidoPor.isBlank()) "—" else item.atendidoPor}"
            tvVehiculo.text = "Vehículo: ${item.vehiculo ?: "—"}"
            tvNise.text = "NISE: ${item.nise}"
            tvRegion.text = "Región: ${item.region}"
            tvAgencia.text = "Agencia: ${item.agencia}"
            tvMateriales.visibility = if (item.materialesResumen.isBlank()) View.GONE else View.VISIBLE
            tvMateriales.text = itemView.context.getString(R.string.averia_materiales_label, item.materialesResumen)
            if (item.kilometrajeInicio != null || item.kilometrajeFinal != null) {
                tvKilometraje.visibility = View.VISIBLE
                val inicio = item.kilometrajeInicio?.let { "${it}" } ?: "—"
                val fin = item.kilometrajeFinal?.let { "${it}" } ?: "—"
                tvKilometraje.text = itemView.context.getString(R.string.averia_kilometraje_label, inicio, fin)
            } else {
                tvKilometraje.visibility = View.GONE
            }

            // Coordenadas + fecha
            tvCoords.text = if (lat == 0.0 && lng == 0.0) "Coords: —" else "Coords: $lat, $lng"
            val fechaEvento = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(item.fechaMillis))
            val inicioAtencion = item.horaAtencionInicio?.let {
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(it))
            }
            val finAtencion = item.horaAtencionFinal?.let {
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(it))
            }
            tvFecha.text = buildString {
                append(itemView.context.getString(R.string.averia_fecha_evento_label, fechaEvento))
                if (inicioAtencion != null) {
                    append('\n')
                    append(itemView.context.getString(R.string.averia_fecha_atencion_inicio, inicioAtencion))
                }
                if (finAtencion != null) {
                    append('\n')
                    append(itemView.context.getString(R.string.averia_fecha_atencion_fin, finAtencion))
                }
            }

            val estadoEnum = Estado.fromLabel(item.estado)
            val chipColor = when (estadoEnum) {
                Estado.PENDIENTE -> R.color.chip_pendiente
                Estado.ASIGNADA -> R.color.chip_asignada
                Estado.EN_ATENCION -> R.color.chip_en_atencion
                Estado.RESUELTA -> R.color.chip_resuelta
            }
            chipEstado.chipBackgroundColor = ContextCompat.getColorStateList(itemView.context, chipColor)

            // Acciones
            itemView.setOnClickListener { onVerDetalle(item) }
            btnAsignar.setOnClickListener { onAsignar(item) }
            btnAtender.setOnClickListener { onAtender(item) }
            btnResolver.setOnClickListener { onResolver(item) }
            btnVer.setOnClickListener { onVerDetalle(item) }

            val currentUid = currentUserUid
            val pertenece = currentUid != null && item.tecnicoUid == currentUid
            when (estadoEnum) {
                Estado.PENDIENTE -> {
                    btnAsignar.text = itemView.context.getString(R.string.averia_asignar)
                    btnAsignar.isEnabled = true
                    btnAtender.text = itemView.context.getString(R.string.averia_atender)
                    btnAtender.isEnabled = false
                    btnResolver.visibility = View.GONE
                    btnResolver.isEnabled = false
                }
                Estado.ASIGNADA -> {
                    btnAsignar.text = itemView.context.getString(R.string.averia_eliminar_asignacion)
                    btnAsignar.isEnabled = pertenece
                    btnAtender.text = itemView.context.getString(R.string.averia_atender)
                    btnAtender.isEnabled = pertenece
                    btnResolver.visibility = View.GONE
                    btnResolver.isEnabled = false
                }
                Estado.EN_ATENCION -> {
                    btnAsignar.text = itemView.context.getString(R.string.averia_eliminar_asignacion)
                    btnAsignar.isEnabled = false
                    btnAtender.text = itemView.context.getString(R.string.averia_cancelar_atencion)
                    btnAtender.isEnabled = pertenece
                    btnResolver.visibility = View.VISIBLE
                    btnResolver.text = itemView.context.getString(R.string.averia_resolver)
                    btnResolver.isEnabled = pertenece
                }
                Estado.RESUELTA -> {
                    btnAsignar.text = itemView.context.getString(R.string.averia_asignar)
                    btnAsignar.isEnabled = false
                    btnAtender.text = itemView.context.getString(R.string.averia_atencion_finalizada)
                    btnAtender.isEnabled = false
                    btnResolver.visibility = View.VISIBLE
                    btnResolver.text = itemView.context.getString(R.string.averia_exportar_pdf)
                    btnResolver.isEnabled = true
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_averia, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}
