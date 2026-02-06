package com.Arasoftsolutions.tecniapp_ice.ui.planillas.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.databinding.ItemWorklogBinding
import com.Arasoftsolutions.tecniapp_ice.ui.planillas.model.WorkLogUi

class WorkLogsAdapter(
    private val onEdit: (String) -> Unit,
    private val onRetry: (String) -> Unit
) : ListAdapter<WorkLogUi, WorkLogsAdapter.WorkLogViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkLogViewHolder {
        val binding = ItemWorklogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return WorkLogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WorkLogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class WorkLogViewHolder(private val binding: ItemWorklogBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: WorkLogUi) {
            binding.worklogTitle.text = "Orden ${item.ordenSap} · ${item.horasOrdMin} min"
            binding.worklogSubtitle.text = "${item.horaEntrada} - ${item.horaSalida} · Pausa ${item.pausaMin} min"
            binding.syncStatusChip.text = item.syncStatus.name
            binding.updatedText.text = "Actualizado: ${item.updatedAt} · ${item.updatedBy}"
            binding.editButton.setOnClickListener { onEdit(item.logId) }
            binding.retryButton.setOnClickListener { onRetry(item.logId) }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<WorkLogUi>() {
            override fun areItemsTheSame(oldItem: WorkLogUi, newItem: WorkLogUi): Boolean =
                oldItem.logId == newItem.logId

            override fun areContentsTheSame(oldItem: WorkLogUi, newItem: WorkLogUi): Boolean =
                oldItem == newItem
        }
    }
}
