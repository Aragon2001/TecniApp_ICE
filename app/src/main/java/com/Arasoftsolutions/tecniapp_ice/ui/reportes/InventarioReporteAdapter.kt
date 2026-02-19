package com.Arasoftsolutions.tecniapp_ice.ui.reportes

import android.content.res.ColorStateList
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.ItemReporteInventarioBinding

class InventarioReporteAdapter : ListAdapter<InventarioReportItem, InventarioReporteAdapter.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<InventarioReportItem>() {
        override fun areItemsTheSame(oldItem: InventarioReportItem, newItem: InventarioReportItem): Boolean =
            oldItem.codigo == newItem.codigo

        override fun areContentsTheSame(oldItem: InventarioReportItem, newItem: InventarioReportItem): Boolean =
            oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemReporteInventarioBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemReporteInventarioBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: InventarioReportItem) {
            val context = binding.root.context
            binding.tvInventarioCodigo.text = context.getString(R.string.reportes_inventario_codigo, item.codigo)
            binding.tvInventarioDescripcion.text = item.descripcion
            binding.tvInventarioCantidad.text = context.getString(
                R.string.reportes_inventario_cantidad,
                item.cantidad,
                item.unidad
            )
            binding.tvInventarioOrigen.text = item.origen ?: context.getString(R.string.reportes_inventario_origen_sin)
            binding.tvInventarioActualizacion.text = item.fechaActualizacion
                ?: context.getString(R.string.reportes_inventario_actualizado_placeholder)

            val isLow = item.minimo?.let { item.cantidad <= it } ?: false
            binding.chipInventarioEstado.text = context.getString(
                if (isLow) R.string.reportes_inventario_estado_bajo else R.string.reportes_inventario_estado_ok
            )
            if (isLow) {
                val criticalColor = context.getColor(R.color.error)
                binding.tvInventarioDescripcion.paintFlags =
                    binding.tvInventarioDescripcion.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                binding.tvInventarioDescripcion.setTextColor(criticalColor)
                binding.chipInventarioEstado.chipBackgroundColor = ColorStateList.valueOf(
                    context.getColor(R.color.error_container)
                )
                binding.chipInventarioEstado.setTextColor(context.getColor(R.color.on_error_container))
            } else {
                binding.tvInventarioDescripcion.paintFlags =
                    binding.tvInventarioDescripcion.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
                binding.tvInventarioDescripcion.setTextColor(context.getColor(R.color.on_surface))
                binding.chipInventarioEstado.chipBackgroundColor = ColorStateList.valueOf(
                    context.getColor(R.color.primary_container)
                )
                binding.chipInventarioEstado.setTextColor(context.getColor(R.color.on_primary_container))
            }
        }
    }
}
