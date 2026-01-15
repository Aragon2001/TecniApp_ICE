package com.Arasoftsolutions.tecniapp_ice.ui.reportes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.ItemReporteMaterialTotalBinding

class MaterialTotalAdapter :
    ListAdapter<MaterialTotalItem, MaterialTotalAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemReporteMaterialTotalBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemReporteMaterialTotalBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MaterialTotalItem) {
            val context = binding.root.context

            binding.tvMaterial.text = item.descripcion

            val codigo = item.codigo
            binding.tvCodigo.isVisible = codigo.isNotBlank()
            if (codigo.isNotBlank()) {
                binding.tvCodigo.text = context.getString(R.string.reportes_material_codigo, codigo)
            }

            val recursos = context.resources
            val unidades = recursos.getQuantityString(R.plurals.reportes_material_total_unidades, item.total, item.total)
            val averias = recursos.getQuantityString(R.plurals.reportes_material_total_averias, item.averias, item.averias)
            val existencia = context.getString(R.string.reportes_material_existencia_actual, item.existenciaTexto)
            binding.tvDetalle.text = context.getString(
                R.string.reportes_material_total_detalle_existencia,
                unidades,
                averias,
                existencia
            )
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<MaterialTotalItem>() {
        override fun areItemsTheSame(oldItem: MaterialTotalItem, newItem: MaterialTotalItem): Boolean {
            return oldItem.codigo == newItem.codigo && oldItem.descripcion == newItem.descripcion
        }

        override fun areContentsTheSame(oldItem: MaterialTotalItem, newItem: MaterialTotalItem): Boolean {
            return oldItem == newItem
        }
    }
}
