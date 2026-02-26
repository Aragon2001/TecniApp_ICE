package com.Arasoftsolutions.tecniapp_ice.ui.legal

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.databinding.ItemLegalEntryBinding

class LegalAdapter(
    private val entries: List<LegalEntryUiModel>,
    private val onClick: (LegalDocType) -> Unit
) : RecyclerView.Adapter<LegalAdapter.LegalViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LegalViewHolder {
        val binding = ItemLegalEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LegalViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LegalViewHolder, position: Int) {
        holder.bind(entries[position])
    }

    override fun getItemCount(): Int = entries.size

    inner class LegalViewHolder(
        private val binding: ItemLegalEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LegalEntryUiModel) {
            binding.legalEntryIcon.setImageResource(item.iconRes)
            binding.legalEntryTitle.setText(item.titleRes)
            binding.legalEntryDescription.setText(item.descriptionRes)
            binding.root.setOnClickListener { onClick(item.type) }
        }
    }
}
