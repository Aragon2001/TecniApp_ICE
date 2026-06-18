package com.Arasoftsolutions.tecniapp_ice.ui.reportes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.ItemReporteMisAveriasBinding

class MisAveriasAdapter(
    private val onItemClick: ((MisAveriaReportItem) -> Unit)? = null
) : ListAdapter<MisAveriaReportItem, MisAveriasAdapter.ViewHolder>(Diff) {

    private val expandedIds = mutableSetOf<String>()

    object Diff : DiffUtil.ItemCallback<MisAveriaReportItem>() {
        override fun areItemsTheSame(oldItem: MisAveriaReportItem, newItem: MisAveriaReportItem): Boolean =
            oldItem.caseId == newItem.caseId

        override fun areContentsTheSame(oldItem: MisAveriaReportItem, newItem: MisAveriaReportItem): Boolean =
            oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemReporteMisAveriasBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, expandedIds.contains(item.caseId),
            onToggle = {
                val isExpanded = expandedIds.contains(item.caseId)
                if (isExpanded) expandedIds.remove(item.caseId) else expandedIds.add(item.caseId)
                notifyItemChanged(position)
            },
            onItemClick = onItemClick?.let { cb -> { cb(item) } }
        )
    }

    class ViewHolder(private val binding: ItemReporteMisAveriasBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MisAveriaReportItem, expanded: Boolean, onToggle: () -> Unit, onItemClick: (() -> Unit)? = null) {
            binding.root.setOnClickListener { onItemClick?.invoke() }
            val context = binding.root.context
            binding.tvAveriaCaso.text = context.getString(R.string.reportes_item_caso, item.caseId)
            binding.tvAveriaNise.text = item.nise.ifBlank { "-" }
            binding.tvAveriaUbicacion.text = item.ubicacion.ifBlank { "-" }
            binding.chipAveriaEstado.text = item.estado
            binding.tvAveriaFechas.text = context.getString(
                R.string.reportes_item_fechas,
                item.fechaReporte,
                item.fechaAtencion
            )
            binding.tvAveriaMateriales.text = context.getString(
                R.string.reportes_item_materiales_mis_averias,
                item.materialesCantidad,
                item.materialesResumen.ifBlank { context.getString(R.string.reportes_item_materiales_sin) }
            )
            binding.containerDetallesExtra.isVisible = expanded
            binding.btnToggleDetalles.setIconResource(
                if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
            )
            binding.btnToggleDetalles.text = context.getString(
                if (expanded) R.string.reportes_item_ver_menos else R.string.reportes_item_ver_mas
            )
            binding.btnToggleDetalles.setOnClickListener { onToggle() }
        }
    }
}
