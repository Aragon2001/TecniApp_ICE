package com.Arasoftsolutions.tecniapp_ice.ui.reportes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.databinding.ItemReporteBitacoraEventoBinding

class BitacoraEventosAdapter : ListAdapter<BitacoraEventItem, BitacoraEventosAdapter.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<BitacoraEventItem>() {
        override fun areItemsTheSame(oldItem: BitacoraEventItem, newItem: BitacoraEventItem): Boolean =
            oldItem.tipo == newItem.tipo && oldItem.referencia == newItem.referencia && oldItem.fecha == newItem.fecha

        override fun areContentsTheSame(oldItem: BitacoraEventItem, newItem: BitacoraEventItem): Boolean =
            oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemReporteBitacoraEventoBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemReporteBitacoraEventoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: BitacoraEventItem) {
            binding.tvBitacoraTipo.text = item.tipo
            binding.tvBitacoraReferencia.text = item.referencia
            binding.tvBitacoraFecha.text = item.fecha
            binding.tvBitacoraDescripcion.text = item.descripcion
            binding.tvBitacoraCantidad.text = item.cantidad
        }
    }
}
