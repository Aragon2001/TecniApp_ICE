package com.Arasoftsolutions.tecniapp_ice.ui.luminarias

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado
import com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaReparacionEntity
import com.Arasoftsolutions.tecniapp_ice.databinding.ItemLuminariaReparacionBinding

class LuminariaReparacionAdapter(
    private var showEdit: Boolean,
    private var showDelete: Boolean,
    private val onEdit: (LuminariaReparacionEntity) -> Unit,
    private val onDelete: (LuminariaReparacionEntity) -> Unit,
    private val onSelect: ((LuminariaReparacionEntity) -> Unit)? = null
) : ListAdapter<LuminariaReparacionEntity, LuminariaReparacionAdapter.ReparacionViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReparacionViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemLuminariaReparacionBinding.inflate(inflater, parent, false)
        return ReparacionViewHolder(binding, onEdit, onDelete, onSelect)
    }

    override fun onBindViewHolder(holder: ReparacionViewHolder, position: Int) {
        holder.bind(getItem(position), showEdit, showDelete)
    }

    fun updatePermissions(showEdit: Boolean, showDelete: Boolean) {
        if (this.showEdit == showEdit && this.showDelete == showDelete) return
        this.showEdit = showEdit
        this.showDelete = showDelete
        notifyDataSetChanged()
    }

    class ReparacionViewHolder(
        private val binding: ItemLuminariaReparacionBinding,
        private val onEdit: (LuminariaReparacionEntity) -> Unit,
        private val onDelete: (LuminariaReparacionEntity) -> Unit,
        private val onSelect: ((LuminariaReparacionEntity) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {
        private fun formatCantidad(cantidad: Double): String {
            return if (cantidad % 1.0 == 0.0) {
                cantidad.toInt().toString()
            } else {
                cantidad.toString()
            }
        }

        fun bind(item: LuminariaReparacionEntity, showEdit: Boolean, showDelete: Boolean) {
            val materiales = LuminariaMaterialSerializer.fromJson(item.materialesJson)
            val resumen = if (materiales.isEmpty()) {
                "Sin materiales"
            } else {
                materiales.joinToString("\n") { material ->
                    "- ${material.descripcion}\nCantidad: ${formatCantidad(material.cantidad)}"
                }
            }
            val estadoTexto = if (LuminariaEstado.fromRaw(item.estado) == LuminariaEstado.PENDIENTE) {
                "Pendiente"
            } else {
                "Reparada"
            }
            val localizacion = item.localizacion.ifBlank { "-" }
            binding.tvReparacionMaterial.text = "Localización: #$localizacion"
            binding.tvReparacionDetalle.text = "Materiales:\n$resumen\n\nEstado: $estadoTexto"
            binding.tvReparacionEjecutor.text = "Ejecutó: ${item.ejecutorNombre.ifBlank { "-" }}"
            binding.btnEditarReparacion.isVisible = showEdit
            binding.btnEliminarReparacion.isVisible = showDelete
            binding.btnEditarReparacion.setOnClickListener { onEdit(item) }
            binding.btnEliminarReparacion.setOnClickListener { onDelete(item) }
            binding.root.setOnClickListener { onSelect?.invoke(item) }
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
