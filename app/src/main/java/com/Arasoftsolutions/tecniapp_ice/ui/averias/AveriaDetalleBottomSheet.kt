package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.BottomsheetAveriaDetalleBinding
import com.google.android.material.chip.Chip
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AveriaDetalleBottomSheet : BottomSheetDialogFragment() {

    private var _b: BottomsheetAveriaDetalleBinding? = null
    private val b get() = _b!!
    private val vm: AveriasViewModel by viewModels({ requireParentFragment() })

    private lateinit var item: AveriaUI
    private var materialesCatalogo: List<com.Arasoftsolutions.tecniapp_ice.Database.entities.MaterialEntity> = emptyList()
    private val materialesSeleccionados = linkedMapOf<String, MaterialUso>()
    private var materialesModificados = false
    private val horaFormatter = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { isLenient = false }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = BottomsheetAveriaDetalleBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        var estado = Estado.fromLabel(item.estado)

        // Datos principales
        b.tvCaso.text = getString(R.string.averia_caso_format, item.id)
        b.tvNise.text = getString(R.string.averia_nise_format, item.nise)
        b.tvAgencia.text = item.agencia
        b.tvRegion.text = item.region
        b.tvEstado.text = item.estado
        b.tvAsignado.text = getString(R.string.averia_asignado_a, item.tecnico.ifBlank { getString(R.string.averia_sin_asignar) })
        b.tvAtendido.text = getString(R.string.averia_atendido_por_format, item.atendidoPor.ifBlank { "—" })
        b.tvVehiculo.text = getString(R.string.averia_vehiculo_format, item.vehiculo ?: "—")

        // Materiales
        materialesModificados = false
        item.materialesDetalle.forEach { uso ->
            materialesSeleccionados[uso.codigo] = uso
        }
        renderMateriales()

        // Vehículos
        val nombreActual = vm.nombreTecnicoActual()
        val vehiculoActual = item.vehiculo ?: vm.vehiculoPreferido()

        b.etCausa.setText(item.causa.takeIf { it.isNotBlank() } ?: "")
        b.etObs.setText(item.observaciones.takeIf { it.isNotBlank() } ?: "")
        b.etAtendido.setText(item.atendidoPor.takeIf { it.isNotBlank() } ?: nombreActual.orEmpty())
        b.actvVehiculo.setText(vehiculoActual.orEmpty(), false)

        b.etHoraInicio.setText(formatHora(item.horaAtencionInicio))
        b.etHoraFin.setText(formatHora(item.horaAtencionFinal))
        b.etKmInicio.setText(item.kilometrajeInicio?.toString().orEmpty())
        b.etKmFinal.setText(item.kilometrajeFinal?.toString().orEmpty())

        b.tilCausa.error = null
        b.etCausa.doAfterTextChanged { b.tilCausa.error = null }

        // Vehículos disponibles
        viewLifecycleOwner.lifecycleScope.launch {
            vm.vehiculosDisponibles.collectLatest { vehiculos ->
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, vehiculos)
                b.actvVehiculo.setAdapter(adapter)
            }
        }

        // Materiales disponibles
        viewLifecycleOwner.lifecycleScope.launch {
            vm.materialesDisponibles.collectLatest { lista ->
                materialesCatalogo = lista
                val display = lista.map { "${it.codigo} - ${it.descripcion}" }
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, display)
                b.actvMaterial.setAdapter(adapter)
            }
        }

        b.actvMaterial.setOnItemClickListener { _, _, position, _ ->
            val material = materialesCatalogo.getOrNull(position) ?: return@setOnItemClickListener
            agregarMaterial(material)
            b.actvMaterial.setText("", false)
        }

        // Botones de acciones
        b.btnAsignar.text = when (estado) {
            Estado.PENDIENTE -> getString(R.string.averia_asignar)
            Estado.ASIGNADA -> getString(R.string.averia_eliminar_asignacion)
            else -> getString(R.string.averia_asignar)
        }
        b.btnAsignar.isEnabled = estado == Estado.PENDIENTE || estado == Estado.ASIGNADA
        b.btnAsignar.setOnClickListener {
            vm.onToggleAsignacion(item)
            dismissAllowingStateLoss()
        }

        val usuarioActualUid = vm.usuarioActual.value?.uid
        val esPropietario = item.tecnicoUid.isNullOrBlank() || item.tecnicoUid == usuarioActualUid

        if (estado == Estado.PENDIENTE && usuarioActualUid != null && item.tecnicoUid.isNullOrBlank()) {
            vm.onAutoAsignarPendiente(item)
            val nombre = vm.nombreTecnicoActual()
            if (!nombre.isNullOrBlank()) {
                b.tvAsignado.text = getString(R.string.averia_asignado_a, nombre)
                item = item.copy(
                    estado = getString(R.string.estado_asignada),
                    tecnico = nombre,
                    tecnicoUid = usuarioActualUid
                )
                estado = Estado.ASIGNADA
                b.tvEstado.text = getString(R.string.estado_asignada)
                b.btnAsignar.text = getString(R.string.averia_eliminar_asignacion)
                b.btnAsignar.isEnabled = true
            }
        }

        if (estado == Estado.ASIGNADA && !esPropietario) {
            b.btnAsignar.isEnabled = false
        }

        when (estado) {
            Estado.ASIGNADA -> {
                b.btnAtender.visibility = View.VISIBLE
                b.btnAtender.text = getString(R.string.averia_guardar_en_atencion)
                b.btnAtender.setOnClickListener {
                    val data = collectFormData() ?: return@setOnClickListener
                    if (data.causa.isBlank()) {
                        b.tilCausa.error = getString(R.string.averia_error_causa_requerida)
                        return@setOnClickListener
                    }
                    vm.onAtender(item, data)
                    dismissAllowingStateLoss()
                }
                b.btnAtender.isEnabled = esPropietario
            }
            Estado.EN_ATENCION -> {
                b.btnAtender.visibility = View.VISIBLE
                b.btnAtender.text = getString(R.string.averia_cancelar_atencion)
                b.btnAtender.setOnClickListener {
                    vm.onCancelarAtencion(item)
                    dismissAllowingStateLoss()
                }
                b.btnAtender.isEnabled = esPropietario
            }
            else -> {
                b.btnAtender.visibility = if (estado == Estado.RESUELTA || estado == Estado.PENDIENTE) View.GONE else View.VISIBLE
                b.btnAtender.isEnabled = false
            }
        }

        if (estado == Estado.EN_ATENCION) {
            b.btnResolver.visibility = View.VISIBLE
            b.btnResolver.setOnClickListener {
                val data = collectFormData() ?: return@setOnClickListener
                if (data.causa.isBlank()) {
                    b.tilCausa.error = getString(R.string.averia_error_causa_requerida)
                    return@setOnClickListener
                }
                vm.onResolver(item, data)
                dismissAllowingStateLoss()
            }
            b.btnResolver.isEnabled = esPropietario
        } else {
            b.btnResolver.visibility = View.GONE
        }

        if (estado == Estado.RESUELTA) {
            b.btnExportar.visibility = View.VISIBLE
            b.btnExportar.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    PdfGenerator.exportAveria(requireContext(), item)
                }
            }
        } else {
            b.btnExportar.visibility = View.GONE
        }

        b.btnVerMapa.setOnClickListener {
            if (item.lat == 0.0 && item.lng == 0.0) return@setOnClickListener
            val uri = Uri.parse("geo:${item.lat},${item.lng}?q=${item.lat},${item.lng}")
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    private fun agregarMaterial(material: com.Arasoftsolutions.tecniapp_ice.Database.entities.MaterialEntity) {
        val actual = materialesSeleccionados[material.codigo]
        val actualizado = if (actual == null) {
            MaterialUso(material.codigo, material.descripcion, 1)
        } else {
            actual.copy(cantidad = actual.cantidad + 1)
        }
        materialesSeleccionados[material.codigo] = actualizado
        materialesModificados = true
        renderMateriales()
    }

    private fun renderMateriales() {
        if (_b == null) return
        val resumenCalculado = MaterialesSerializer.toSummary(materialesSeleccionados.values.toList())
        val resumen = when {
            resumenCalculado.isNotBlank() -> resumenCalculado
            !materialesModificados && item.materialesResumen.isNotBlank() -> item.materialesResumen
            else -> ""
        }
        if (resumen.isNotBlank()) {
            b.tvMateriales.visibility = View.VISIBLE
            b.tvMateriales.text = getString(R.string.averia_materiales_label, resumen)
        } else {
            b.tvMateriales.visibility = View.GONE
        }
        b.chipGroupMateriales.removeAllViews()
        materialesSeleccionados.values.forEach { uso ->
            val chip = Chip(requireContext())
            chip.text = getString(R.string.averia_chip_material_format, uso.descripcion.ifBlank { uso.codigo }, uso.cantidad)
            chip.isCloseIconVisible = true
            chip.setOnCloseIconClickListener {
                materialesSeleccionados.remove(uso.codigo)
                materialesModificados = true
                renderMateriales()
            }
            b.chipGroupMateriales.addView(chip)
        }
        b.chipGroupMateriales.visibility = if (materialesSeleccionados.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun formatHora(millis: Long?): String {
        if (millis == null || millis <= 0) return ""
        return horaFormatter.format(millis)
    }

    private fun parseHora(texto: String?, onError: (String) -> Unit): Long? {
        if (texto.isNullOrBlank()) return null
        return try {
            val parsed = horaFormatter.parse(texto.trim()) ?: return null
            val calendarParsed = Calendar.getInstance().apply { time = parsed }
            val calendarNow = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, calendarParsed.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, calendarParsed.get(Calendar.MINUTE))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            calendarNow.timeInMillis
        } catch (t: Throwable) {
            onError(getString(R.string.averia_error_hora_formato))
            null
        }
    }

    private fun collectFormData(): AveriaActionData? {
        val causa = b.etCausa.text?.toString()?.trim().orEmpty()
        val obs = b.etObs.text?.toString()?.trim().takeIf { !it.isNullOrBlank() }
        val vehiculo = b.actvVehiculo.text?.toString()?.trim().takeIf { !it.isNullOrBlank() }
        val atendido = b.etAtendido.text?.toString()?.trim().takeIf { !it.isNullOrBlank() }
        val uid = vm.usuarioActual.value?.uid ?: item.tecnicoUid

        b.tilHoraInicio.error = null
        b.tilHoraFinal.error = null
        b.tilKmInicio.error = null
        b.tilKmFinal.error = null

        val horaInicioTexto = b.etHoraInicio.text?.toString()?.trim()
        val horaFinalTexto = b.etHoraFin.text?.toString()?.trim()
        val horaInicio = parseHora(horaInicioTexto) { error -> b.tilHoraInicio.error = error }
        val horaFinal = parseHora(horaFinalTexto) { error -> b.tilHoraFinal.error = error }

        if (!horaInicioTexto.isNullOrBlank() && horaInicio == null) return null
        if (!horaFinalTexto.isNullOrBlank() && horaFinal == null) return null
        if (horaInicio != null && horaFinal != null && horaFinal <= horaInicio) {
            b.tilHoraFinal.error = getString(R.string.averia_error_hora_final_menor)
            return null
        }

        val kmInicioTexto = b.etKmInicio.text?.toString()?.trim()
        val kmFinalTexto = b.etKmFinal.text?.toString()?.trim()
        val kmInicio = kmInicioTexto?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
        val kmFinal = kmFinalTexto?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
        if (!kmInicioTexto.isNullOrBlank() && kmInicio == null) {
            b.tilKmInicio.error = getString(R.string.averia_error_km_invalido)
            return null
        }
        if (!kmFinalTexto.isNullOrBlank() && kmFinal == null) {
            b.tilKmFinal.error = getString(R.string.averia_error_km_invalido)
            return null
        }
        if (kmInicio != null && kmFinal != null && kmFinal < kmInicio) {
            b.tilKmFinal.error = getString(R.string.averia_error_km_final_menor)
            return null
        }

        val materiales = materialesSeleccionados.values.toList()

        return AveriaActionData(
            causa = causa,
            observaciones = obs,
            vehiculo = vehiculo,
            materiales = materiales,
            atendidoPorUid = uid,
            atendidoPorNombre = atendido,
            horaInicioMillis = horaInicio,
            horaFinalMillis = horaFinal,
            kilometrajeInicio = kmInicio,
            kilometrajeFinal = kmFinal
        )
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(item: AveriaUI): AveriaDetalleBottomSheet {
            val bs = AveriaDetalleBottomSheet()
            bs.item = item
            return bs
        }
    }
}
