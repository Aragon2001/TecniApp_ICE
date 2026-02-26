package com.Arasoftsolutions.tecniapp_ice.ui.legal

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.databinding.ItemLegalSectionBinding

class LegalSectionsAdapter : RecyclerView.Adapter<LegalSectionsAdapter.LegalSectionViewHolder>() {

    private val items = mutableListOf<LegalSectionUiModel>()

    fun submitList(values: List<LegalSectionUiModel>) {
        items.clear()
        items.addAll(values)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LegalSectionViewHolder {
        val binding = ItemLegalSectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LegalSectionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LegalSectionViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class LegalSectionViewHolder(
        private val binding: ItemLegalSectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LegalSectionUiModel) {
            binding.textSectionTitle.text = item.title
            binding.textSectionTitle.isVisible = item.title.isNotBlank()
            binding.textSectionBody.renderStructuredContent(item.body)
        }
    }
}
