package com.Arasoftsolutions.tecniapp_ice.ui.reportes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.ItemReporteMaterialPorAveriaBinding

class MaterialesPorAveriaAdapter :
    ListAdapter<MaterialPorAveriaReportItem, MaterialesPorAveriaAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemReporteMaterialPorAveriaBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemReporteMaterialPorAveriaBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MaterialPorAveriaReportItem) {
            val context = binding.root.context
            val emptyValue = context.getString(R.string.averia_pdf_empty_value)

            binding.tvTitulo.text = context.getString(R.string.reportes_item_caso, item.caseId)
            binding.tvSubtitulo.text = context.getString(R.string.reportes_item_fecha_compacto, item.fechaTexto)

            val agencia = item.agencia.ifBlank { emptyValue }
            binding.tvAgencia.text = context.getString(R.string.reportes_item_agencia, agencia)

            val vehiculo = item.vehiculo?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.reportes_item_vehiculo_desconocido)
            binding.tvVehiculo.text = context.getString(R.string.reportes_item_vehiculo, vehiculo)

            val materialesTexto = if (item.tieneMateriales && item.materialesDetalle.isNotEmpty()) {
                item.materialesDetalle.joinToString(separator = "\n") { uso ->
                    formatMaterialLine(
                        context.getString(R.string.reportes_material_desconocido),
                        uso,
                        context.getString(R.string.reportes_material_existencia_actual, uso.existenciaTexto)
                    )
                }
            } else {
                context.getString(R.string.reportes_material_sin_detalle)
            }

            binding.tvMateriales.text = materialesTexto
        }

        private fun formatMaterialLine(
            desconocido: String,
            uso: MaterialDetalleReportItem,
            existenciaTexto: String
        ): String {
            val descripcion = uso.descripcion.ifBlank {
                uso.codigo.takeIf { it.isNotBlank() } ?: desconocido
            }
            val cantidad = uso.cantidad
            return if (cantidad <= 1) {
                "• $descripcion · $existenciaTexto"
            } else {
                "• ${cantidad}x $descripcion · $existenciaTexto"
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<MaterialPorAveriaReportItem>() {
        override fun areItemsTheSame(
            oldItem: MaterialPorAveriaReportItem,
            newItem: MaterialPorAveriaReportItem
        ): Boolean = oldItem.caseId == newItem.caseId

        override fun areContentsTheSame(
            oldItem: MaterialPorAveriaReportItem,
            newItem: MaterialPorAveriaReportItem
        ): Boolean = oldItem == newItem
    }
}
