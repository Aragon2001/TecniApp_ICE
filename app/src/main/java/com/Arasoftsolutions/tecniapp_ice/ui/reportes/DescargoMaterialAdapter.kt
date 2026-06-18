package com.Arasoftsolutions.tecniapp_ice.ui.reportes

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.ItemDescargoMaterialBinding

class DescargoMaterialAdapter :
    ListAdapter<DescargoMaterialItem, DescargoMaterialAdapter.ViewHolder>(DiffCallback) {

    object DiffCallback : DiffUtil.ItemCallback<DescargoMaterialItem>() {
        override fun areItemsTheSame(a: DescargoMaterialItem, b: DescargoMaterialItem) =
            a.origenId == b.origenId && a.material == b.material && a.origenTipo == b.origenTipo

        override fun areContentsTheSame(a: DescargoMaterialItem, b: DescargoMaterialItem) = a == b
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDescargoMaterialBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val b: ItemDescargoMaterialBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(item: DescargoMaterialItem) {
            val ctx = b.root.context

            // Barra de color + ícono + chip según origen
            when (item.origenTipo) {
                DescargoOrigen.LUMINARIA -> {
                    val color = Color.parseColor("#F57C00")
                    applyOrigenStyle(color, Color.parseColor("#FFF3E0"), R.drawable.ic_summary_material,
                        ctx.getString(R.string.descargo_origen_luminaria),
                        ctx.getString(R.string.descargo_id_luminaria, item.origenId))
                }
                DescargoOrigen.AVERIA -> {
                    val color = Color.parseColor("#D32F2F")
                    applyOrigenStyle(color, Color.parseColor("#FFEBEE"), R.drawable.ic_summary_case,
                        ctx.getString(R.string.descargo_origen_averia),
                        ctx.getString(R.string.descargo_id_averia, item.origenId))
                }
                DescargoOrigen.PROGRAMACION -> {
                    val color = Color.parseColor("#1565C0")
                    applyOrigenStyle(color, Color.parseColor("#E3F2FD"), R.drawable.ic_calendar,
                        ctx.getString(R.string.descargo_origen_programacion),
                        ctx.getString(R.string.descargo_id_programacion, item.origenId))
                }
            }

            b.tvMaterialNombre.text = item.material

            val localDisplay = buildString {
                if (item.localizacion.isNotBlank()) append(item.localizacion)
                if (item.pueblo.isNotBlank() && item.pueblo != "-" && item.pueblo != item.localizacion) {
                    if (isNotEmpty()) append(" · ")
                    append(item.pueblo)
                }
            }.ifBlank { "-" }
            b.tvLocalizacion.text = localDisplay

            b.tvCantidad.text = if (item.cantidad % 1.0 == 0.0) {
                item.cantidad.toInt().toString()
            } else {
                String.format("%.1f", item.cantidad)
            }

            b.tvTecnico.text = item.tecnico.ifBlank { "-" }
            b.tvFecha.text = item.fechaAtencion.ifBlank { "-" }

            val obs = item.observaciones.trim()
            b.tvObservaciones.isVisible = obs.isNotBlank()
            b.tvObservaciones.text = obs
        }

        private fun applyOrigenStyle(
            accentColor: Int,
            chipBgColor: Int,
            iconRes: Int,
            chipLabel: String,
            origenIdText: String
        ) {
            b.accentOrigen.setBackgroundColor(accentColor)
            b.ivIconOrigen.setImageResource(iconRes)
            b.ivIconOrigen.setColorFilter(accentColor)
            b.chipOrigenTipo.text = chipLabel
            b.chipOrigenTipo.chipBackgroundColor = ColorStateList.valueOf(chipBgColor)
            b.tvOrigenId.text = origenIdText
            // Badge de cantidad en el mismo color de acento
            (b.tvCantidad.parent as? android.view.View)?.let { container ->
                val bg = container.background
                if (bg is GradientDrawable) {
                    bg.mutate()
                    (bg as GradientDrawable).setColor(accentColor)
                }
            }
        }
    }
}
