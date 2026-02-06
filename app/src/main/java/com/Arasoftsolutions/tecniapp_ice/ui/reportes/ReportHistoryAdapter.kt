package com.Arasoftsolutions.tecniapp_ice.ui.reportes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.ItemReporteHistoryBinding

class ReportHistoryAdapter(
    private val onOpen: (ExportHistoryItem) -> Unit,
    private val onShare: (ExportHistoryItem) -> Unit,
    private val onDelete: (ExportHistoryItem) -> Unit
) : ListAdapter<ExportHistoryItem, ReportHistoryAdapter.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<ExportHistoryItem>() {
        override fun areItemsTheSame(oldItem: ExportHistoryItem, newItem: ExportHistoryItem): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ExportHistoryItem, newItem: ExportHistoryItem): Boolean =
            oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemReporteHistoryBinding.inflate(inflater, parent, false)
        return ViewHolder(binding, onOpen, onShare, onDelete)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemReporteHistoryBinding,
        private val onOpen: (ExportHistoryItem) -> Unit,
        private val onShare: (ExportHistoryItem) -> Unit,
        private val onDelete: (ExportHistoryItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ExportHistoryItem) {
            val context = binding.root.context
            val typeLabel = context.getString(
                if (item.fileType == ReportFileType.EXCEL) {
                    R.string.reportes_historial_tipo_excel
                } else {
                    R.string.reportes_historial_tipo_pdf
                }
            )
            binding.tvHistoryName.text = item.fileName
            binding.tvHistoryMeta.text = context.getString(
                R.string.reportes_historial_meta,
                typeLabel,
                item.dateTime,
                item.fileSize ?: "-"
            )
            binding.chipHistoryStatus.text = context.getString(
                if (item.status == ReportExportStatus.OK) {
                    R.string.reportes_historial_ok
                } else {
                    R.string.reportes_historial_error
                }
            )
            binding.btnHistoryOpen.setOnClickListener { onOpen(item) }
            binding.btnHistoryShare.setOnClickListener { onShare(item) }
            binding.btnHistoryDelete.setOnClickListener { onDelete(item) }
        }
    }
}
