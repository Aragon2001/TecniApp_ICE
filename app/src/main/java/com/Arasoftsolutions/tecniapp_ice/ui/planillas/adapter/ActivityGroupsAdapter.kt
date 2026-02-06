package com.Arasoftsolutions.tecniapp_ice.ui.planillas.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.databinding.ItemActivityGroupBinding
import com.Arasoftsolutions.tecniapp_ice.ui.planillas.model.ActivityGroupUi

class ActivityGroupsAdapter(
    private val onOpen: (String) -> Unit,
    private val onRetry: (String) -> Unit
) : ListAdapter<ActivityGroupUi, ActivityGroupsAdapter.GroupViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val binding = ItemActivityGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GroupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class GroupViewHolder(private val binding: ItemActivityGroupBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ActivityGroupUi) {
            binding.groupTitle.text = item.ordenSap
            binding.groupSubtitle.text = "${item.tecnico} · ${item.fecha}"
            binding.syncStatusChip.text = item.syncStatus.name
            binding.openButton.setOnClickListener { onOpen(item.groupId) }
            binding.retryButton.setOnClickListener { onRetry(item.groupId) }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<ActivityGroupUi>() {
            override fun areItemsTheSame(oldItem: ActivityGroupUi, newItem: ActivityGroupUi): Boolean =
                oldItem.groupId == newItem.groupId

            override fun areContentsTheSame(oldItem: ActivityGroupUi, newItem: ActivityGroupUi): Boolean =
                oldItem == newItem
        }
    }
}
