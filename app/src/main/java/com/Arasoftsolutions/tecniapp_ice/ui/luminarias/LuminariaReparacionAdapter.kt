package com.Arasoftsolutions.tecniapp_ice.ui.luminarias

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado
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
            val materiales = LuminariaMaterialSerializer.fromJson(item.materialesJson)
            val resumen = LuminariaMaterialSerializer.toSummary(materiales).ifBlank { "Sin materiales" }
            val total = materiales.sumOf { it.cantidad }
            val estadoTexto = if (LuminariaEstado.fromRaw(item.estado) == LuminariaEstado.PENDIENTE) {
                "Pendiente"
            } else {
                "Reparada"
            }
            binding.tvReparacionMaterial.text = resumen
            binding.tvReparacionDetalle.text = "Localización #${item.localizacion} · Total: $total · $estadoTexto"
            binding.tvReparacionEjecutor.text = "Ejecutor: ${item.ejecutorNombre.ifBlank { "-" }}"
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
