package com.Arasoftsolutions.tecniapp_ice.ui.inventario

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.Database.entities.InventarioConVehiculo
import com.Arasoftsolutions.tecniapp_ice.databinding.ItemInventarioMaterialBinding

class InventarioAdapter : ListAdapter<InventarioConVehiculo, InventarioAdapter.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<InventarioConVehiculo>() {
        override fun areItemsTheSame(oldItem: InventarioConVehiculo, newItem: InventarioConVehiculo): Boolean =
            oldItem.item.id == newItem.item.id

        override fun areContentsTheSame(oldItem: InventarioConVehiculo, newItem: InventarioConVehiculo): Boolean =
            oldItem == newItem
    }

    inner class ViewHolder(private val binding: ItemInventarioMaterialBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: InventarioConVehiculo) {
            val codigo = item.item.codigoMaterial
            val descripcion = item.item.descripcionMaterial.ifBlank { "Sin descripción" }
            val placa = item.vehiculoPlaca?.toString() ?: "Vehículo"
            binding.tvMaterial.text = "$codigo - $descripcion"
            binding.tvCantidad.text = "${item.item.cantidadDisponible} uds en $placa"
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
