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
            val titulo = item.actividad.ifBlank { item.descripcion.orEmpty() }
            b.tvActividad.text = titulo

            val locDisplay = if (item.localizacionNormalizada.isNotBlank())
                LocalizacionUtils.formatearParaMostrar(item.localizacionNormalizada)
            else item.localizacion
            b.tvLocalizacion.text = locDisplay

            b.tvVehiculo.text = "Camión: ${item.placa.ifBlank { item.vehiculoId }}"
            b.tvFecha.text = df.format(Date(item.fechaAsignacion))

            val estadoLabel = when (item.estado) {
                ProgramacionRepository.ESTADO_PENDIENTE -> "Pendiente"
                ProgramacionRepository.ESTADO_EN_ATENCION -> "En atención"
                ProgramacionRepository.ESTADO_ATENDIDA -> "Atendida"
                ProgramacionRepository.ESTADO_REABIERTA -> "Reabierta"
                ProgramacionRepository.ESTADO_CANCELADA -> "Cancelada"
                ProgramacionRepository.ESTADO_ELIMINADA -> "Eliminada"
                else -> item.estado.replace("_", " ")
            }
            b.chipEstado.text = estadoLabel

            val colorRes = when (item.estado) {
                ProgramacionRepository.ESTADO_PENDIENTE -> R.color.warning_yellow
                ProgramacionRepository.ESTADO_EN_ATENCION -> R.color.status_in_progress
                ProgramacionRepository.ESTADO_ATENDIDA -> R.color.success_500
                ProgramacionRepository.ESTADO_REABIERTA -> R.color.warning_yellow
                ProgramacionRepository.ESTADO_CANCELADA -> R.color.danger_red
                ProgramacionRepository.ESTADO_ELIMINADA -> R.color.danger_red
                else -> R.color.success_500
            }
            b.chipEstado.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(b.root.context, colorRes)
            )

            b.ivMap.isVisible = item.lat != null && item.lng != null
            b.ivPhoto.isVisible = !item.fotosSupervisorJson.isNullOrBlank() || !item.fotosAtencionJson.isNullOrBlank()
            b.ivMateriales.isVisible = item.gastoMateriales

            b.root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ProgramacionEntity>() {
            override fun areItemsTheSame(o: ProgramacionEntity, n: ProgramacionEntity) =
                o.programacionId == n.programacionId

            override fun areContentsTheSame(o: ProgramacionEntity, n: ProgramacionEntity) =
                o == n
        }
    }
}
