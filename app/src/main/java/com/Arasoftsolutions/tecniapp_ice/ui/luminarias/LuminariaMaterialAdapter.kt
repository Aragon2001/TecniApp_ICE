package com.Arasoftsolutions.tecniapp_ice.ui.luminarias

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.databinding.ItemLuminariaMaterialBinding

class LuminariaMaterialAdapter(
    private val onRemove: (LuminariaMaterialSeleccionado) -> Unit
) : ListAdapter<LuminariaMaterialSeleccionado, LuminariaMaterialAdapter.MaterialViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MaterialViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemLuminariaMaterialBinding.inflate(inflater, parent, false)
        return MaterialViewHolder(binding, onRemove)
    }

    override fun onBindViewHolder(holder: MaterialViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MaterialViewHolder(
        private val binding: ItemLuminariaMaterialBinding,
        private val onRemove: (LuminariaMaterialSeleccionado) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: LuminariaMaterialSeleccionado) {
            val nombre = if (item.descripcion.isNotBlank()) {
                "${item.codigo} · ${item.descripcion}"
            } else {
                item.codigo
            }
            binding.tvMaterialNombre.text = nombre
            binding.tvMaterialCantidad.text = "Cantidad: ${item.cantidad}"
            binding.btnEliminarMaterial.setOnClickListener { onRemove(item) }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<LuminariaMaterialSeleccionado>() {
            override fun areItemsTheSame(
                oldItem: LuminariaMaterialSeleccionado,
                newItem: LuminariaMaterialSeleccionado
            ): Boolean = oldItem.codigo == newItem.codigo

            override fun areContentsTheSame(
                oldItem: LuminariaMaterialSeleccionado,
                newItem: LuminariaMaterialSeleccionado
            ): Boolean = oldItem == newItem
        }
    }
}
