package com.Arasoftsolutions.tecniapp_ice.ui.programacion

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.Database.entities.ProgramacionEntity
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.ItemProgramacionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProgramacionAdapter(
    private val onClick: (ProgramacionEntity) -> Unit
) : ListAdapter<ProgramacionEntity, ProgramacionAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemProgramacionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(
        private val b: ItemProgramacionBinding,
        private val onClick: (ProgramacionEntity) -> Unit
    ) : RecyclerView.ViewHolder(b.root) {
        private val df = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        fun bind(item: ProgramacionEntity) {
            b.tvActividad.text = item.actividad
            b.tvCuenta.text = item.cuenta
            b.tvLocalizacion.text = item.localizacion
            b.tvFecha.text = df.format(Date(item.fechaAsignacion))
            b.chipEstado.text = item.estado.replace("_", " ")
            val color = when (item.estado) {
                ProgramacionRepository.ESTADO_PENDIENTE -> R.color.warning_yellow
                ProgramacionRepository.ESTADO_EN_PROCESO -> R.color.status_in_progress
                else -> R.color.success_500
            }
            b.chipEstado.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(b.root.context, color)
            )
            b.ivMap.isVisible = item.lat != null && item.lng != null
            b.ivPhoto.isVisible = true
            b.root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ProgramacionEntity>() {
            override fun areItemsTheSame(oldItem: ProgramacionEntity, newItem: ProgramacionEntity): Boolean =
                oldItem.programacionId == newItem.programacionId

            override fun areContentsTheSame(oldItem: ProgramacionEntity, newItem: ProgramacionEntity): Boolean =
                oldItem == newItem
        }
    }
}
