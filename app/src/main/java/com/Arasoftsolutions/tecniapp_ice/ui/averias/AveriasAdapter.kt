package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
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
    val observaciones: String,
    val nise: String,
    val agencia: String,
    val region: String,
    val zonaTag: String,
    val lat: Double,
    val lng: Double
) : Serializable

class AveriasAdapter(
    private val onVerDetalle: (AveriaUI) -> Unit,
    private val onAsignar: (AveriaUI) -> Unit,
    private val onAtender: (AveriaUI) -> Unit
) : ListAdapter<AveriaUI, AveriasAdapter.VH>(Diff()) {

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
        private val tvNise: TextView = view.findViewById(R.id.tvNise)
        private val tvRegion: TextView = view.findViewById(R.id.tvRegion)
        private val tvAgencia: TextView = view.findViewById(R.id.tvAgencia)

        private val tvCoords: TextView = view.findViewById(R.id.tvCoords)
        private val tvFecha: TextView = view.findViewById(R.id.tvFecha)

        private val btnAsignar: MaterialButton = view.findViewById(R.id.btnAsignar)
        private val btnAtender: MaterialButton = view.findViewById(R.id.btnAtender)
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
            tvNise.text = "NISE: ${item.nise}"
            tvRegion.text = "Región: ${item.region}"
            tvAgencia.text = "Agencia: ${item.agencia}"

            // Coordenadas + fecha
            tvCoords.text = if (lat == 0.0 && lng == 0.0) "Coords: —" else "Coords: $lat, $lng"
            tvFecha.text = "Fecha: " + DateFormat.getDateTimeInstance(
                DateFormat.SHORT, DateFormat.SHORT
            ).format(Date(item.fechaMillis))

            // Acciones
            itemView.setOnClickListener { onVerDetalle(item) }
            btnAsignar.setOnClickListener { onAsignar(item) }
            btnAtender.setOnClickListener { onAtender(item) }
            btnVer.setOnClickListener { onVerDetalle(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_averia, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}
