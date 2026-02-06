package com.Arasoftsolutions.tecniapp_ice.ui.planillas.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.databinding.ItemPlanillaCardBinding
import com.Arasoftsolutions.tecniapp_ice.ui.planillas.model.PlanillaModeUi
import com.Arasoftsolutions.tecniapp_ice.ui.planillas.model.PlanillaUi

class PlanillasAdapter(
    private val onOpen: (String) -> Unit,
    private val onRetry: (String) -> Unit
) : ListAdapter<PlanillaUi, PlanillasAdapter.PlanillaViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlanillaViewHolder {
        val binding = ItemPlanillaCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PlanillaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlanillaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PlanillaViewHolder(private val binding: ItemPlanillaCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: PlanillaUi) {
            binding.title.text = if (item.mode == PlanillaModeUi.PERSONA) "Planilla Persona" else "Planilla Cuadrilla"
            binding.subtitle.text = "Fecha: ${item.fecha} · HH: ${item.totalHoras}"
            binding.syncStatusChip.text = item.syncStatus.name

            binding.viewPdfButton.isEnabled = item.canViewPdf
            binding.viewPdfButton.setOnClickListener { onOpen(item.planillaId) }
            binding.shareButton.setOnClickListener { onOpen(item.planillaId) }
            binding.retryButton.setOnClickListener { onRetry(item.planillaId) }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<PlanillaUi>() {
            override fun areItemsTheSame(oldItem: PlanillaUi, newItem: PlanillaUi): Boolean =
                oldItem.planillaId == newItem.planillaId

            override fun areContentsTheSame(oldItem: PlanillaUi, newItem: PlanillaUi): Boolean =
                oldItem == newItem
        }
    }
}
