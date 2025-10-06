package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.app.TimePickerDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.lifecycle.lifecycleScope

import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.Arasoftsolutions.tecniapp_ice.Database.entities.MaterialEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.TecnicoEntity
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.BottomsheetAveriaDetalleBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AveriaDetalleBottomSheet : BottomSheetDialogFragment() {

    private var _b: BottomsheetAveriaDetalleBinding? = null
    private val b get() = _b!!
    private val vm: AveriasViewModel by viewModels({ requireParentFragment() })


    private lateinit var item: AveriaUI
    private var materialesCatalogo: List<MaterialEntity> = emptyList()
    private val materialesSeleccionados = linkedMapOf<String, MaterialUso>()
    private var materialesModificados = false
    private var tecnicosCatalogo: List<TecnicoEntity> = emptyList()
    private val tecnicosSeleccionados = linkedMapOf<String, TecnicoAtencion>()
    private val horaFormatter = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { isLenient = false }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = BottomsheetAveriaDetalleBinding.inflate(inflater, container, false)
        return b.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val estadoInicial = Estado.fromLabel(item.estado)

        b.etHoraInicio.setOnClickListener { openTimePicker { h, m ->
            b.etHoraInicio.setText(String.format(Locale.getDefault(), "%02d:%02d", h, m))
        } }
        b.etHoraFin.setOnClickListener { openTimePicker { h, m ->
            b.etHoraFin.setText(String.format(Locale.getDefault(), "%02d:%02d", h, m))
        } }

        bindHeader(estadoInicial)
        bindResumenes()
        bindInputs()

        materialesSeleccionados.clear()
        item.materialesDetalle.forEach { materialesSeleccionados[it.codigo] = it }
        materialesModificados = false
        renderMateriales()

        tecnicosSeleccionados.clear()
        item.tecnicosAtendieron.forEach { tecnico ->
            val key = tecnico.cedula.takeIf { it.isNotBlank() } ?: tecnico.nombre
            if (key.isNotBlank()) tecnicosSeleccionados[key] = tecnico
        }
        ensureTecnicoActual()
        renderTecnicos()

        // --- Catálogos en tiempo real ---
        viewLifecycleOwner.lifecycleScope.launch {
            vm.vehiculosDisponibles.collectLatest {
                b.actvVehiculo.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, it))
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.materialesDisponibles.collectLatest { lista ->
                materialesCatalogo = lista
                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    lista.map { "${it.codigo} - ${it.descripcion}" }
                )
                b.actvMaterial.setAdapter(adapter)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.tecnicosDisponibles.collectLatest { lista ->
                tecnicosCatalogo = lista
                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    lista.map { "${it.cedula} - ${it.nombre}" }
                )
                b.actvTecnico.setAdapter(adapter)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.usuarioActual.filterNotNull().collectLatest {
                ensureTecnicoActual()
                renderTecnicos()
            }
        }

        b.actvMaterial.setOnItemClickListener { _, _, position, _ ->
            materialesCatalogo.getOrNull(position)?.let { agregarMaterial(it) }
            b.actvMaterial.setText("", false)
        }

        b.btnAgregarMaterial.setOnClickListener {
            val texto = b.actvMaterial.text?.toString()?.lowercase(Locale.getDefault()) ?: ""
            materialesCatalogo.find {
                texto.contains(it.codigo.lowercase(Locale.getDefault())) || texto.contains(it.descripcion.lowercase(Locale.getDefault()))
            }?.let { agregarMaterial(it) }
            b.actvMaterial.setText("", false)
        }

        b.actvTecnico.setOnItemClickListener { _, _, position, _ ->
            tecnicosCatalogo.getOrNull(position)?.let { agregarTecnico(it) }
            b.actvTecnico.setText("", false)
        }

        b.actvTecnico.setOnItemClickListener { _, _, position, _ ->
            tecnicosCatalogo.getOrNull(position)?.let { agregarTecnico(it) }
            b.actvTecnico.setText("", false)
        }

        b.btnAgregarTecnico.setOnClickListener {
            val texto = b.actvTecnico.text?.toString()?.lowercase(Locale.getDefault()) ?: ""
            tecnicosCatalogo.find {
                texto.contains(it.cedula.lowercase(Locale.getDefault())) || texto.contains(it.nombre.lowercase(Locale.getDefault()))
            }?.let { agregarTecnico(it) }
            b.actvTecnico.setText("", false)
        }

        configureButtons(estadoInicial)
    }

    private fun bindHeader(estado: Estado) {
        b.tvCaso.text = getString(R.string.averia_caso_format, item.id)
        b.chipEstado.text = item.estado
        val color = when (estado) {
            Estado.PENDIENTE -> "#E53935"
            Estado.ASIGNADA -> "#FBC02D"
            Estado.EN_ATENCION -> "#1E88E5"
            Estado.RESUELTA -> "#43A047"
        }
        b.chipEstado.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor(color))
        b.chipEstado.setTextColor(Color.WHITE)

        b.tvNise.text = getString(R.string.averia_nise_format, item.nise.ifBlank { "—" })
        b.tvRegion.text = getString(R.string.averia_region_label, item.region.ifBlank { "—" })
        b.tvAgencia.text = getString(R.string.averia_agencia_label, item.agencia.ifBlank { "—" })

        val cliente = item.cliente?.takeIf { it.isNotBlank() }
        b.tvCliente.apply {
            isVisible = !cliente.isNullOrBlank()
            text = cliente?.let { getString(R.string.averia_cliente_label, it) } ?: ""
        }

        val coordsText = if (item.lat != 0.0 && item.lng != 0.0) {
            getString(R.string.averia_reporte_coordenadas, item.lat, item.lng)
        } else {
            getString(R.string.averia_reporte_coordenadas_sin_datos)
        }
        b.tvCoordenadas.text = coordsText

        val fechaEvento = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(item.fechaMillis)
        b.tvFechaDetalle.text = getString(R.string.averia_fecha_evento_label, fechaEvento)

        b.tvAsignado.text = getString(
            R.string.averia_asignado_a,
            item.tecnico.ifBlank { getString(R.string.averia_sin_asignar) }
        )
        b.tvAtendido.text = getString(R.string.averia_atendido_por_format, item.atendidoPor.ifBlank { "—" })
        b.tvVehiculo.text = getString(R.string.averia_vehiculo_format, item.vehiculo ?: "—")
    }

    private fun bindResumenes() {
        b.tvCausaActual.text = getString(R.string.averia_causa_resumen, item.causa.ifBlank { "—" })
        b.tvObservacionesActuales.text = getString(R.string.averia_observaciones_resumen, item.observaciones.ifBlank { "—" })
        b.tvLocalizacionActual.isVisible = !item.localizacion.isNullOrBlank()
        b.tvLocalizacionActual.text = item.localizacion?.let { getString(R.string.averia_localizacion_label, it) }
    }

    private fun bindInputs() {
        val nombre = vm.nombreTecnicoActual().orEmpty()
        val vehiculo = item.vehiculo ?: vm.vehiculoPreferido()
        b.etLocalizacion.setText(item.localizacion.orEmpty())
        b.etCausa.setText(item.causa)
        b.etObs.setText(item.observaciones)
        b.etAtendido.setText(item.atendidoPor.ifBlank { nombre })
        b.actvVehiculo.setText(vehiculo.orEmpty(), false)
        b.etHoraInicio.setText(formatHora(item.horaAtencionInicio))
        b.etHoraFin.setText(formatHora(item.horaAtencionFinal))
        b.etKmInicio.setText(item.kilometrajeInicio?.toString().orEmpty())
        b.etKmFinal.setText(item.kilometrajeFinal?.toString().orEmpty())
        b.tilCausa.error = null
        b.etCausa.doAfterTextChanged { b.tilCausa.error = null }
    }

    private fun configureButtons(initialEstado: Estado) {
        var estado = initialEstado
        val usuarioUid = vm.usuarioActual.value?.uid
        val esPropio = item.tecnicoUid.isNullOrBlank() || item.tecnicoUid == usuarioUid

        b.btnAsignar.text = when (estado) {
            Estado.PENDIENTE -> getString(R.string.averia_asignar)
            Estado.ASIGNADA -> getString(R.string.averia_eliminar_asignacion)
            else -> getString(R.string.averia_asignar)
        }

        val usuarioActualUid = vm.usuarioActual.value?.uid
        val puedeGestionar = item.tecnicoUid.isNullOrBlank() || item.tecnicoUid == usuarioActualUid

        if (estado == Estado.PENDIENTE && usuarioActualUid != null && item.tecnicoUid.isNullOrBlank()) {
            vm.onAutoAsignarPendiente(item)
            vm.nombreTecnicoActual()?.let { nombre ->
                b.tvAsignado.text = getString(R.string.averia_asignado_a, nombre)
                item = item.copy(
                    estado = getString(R.string.estado_asignada),
                    tecnico = nombre,
                    tecnicoUid = usuarioActualUid
                )
                estado = Estado.ASIGNADA
                b.chipEstado.text = getString(R.string.estado_asignada)
                b.chipEstado.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#FBC02D"))
                b.btnAsignar.text = getString(R.string.averia_eliminar_asignacion)
                b.btnAsignar.isEnabled = true
            }
        }

        if (estado == Estado.ASIGNADA && !puedeGestionar) {
            b.btnAsignar.isEnabled = false
        }

        when (estado) {
            Estado.ASIGNADA -> {
                b.btnAtender.isVisible = true
                b.btnAtender.text = getString(R.string.averia_guardar_en_atencion)
                b.btnAtender.isEnabled = puedeGestionar
                b.btnAtender.setOnClickListener {
                    val data = collectFormData() ?: return@setOnClickListener
                    if (data.causa.isBlank()) {
                        b.tilCausa.error = getString(R.string.averia_error_causa_requerida)
                        return@setOnClickListener
                    }
                    vm.onAtender(item, data)
                    dismissAllowingStateLoss()
                }
            }
            Estado.EN_ATENCION -> {
                b.btnAtender.isVisible = true
                b.btnAtender.text = getString(R.string.averia_cancelar_atencion)
                b.btnAtender.setOnClickListener {
                    vm.onCancelarAtencion(item)
                    dismissAllowingStateLoss()
                }
                b.btnResolver.isVisible = true
                b.btnResolver.setOnClickListener {
                    val data = collectFormData() ?: return@setOnClickListener
                    vm.onResolver(item, data)
                    dismissAllowingStateLoss()
                }
            }
            else -> {
                b.btnAtender.isVisible = false
                b.btnResolver.isVisible = false
            }
        }

        b.btnResolver.isVisible = estado == Estado.EN_ATENCION || estado == Estado.ASIGNADA
        b.btnResolver.setOnClickListener {
            val data = collectFormData() ?: return@setOnClickListener
            if (data.causa.isBlank()) {
                b.tilCausa.error = getString(R.string.averia_error_causa_requerida)
                return@setOnClickListener
            }
            if (data.materiales.isEmpty()) {
                materialesModificados = true
                renderMateriales()
            }
            vm.onResolver(item, data)
            dismissAllowingStateLoss()
        }
        b.btnResolver.isEnabled = puedeGestionar

        b.btnVerMapa.setOnClickListener {
            val lat = item.lat
            val lng = item.lng
            if (lat != 0.0 && lng != 0.0) {
                val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng")
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
        }
    }

    private fun openTimePicker(onPick: (Int, Int) -> Unit) {
        val cal = Calendar.getInstance()
        TimePickerDialog(requireContext(), { _, h, m -> onPick(h, m) },
            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true
        ).show()
    }

    private fun agregarMaterial(material: MaterialEntity) {
        val actual = materialesSeleccionados[material.codigo]
        val actualizado = actual?.copy(cantidad = actual.cantidad + 1)
            ?: MaterialUso(material.codigo, material.descripcion, 1)
        materialesSeleccionados[material.codigo] = actualizado
        materialesModificados = true
        renderMateriales()
    }

    private fun agregarTecnico(t: TecnicoEntity) {
        val key = if (t.cedula.isNotBlank()) t.cedula else t.nombre
        if (key.isBlank()) return
        tecnicosSeleccionados[key] = TecnicoAtencion(t.cedula, t.nombre)
        renderTecnicos()
    }

    private fun ensureTecnicoActual() {
        val user = vm.usuarioActual.value ?: return
        val nombre = vm.nombreTecnicoActual() ?: return
        val cedula = user.cedula?.trim().orEmpty()
        val key = if (cedula.isNotBlank()) cedula else nombre
        if (!tecnicosSeleccionados.containsKey(key)) {
            tecnicosSeleccionados[key] = TecnicoAtencion(cedula, nombre)
        }
    }

    private fun renderTecnicos() {
        if (_b == null) return
        b.chipGroupTecnicos.removeAllViews()
        tecnicosSeleccionados.forEach { (key, tecnico) ->
            val chip = Chip(requireContext()).apply {
                text = tecnico.nombre.ifBlank { tecnico.cedula }
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    tecnicosSeleccionados.remove(key)
                    renderTecnicos()
                }
            }
            b.chipGroupTecnicos.addView(chip)
        }
        val tieneTecnicos = tecnicosSeleccionados.isNotEmpty()
        b.chipGroupTecnicos.isVisible = tieneTecnicos
        b.tvTecnicosTitulo.isVisible = tieneTecnicos
    }

    private fun renderMateriales() {
        if (_b == null) return
        val materiales = materialesSeleccionados.values.toList()
        val resumenCalculado = MaterialesSerializer.toSummary(materiales)
        val resumen = when {
            resumenCalculado.isNotBlank() -> resumenCalculado
            !materialesModificados && item.materialesResumen.isNotBlank() -> item.materialesResumen
            else -> ""
        }
        val bulletList = materiales.filter { it.cantidad > 0 }
            .joinToString(separator = "\n") { uso ->
                val nombre = uso.descripcion.ifBlank { uso.codigo }
                if (uso.cantidad <= 1) "• $nombre" else "• $nombre x ${uso.cantidad}"
            }
        val content = when {
            bulletList.isNotBlank() -> bulletList
            resumen.isNotBlank() -> resumen
            else -> ""
        }
        b.tvMateriales.isVisible = content.isNotBlank()
        b.tvMaterialesLista.apply {
            isVisible = content.isNotBlank()
            text = content
        }
        b.chipGroupMateriales.removeAllViews()
        materiales.forEach { uso ->
            val chip = Chip(requireContext()).apply {
                text = getString(
                    R.string.averia_chip_material_format,
                    uso.descripcion.ifBlank { uso.codigo },
                    uso.cantidad
                )
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    materialesSeleccionados.remove(uso.codigo)
                    materialesModificados = true
                    renderMateriales()
                }
            }
            b.chipGroupMateriales.addView(chip)
        }
        b.chipGroupMateriales.isVisible = materiales.isNotEmpty()
    }

    private fun formatHora(millis: Long?): String =
        millis?.takeIf { it > 0 }?.let { horaFormatter.format(it) } ?: ""

    private fun parseHora(texto: String?, onError: (String) -> Unit): Long? {
        if (texto.isNullOrBlank()) return null
        return try {
            val parsed = horaFormatter.parse(texto)
            val base = Calendar.getInstance()
            val hora = Calendar.getInstance().apply {
                if (parsed != null) time = parsed
            }
            base.set(Calendar.HOUR_OF_DAY, hora.get(Calendar.HOUR_OF_DAY))
            base.set(Calendar.MINUTE, hora.get(Calendar.MINUTE))
            base.set(Calendar.SECOND, 0)
            base.set(Calendar.MILLISECOND, 0)
            base.timeInMillis
        } catch (ex: ParseException) {
            onError(getString(R.string.averia_error_hora_formato))
            null
        }
    }

    private fun collectFormData(): AveriaActionData? {
        val causa = b.etCausa.text?.toString()?.trim().orEmpty()
        val obs = b.etObs.text?.toString()?.trim()
        val vehiculo = b.actvVehiculo.text?.toString()?.trim()
        val atendido = b.etAtendido.text?.toString()?.trim()
        val uid = vm.usuarioActual.value?.uid ?: item.tecnicoUid

        b.tilHoraInicio.error = null
        b.tilHoraFinal.error = null
        b.tilKmInicio.error = null
        b.tilKmFinal.error = null

        val horaInicioTexto = b.etHoraInicio.text?.toString()?.trim()
        val horaFinalTexto = b.etHoraFin.text?.toString()?.trim()
        val horaInicio = parseHora(horaInicioTexto) { error -> b.tilHoraInicio.error = error }
            ?: System.currentTimeMillis()
        val horaInicioAuto = horaInicioTexto.isNullOrBlank()
        val horaFinal = parseHora(horaFinalTexto) { error -> b.tilHoraFinal.error = error }

        if (!horaInicioAuto && b.tilHoraInicio.error != null) return null
        if (!horaFinalTexto.isNullOrBlank() && horaFinal == null) return null
        if (horaFinal != null && horaFinal <= horaInicio) {
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
        val tecnicos = tecnicosSeleccionados.values.toList()

        return AveriaActionData(
            causa = causa,
            observaciones = obs,
            vehiculo = vehiculo,
            materiales = materiales,
            atendidoPorUid = uid,
            atendidoPorNombre = atendido,
            horaInicioMillis = horaInicio,
            horaFinalMillis = null,
            kilometrajeInicio = b.etKmInicio.text.toString().toDoubleOrNull(),
            kilometrajeFinal = b.etKmFinal.text.toString().toDoubleOrNull(),
            cliente = item.cliente,
            localizacion = b.etLocalizacion.text?.toString(),
            tecnicos = tecnicos
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
