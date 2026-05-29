package com.Arasoftsolutions.tecniapp_ice.ui.reportes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.databinding.ItemEtmRegistroBinding

class BitacoraEventosAdapter : ListAdapter<BitacoraEventItem, BitacoraEventosAdapter.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<BitacoraEventItem>() {
        override fun areItemsTheSame(oldItem: BitacoraEventItem, newItem: BitacoraEventItem): Boolean =
            oldItem.tipo == newItem.tipo && oldItem.referencia == newItem.referencia && oldItem.fecha == newItem.fecha

        override fun areContentsTheSame(oldItem: BitacoraEventItem, newItem: BitacoraEventItem): Boolean =
            oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemEtmRegistroBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemEtmRegistroBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: BitacoraEventItem) {
            val (inicial, final, diferencia) = parseLecturas(item.cantidad)

            binding.tvFecha.text = item.fecha
            binding.chipEstado.text = item.tipo
            binding.tvMeta.text = item.referencia
            binding.tvInicial.text = inicial
            binding.tvFinal.text = final
            binding.tvDiferencia.text = diferencia
            binding.tvDetalleTitle.text = item.tipo
            binding.tvActividad.text = item.descripcion
            binding.tvOrdenSap.text = item.referencia
            binding.tvCuenta.text = item.cantidad
            binding.tvUbicacion.text = item.referencia
            binding.tvObservaciones.text = item.descripcion
            binding.tvEvidencia.text = item.cantidad
        }

        private fun parseLecturas(cantidad: String): Triple<String?, String?, String?> {
            val partes = cantidad.split("•").map { it.trim() }
            val inicial = partes.getOrNull(0)?.substringAfter(":", missingDelimiterValue = "")?.trim()
                ?.ifEmpty { "-" }
            val final = partes.getOrNull(1)?.substringAfter(":", missingDelimiterValue = "")?.trim()
                ?.ifEmpty { "-" }
            val diferencia = partes.getOrNull(2)?.substringAfter(":", missingDelimiterValue = "")
                ?.trim()
                ?.ifEmpty { "-" }
            return Triple(inicial, final, diferencia)
        }
    }
}
