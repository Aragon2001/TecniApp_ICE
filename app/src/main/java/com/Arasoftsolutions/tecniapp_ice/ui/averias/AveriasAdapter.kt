package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.R

data class Averia(
    val descripcion: String,
    val enviadoPor: String,
    val fechaEnvio: String,
    val prioridad: String,
    val estado: String,
    val supervisor: String,
    val direccion: String,
    val localizacion: String
)

class AveriasAdapter(
    private var averiasList: MutableList<Averia>,
    private val onAveriaSelected: (Averia) -> Unit,
    private val onAveriaAccepted: (Averia) -> Unit // Callback para aceptar la avería
) : RecyclerView.Adapter<AveriasAdapter.AveriaViewHolder>() {

    inner class AveriaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val descripcionAveria: TextView = itemView.findViewById(R.id.tvDescripcionAveria)
        val enviadoPor: TextView = itemView.findViewById(R.id.tvEnviadoPor)
        val fechaEnvio: TextView = itemView.findViewById(R.id.tvFechaEnvio)
        val prioridad: TextView = itemView.findViewById(R.id.tvPrioridad)
        val estado: TextView = itemView.findViewById(R.id.tvEstado)
        val supervisor: TextView = itemView.findViewById(R.id.tvSupervisor)
        val direccion: TextView = itemView.findViewById(R.id.tvUbicacion)
        val localizacion: TextView = itemView.findViewById(R.id.tvLocalizacion)
        val aceptarButton: Button = itemView.findViewById(R.id.btnAceptar)

        init {
            // Listener para seleccionar la avería
            itemView.setOnClickListener {
                onAveriaSelected(averiasList[adapterPosition])
            }
            // Listener para aceptar la avería
            aceptarButton.setOnClickListener {
                onAveriaAccepted(averiasList[adapterPosition])
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AveriaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_averia, parent, false)
        return AveriaViewHolder(view)
    }

    override fun onBindViewHolder(holder: AveriaViewHolder, position: Int) {
        val averia = averiasList[position]
        holder.descripcionAveria.text = averia.descripcion
        holder.enviadoPor.text = "Enviado por: ${averia.enviadoPor}"
        holder.fechaEnvio.text = "Fecha de envío: ${averia.fechaEnvio}"
        holder.prioridad.text = "Prioridad: ${averia.prioridad}"
        holder.estado.text = "Estado: ${averia.estado}"
        holder.supervisor.text = "Supervisor: ${averia.supervisor}"
        holder.direccion.text = "Dirección: ${averia.direccion}"
        holder.localizacion.text = "Localización: ${averia.localizacion}"
    }

    override fun getItemCount(): Int {
        return averiasList.size
    }

    // Método para actualizar la lista de averías
    fun updateAverias(nuevasAverias: List<Averia>) {
        averiasList.clear()
        averiasList.addAll(nuevasAverias)
        notifyDataSetChanged()
    }
}
