package com.Arasoftsolutions.tecniapp_ice.ui.inventario

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.Database.entities.InventarioConVehiculo
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.ItemInventarioMaterialBinding
import java.text.DecimalFormat

class InventarioAdapter(
    private val showActions: Boolean = false,
    private val onIncrease: ((InventarioConVehiculo) -> Unit)? = null,
    private val onDecrease: ((InventarioConVehiculo) -> Unit)? = null,
    private val onDelete: ((InventarioConVehiculo) -> Unit)? = null
) : ListAdapter<InventarioConVehiculo, InventarioAdapter.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<InventarioConVehiculo>() {
        override fun areItemsTheSame(oldItem: InventarioConVehiculo, newItem: InventarioConVehiculo): Boolean =
            oldItem.item.id == newItem.item.id

        override fun areContentsTheSame(oldItem: InventarioConVehiculo, newItem: InventarioConVehiculo): Boolean =
            oldItem == newItem
    }

    private val quantityFormatter = DecimalFormat("#,##0.##")

    inner class ViewHolder(private val binding: ItemInventarioMaterialBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: InventarioConVehiculo) {
            val context = binding.root.context
            val codigo = item.item.codigoMaterial
            val descripcion = item.item.descripcionMaterial.ifBlank {
                context.getString(R.string.inventario_material_sin_descripcion)
            }
            val placa = item.vehiculoPlaca?.toString()?.ifBlank { null } ?: "Camión"
            val cantidad = quantityFormatter.format(item.item.cantidadDisponible)
            binding.tvMaterial.text = descripcion
            binding.tvCodigo.text = context.getString(R.string.inventario_material_codigo, codigo)
            binding.tvCantidadBadge.text = context.getString(R.string.inventario_material_existencia_badge, cantidad)
            binding.tvCantidad.text = context.getString(R.string.inventario_material_existencia, cantidad)
            binding.tvVehiculo.text = context.getString(R.string.inventario_material_vehiculo, placa)
            binding.layoutAcciones.isVisible = showActions
            val puedeRestar = item.item.cantidadDisponible > 1.0
            binding.btnRestar.isEnabled = puedeRestar
            binding.btnRestar.alpha = if (puedeRestar) 1f else 0.4f
            binding.btnSumar.setOnClickListener { onIncrease?.invoke(item) }
            binding.btnRestar.setOnClickListener { onDecrease?.invoke(item) }
            binding.btnEliminar.setOnClickListener { onDelete?.invoke(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemInventarioMaterialBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
