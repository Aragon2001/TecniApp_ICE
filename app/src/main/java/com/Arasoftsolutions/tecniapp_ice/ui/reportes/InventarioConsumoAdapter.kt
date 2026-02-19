package com.Arasoftsolutions.tecniapp_ice.ui.reportes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.ItemReporteInventarioConsumoBinding

class InventarioConsumoAdapter :
    ListAdapter<InventarioConsumoItem, InventarioConsumoAdapter.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<InventarioConsumoItem>() {
        override fun areItemsTheSame(oldItem: InventarioConsumoItem, newItem: InventarioConsumoItem): Boolean =
            oldItem.material == newItem.material && oldItem.cuando == newItem.cuando && oldItem.donde == newItem.donde

        override fun areContentsTheSame(oldItem: InventarioConsumoItem, newItem: InventarioConsumoItem): Boolean =
            oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReporteInventarioConsumoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class ViewHolder(private val binding: ItemReporteInventarioConsumoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: InventarioConsumoItem) {
            val context = binding.root.context
            binding.tvConsumoMaterial.text = item.material
            binding.tvConsumoMeta.text = context.getString(
                R.string.reportes_inventario_consumo_meta,
                item.fecha,
                item.cantidad
            )
            binding.tvConsumoUso.text = context.getString(R.string.reportes_inventario_consumo_uso, item.usoEn)
            binding.tvConsumoDonde.text = context.getString(R.string.reportes_inventario_consumo_donde, item.donde)
        }
    }
}
