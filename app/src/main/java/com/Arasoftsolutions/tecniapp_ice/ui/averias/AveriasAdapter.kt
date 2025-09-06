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
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.bumptech.glide.Glide
import java.text.DateFormat
import java.util.Date

data class AveriaUI(
    val id: String,
    val descripcion: String,
    val enviadoPor: String,
    val fechaMillis: Long,
    val prioridad: String,
    val estado: String,
    val supervisor: String,
    val ubicacion: String,
    val localizacion: String,
    val lat: Double,
    val lng: Double
)

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
        private val tvDesc = view.findViewById<TextView>(R.id.tvDescripcionAveria)
        private val chipEstado = view.findViewById<Chip>(R.id.chipEstado)
        private val chipPrioridad = view.findViewById<Chip>(R.id.chipPrioridad)
        private val tvResumen = view.findViewById<TextView>(R.id.tvResumen)
        private val tvUbic = view.findViewById<TextView>(R.id.tvUbicacion)
        private val tvLoc = view.findViewById<TextView>(R.id.tvLocalizacion)
        private val imgMapa = view.findViewById<ImageView>(R.id.imgMapa)
        private val btnAsignar = view.findViewById<MaterialButton>(R.id.btnAsignar)
        private val btnVer = view.findViewById<MaterialButton>(R.id.btnVer)
        private val btnAtender = view.findViewById<MaterialButton>(R.id.btnAtender)


        fun bind(item: AveriaUI) {
            tvDesc.text = item.descripcion
            chipEstado.text = item.estado
            chipPrioridad.text = item.prioridad
            tvResumen.text = "Enviado por: ${item.enviadoPor}  •  Supervisor: ${item.supervisor}  •  ${
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.fechaMillis))
            }"
            tvUbic.text = "Ubicación: ${item.ubicacion}"
            tvLoc.text = "Localización: ${item.localizacion}"

            // ✅ Mostrar mapa solo si hay coordenadas válidas
            if (item.lat == 0.0 && item.lng == 0.0) {
                imgMapa.visibility = View.GONE
            } else {
                imgMapa.visibility = View.VISIBLE
                val urlStatic = buildStaticMapUrl(item.lat, item.lng)
                Glide.with(imgMapa)
                    .load(urlStatic)
                    .placeholder(R.drawable.placeholder_map) // crea este drawable
                    .error(R.drawable.placeholder_map)
                    .into(imgMapa)
            }

            btnVer.setOnClickListener { onVerDetalle(item) }
            btnAsignar.setOnClickListener { onAsignar(item) }
            btnAtender.setOnClickListener { onAtender(item) }
            itemView.setOnClickListener { onVerDetalle(item) }
        }


        private fun buildStaticMapUrl(lat: Double, lng: Double): String {
            // Reemplaza con tu proveedor (Google Static / Mapbox / OSM tile server propio)
            return "https://maps.googleapis.com/maps/api/staticmap?center=$lat,$lng&zoom=15&size=400x300&markers=color:red|$lat,$lng&key=TU_API_KEY"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_averia, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}
