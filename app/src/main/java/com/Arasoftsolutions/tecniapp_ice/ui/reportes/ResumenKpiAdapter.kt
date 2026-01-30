package com.Arasoftsolutions.tecniapp_ice.ui.reportes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.databinding.ItemReporteKpiBinding

class ResumenKpiAdapter : ListAdapter<ResumenKpiItem, ResumenKpiAdapter.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<ResumenKpiItem>() {
        override fun areItemsTheSame(oldItem: ResumenKpiItem, newItem: ResumenKpiItem): Boolean =
            oldItem.titulo == newItem.titulo

        override fun areContentsTheSame(oldItem: ResumenKpiItem, newItem: ResumenKpiItem): Boolean =
            oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemReporteKpiBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemReporteKpiBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ResumenKpiItem) {
            binding.tvKpiTitulo.text = item.titulo
            binding.tvKpiValor.text = item.valor
            binding.tvKpiDetalle.text = item.detalle.orEmpty()
        }
    }
}
