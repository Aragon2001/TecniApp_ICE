package com.Arasoftsolutions.tecniapp_ice.ui.reportes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.ItemReporteAveriaBinding

class AveriasReportAdapter : ListAdapter<AveriaReportItem, AveriasReportAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemReporteAveriaBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemReporteAveriaBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AveriaReportItem) {
            val context = binding.root.context
            val emptyValue = context.getString(R.string.averia_pdf_empty_value)

            binding.tvCaso.text = context.getString(R.string.reportes_item_caso, item.caseId)

            val estado = item.estado.ifBlank { emptyValue }
            binding.tvEstado.text = estado
            applyEstadoStyle(estado)

            binding.tvFecha.text = context.getString(R.string.reportes_item_fecha_compacto, item.fechaTexto)

            val agencia = item.agencia.ifBlank { emptyValue }
            binding.tvAgencia.text = context.getString(R.string.reportes_item_agencia, agencia)

            val atendido = item.atendidoPor.ifBlank { emptyValue }
            binding.tvAtendido.text = context.getString(R.string.reportes_item_atendido, atendido)

            val vehiculo = item.vehiculo?.takeIf { it.isNotBlank() }
            binding.tvVehiculo.isVisible = !vehiculo.isNullOrEmpty()
            if (!vehiculo.isNullOrEmpty()) {
                binding.tvVehiculo.text = context.getString(R.string.reportes_item_vehiculo, vehiculo)
            }

            val materialesTexto = if (item.tieneMateriales && item.materialesResumen.isNotBlank()) {
                context.getString(R.string.reportes_item_materiales, item.materialesCantidad, item.materialesResumen)
            } else {
                context.getString(R.string.reportes_item_materiales_sin)
            }
            binding.tvMateriales.text = materialesTexto
        }

        private fun applyEstadoStyle(estado: String) {
            val context = binding.root.context
            val normalized = estado.trim().lowercase()
            val colorRes = when {
                normalized.contains("resuel") -> R.color.chip_resuelta
                normalized.contains("pend") -> R.color.chip_pendiente
                normalized.contains("aten") -> R.color.chip_en_atencion
                normalized.contains("anula") -> R.color.chip_anulada
                else -> R.color.chip_asignada
            }
            val background = AppCompatResources.getDrawable(context, R.drawable.bg_report_status_badge)?.mutate()
            val color = ContextCompat.getColor(context, colorRes)
            background?.setTint(color)
            binding.tvEstado.background = background
            binding.tvEstado.setTextColor(ContextCompat.getColor(context, R.color.white))
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<AveriaReportItem>() {
        override fun areItemsTheSame(oldItem: AveriaReportItem, newItem: AveriaReportItem): Boolean =
            oldItem.caseId == newItem.caseId

        override fun areContentsTheSame(oldItem: AveriaReportItem, newItem: AveriaReportItem): Boolean =
            oldItem == newItem
    }
}
