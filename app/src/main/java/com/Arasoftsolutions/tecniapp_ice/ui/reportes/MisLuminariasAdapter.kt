package com.Arasoftsolutions.tecniapp_ice.ui.reportes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.ItemReporteMisLuminariasBinding

class MisLuminariasAdapter : ListAdapter<MisLuminariaReportItem, MisLuminariasAdapter.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<MisLuminariaReportItem>() {
        override fun areItemsTheSame(oldItem: MisLuminariaReportItem, newItem: MisLuminariaReportItem): Boolean =
            oldItem.localizacion == newItem.localizacion && oldItem.fecha == newItem.fecha

        override fun areContentsTheSame(oldItem: MisLuminariaReportItem, newItem: MisLuminariaReportItem): Boolean =
            oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemReporteMisLuminariasBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemReporteMisLuminariasBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MisLuminariaReportItem) {
            val context = binding.root.context
            binding.tvLuminariaLocalizacion.text = context.getString(
                R.string.reportes_item_localizacion,
                item.localizacion
            )
            binding.tvLuminariaEstado.text = context.getString(R.string.reportes_item_estado, item.estado)
            binding.tvLuminariaFecha.text = context.getString(R.string.reportes_item_fecha_compacto, item.fecha)
            binding.tvLuminariaVehiculo.text = context.getString(R.string.reportes_item_vehiculo, item.vehiculo)
            binding.tvLuminariaComunidad.text = context.getString(R.string.reportes_item_comunidad, item.comunidad)
            binding.tvLuminariaMateriales.text = context.getString(
                R.string.reportes_item_materiales_resumen,
                item.materialesResumen
            )
        }
    }
}
