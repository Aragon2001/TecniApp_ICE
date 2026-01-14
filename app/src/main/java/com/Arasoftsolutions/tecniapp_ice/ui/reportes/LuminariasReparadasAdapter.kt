package com.Arasoftsolutions.tecniapp_ice.ui.reportes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.databinding.ItemReporteLuminariaReparadaBinding

class LuminariasReparadasAdapter :
    ListAdapter<LuminariaReparadaReportItem, LuminariasReparadasAdapter.LuminariaViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LuminariaViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemReporteLuminariaReparadaBinding.inflate(inflater, parent, false)
        return LuminariaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LuminariaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class LuminariaViewHolder(
        private val binding: ItemReporteLuminariaReparadaBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: LuminariaReparadaReportItem) {
            binding.tvFecha.text = item.fechaTexto
            binding.tvLocalizacion.text = item.localizacionTexto
            binding.tvMaterial.text = item.materialTexto
            binding.tvCantidad.text = item.cantidadTexto
            binding.tvVehiculo.text = item.vehiculoTexto
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<LuminariaReparadaReportItem>() {
            override fun areItemsTheSame(
                oldItem: LuminariaReparadaReportItem,
                newItem: LuminariaReparadaReportItem
            ): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: LuminariaReparadaReportItem,
                newItem: LuminariaReparadaReportItem
            ): Boolean = oldItem == newItem
        }
    }
}
