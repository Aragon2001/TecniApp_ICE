package com.Arasoftsolutions.tecniapp_ice.ui.vehiculo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentMiVehiculoBinding
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.launch
import java.util.Locale

class MiVehiculoFragment : Fragment() {

    private var _binding: FragmentMiVehiculoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MiVehiculoViewModel by viewModels()
    private var latestState: MiVehiculoUiState = MiVehiculoUiState()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMiVehiculoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnRegistrarMantenimiento.setOnClickListener {
            RegistroVehiculoDialogFragment().show(childFragmentManager, RegistroVehiculoDialogFragment.TAG)
        }
        binding.tvKpiAlertas.setOnClickListener { mostrarHistorialAlertas() }
        binding.tvTituloMantenimientos.setOnClickListener { mostrarHistorialMantenimientos() }
        binding.btnRegistrarMantenimiento.setOnLongClickListener {
            mostrarConfigIntervaloMantenimiento()
            true
        }
        observarEstado()
        animateCta()
    }

    private fun observarEstado() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        latestState = state
                        val vehiculo = state.vehiculo
                        val visible = vehiculo != null
                        binding.layoutSinVehiculo.isVisible = !visible
                        binding.btnRegistrarMantenimiento.isVisible = visible
                        binding.cardVehiculoHeader.isVisible = visible
                        binding.cardMotivacion.isVisible = visible
                        binding.tvTituloMantenimientos.isVisible = visible
                        binding.containerMantenimientos.isVisible = visible
                        binding.tvTituloUsoMensual.isVisible = visible
                        binding.containerUsoMensual.isVisible = visible
                        binding.tvChartPlaceholder.isVisible = visible
                        if (!visible) return@collect

                        if (vehiculo != null) {
                            binding.tvVehiculoPlaca.text = vehiculo.placaRaw.ifBlank { vehiculo.vehiculoId }
                        }
                        binding.tvVehiculoTipo.text = when (state.tipoVehiculo) {
                            TipoVehiculo.CAMION_GRUA -> getString(R.string.mi_vehiculo_tipo_grua)
                            TipoVehiculo.MAQUINARIA_PESADA -> getString(R.string.mi_vehiculo_tipo_maquinaria)
                            TipoVehiculo.LIVIANO -> getString(R.string.mi_vehiculo_tipo_liviano)
                        }
                        binding.ivVehiculoTipo.setImageResource(
                            when (state.tipoVehiculo) {
                                TipoVehiculo.CAMION_GRUA -> R.drawable.grua
                                TipoVehiculo.MAQUINARIA_PESADA -> R.drawable.maquinaria
                                TipoVehiculo.LIVIANO -> R.drawable.liviano
                            }
                        )
                        if (vehiculo != null) {
                            binding.tvVehiculoAgencia.text = vehiculo.agencia
                        }

                        binding.tvEstadoMensaje.text = state.estadoMensaje
                        binding.tvMotivacion.text = state.motivacion
                        binding.tvValorActualLabel.text = getString(
                            R.string.mi_vehiculo_valor_actual_label,
                            state.unidad
                        )
                        binding.tvValorActual.text = state.valorActual?.let {
                            String.format(Locale.getDefault(), "%.0f %s", it, state.unidad)
                        } ?: getString(R.string.mi_vehiculo_valor_actual_placeholder)
                        binding.tvKpiKmHoy.text = String.format(Locale.getDefault(), "%.0f %s", state.kmHoy, state.unidad)
                        binding.tvKpiMantenimientos.text = state.mantenimientosMes.toString()
                        binding.tvKpiAlertas.text = state.alertasCount.toString()

                        actualizarEstadoChip(state.estado)
                        renderMantenimientos(state.mantenimientoCards)
                        renderUsoMensual(state.usoMensual, state.unidad)
                    }
                }
                launch {
                    viewModel.eventos.collect { mensaje ->
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setMessage(mensaje)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                }
            }
        }
    }

    private fun actualizarEstadoChip(estado: EstadoVehiculo) {
        val (texto, color) = when (estado) {
            EstadoVehiculo.OPTIMO -> getString(R.string.mi_vehiculo_estado_optimo) to R.color.success_500
            EstadoVehiculo.ATENCION -> getString(R.string.mi_vehiculo_estado_atencion) to R.color.ice_yellow
            EstadoVehiculo.VENCIDO -> getString(R.string.mi_vehiculo_estado_vencido) to R.color.error_500
        }
        binding.chipEstadoHeader.text = texto
        binding.chipEstadoHeader.chipBackgroundColor = ContextCompat.getColorStateList(requireContext(), color)
    }

    private fun renderMantenimientos(cards: List<MantenimientoCardUi>) {
        binding.containerMantenimientos.removeAllViews()
        if (cards.isEmpty()) {
            val empty = layoutInflater.inflate(R.layout.item_mantenimiento_card, binding.containerMantenimientos, false)
            val card = empty as MaterialCardView
            card.findViewById<android.widget.TextView>(R.id.tvMantenimientoTitulo)
                .text = getString(R.string.mi_vehiculo_mantenimiento_placeholder)
            card.findViewById<android.widget.TextView>(R.id.tvMantenimientoDetalle)
                .text = getString(R.string.mi_vehiculo_mantenimiento_placeholder_detalle)
            binding.containerMantenimientos.addView(card)
            return
        }
        cards.forEachIndexed { index, item ->
            val view = layoutInflater.inflate(R.layout.item_mantenimiento_card, binding.containerMantenimientos, false)
            val card = view as MaterialCardView
            val title = card.findViewById<android.widget.TextView>(R.id.tvMantenimientoTitulo)
            val detail = card.findViewById<android.widget.TextView>(R.id.tvMantenimientoDetalle)
            val icon = card.findViewById<android.widget.ImageView>(R.id.ivEstado)
            title.text = item.titulo
            detail.text = item.detalle
            val color = when (item.estado) {
                EstadoVehiculo.OPTIMO -> R.color.tertiary_container_light
                EstadoVehiculo.ATENCION -> R.color.secondary_container_light
                EstadoVehiculo.VENCIDO -> R.color.error_500
            }
            val iconRes = when (item.estado) {
                EstadoVehiculo.OPTIMO -> R.drawable.ic_check
                EstadoVehiculo.ATENCION -> R.drawable.ic_warning
                EstadoVehiculo.VENCIDO -> R.drawable.ic_close_sheet
            }
            val iconColor = when (item.estado) {
                EstadoVehiculo.OPTIMO -> R.color.on_tertiary_container_light
                EstadoVehiculo.ATENCION -> R.color.on_secondary_container_light
                EstadoVehiculo.VENCIDO -> R.color.white
            }
            card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), color))
            icon.setImageResource(iconRes)
            icon.setColorFilter(ContextCompat.getColor(requireContext(), iconColor))
            card.alpha = 0f
            card.translationY = 16f
            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((index * 60).toLong())
                .setDuration(220)
                .start()
            binding.containerMantenimientos.addView(card)
        }
    }

    private fun renderUsoMensual(items: List<UsoMensualUi>, unidad: String) {
        binding.containerUsoMensual.removeAllViews()
        binding.tvChartPlaceholder.isVisible = items.isEmpty()
        if (items.isEmpty()) {
            val empty = layoutInflater.inflate(R.layout.item_uso_mes, binding.containerUsoMensual, false)
            empty.findViewById<android.widget.TextView>(R.id.tvMes).text = "—"
            empty.findViewById<android.widget.TextView>(R.id.tvTotal)
                .text = getString(R.string.mi_vehiculo_uso_mensual_placeholder)
            empty.findViewById<LinearProgressIndicator>(R.id.progressUso).progress = 0
            binding.containerUsoMensual.addView(empty)
            return
        }
        items.forEach { item ->
            val view = layoutInflater.inflate(R.layout.item_uso_mes, binding.containerUsoMensual, false)
            view.findViewById<android.widget.TextView>(R.id.tvMes).text = item.mes
            view.findViewById<android.widget.TextView>(R.id.tvTotal).text =
                getString(R.string.mi_vehiculo_uso_mensual_total_format, item.total, unidad)
            view.findViewById<LinearProgressIndicator>(R.id.progressUso).progress = item.porcentaje
            binding.containerUsoMensual.addView(view)
        }
    }



    private fun mostrarHistorialAlertas() {
        val state = latestState
        if (state.vehiculo == null) return
        val mensajes = mutableListOf<String>()
        if (state.alertasCount <= 0) {
            mensajes += "Sin alertas activas."
        } else {
            mensajes += "Alertas activas: ${state.alertasCount}"
            state.mantenimientoCards.forEach { card ->
                mensajes += "• ${card.titulo}: ${card.detalle}"
            }
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Historial de alertas")
            .setItems(mensajes.toTypedArray(), null)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun mostrarHistorialMantenimientos() {
        val cards = latestState.mantenimientoCards
        val items = if (cards.isEmpty()) {
            arrayOf(getString(R.string.mi_vehiculo_mantenimiento_placeholder_detalle))
        } else {
            cards.map { "${it.titulo}: ${it.detalle}" }.toTypedArray()
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Mantenimientos realizados")
            .setItems(items, null)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun mostrarConfigIntervaloMantenimiento() {
        val view = layoutInflater.inflate(R.layout.dialog_config_mantenimiento_intervalo, null)
        val kmInput = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etIntervaloKm)
        val horasInput = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etIntervaloHoras)
        kmInput.setText(String.format(Locale.getDefault(), "%.0f", viewModel.obtenerIntervaloMantenimientoKm()))
        horasInput.setText(String.format(Locale.getDefault(), "%.0f", viewModel.obtenerIntervaloMantenimientoHoras()))
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Configurar intervalo de mantenimiento")
            .setView(view)
            .setPositiveButton("Guardar") { _, _ ->
                val km = kmInput.text?.toString()?.trim()?.replace(",", ".")?.toDoubleOrNull()
                val horas = horasInput.text?.toString()?.trim()?.replace(",", ".")?.toDoubleOrNull()
                viewModel.guardarIntervaloMantenimiento(km, horas)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun animateCta() {
        binding.btnRegistrarMantenimiento.apply {
            scaleX = 0.98f
            scaleY = 0.98f
            alpha = 0.9f
            animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(220).start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
