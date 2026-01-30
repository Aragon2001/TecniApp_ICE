package com.Arasoftsolutions.tecniapp_ice.ui.reportes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.ItemReporteMisAveriasBinding

class MisAveriasAdapter : ListAdapter<MisAveriaReportItem, MisAveriasAdapter.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<MisAveriaReportItem>() {
        override fun areItemsTheSame(oldItem: MisAveriaReportItem, newItem: MisAveriaReportItem): Boolean =
            oldItem.caseId == newItem.caseId

        override fun areContentsTheSame(oldItem: MisAveriaReportItem, newItem: MisAveriaReportItem): Boolean =
            oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemReporteMisAveriasBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemReporteMisAveriasBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MisAveriaReportItem) {
            val context = binding.root.context
            binding.tvAveriaCaso.text = context.getString(R.string.reportes_item_caso, item.caseId)
            binding.tvAveriaNise.text = context.getString(R.string.reportes_item_nise, item.nise.ifBlank { "-" })
            binding.tvAveriaUbicacion.text = context.getString(
                R.string.reportes_item_ubicacion,
                item.ubicacion.ifBlank { "-" }
            )
            binding.tvAveriaEstado.text = context.getString(R.string.reportes_item_estado, item.estado)
            binding.tvAveriaFechas.text = context.getString(
                R.string.reportes_item_fechas,
                item.fechaReporte,
                item.fechaAtencion
            )
            binding.tvAveriaMateriales.text = context.getString(
                R.string.reportes_item_materiales_mis_averias,
                item.materialesCantidad,
                item.materialesResumen.ifBlank { context.getString(R.string.reportes_item_materiales_sin) }
            )
        }
    }
}
