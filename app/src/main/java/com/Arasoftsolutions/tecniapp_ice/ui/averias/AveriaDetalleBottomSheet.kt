package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.app.Dialog
import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.Arasoftsolutions.tecniapp_ice.Database.entities.MaterialEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.MedidorEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.TecnicoEntity
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.BottomsheetAveriaDetalleBinding
import com.Arasoftsolutions.tecniapp_ice.databinding.BottomsheetMedidorMaterialBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import androidx.navigation.findNavController
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
class AveriaDetalleBottomSheet : BottomSheetDialogFragment() {

    private var _b: BottomsheetAveriaDetalleBinding? = null
    private val b get() = _b!!
    private val vm: AveriasViewModel by viewModels({ requireParentFragment() })


    private lateinit var item: AveriaUI
    private var tipoSeleccionado: TipoAfectacion = TipoAfectacion.SECTOR
    private var medidorSeleccionado: MedidorEntity? = null
    private var clienteSeleccionado: String? = null
    private var suppressTipoListener = false
    private var materialesCatalogo: List<MaterialEntity> = emptyList()
    private val materialesSeleccionados = linkedMapOf<String, MaterialUso>()
    private var materialesModificados = false
    private var tecnicosCatalogo: List<TecnicoEntity> = emptyList()
    private val tecnicosSeleccionados = linkedMapOf<String, TecnicoAtencion>()
    private val horaFormatter = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { isLenient = false }
    private var inputsEditable = true
    private var finalInputsEnabled = false
    private var horaInicioEditable = false
    private var estadoActual: Estado = Estado.PENDIENTE

    private fun TextInputLayout.applyAvailability(enabled: Boolean) {
        val hasContent = !editText?.text.isNullOrBlank()
        isVisible = enabled || hasContent
        isEnabled = enabled
        editText?.let { input ->
            input.isEnabled = enabled
            input.isFocusable = enabled
            input.isFocusableInTouchMode = enabled
            input.isClickable = enabled
        }
        // TODO(Codex): Ocultar campos bloqueados manteniendo lectura cuando hay datos previos
    }

    private fun parseLecturas(raw: String?): Pair<String?, String?> {
        if (raw.isNullOrBlank()) return null to null
        val parts = raw.split("|", limit = 2)
        val nueva = parts.getOrNull(0)?.takeIf { it.isNotBlank() }
        val anterior = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
        return nueva to anterior
    }

    private fun buildLecturaPayload(nueva: String, anterior: String?): String {
        return if (anterior.isNullOrBlank()) nueva else "$nueva|$anterior"
        // TODO(Codex): Empaquetar lectura nueva y anterior en formato compacto
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
        // TODO(Codex): Inhabilitar cancelación por gestos o back del BottomSheet de detalle
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setCanceledOnTouchOutside(false)
        dialog.behavior.isDraggable = false
        dialog.behavior.isHideable = false
        dialog.setOnKeyListener { _, keyCode, _ ->
            keyCode == KeyEvent.KEYCODE_BACK
        }
        dialog.setOnShowListener {
            dialog.behavior.skipCollapsed = true
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            // TODO(Codex): Forzar el estado expandido para mostrar controles completos sin gestos
        }
        // TODO(Codex): Forzar cierre únicamente mediante el botón de la esquina
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = BottomsheetAveriaDetalleBinding.inflate(inflater, container, false)
        return b.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val estadoInicial = Estado.fromLabel(item.estado)
        estadoActual = estadoInicial
        vm.resetMedidorEstado()
        tipoSeleccionado = item.tipoAfectacion
        clienteSeleccionado = item.cliente

        b.btnCerrar.setOnClickListener { dismissAllowingStateLoss() }
        // TODO(Codex): Cerrar hoja únicamente mediante botón explícito

        b.etHoraInicio.setOnClickListener {
            if (!horaInicioEditable) return@setOnClickListener
            openTimePicker { h, m ->
                b.etHoraInicio.setText(String.format(Locale.getDefault(), "%02d:%02d", h, m))
            }
        }
        b.etHoraFin.setOnClickListener {
            if (!finalInputsEnabled) return@setOnClickListener
            openTimePicker { h, m ->
                b.etHoraFin.setText(String.format(Locale.getDefault(), "%02d:%02d", h, m))
            }
        }

        listOf(b.etHoraInicio, b.etHoraFin).forEach { editText ->
            editText.inputType = InputType.TYPE_NULL
            editText.keyListener = null
            editText.showSoftInputOnFocus = false
            editText.setOnFocusChangeListener { view, hasFocus ->
                val canOpen = if (editText == b.etHoraInicio) horaInicioEditable else finalInputsEnabled
                if (hasFocus && canOpen) {
                    view.performClick()
                }
            }
        }
        b.etHoraInicio.setOnLongClickListener {
            if (!horaInicioEditable) return@setOnLongClickListener false
            b.etHoraInicio.setText("")
            true
        }
        b.etHoraFin.setOnLongClickListener {
            if (!finalInputsEnabled) return@setOnLongClickListener false
            b.etHoraFin.setText("")
            true
        }

        if (estadoInicial == Estado.ASIGNADA && b.etHoraInicio.text.isNullOrBlank()) {
            b.etHoraInicio.setText(horaFormatter.format(Date()))
        }
        // TODO(Codex): Prefijar hora de inicio con la hora actual al asignar

        bindHeader(estadoInicial)
        bindResumenes()
        bindInputs()
        setupTipoAfectacion()
        setupMedidorBusqueda()

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

        // Vehículos asociados a la agencia del usuario
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                vm.vehiculosDisponibles.combine(vm.usuarioActual) { vehiculos, usuario ->
                    vehiculos to usuario
                }.collectLatest { (lista, usuario) ->
                    val agenciaUsuario = usuario?.subregionNombre?.lowercase(Locale.getDefault())?.trim().orEmpty()
                    val opciones = lista.filter { vehiculo ->
                        if (agenciaUsuario.isBlank()) return@filter true
                        vehiculo.agencia.lowercase(Locale.getDefault()).contains(agenciaUsuario)
                    }
                        .ifEmpty { lista }
                        .map { it.placa }
                        .distinct()
                    b.actvVehiculo.setAdapter(
                        ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, opciones)
                    )
                }
            }
        }
        // TODO(Codex): Cargar vehículos filtrados por la agencia del usuario autenticado

        // Materiales con búsqueda libre y selección precisa
        viewLifecycleOwner.lifecycleScope.launch {
            vm.materialesDisponibles.collectLatest { lista ->
                materialesCatalogo = lista

                val adapterMat = object : ArrayAdapter<MaterialEntity>(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    lista.toMutableList()
                ) {
                    private var filtrados = lista.toMutableList()

                    override fun getCount(): Int = filtrados.size
                    override fun getItem(position: Int): MaterialEntity? = filtrados.getOrNull(position)
                    fun getObject(position: Int): MaterialEntity = filtrados[position]

                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val view = super.getView(position, convertView, parent)
                        (view as TextView).text =
                            "${filtrados[position].codigo} - ${filtrados[position].descripcion}"
                        return view
                    }

                    override fun getFilter(): Filter {
                        return object : Filter() {
                            override fun performFiltering(prefix: CharSequence?): FilterResults {
                                val query = prefix?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty()
                                filtrados = if (query.isBlank()) {
                                    lista.toMutableList()
                                } else {
                                    lista.filter { m ->
                                        val texto = "${m.codigo} ${m.descripcion}".lowercase(Locale.getDefault())
                                        query.split("\\s+".toRegex()).all { texto.contains(it) }
                                    }.toMutableList()
                                }
                                return FilterResults().apply {
                                    values = filtrados
                                    count = filtrados.size
                                }
                            }

                            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                                filtrados = when (val value = results?.values) {
                                    is Collection<*> -> value.filterIsInstance<MaterialEntity>().toMutableList()
                                    is Array<*> -> value.filterIsInstance<MaterialEntity>().toMutableList()
                                    else -> lista.toMutableList()
                                }
                                notifyDataSetChanged()
                            }

                            override fun convertResultToString(resultValue: Any?): CharSequence {
                                val mat = resultValue as? MaterialEntity
                                return if (mat != null) "${mat.codigo} - ${mat.descripcion}" else ""
                            }
                        }
                    }
                }

                b.actvMaterial.setAdapter(adapterMat)
                b.actvMaterial.threshold = 1

                b.actvMaterial.setOnItemClickListener { _, _, position, _ ->
                    val material = adapterMat.getObject(position)
                    b.actvMaterial.setText("", false)
                    manejarSeleccionMaterial(material)
                }
            }
        }

// Técnicos con búsqueda libre y selección precisa
        viewLifecycleOwner.lifecycleScope.launch {
            vm.tecnicosDisponibles.collectLatest { lista ->
                tecnicosCatalogo = lista

                val adapterTec = object : ArrayAdapter<TecnicoEntity>(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    lista.toMutableList()
                ) {
                    private var filtrados = lista.toMutableList()

                    override fun getCount(): Int = filtrados.size
                    override fun getItem(position: Int): TecnicoEntity? = filtrados.getOrNull(position)
                    fun getObject(position: Int): TecnicoEntity = filtrados[position]

                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val view = super.getView(position, convertView, parent)
                        (view as TextView).text =
                            "${filtrados[position].cedula} - ${filtrados[position].nombre}"
                        return view
                    }

                    override fun getFilter(): Filter {
                        return object : Filter() {
                            override fun performFiltering(prefix: CharSequence?): FilterResults {
                                val query = prefix?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty()
                                filtrados = if (query.isBlank()) {
                                    lista.toMutableList()
                                } else {
                                    lista.filter { t ->
                                        val texto = "${t.cedula} ${t.nombre}".lowercase(Locale.getDefault())
                                        query.split("\\s+".toRegex()).all { texto.contains(it) }
                                    }.toMutableList()
                                }
                                return FilterResults().apply {
                                    values = filtrados
                                    count = filtrados.size
                                }
                            }

                            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                                filtrados = when (val value = results?.values) {
                                    is Collection<*> -> value.filterIsInstance<TecnicoEntity>().toMutableList()
                                    is Array<*> -> value.filterIsInstance<TecnicoEntity>().toMutableList()
                                    else -> lista.toMutableList()
                                }
                                notifyDataSetChanged()
                            }

                            override fun convertResultToString(resultValue: Any?): CharSequence {
                                val tec = resultValue as? TecnicoEntity
                                return if (tec != null) "${tec.cedula} - ${tec.nombre}" else ""
                            }
                        }
                    }
                }

                b.actvTecnico.setAdapter(adapterTec)
                b.actvTecnico.threshold = 1

                b.actvTecnico.setOnItemClickListener { _, _, position, _ ->
                    val tecnico = adapterTec.getObject(position)
                    agregarTecnico(tecnico)
                    b.actvTecnico.setText("", false)
                }
            }
        }


        // Mantén técnico actual sincronizado
        viewLifecycleOwner.lifecycleScope.launch {
            vm.usuarioActual.filterNotNull().collectLatest {
                ensureTecnicoActual()
                renderTecnicos()
            }
        }

        // Asegurar teclado y foco en autocompletes
        b.actvMaterial.inputType = InputType.TYPE_CLASS_TEXT
        b.actvMaterial.isFocusable = true
        b.actvMaterial.isFocusableInTouchMode = true
        b.actvTecnico.inputType = InputType.TYPE_CLASS_TEXT
        b.actvTecnico.isFocusable = true
        b.actvTecnico.isFocusableInTouchMode = true
        b.actvVehiculo.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        b.actvVehiculo.isFocusable = true
        b.actvVehiculo.isFocusableInTouchMode = true

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

        val tipoTexto = when (item.tipoAfectacion) {
            TipoAfectacion.CLIENTE -> getString(R.string.averia_tipo_cliente)
            TipoAfectacion.SECTOR -> getString(R.string.averia_tipo_sector)
        }
        b.tvTipoAfectacion.text = getString(R.string.averia_tipo_actual_label, tipoTexto)
        val medidorNumero = item.numeroMedidor?.takeIf { it.isNotBlank() }
        val medidorResumen = medidorNumero?.let {
            getString(R.string.averia_medidor_actual_label, it)
        } ?: getString(R.string.averia_medidor_sin_datos)
        b.tvMedidorActual.text = medidorResumen
        val detalleParts = buildList {
            item.medidorPueblo?.takeIf { it.isNotBlank() }?.let {
                add(getString(R.string.averia_medidor_pueblo, it))
            }
            item.medidorCalle?.takeIf { it.isNotBlank() }?.let {
                add(getString(R.string.averia_medidor_calle, it))
            }
            item.medidorPoste?.takeIf { it.isNotBlank() }?.let {
                add(getString(R.string.averia_medidor_poste, it))
            }
            item.medidorMetros?.takeIf { it.isNotBlank() }?.let {
                add(getString(R.string.averia_medidor_metros, it))
            }
        }
        b.tvMedidorResumen.apply {
            text = detalleParts.joinToString(" • ")
            isVisible = detalleParts.isNotEmpty()
        }

        val emptyValue = getString(R.string.averia_pdf_empty_value)
        val cliente = item.cliente?.takeIf { it.isNotBlank() } ?: emptyValue
        b.tvCliente.apply {
            isVisible = true
            text = getString(R.string.averia_cliente_label, cliente)
        }

        val coordsText = if (item.lat != 0.0 && item.lng != 0.0) {
            getString(R.string.averia_reporte_coordenadas, item.lat, item.lng)
        } else {
            getString(R.string.averia_reporte_coordenadas_sin_datos)
        }
        b.tvCoordenadas.text = coordsText
        val direccionGps = item.direccion?.takeIf { it.isNotBlank() }
        b.tvDireccionGps.apply {
            isVisible = !direccionGps.isNullOrBlank()
            text = direccionGps?.let { getString(R.string.averia_direccion_label, it) }.orEmpty()
        }

        val fechaEvento = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(item.fechaMillis)
        b.tvFechaDetalle.text = getString(R.string.averia_fecha_evento_label, fechaEvento)

        b.tvAsignado.text = getString(
            R.string.averia_asignado_a,
            item.tecnico.ifBlank { getString(R.string.averia_sin_asignar) }
        )
        val atendidoDisplay = item.resolvedAtendidoDisplay(emptyValue)
        b.tvAtendido.text = getString(R.string.averia_atendido_por_format, atendidoDisplay)
        b.tvVehiculo.text = getString(R.string.averia_vehiculo_format, item.vehiculo ?: "—")
    }

    private fun bindResumenes() {
        val emptyValue = getString(R.string.averia_pdf_empty_value)
        b.tvCausaActual.text = item.causa.ifBlank { emptyValue }
        b.tvObservacionesActuales.text = item.observaciones.ifBlank { emptyValue }
        val localizacion = item.localizacion?.takeIf { it.isNotBlank() } ?: emptyValue
        val detalleParts = buildList {
            item.medidorPueblo?.takeIf { it.isNotBlank() }?.let {
                add(getString(R.string.averia_medidor_pueblo, it))
            }
            item.medidorCalle?.takeIf { it.isNotBlank() }?.let {
                add(getString(R.string.averia_medidor_calle, it))
            }
            item.medidorPoste?.takeIf { it.isNotBlank() }?.let {
                add(getString(R.string.averia_medidor_poste, it))
            }
            item.medidorMetros?.takeIf { it.isNotBlank() }?.let {
                add(getString(R.string.averia_medidor_metros, it))
            }
        }
        val baseLocalizacion = getString(R.string.averia_localizacion_label, localizacion)
        b.tvLocalizacionActual.text = if (detalleParts.isNotEmpty()) {
            getString(
                R.string.averia_localizacion_detalle_format,
                baseLocalizacion,
                detalleParts.joinToString(" • ")
            )
        } else {
            baseLocalizacion
        }
    }

    private fun bindInputs() {
        val vehiculo = item.vehiculo ?: vm.vehiculoPreferido()
        b.etLocalizacion.setText(item.localizacion.orEmpty())
        b.etCausa.setText(item.causa)
        b.etObs.setText(item.observaciones)
        b.actvVehiculo.setText(vehiculo.orEmpty(), false)
        b.etHoraInicio.setText(formatHora(item.horaAtencionInicio))
        b.etHoraFin.setText(formatHora(item.horaAtencionFinal))
        b.etKmInicio.setText(item.kilometrajeInicio?.toString().orEmpty())
        b.etKmFinal.setText(item.kilometrajeFinal?.toString().orEmpty())
        b.etMedidor.setText(item.numeroMedidor.orEmpty())
        b.tilCausa.error = null
        b.etCausa.doAfterTextChanged { b.tilCausa.error = null }
    }

    private fun setupTipoAfectacion() {
        suppressTipoListener = true
        b.toggleTipoAfectacion.check(
            if (tipoSeleccionado == TipoAfectacion.CLIENTE) b.btnTipoCliente.id else b.btnTipoSector.id
        )
        suppressTipoListener = false
        b.toggleTipoAfectacion.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || suppressTipoListener) return@addOnButtonCheckedListener
            tipoSeleccionado = if (checkedId == b.btnTipoCliente.id) {
                TipoAfectacion.CLIENTE
            } else {
                TipoAfectacion.SECTOR
            }
            if (tipoSeleccionado == TipoAfectacion.SECTOR) {
                medidorSeleccionado = null
                clienteSeleccionado = item.cliente
                vm.resetMedidorEstado()
            }
            updateTipoSection()
            applyInputState(estadoActual)
        }
        updateTipoSection()
        applyInputState(estadoActual)
    }

    private fun setupMedidorBusqueda() {
        b.tilMedidor.setEndIconOnClickListener {
            if (!inputsEditable) return@setEndIconOnClickListener
            if (tipoSeleccionado != TipoAfectacion.CLIENTE) {
                b.tilMedidor.error = getString(R.string.averia_medidor_error_tipo)
                return@setEndIconOnClickListener
            }
            val numero = b.etMedidor.text?.toString()?.trim().orEmpty()
            if (numero.isEmpty()) {
                b.tilMedidor.error = getString(R.string.averia_medidor_error_requerido)
                return@setEndIconOnClickListener
            }
            b.tilMedidor.error = null
            b.tilMedidor.helperText = null
            vm.buscarMedidor(numero)
            // TODO(Codex): Validar estado del medidor antes de lanzar la búsqueda
        }
        b.etMedidor.doAfterTextChanged {
            if (it.isNullOrBlank()) {
                medidorSeleccionado = null
                clienteSeleccionado = item.cliente
                b.tilMedidor.error = null
                b.tilMedidor.helperText = null
                if (tipoSeleccionado == TipoAfectacion.CLIENTE) {
                    renderMedidorInfo(null)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.medidorEstado.collectLatest { state ->
                when (state) {
                    MedidorLookupState.Idle -> {
                        b.tilMedidor.isEndIconVisible = inputsEditable && tipoSeleccionado == TipoAfectacion.CLIENTE
                        if (tipoSeleccionado == TipoAfectacion.CLIENTE) {
                            b.tilMedidor.error = null
                            b.tilMedidor.helperText = null
                        }
                    }
                    MedidorLookupState.Loading -> {
                        b.tilMedidor.isEndIconVisible = false
                        b.tilMedidor.error = null
                        b.tilMedidor.helperText = getString(R.string.averia_medidor_busqueda)
                    }
                    is MedidorLookupState.Success -> {
                        medidorSeleccionado = state.medidor
                        clienteSeleccionado = state.medidor.cliente?.trim().takeIf { !it.isNullOrBlank() }
                            ?: clienteSeleccionado
                        b.tilMedidor.isEndIconVisible = inputsEditable && tipoSeleccionado == TipoAfectacion.CLIENTE
                        b.tilMedidor.error = null
                        b.tilMedidor.helperText = getString(
                            R.string.averia_medidor_encontrado,
                            state.medidor.medidorNumber
                        )
                        val numero = state.medidor.medidorNumber.takeIf { it.isNotBlank() }
                        if (!numero.isNullOrBlank()) {
                            b.etMedidor.setText(numero)
                            b.etMedidor.setSelection(numero.length)
                        }
                        state.medidor.localizacion?.toString()?.takeIf { it.isNotBlank() }?.let { loc ->
                            if (b.etLocalizacion.text.isNullOrBlank()) {
                                b.etLocalizacion.setText(loc)
                            }
                        }
                        if (tipoSeleccionado != TipoAfectacion.CLIENTE) {
                            suppressTipoListener = true
                            b.toggleTipoAfectacion.check(b.btnTipoCliente.id)
                            suppressTipoListener = false
                            tipoSeleccionado = TipoAfectacion.CLIENTE
                        }
                        renderMedidorInfo(state.medidor)
                        updateTipoSection()
                    }
                    is MedidorLookupState.NotFound -> {
                        medidorSeleccionado = null
                        b.tilMedidor.isEndIconVisible = inputsEditable && tipoSeleccionado == TipoAfectacion.CLIENTE
                        b.tilMedidor.helperText = null
                        b.tilMedidor.error = getString(R.string.averia_medidor_no_encontrado, state.numero)
                    }
                    is MedidorLookupState.Error -> {
                        b.tilMedidor.isEndIconVisible = inputsEditable && tipoSeleccionado == TipoAfectacion.CLIENTE
                        b.tilMedidor.helperText = null
                        b.tilMedidor.error = state.message.ifBlank {
                            getString(R.string.medidor_estado_error_generico)
                        }
                    }
                }
            }
        }

        renderMedidorInfo(if (tipoSeleccionado == TipoAfectacion.CLIENTE) medidorSeleccionado else null)
    }

    private fun updateTipoSection() {
        if (_b == null) return
        suppressTipoListener = true
        b.toggleTipoAfectacion.check(
            if (tipoSeleccionado == TipoAfectacion.CLIENTE) b.btnTipoCliente.id else b.btnTipoSector.id
        )
        suppressTipoListener = false
        val esCliente = tipoSeleccionado == TipoAfectacion.CLIENTE
        b.tilMedidor.isVisible = esCliente
        b.tilMedidor.isEndIconVisible = esCliente && b.tilMedidor.isEnabled
        if (!esCliente) {
            b.tilMedidor.error = null
            b.tilMedidor.helperText = null
        }
        renderMedidorInfo(if (esCliente) medidorSeleccionado else null)
    }

    private fun renderMedidorInfo(medidor: MedidorEntity?) {
        if (_b == null) return
        val esCliente = tipoSeleccionado == TipoAfectacion.CLIENTE
        val emptyValue = getString(R.string.averia_pdf_empty_value)
        val tipoLabel = when (tipoSeleccionado) {
            TipoAfectacion.CLIENTE -> getString(R.string.averia_tipo_cliente)
            TipoAfectacion.SECTOR -> getString(R.string.averia_tipo_sector)
        }
        b.tvTipoAfectacion.text = getString(R.string.averia_tipo_actual_label, tipoLabel)
        val clienteTexto = when {
            medidor?.cliente?.isNotBlank() == true -> medidor.cliente!!.trim()
            clienteSeleccionado?.isNotBlank() == true -> clienteSeleccionado!!.trim()
            item.cliente?.isNotBlank() == true -> item.cliente!!.trim()
            else -> emptyValue
        }
        b.tvCliente.text = getString(R.string.averia_cliente_label, clienteTexto.ifBlank { emptyValue })
        b.tvMedidorCliente.apply {
            text = getString(R.string.averia_cliente_label, clienteTexto.ifBlank { emptyValue })
            isVisible = esCliente && clienteTexto.isNotBlank()
        }
        val localizacionTexto = medidor?.localizacion?.toString()?.takeIf { it.isNotBlank() }
            ?: b.etLocalizacion.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            ?: item.localizacion?.takeIf { it.isNotBlank() }
        b.tvMedidorLocalizacion.apply {
            text = getString(
                R.string.averia_localizacion_label,
                localizacionTexto ?: emptyValue
            )
            isVisible = esCliente && !localizacionTexto.isNullOrBlank()
        }
        val detalleParts = buildList {
            val pueblo = medidor?.pueblo?.takeIf { it.isNotBlank() } ?: item.medidorPueblo
            if (!pueblo.isNullOrBlank()) add(getString(R.string.averia_medidor_pueblo, pueblo))
            val calle = medidor?.calle?.takeIf { it.isNotBlank() } ?: item.medidorCalle
            if (!calle.isNullOrBlank()) add(getString(R.string.averia_medidor_calle, calle))
            val poste = medidor?.poste?.takeIf { it.isNotBlank() } ?: item.medidorPoste
            if (!poste.isNullOrBlank()) add(getString(R.string.averia_medidor_poste, poste))
            val metros = medidor?.metros?.takeIf { it.isNotBlank() } ?: item.medidorMetros
            if (!metros.isNullOrBlank()) add(getString(R.string.averia_medidor_metros, metros))
        }
        val numero = medidor?.medidorNumber?.takeIf { it.isNotBlank() } ?: item.numeroMedidor
        val medidorLabel = numero?.takeIf { it.isNotBlank() }
            ?.let { getString(R.string.averia_medidor_actual_label, it) }
            ?: getString(R.string.averia_medidor_sin_datos)
        b.tvMedidorActual.text = medidorLabel
        b.tvMedidorResumen.apply {
            text = detalleParts.joinToString(" • ")
            isVisible = text.isNotBlank()
        }
        b.tvMedidorDetalle.apply {
            text = detalleParts.joinToString(" • ")
            isVisible = esCliente && detalleParts.isNotEmpty()
        }
    }

    private fun applyInputState(estado: Estado) {
        val isPending = estado == Estado.PENDIENTE
        val isAsignada = estado == Estado.ASIGNADA
        val isEnAtencion = estado == Estado.EN_ATENCION
        val isResuelta = estado == Estado.RESUELTA

        inputsEditable = !isPending
        finalInputsEnabled = isEnAtencion || isResuelta
        horaInicioEditable = isAsignada || finalInputsEnabled

        val allowCauseObs = isEnAtencion || isResuelta
        val allowMaterials = isEnAtencion || isResuelta
        val allowLocalizacion = isEnAtencion || isResuelta
        val allowInitialKm = !isPending
        val allowMedidor = !isPending && tipoSeleccionado == TipoAfectacion.CLIENTE
        val allowVehiculo = !isPending
        val allowTecnicos = !isPending

        b.toggleTipoAfectacion.isEnabled = !isPending
        b.btnTipoCliente.isEnabled = !isPending
        b.btnTipoSector.isEnabled = !isPending

        b.tilTecnicoBuscar.applyAvailability(allowTecnicos)
        b.tilMaterialBuscar.applyAvailability(allowMaterials)
        b.tilMedidor.applyAvailability(allowMedidor)
        b.tilLocalizacion.applyAvailability(allowLocalizacion)
        b.tilCausa.applyAvailability(allowCauseObs)
        b.tilObs.applyAvailability(allowCauseObs)
        b.tilVehiculo.applyAvailability(allowVehiculo)
        b.tilHoraInicio.applyAvailability(horaInicioEditable)
        b.tilHoraFinal.applyAvailability(finalInputsEnabled)
        b.tilKmInicio.applyAvailability(allowInitialKm)
        b.tilKmFinal.applyAvailability(finalInputsEnabled)

        b.actvTecnico.isEnabled = allowTecnicos
        b.actvTecnico.isClickable = allowTecnicos
        b.actvMaterial.isEnabled = allowMaterials
        b.actvMaterial.isClickable = allowMaterials
        b.actvVehiculo.isEnabled = allowVehiculo
        b.actvVehiculo.isClickable = allowVehiculo

        b.etHoraInicio.isVisible = b.tilHoraInicio.isVisible
        b.etHoraFin.isVisible = b.tilHoraFinal.isVisible
        b.etKmInicio.isVisible = b.tilKmInicio.isVisible
        b.etKmFinal.isVisible = b.tilKmFinal.isVisible
        // TODO(Codex): Mantener consistencia de visibilidad entre TextInputLayout y editText asociados

        b.tilMedidor.isEndIconVisible = allowMedidor
        b.chipGroupTecnicos.isEnabled = allowTecnicos
        val hasTecnicos = b.chipGroupTecnicos.childCount > 0
        b.chipGroupTecnicos.isVisible = allowTecnicos || hasTecnicos
        b.chipGroupMateriales.isEnabled = allowMaterials
        val hasMateriales = b.chipGroupMateriales.childCount > 0
        b.chipGroupMateriales.isVisible = allowMaterials || hasMateriales
        // TODO(Codex): Esconder agrupadores cuando no son interactivos y no presentan datos cargados

        renderTecnicos()
        renderMateriales()
    }

    private fun configureButtons(initialEstado: Estado) {
        val usuarioUid = vm.usuarioActual.value?.uid
        val puedeGestionar = item.tecnicoUid.isNullOrBlank() || item.tecnicoUid == usuarioUid
        val green = ContextCompat.getColor(requireContext(), R.color.success_500)
        val red = ContextCompat.getColor(requireContext(), R.color.error_500)
        val blue = ContextCompat.getColor(requireContext(), R.color.ice_blue)
        fun MaterialButton.applyPalette(background: Int, textColor: Int = Color.WHITE) {
            backgroundTintList = ColorStateList.valueOf(background)
            setTextColor(textColor)
            iconTint = ColorStateList.valueOf(textColor)
        }

        b.btnVerMapa.applyPalette(blue)
        b.btnEliminar.applyPalette(red)
        b.btnResolver.applyPalette(green)
        b.btnAtender.applyPalette(green)
        b.btnModificar.applyPalette(green)
        b.btnModificar.isVisible = false

        b.btnAsignar.apply {
            when (initialEstado) {
                Estado.PENDIENTE -> {
                    text = getString(R.string.averia_asignar)
                    isVisible = true
                    isEnabled = usuarioUid != null
                    setOnClickListener {
                        vm.onToggleAsignacion(item)
                        dismissAllowingStateLoss()
                    }
                }
                Estado.ASIGNADA -> {
                    text = getString(R.string.averia_eliminar_asignacion)
                    isVisible = true
                    isEnabled = puedeGestionar
                    setOnClickListener {
                        if (!puedeGestionar) return@setOnClickListener
                        vm.onToggleAsignacion(item)
                        dismissAllowingStateLoss()
                    }
                }
                else -> {
                    isVisible = false
                }
            }
        }

        b.btnAtender.isVisible = false
        b.btnResolver.isVisible = false
        b.btnExportar.isVisible = false
        b.btnAnular.isVisible = false
        b.btnEliminar.isVisible = false
        b.btnModificar.isVisible = false

        when (initialEstado) {
            Estado.ASIGNADA -> {
                b.btnAtender.apply {
                    isVisible = true
                    text = getString(R.string.averia_iniciar_atencion)
                    isEnabled = puedeGestionar
                    setOnClickListener {
                        if (!puedeGestionar) return@setOnClickListener
                        val data = collectFormData() ?: return@setOnClickListener
                        vm.onAtender(item, data)
                        dismissAllowingStateLoss()
                    }
                }
            }
            Estado.EN_ATENCION -> {
                b.btnAtender.apply {
                    isVisible = true
                    text = getString(R.string.averia_cancelar_atencion)
                    isEnabled = puedeGestionar
                    applyPalette(red)
                    setOnClickListener {
                        if (!puedeGestionar) return@setOnClickListener
                        vm.onCancelarAtencion(item)
                        dismissAllowingStateLoss()
                    }
                }
                b.btnResolver.apply {
                    isVisible = true
                    isEnabled = puedeGestionar
                    text = getString(R.string.averia_guardar_resolucion)
                    applyPalette(green)
                    setOnClickListener {
                        if (!puedeGestionar) return@setOnClickListener
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
                }
            }
            Estado.RESUELTA -> {
                b.btnExportar.apply {
                    isVisible = true
                    setOnClickListener {
                        viewLifecycleOwner.lifecycleScope.launch {
                            PdfGenerator.exportAveria(requireContext(), item)
                        }
                    }
                }
                b.btnModificar.apply {
                    isVisible = true
                    isEnabled = puedeGestionar
                    setOnClickListener {
                        if (!puedeGestionar) return@setOnClickListener
                        val data = collectFormData() ?: return@setOnClickListener
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.averia_modificar_confirm_title)
                            .setMessage(R.string.averia_modificar_confirm_message)
                            .setPositiveButton(R.string.averia_modificar_confirm_positive) { _, _ ->
                                vm.onResolver(item, data)
                                dismissAllowingStateLoss()
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                    }
                }
                b.btnEliminar.apply {
                    isVisible = true
                    isEnabled = puedeGestionar
                    setOnClickListener {
                        if (!puedeGestionar) return@setOnClickListener
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.averia_eliminar_confirm_title)
                            .setMessage(R.string.averia_eliminar_confirm_message)
                            .setPositiveButton(R.string.averia_eliminar_confirm_positive) { _, _ ->
                                vm.onEliminarResuelta(item)
                                dismissAllowingStateLoss()
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                    }
                }
            }
            else -> Unit
        }

        if (initialEstado == Estado.PENDIENTE) {
            b.btnAnular.apply {
                isVisible = true
                isEnabled = puedeGestionar
                applyPalette(red)
                setOnClickListener {
                    if (!puedeGestionar) return@setOnClickListener
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.averia_anular_confirm_title)
                        .setMessage(R.string.averia_anular_confirm_message)
                        .setPositiveButton(R.string.averia_anular_confirm_positive) { _, _ ->
                            vm.onAnularPendiente(item)
                            dismissAllowingStateLoss()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            }
        }

        b.btnVerMapa.setOnClickListener {
            MapNavigation.open(requireContext(), item.lat, item.lng, item.localizacion ?: item.direccion)
        }
    }

    private fun openTimePicker(onPick: (Int, Int) -> Unit) {
        val cal = Calendar.getInstance()
        TimePickerDialog(requireContext(), { _, h, m -> onPick(h, m) },
            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true
        ).show()
    }

    private fun manejarSeleccionMaterial(material: MaterialEntity) {
        val existente = materialesSeleccionados[material.codigo]
        if (esMaterialMedidor(material) || existente?.medidorInstalado != null) {
            solicitarMedidorMaterial(material, existente)
        } else {
            val cantidadActual = existente?.cantidad?.takeIf { it > 0 } ?: 1
            mostrarDialogoCantidadMaterial(material, cantidadActual) { cantidadSeleccionada ->
                actualizarMaterial(material, cantidadSeleccionada)
            }
        }
    }

    private fun solicitarMedidorMaterial(
        material: MaterialEntity,
        existente: MaterialUso?
    ) {
        if (tipoSeleccionado != TipoAfectacion.CLIENTE) {
            b.tilMedidor.error = getString(R.string.averia_medidor_error_tipo)
            return
        }
        val numeroActual = b.etMedidor.text?.toString()?.trim().orEmpty().ifBlank { item.numeroMedidor.orEmpty() }
        if (numeroActual.isBlank()) {
            b.tilMedidor.error = getString(R.string.averia_medidor_error_requerido)
            return
        }
        val cantidadInicial = existente?.cantidad?.takeIf { it > 0 } ?: 1
        val metadataInicial = existente?.medidorInstalado
        mostrarDialogoMedidor(material, cantidadInicial, metadataInicial) { cantidad, metadata ->
            actualizarMaterial(material, cantidad, metadata)
            if (cantidad > 0 && metadata != null) {
                solicitarAbrirPanelAdminMedidor(metadata)
            }
        }
    }

    private fun actualizarMaterial(
        material: MaterialEntity,
        cantidad: Int,
        metadata: MedidorInstalacion? = null
    ) {
        val clave = material.codigo
        val cantidadNormalizada = cantidad.coerceAtLeast(0)
        if (cantidadNormalizada == 0) {
            if (materialesSeleccionados.remove(clave) != null) {
                materialesModificados = true
                renderMateriales()
            }
            return
        }
        val anterior = materialesSeleccionados[clave]
        val meta = metadata ?: anterior?.medidorInstalado
        val actualizado = MaterialUso(clave, material.descripcion, cantidadNormalizada, meta)
        materialesSeleccionados[clave] = actualizado
        if (anterior != actualizado) {
            materialesModificados = true
        }
        renderMateriales()
    }

    private fun mostrarDialogoCantidadMaterial(
        material: MaterialEntity,
        cantidadInicial: Int,
        onCantidadSeleccionada: (Int) -> Unit
    ) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_material_cantidad, null)
        val tilCantidad = view.findViewById<TextInputLayout>(R.id.tilCantidad)
        val etCantidad = view.findViewById<TextInputEditText>(R.id.etCantidad)
        if (cantidadInicial > 0) {
            etCantidad.setText(cantidadInicial.toString())
            etCantidad.setSelection(etCantidad.text?.length ?: 0)
        }

        val descripcion = material.descripcion.ifBlank { material.codigo }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.averia_material_cantidad_titulo, descripcion))
            .setView(view)
            .setPositiveButton(R.string.averia_material_cantidad_guardar, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.setOnClickListener {
                tilCantidad.error = null
                val cantidadTexto = etCantidad.text?.toString()?.trim()
                val cantidadSeleccionada = cantidadTexto?.toIntOrNull()
                if (cantidadSeleccionada == null || cantidadSeleccionada < 0) {
                    tilCantidad.error = getString(R.string.averia_material_cantidad_error)
                    return@setOnClickListener
                }
                onCantidadSeleccionada(cantidadSeleccionada)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun mostrarDialogoMedidor(
        material: MaterialEntity,
        cantidadInicial: Int,
        metadataInicial: MedidorInstalacion?,
        onResultado: (Int, MedidorInstalacion?) -> Unit
    ) {
        val dialog = BottomSheetDialog(requireContext())
        val binding = BottomsheetMedidorMaterialBinding.inflate(layoutInflater)
        dialog.setContentView(binding.root)
        dialog.setCanceledOnTouchOutside(false)
        val descripcion = material.descripcion.ifBlank { material.codigo }
        binding.tvTitulo.text = getString(R.string.averia_medidor_dialog_title, descripcion)
        if (cantidadInicial > 0) {
            binding.etCantidad.setText(cantidadInicial.toString())
            binding.etCantidad.setSelection(binding.etCantidad.text?.length ?: 0)
        }
        binding.etNumero.setText(metadataInicial?.numero)
        val (lecturaNueva, lecturaAnterior) = parseLecturas(metadataInicial?.lectura)
        binding.etLecturaNueva.setText(lecturaNueva)
        binding.etLecturaAnterior.setText(lecturaAnterior)

        fun buildMetadata(cantidad: Int): Pair<Int, MedidorInstalacion?>? {
            binding.tilCantidad.error = null
            binding.tilNumero.error = null
            binding.tilLecturaNueva.error = null

            if (cantidad < 0) {
                binding.tilCantidad.error = getString(R.string.averia_material_cantidad_error)
                return null
            }
            if (cantidad == 0) {
                return 0 to null
            }

            val numero = binding.etNumero.text?.toString()?.trim().orEmpty()
            val lecturaNuevaTexto = binding.etLecturaNueva.text?.toString()?.trim().orEmpty()
            val lecturaAnteriorTexto = binding.etLecturaAnterior.text?.toString()?.trim().orEmpty()
            if (numero.isBlank()) {
                binding.tilNumero.error = getString(R.string.averia_medidor_error_numero)
                return null
            }
            if (lecturaNuevaTexto.isBlank()) {
                binding.tilLecturaNueva.error = getString(R.string.averia_medidor_error_lectura)
                return null
            }
            val payload = buildLecturaPayload(lecturaNuevaTexto, lecturaAnteriorTexto)
            val metadata = MedidorInstalacion(numero, payload)
            return cantidad to metadata
        }

        binding.btnGuardar.setOnClickListener {
            val cantidad = binding.etCantidad.text?.toString()?.trim()?.toIntOrNull()
            if (cantidad == null) {
                binding.tilCantidad.error = getString(R.string.averia_material_cantidad_error)
                return@setOnClickListener
            }
            val resultado = buildMetadata(cantidad) ?: return@setOnClickListener
            val (cantidadFinal, metadataFinal) = resultado
            val mensaje = if (cantidadFinal == 0) {
                getString(R.string.averia_medidor_confirm_eliminar)
            } else {
                getString(R.string.averia_medidor_confirm_guardar, metadataFinal?.numero.orEmpty())
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.averia_medidor_confirm_title)
                .setMessage(mensaje)
                .setPositiveButton(R.string.averia_medidor_confirm_positive) { _, _ ->
                    onResultado(cantidadFinal, metadataFinal)
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        binding.btnCancelar.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.averia_medidor_confirm_cancelar_title)
                .setMessage(R.string.averia_medidor_confirm_cancelar_message)
                .setPositiveButton(R.string.averia_medidor_confirm_cancelar_positive) { _, _ ->
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.no, null)
                .show()
        }

        dialog.show()
        // TODO(Codex): Migrar flujo de material medidor a BottomSheet con confirmaciones
    }

    private fun esMaterialMedidor(material: MaterialEntity): Boolean {
        val texto = "${material.codigo} ${material.descripcion}".lowercase(Locale.getDefault())
        return texto.contains("medidor")
    }

    private fun medidorDetalle(metadata: MedidorInstalacion?): String? {
        metadata ?: return null
        val partes = buildList {
            metadata.numero?.takeIf { it.isNotBlank() }?.let {
                add(getString(R.string.averia_medidor_detalle_numero, it))
            }
            val (lecturaNueva, lecturaAnterior) = parseLecturas(metadata.lectura)
            lecturaNueva?.let { add(getString(R.string.averia_medidor_detalle_lectura, it)) }
            lecturaAnterior?.let { add(getString(R.string.averia_medidor_detalle_lectura_anterior, it)) }
        }
        return partes.takeIf { it.isNotEmpty() }?.joinToString(" • ")
    }

    private fun solicitarAbrirPanelAdminMedidor(metadata: MedidorInstalacion) {
        val (lecturaNueva, lecturaAnterior) = parseLecturas(metadata.lectura)
        val mensaje = if (lecturaAnterior.isNullOrBlank()) {
            getString(
                R.string.averia_medidor_admin_message,
                metadata.numero.orEmpty(),
                lecturaNueva.orEmpty()
            )
        } else {
            getString(
                R.string.averia_medidor_admin_message_prev,
                metadata.numero.orEmpty(),
                lecturaNueva.orEmpty(),
                lecturaAnterior
            )
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.averia_medidor_admin_title)
            .setMessage(mensaje)
            .setPositiveButton(R.string.averia_medidor_admin_ir) { _, _ ->
                if (!isAdded) return@setPositiveButton
                dismissAllowingStateLoss()
                runCatching {
                    requireActivity()
                        .findNavController(R.id.nav_host_fragment_content_main)
                        .navigate(R.id.nav_admin)
                }
            }
            .setNegativeButton(R.string.averia_medidor_admin_luego, null)
            .show()
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
        val editable = inputsEditable
        tecnicosSeleccionados.forEach { (key, tecnico) ->
            val chip = Chip(requireContext()).apply {
                text = tecnico.nombre.ifBlank { tecnico.cedula }
                isCloseIconVisible = editable
                isEnabled = editable
                if (editable) {
                    setOnCloseIconClickListener {
                        tecnicosSeleccionados.remove(key)
                        renderTecnicos()
                    }
                } else {
                    setOnCloseIconClickListener(null)
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
        val materiales = materialesSeleccionados.values.filter { it.cantidad > 0 }
        val resumenCalculado = MaterialesSerializer.toSummary(materiales)
        val resumen = when {
            resumenCalculado.isNotBlank() -> resumenCalculado
            !materialesModificados && item.materialesResumen.isNotBlank() -> item.materialesResumen
            else -> ""
        }
        val bulletList = materiales.filter { it.cantidad > 0 }
            .joinToString(separator = "\n") { uso ->
                val base = uso.descripcion.ifBlank { uso.codigo }
                val detalle = medidorDetalle(uso.medidorInstalado)
                val nombre = if (detalle != null) "$base ($detalle)" else base
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
        val editable = inputsEditable
        materiales.forEach { uso ->
            val chip = Chip(requireContext()).apply {
                val base = uso.descripcion.ifBlank { uso.codigo }
                val detalle = medidorDetalle(uso.medidorInstalado)
                val nombre = if (detalle != null) "$base • $detalle" else base
                text = getString(
                    R.string.averia_chip_material_format,
                    nombre,
                    uso.cantidad
                )
                isCloseIconVisible = editable
                isEnabled = editable
                if (editable) {
                    setOnCloseIconClickListener {
                        materialesSeleccionados.remove(uso.codigo)
                        materialesModificados = true
                        renderMateriales()
                    }
                    setOnClickListener {
                        val materialBase = materialesCatalogo.firstOrNull { it.codigo == uso.codigo }
                            ?: MaterialEntity(codigo = uso.codigo, descripcion = uso.descripcion)
                        if (uso.medidorInstalado != null || esMaterialMedidor(materialBase)) {
                            mostrarDialogoMedidor(materialBase, uso.cantidad, uso.medidorInstalado) { cantidadActualizada, metadata ->
                                actualizarMaterial(materialBase, cantidadActualizada, metadata)
                                if (cantidadActualizada > 0 && metadata != null) {
                                    solicitarAbrirPanelAdminMedidor(metadata)
                                }
                            }
                        } else {
                            mostrarDialogoCantidadMaterial(materialBase, uso.cantidad) { cantidadActualizada ->
                                actualizarMaterial(materialBase, cantidadActualizada)
                            }
                        }
                    }
                } else {
                    setOnCloseIconClickListener(null)
                    setOnClickListener(null)
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
        val atendido = vm.nombreTecnicoActual()
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

        val materiales = materialesSeleccionados.values.filter { it.cantidad > 0 }
        val tecnicos = tecnicosSeleccionados.values.toList()
        val tipo = tipoSeleccionado
        val numeroMedidor = if (tipo == TipoAfectacion.CLIENTE) {
            val input = b.etMedidor.text?.toString()?.trim()
            when {
                !input.isNullOrBlank() -> input
                medidorSeleccionado?.medidorNumber?.isNotBlank() == true -> medidorSeleccionado!!.medidorNumber.trim()
                else -> null
            }
        } else null
        val medidorCalle = if (tipo == TipoAfectacion.CLIENTE) medidorSeleccionado?.calle?.trim() else null
        val medidorPueblo = if (tipo == TipoAfectacion.CLIENTE) medidorSeleccionado?.pueblo?.trim() else null
        val medidorMetros = if (tipo == TipoAfectacion.CLIENTE) medidorSeleccionado?.metros?.trim() else null
        val medidorPoste = if (tipo == TipoAfectacion.CLIENTE) medidorSeleccionado?.poste?.trim() else null
        val localizacionTexto = b.etLocalizacion.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        val cliente = clienteSeleccionado ?: item.cliente

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
            kilometrajeFinal = kmFinal,
            cliente = cliente,
            localizacion = localizacionTexto,
            tecnicos = tecnicos,
            tipoAfectacion = tipo,
            numeroMedidor = numeroMedidor,
            medidorCalle = medidorCalle,
            medidorPueblo = medidorPueblo,
            medidorMetros = medidorMetros,
            medidorPoste = medidorPoste
        )
    }

    override fun onDestroyView() {
        vm.resetMedidorEstado()
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
