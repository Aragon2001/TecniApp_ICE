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
    private var fabExpanded = false

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
            collapseFab()
            RegistroVehiculoDialogFragment().show(childFragmentManager, RegistroVehiculoDialogFragment.TAG)
        }
        binding.btnRegistroEtm.setOnClickListener {
            collapseFab()
            showRegistroVehiculoPendienteDialog(
                onRegistroGuardado = { viewModel.syncAhora() },
                onNoVehiculo = { }
            )
        }
        binding.btnConfigurarVehiculo.setOnClickListener {
            collapseFab()
            mostrarConfigIntervaloMantenimiento()
        }
        binding.tvKpiAlertas.setOnClickListener { mostrarHistorialAlertas() }
        binding.cardKpiAlertas.setOnClickListener { mostrarHistorialAlertas() }
        binding.tvTituloMantenimientos.setOnClickListener { mostrarHistorialMantenimientos() }
        binding.cardKpiMantenimientos.setOnClickListener { mostrarHistorialMantenimientos() }
        binding.tvAccionRegistrar.setOnClickListener {
            collapseFab()
            RegistroVehiculoDialogFragment().show(childFragmentManager, RegistroVehiculoDialogFragment.TAG)
        }

        binding.btnFabMain.setOnClickListener { toggleFab() }

        observarEstado()
        animateFab()
    }

    private fun toggleFab() {
        fabExpanded = !fabExpanded
        binding.layoutFabMiniGroup.isVisible = fabExpanded
        binding.btnFabMain.setImageResource(
            if (fabExpanded) R.drawable.ic_close_sheet else R.drawable.ic_add
        )
    }

    private fun collapseFab() {
        fabExpanded = false
        binding.layoutFabMiniGroup.isVisible = false
        binding.btnFabMain.setImageResource(R.drawable.ic_add)
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
                        binding.layoutFabGroup.isVisible = visible
                        binding.cardVehiculoHeader.isVisible = visible
                        binding.cardMotivacion.isVisible = visible
                        binding.tvTituloMantenimientos.isVisible = visible
                        binding.tvAccionRegistrar.isVisible = visible
                        binding.containerMantenimientos.isVisible = visible
                        binding.tvTituloHistorialMantenimientos.isVisible = visible
                        binding.containerHistorialMantenimientos.isVisible = visible
                        binding.tvTituloUsoMensual.isVisible = visible
                        binding.containerUsoMensual.isVisible = visible
                        binding.tvChartPlaceholder.isVisible = visible

                        if (!visible) return@collect

                        binding.tvVehiculoPlaca.text = vehiculo!!.placaRaw.ifBlank { vehiculo.vehiculoId }
                        binding.tvVehiculoAgencia.text = buildString {
                            vehiculo.agencia?.let { append(it) }
                            vehiculo.subregion?.let {
                                if (isNotEmpty()) append(" · ")
                                append(it)
                            }
                        }
                        binding.tvVehiculoTipo.text = when (state.tipoVehiculo) {
                            TipoVehiculo.CAMION_GRUA -> getString(R.string.mi_vehiculo_tipo_grua)
                            TipoVehiculo.MAQUINARIA_PESADA -> getString(R.string.mi_vehiculo_tipo_maquinaria)
                            TipoVehiculo.LIVIANO -> getString(R.string.mi_vehiculo_tipo_liviano)
                        }
                        binding.ivVehiculoTipo.setImageResource(
                            when (state.tipoVehiculo) {
                                TipoVehiculo.CAMION_GRUA -> R.drawable.maquinaria
                                TipoVehiculo.MAQUINARIA_PESADA -> R.drawable.precision_manufacturing_24px
                                TipoVehiculo.LIVIANO -> R.drawable.liviano
                            }
                        )

                        binding.tvEstadoMensaje.text = when (state.estado) {
                            EstadoVehiculo.OPTIMO -> getString(R.string.mi_vehiculo_estado_optimo)
                            EstadoVehiculo.ATENCION -> getString(R.string.mi_vehiculo_estado_atencion)
                            EstadoVehiculo.VENCIDO -> getString(R.string.mi_vehiculo_estado_vencido)
                        }
                        binding.tvEstadoMensaje.setTextColor(
                            ContextCompat.getColor(
                                requireContext(), when (state.estado) {
                                    EstadoVehiculo.OPTIMO -> R.color.success_green
                                    EstadoVehiculo.ATENCION -> R.color.status_assigned
                                    EstadoVehiculo.VENCIDO -> R.color.danger_red
                                }
                            )
                        )

                        val fechaEtm = state.etmEstadoTexto
                        val fechaDisplay = runCatching {
                            java.time.LocalDate.parse(fechaEtm, java.time.format.DateTimeFormatter.ISO_DATE)
                                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        }.getOrDefault(fechaEtm)
                        binding.chipEtmEstadoHeader.text = fechaDisplay
                        val colorEtm = if (state.etmEstadoCerrado) R.color.success_500 else R.color.status_assigned
                        binding.chipEtmEstadoHeader.setChipBackgroundColorResource(colorEtm)
                        binding.tvMotivacion.text = state.motivacion
                        binding.tvValorActualLabel.text = getString(
                            R.string.mi_vehiculo_valor_actual_label,
                            state.unidad
                        )
                        val valorFormateado = state.valorActual?.let {
                            String.format(Locale.getDefault(), "%.0f %s", it, state.unidad)
                        } ?: getString(R.string.mi_vehiculo_valor_actual_placeholder)
                        binding.tvValorActual.text = valorFormateado

                        val kmHoyFormateado = String.format(Locale.getDefault(), "%.0f %s", state.kmHoy, state.unidad)
                        binding.tvKpiKmHoy.text = kmHoyFormateado
                        binding.tvHeroKmHoy.text = kmHoyFormateado
                        binding.tvKpiMantenimientos.text = state.mantenimientosMes.toString()
                        binding.tvKpiAlertas.text = state.alertasCount.toString()

                        actualizarEstadoChip(state.estado)
                        renderMantenimientos(state.mantenimientoCards)
                        renderHistorialMantenimientos(state.historialMantenimientosUi)
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
            val strokeColor = when (item.estado) {
                EstadoVehiculo.OPTIMO -> R.color.success_green
                EstadoVehiculo.ATENCION -> R.color.status_assigned
                EstadoVehiculo.VENCIDO -> R.color.danger_red
            }
            val iconRes = when (item.estado) {
                EstadoVehiculo.OPTIMO -> R.drawable.ic_check
                EstadoVehiculo.ATENCION -> R.drawable.ic_warning
                EstadoVehiculo.VENCIDO -> R.drawable.ic_close_sheet
            }
            card.strokeColor = ContextCompat.getColor(requireContext(), strokeColor)
            icon.setImageResource(iconRes)
            icon.setColorFilter(ContextCompat.getColor(requireContext(), strokeColor))
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

    private fun renderHistorialMantenimientos(items: List<HistorialMantenimientoUi>) {
        binding.containerHistorialMantenimientos.removeAllViews()
        if (items.isEmpty()) return
        items.forEach { item ->
            val view = layoutInflater.inflate(R.layout.item_mantenimiento_card, binding.containerHistorialMantenimientos, false)
            val card = view as MaterialCardView
            val title = card.findViewById<android.widget.TextView>(R.id.tvMantenimientoTitulo)
            val detail = card.findViewById<android.widget.TextView>(R.id.tvMantenimientoDetalle)
            val icon = card.findViewById<android.widget.ImageView>(R.id.ivMantenimientoIcon)
            val estadoIcon = card.findViewById<android.widget.ImageView>(R.id.ivEstado)
            title.text = item.tipo
            detail.text = item.detalle
            val tipo = item.tipo.lowercase(Locale.getDefault())
            val iconRes = when {
                "aceite" in tipo -> R.drawable.ic_settings
                "freno" in tipo -> R.drawable.ic_warning
                "llanta" in tipo -> R.drawable.ic_car
                "filtro" in tipo -> R.drawable.ic_check
                else -> R.drawable.ic_edit
            }
            icon.setImageResource(iconRes)
            estadoIcon.isVisible = false
            binding.containerHistorialMantenimientos.addView(card)
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
        val mensajes = state.historialAlertas.ifEmpty { listOf("Sin alertas activas.") }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Historial de alertas")
            .setItems(mensajes.toTypedArray(), null)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun mostrarHistorialMantenimientos() {
        val items = latestState.historialMantenimientos.ifEmpty {
            listOf(getString(R.string.mi_vehiculo_mantenimiento_placeholder_detalle))
        }.toTypedArray()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Mantenimientos realizados")
            .setItems(items, null)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun mostrarConfigIntervaloMantenimiento() {
        val tipos = listOf(
            getString(R.string.mi_vehiculo_chip_aceite),
            getString(R.string.mi_vehiculo_chip_frenos),
            getString(R.string.mi_vehiculo_chip_revision),
            getString(R.string.mi_vehiculo_chip_llantas),
            getString(R.string.mi_vehiculo_chip_filtros),
            getString(R.string.mi_vehiculo_chip_otros)
        )
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.mi_vehiculo_config_tipo_titulo)
            .setItems(tipos.toTypedArray()) { _, which ->
                mostrarDialogoIntervaloParaTipo(tipos[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun mostrarDialogoIntervaloParaTipo(tipoMantenimiento: String) {
        val view = layoutInflater.inflate(R.layout.dialog_config_mantenimiento_intervalo, null)
        val kmInput = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etIntervaloKm)
        val horasInput = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etIntervaloHoras)
        kmInput.setText(String.format(Locale.getDefault(), "%.0f", viewModel.obtenerIntervaloMantenimientoKm(tipoMantenimiento)))
        horasInput.setText(String.format(Locale.getDefault(), "%.0f", viewModel.obtenerIntervaloMantenimientoHoras(tipoMantenimiento)))
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.mi_vehiculo_config_intervalo_titulo, tipoMantenimiento))
            .setView(view)
            .setPositiveButton(R.string.mi_vehiculo_config_guardar) { _, _ ->
                val km = kmInput.text?.toString()?.trim()?.replace(",", ".")?.toDoubleOrNull()
                val horas = horasInput.text?.toString()?.trim()?.replace(",", ".")?.toDoubleOrNull()
                viewModel.guardarIntervaloMantenimiento(tipoMantenimiento, km, horas)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun animateFab() {
        binding.btnFabMain.apply {
            scaleX = 0f
            scaleY = 0f
            alpha = 0f
            animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(300).setDuration(280).start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
