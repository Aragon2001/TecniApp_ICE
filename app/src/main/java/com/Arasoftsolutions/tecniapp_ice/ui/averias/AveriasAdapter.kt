package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
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
        private val tvTitulo = view.findViewById<TextView>(R.id.tvTitulo)
        private val chipEstado = view.findViewById<Chip>(R.id.chipEstado)
        private val chipAsignado = view.findViewById<Chip>(R.id.chipAsignadoA)
        private val tvCausa = view.findViewById<TextView>(R.id.tvCausa)
        private val tvObs = view.findViewById<TextView>(R.id.tvObs)
        private val tvCoords = view.findViewById<TextView>(R.id.tvCoords)
        private val tvFecha = view.findViewById<TextView>(R.id.tvFecha)
        private val btnAsignar = view.findViewById<MaterialButton>(R.id.btnAsignar)
        private val btnAtender = view.findViewById<MaterialButton>(R.id.btnAtender)


        fun bind(item: AveriaUI) {
            tvTitulo.text = item.descripcion
            chipEstado.text = item.estado
            chipAsignado.text = item.supervisor.ifBlank { view.context.getString(R.string.averia_sin_asignar) }
            tvCausa.text = item.prioridad
            tvObs.text = item.ubicacion
            tvCoords.text = if (item.lat == 0.0 && item.lng == 0.0) "—" else "${item.lat}, ${item.lng}"
            tvFecha.text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.fechaMillis))

            itemView.setOnClickListener { onVerDetalle(item) }
            btnAsignar.setOnClickListener { onAsignar(item) }
            btnAtender.setOnClickListener { onAtender(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_averia, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}
