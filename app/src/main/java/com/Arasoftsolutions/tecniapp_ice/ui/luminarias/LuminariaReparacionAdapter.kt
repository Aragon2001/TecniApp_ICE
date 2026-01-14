package com.Arasoftsolutions.tecniapp_ice.ui.luminarias

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaReparacionEntity
import com.Arasoftsolutions.tecniapp_ice.databinding.ItemLuminariaReparacionBinding

class LuminariaReparacionAdapter(
    private val onEdit: (LuminariaReparacionEntity) -> Unit,
    private val onDelete: (LuminariaReparacionEntity) -> Unit
) : ListAdapter<LuminariaReparacionEntity, LuminariaReparacionAdapter.ReparacionViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReparacionViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemLuminariaReparacionBinding.inflate(inflater, parent, false)
        return ReparacionViewHolder(binding, onEdit, onDelete)
    }

    override fun onBindViewHolder(holder: ReparacionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ReparacionViewHolder(
        private val binding: ItemLuminariaReparacionBinding,
        private val onEdit: (LuminariaReparacionEntity) -> Unit,
        private val onDelete: (LuminariaReparacionEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: LuminariaReparacionEntity) {
            val nombreMaterial = if (item.descripcionMaterial.isNotBlank()) {
                "${item.codigoMaterial} · ${item.descripcionMaterial}"
            } else {
                item.codigoMaterial
            }
            binding.tvReparacionMaterial.text = nombreMaterial
            binding.tvReparacionDetalle.text = "Localización #${item.localizacion} · Cantidad: ${item.cantidadUtilizada}"
            binding.btnEditarReparacion.setOnClickListener { onEdit(item) }
            binding.btnEliminarReparacion.setOnClickListener { onDelete(item) }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<LuminariaReparacionEntity>() {
            override fun areItemsTheSame(
                oldItem: LuminariaReparacionEntity,
                newItem: LuminariaReparacionEntity
            ): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: LuminariaReparacionEntity,
                newItem: LuminariaReparacionEntity
            ): Boolean = oldItem == newItem
        }
    }
}
