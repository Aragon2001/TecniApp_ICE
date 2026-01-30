package com.Arasoftsolutions.tecniapp_ice.ui.luminarias

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado
import com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaReparacionEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculosEntity
import com.Arasoftsolutions.tecniapp_ice.databinding.ItemLuminariaReparacionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LuminariaReparacionAdapter(
    private var showEdit: Boolean,
    private var showDelete: Boolean,
    private val onEdit: (LuminariaReparacionEntity) -> Unit,
    private val onDelete: (LuminariaReparacionEntity) -> Unit,
    private val onSelect: ((LuminariaReparacionEntity) -> Unit)? = null
) : ListAdapter<LuminariaReparacionEntity, LuminariaReparacionAdapter.ReparacionViewHolder>(DiffCallback) {

    private var pueblosById: Map<Int, String> = emptyMap()
    private var vehiculosById: Map<Int, VehiculosEntity> = emptyMap()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReparacionViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemLuminariaReparacionBinding.inflate(inflater, parent, false)
        return ReparacionViewHolder(binding, onEdit, onDelete, onSelect)
    }

    override fun onBindViewHolder(holder: ReparacionViewHolder, position: Int) {
        holder.bind(getItem(position), showEdit, showDelete, pueblosById, vehiculosById)
    }

    fun updatePermissions(showEdit: Boolean, showDelete: Boolean) {
        if (this.showEdit == showEdit && this.showDelete == showDelete) return
        this.showEdit = showEdit
        this.showDelete = showDelete
        notifyDataSetChanged()
    }

    fun updateCatalogs(pueblosById: Map<Int, String>, vehiculosById: Map<Int, VehiculosEntity>) {
        if (this.pueblosById == pueblosById && this.vehiculosById == vehiculosById) return
        this.pueblosById = pueblosById
        this.vehiculosById = vehiculosById
        notifyDataSetChanged()
    }

    class ReparacionViewHolder(
        private val binding: ItemLuminariaReparacionBinding,
        private val onEdit: (LuminariaReparacionEntity) -> Unit,
        private val onDelete: (LuminariaReparacionEntity) -> Unit,
        private val onSelect: ((LuminariaReparacionEntity) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {
        private val localizacionRegex = Regex("^\\d{12}$")

        private fun formatCantidad(cantidad: Double): String {
            return if (cantidad % 1.0 == 0.0) {
                cantidad.toInt().toString()
            } else {
                cantidad.toString()
            }
        }

        private fun formatLocalizacion(valor: String): String {
            val trimmed = valor.trim()
            if (trimmed.isBlank()) return "-"
            if (localizacionRegex.matches(trimmed)) return trimmed
            val digits = trimmed.filter(Char::isDigit)
            if (digits.isBlank()) return "-"
            return digits.take(12)
        }

        private fun obtenerPuebloCodigo(localizacion: String): String? {
            val digits = localizacion.filter(Char::isDigit)
            if (digits.length < 4) return null
            return digits.take(4)
        }

        private fun formatFecha(timestamp: Long): String {
            val formato = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            return formato.format(Date(timestamp))
        }

        fun bind(
            item: LuminariaReparacionEntity,
            showEdit: Boolean,
            showDelete: Boolean,
            pueblosById: Map<Int, String>,
            vehiculosById: Map<Int, VehiculosEntity>
        ) {
            val materiales = LuminariaMaterialSerializer.fromJson(item.materialesJson)
            val resumenMateriales = if (materiales.isEmpty()) {
                "Sin materiales"
            } else {
                materiales.joinToString("\n") { material ->
                    "• ${material.descripcion} (x${formatCantidad(material.cantidad)})"
                }
            }
            val estado = LuminariaEstado.fromRaw(item.estado)
            val estadoTexto = if (estado == LuminariaEstado.PENDIENTE) "Pendiente" else "Reparada"
            val localizacionFormateada = formatLocalizacion(item.localizacion)
            val puebloCodigo = obtenerPuebloCodigo(localizacionFormateada)
            val puebloId = puebloCodigo?.toIntOrNull()
            val puebloNombre = puebloId?.let { pueblosById[it] }?.takeIf { it.isNotBlank() }
            val puebloLabel = when {
                puebloCodigo != null && puebloNombre != null -> "$puebloCodigo - $puebloNombre"
                puebloCodigo != null -> puebloCodigo
                else -> "-"
            }
            val vehiculoPlaca = vehiculosById[item.vehiculoId]?.placa?.toString().orEmpty().ifBlank { "-" }

            binding.tvReparacionTitulo.text = "Localización $localizacionFormateada"
            binding.tvReparacionEstado.text = estadoTexto
            binding.tvReparacionPueblo.text = "Pueblo: $puebloLabel"

            if (estado == LuminariaEstado.PENDIENTE) {
                val cliente = item.cliente?.trim().orEmpty().ifBlank { "Sin datos" }
                val contacto = item.contacto?.trim().orEmpty().ifBlank { "Sin datos" }
                val observaciones = item.observaciones?.trim().orEmpty().ifBlank { "Sin datos" }
                binding.tvReparacionDetalle.text = buildString {
                    append("Camión asignado: $vehiculoPlaca")
                    append("\nEstado: $estadoTexto")
                    append("\nEjecutor: -")
                }
                binding.tvReparacionExtra.text = buildString {
                    append("Reporte: ${formatFecha(item.fechaRegistro)}")
                    append("\nCliente: $cliente")
                    append("\nTeléfono: $contacto")
                    append("\nObservaciones: $observaciones")
                }
            } else {
                val ejecutor = item.ejecutorNombre.trim().ifBlank { "Sin datos" }
                binding.tvReparacionDetalle.text = "Materiales:\n$resumenMateriales"
                binding.tvReparacionExtra.text = buildString {
                    append("Estado: $estadoTexto")
                    append("\nVehículo: $vehiculoPlaca")
                    append("\nEjecutor: $ejecutor")
                }
            }

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
