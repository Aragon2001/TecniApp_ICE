package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.app.Dialog
import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
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
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.Arasoftsolutions.tecniapp_ice.Database.entities.InventarioConVehiculo
import com.Arasoftsolutions.tecniapp_ice.Database.entities.InventarioItemEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.MaterialEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.MedidorEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.TecnicoEntity
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.BottomsheetAveriaDetalleBinding
import com.Arasoftsolutions.tecniapp_ice.databinding.BottomsheetMedidorMaterialBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import androidx.navigation.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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
    private var inventarioPorCodigo: Map<String, InventarioItemEntity> = emptyMap()
    private val materialesSeleccionados = linkedMapOf<String, MaterialUso>()
    private var tecnicosCatalogo: List<TecnicoEntity> = emptyList()
    private val tecnicosSeleccionados = linkedMapOf<String, TecnicoAtencion>()
    private val horaFormatter = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { isLenient = false }
    private var inputsEditable = true
    private var finalInputsEnabled = false
    private var horaInicioEditable = false
    private var estadoActual: Estado = Estado.PENDIENTE
    private var persistDraftOnDestroy = true
    private var medidorLookupJob: Job? = null
    private var lastMedidorLookup: String? = null
    private var inventarioJob: Job? = null
    private var kilometrajeJob: Job? = null
    private var ultimoKmRegistrado: Double? = null

    private enum class ValidationContext { NONE, INICIAR, RESOLVER }

    private inner class MaterialAdapter(
        items: List<MaterialEntity>
    ) : ArrayAdapter<MaterialEntity>(requireContext(), android.R.layout.simple_dropdown_item_1line, items.toMutableList()) {
        private var base = items.toMutableList()
        private var filtrados = items.toMutableList()

        override fun getCount(): Int = filtrados.size
        override fun getItem(position: Int): MaterialEntity? = filtrados.getOrNull(position)
        fun getObject(position: Int): MaterialEntity = filtrados[position]

        fun updateData(nuevos: List<MaterialEntity>) {
            base = nuevos.toMutableList()
            filtrados = nuevos.toMutableList()
            clear()
            addAll(filtrados)
            notifyDataSetChanged()
        }

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
                        base.toMutableList()
                    } else {
                        base.filter { m ->
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
                        else -> base.toMutableList()
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
        if (!::item.isInitialized) {
            item = extractItem()
        }
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
        if (!::item.isInitialized) {
            dismissAllowingStateLoss()
            return
        }
        val bloqueadaPorClor = item.estadoClor.equals("RESUELTA", true)

        val estadoInicial = if (bloqueadaPorClor) {
            Estado.RESUELTA
        } else {
            Estado.fromLabel(item.estado)
        }

        estadoActual = estadoInicial
        vm.resetMedidorEstado()
        val draft = vm.getDraft(item.id)
        tipoSeleccionado = draft?.tipoAfectacion ?: item.tipoAfectacion
        clienteSeleccionado = draft?.cliente ?: item.cliente

        b.btnCerrar.setOnClickListener { dismissAllowingStateLoss() }
        // TODO(Codex): Cerrar hoja únicamente mediante botón explícito

        b.etHoraInicio.setOnClickListener {
            if (!horaInicioEditable) return@setOnClickListener
            openTimePicker { h, m ->
                b.etHoraInicio.setText(String.format(Locale.getDefault(), "%02d:%02d", h, m))
            }
        }
        b.etHoraLlegada.setOnClickListener {
            if (!finalInputsEnabled) return@setOnClickListener
            openTimePicker { h, m ->
                b.etHoraLlegada.setText(String.format(Locale.getDefault(), "%02d:%02d", h, m))
            }
        }
        b.etHoraFin.setOnClickListener {
            if (!finalInputsEnabled) return@setOnClickListener
            openTimePicker { h, m ->
                b.etHoraFin.setText(String.format(Locale.getDefault(), "%02d:%02d", h, m))
            }
        }

        listOf(b.etHoraLlegada, b.etHoraInicio, b.etHoraFin).forEach { editText ->
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
        b.etHoraLlegada.setOnLongClickListener {
            if (!finalInputsEnabled) return@setOnLongClickListener false
            b.etHoraLlegada.setText("")
            true
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

        if ((estadoInicial == Estado.ASIGNADA || estadoInicial == Estado.PENDIENTE) &&
    b.etHoraInicio.text.isNullOrBlank()
) {
    b.etHoraInicio.setText(horaFormatter.format(Date()))
}

        // TODO(Codex): Prefijar hora de inicio con la hora actual al asignar

        bindHeader(estadoInicial)
        bindResumenes()
        bindInputs()
        setupTipoAfectacion()
        setupMedidorBusqueda()

        materialesSeleccionados.clear()
        val materialesIniciales = draft?.materiales?.takeIf { it.isNotEmpty() } ?: item.materialesDetalle
        materialesIniciales.forEach { materialesSeleccionados[it.codigo] = it }
        renderMateriales()

        tecnicosSeleccionados.clear()
        val tecnicosIniciales = draft?.tecnicos?.takeIf { it.isNotEmpty() } ?: item.tecnicosAtendieron
        tecnicosIniciales.forEach { tecnico ->
            val key = tecnico.cedula.takeIf { it.isNotBlank() } ?: tecnico.nombre
            if (key.isNotBlank()) tecnicosSeleccionados[key] = tecnico
        }
        ensureTecnicoActual()
        renderTecnicos()

        // Vehículos asociados a la agencia del usuario
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                vm.vehiculosDisponibles.collectLatest { lista ->
                    val opciones = lista.mapNotNull { vehiculo ->
                        vehiculo.placa.trim().takeIf { it.isNotEmpty() }
                    }.distinct()
                    b.actvVehiculo.setAdapter(
                        ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, opciones)
                    )
                }
            }
        }

        setupMaterialesInventario()

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


        renderState()

    }

    private fun setupMaterialesInventario() {
        val adapterMat = MaterialAdapter(emptyList())
        b.actvMaterial.setAdapter(adapterMat)
        b.actvMaterial.threshold = 1
        b.actvMaterial.setOnItemClickListener { _, _, position, _ ->
            val material = adapterMat.getObject(position)
            b.actvMaterial.setText("", false)
            manejarSeleccionMaterial(material)
        }

        fun actualizarInventario(placa: String?) {
            inventarioJob?.cancel()
            inventarioJob = viewLifecycleOwner.lifecycleScope.launch {
                vm.observarInventarioPorPlaca(placa).collectLatest { lista: List<InventarioConVehiculo> ->
                    val items = lista.map { it.item }
                        .filter { it.cantidadDisponible > 0 }
                        .sortedBy { it.descripcionMaterial.ifBlank { it.codigoMaterial } }
                    inventarioPorCodigo = items.associateBy { it.codigoMaterial }
                    materialesCatalogo = items.map {
                        MaterialEntity(
                            codigo = it.codigoMaterial,
                            descripcion = it.descripcionMaterial
                        )
                    }
                    adapterMat.updateData(materialesCatalogo)
                }
            }
        }

        b.actvVehiculo.doAfterTextChanged {
            val placa = it?.toString()
            actualizarInventario(placa)
            startKilometrajeObserver(placa)
        }

        val placaInicial = b.actvVehiculo.text?.toString()
        actualizarInventario(placaInicial)
        startKilometrajeObserver(placaInicial)
    }

    private fun startKilometrajeObserver(placa: String?) {
        kilometrajeJob?.cancel()
        kilometrajeJob = viewLifecycleOwner.lifecycleScope.launch {
            vm.observarUltimoKilometrajePorPlaca(placa).collectLatest { ultimo: Double? ->
                ultimoKmRegistrado = ultimo
                if (ultimo != null && b.etKmInicio.text.isNullOrBlank()) {
                    val texto = formatKilometraje(ultimo)
                    b.etKmInicio.setText(texto)
                    b.etKmInicio.setSelection(texto.length)
                }
            }
        }
    }

    private fun formatKilometraje(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            value.toString()
        }
    }

    private fun bindHeader(estado: Estado) {
        b.tvCaso.text = getString(R.string.averia_caso_format, item.id)
        val bloqueadaPorClor = item.estadoClor.equals("RESUELTA", true)
        b.chipEstado.text = if (bloqueadaPorClor) "Resuelta (CLOR)" else item.estado

        val color = when (estado) {
            Estado.PENDIENTE -> "#E53935"
            Estado.ASIGNADA -> "#FBC02D"
            Estado.EN_ATENCION -> "#1E88E5"
            Estado.RESUELTA -> "#43A047"
            Estado.ANULADA -> "#546E7A"
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
        val hasCoords = item.lat != 0.0 && item.lng != 0.0
        b.cardMapaDireccion.isVisible = hasCoords
        b.btnDetalleVerMapa.apply {
            isVisible = hasCoords
            setOnClickListener {
                AveriaMapLauncher.show(
                    requireContext(),
                    item.lat,
                    item.lng,
                    item.descripcion.takeIf { it.isNotBlank() } ?: item.id
                ) {
                    Snackbar.make(b.root, R.string.averia_error_app_mapa, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
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
        val causaDisplay = item.causa
        val obsDisplay = item.observaciones

        b.tvCausaActual.text = causaDisplay.ifBlank { emptyValue }
        b.tvObservacionesActuales.text = obsDisplay.ifBlank { emptyValue }
        b.cardCausaClor.isVisible = true
        b.cardObservacionesClor.isVisible = true
        b.tvCausaClor.text = item.causaClor?.ifBlank { emptyValue } ?: emptyValue
        b.tvObservacionesClor.text = item.observacionesClor?.ifBlank { emptyValue } ?: emptyValue

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
        val draft = vm.getDraft(item.id)
        val vehiculo = draft?.vehiculo ?: item.vehiculo ?: vm.vehiculoPreferido()
        b.etLocalizacion.setText(draft?.localizacion ?: item.localizacion.orEmpty())
        b.etCausa.setText(draft?.causa ?: item.causa)
        b.etObs.setText(draft?.observaciones ?: item.observaciones)
        b.actvVehiculo.setText(vehiculo.orEmpty(), false)
        b.etHoraLlegada.setText(draft?.horaLlegada ?: formatHora(item.horaLlegada))
        b.etHoraInicio.setText(draft?.horaInicio ?: formatHora(item.horaAtencionInicio))
        b.etHoraFin.setText(draft?.horaFinal ?: formatHora(item.horaAtencionFinal))
        b.etKmLlegada.setText(draft?.kmLlegada ?: item.kilometrajeLlegada?.toString().orEmpty())
        b.etKmInicio.setText(draft?.kmInicio ?: item.kilometrajeInicio?.toString().orEmpty())
        b.etKmFinal.setText(draft?.kmFinal ?: item.kilometrajeFinal?.toString().orEmpty())
        b.etMedidor.setText(draft?.numeroMedidor ?: item.numeroMedidor.orEmpty())
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
            renderState()
        }
        updateTipoSection()
        renderState()
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
            lastMedidorLookup = numero
            vm.buscarMedidor(numero)
            // TODO(Codex): Validar estado del medidor antes de lanzar la búsqueda
        }
        b.etMedidor.doAfterTextChanged {
            if (it.isNullOrBlank()) {
                medidorLookupJob?.cancel()
                lastMedidorLookup = null
                medidorSeleccionado = null
                clienteSeleccionado = item.cliente
                b.tilMedidor.error = null
                b.tilMedidor.helperText = null
                if (tipoSeleccionado == TipoAfectacion.CLIENTE) {
                    renderMedidorInfo(null)
                }
                return@doAfterTextChanged
            }
            if (!inputsEditable || tipoSeleccionado != TipoAfectacion.CLIENTE) return@doAfterTextChanged
            val numero = it.toString().trim()
            if (numero.isBlank() || numero == lastMedidorLookup) return@doAfterTextChanged
            medidorLookupJob?.cancel()
            medidorLookupJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(600)
                val current = b.etMedidor.text?.toString()?.trim().orEmpty()
                if (current.isBlank() || current != numero) return@launch
                if (!inputsEditable || tipoSeleccionado != TipoAfectacion.CLIENTE) return@launch
                lastMedidorLookup = current
                b.tilMedidor.error = null
                b.tilMedidor.helperText = null
                vm.buscarMedidor(current)
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
                        lastMedidorLookup = state.medidor.medidorNumber.trim()
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

private fun renderState() {
    val uid = vm.usuarioActual.value?.uid
    val clorResuelta = item.estadoClor.equals("RESUELTA", true)
    val regionMismatch = !vm.isSubregionAllowed(item)

    val estado = if (clorResuelta) Estado.RESUELTA else Estado.fromLabel(item.estado)
    estadoActual = estado

    val ownerUid = item.ownerUidFor(estado)
    val tieneAsignacion = !ownerUid.isNullOrBlank()
    val pertenece = uid != null && ownerUid == uid
    val asignadaAOtro = tieneAsignacion && (uid == null || ownerUid != uid)

    val puedeEditarInicio = !regionMismatch && !clorResuelta && !asignadaAOtro &&
        (estado == Estado.PENDIENTE || estado == Estado.ASIGNADA || (estado == Estado.EN_ATENCION && pertenece))
    val puedeEditarCompleto = !regionMismatch && !clorResuelta &&
        estado == Estado.EN_ATENCION && pertenece && !asignadaAOtro

    applyInputStateForRules(
        estado = estado,
        puedeEditarInicio = puedeEditarInicio,
        puedeEditarCompleto = puedeEditarCompleto
    )

    configureButtonsForRules(
        estado = estado,
        pertenece = pertenece,
        asignadaAOtro = asignadaAOtro,
        clorResuelta = clorResuelta,
        regionMismatch = regionMismatch
    )
}

private fun applyInputStateForRules(
    estado: Estado,
    puedeEditarInicio: Boolean,
    puedeEditarCompleto: Boolean
) {
    val showInicio = estado == Estado.PENDIENTE || estado == Estado.ASIGNADA || estado == Estado.EN_ATENCION
    val showCompleto = estado == Estado.EN_ATENCION

    inputsEditable = puedeEditarCompleto
    finalInputsEnabled = puedeEditarCompleto
    horaInicioEditable = puedeEditarInicio

    b.toggleTipoAfectacion.isVisible = showCompleto
    b.toggleTipoAfectacion.isEnabled = puedeEditarCompleto
    b.btnTipoCliente.isEnabled = puedeEditarCompleto
    b.btnTipoSector.isEnabled = puedeEditarCompleto
    b.tilTecnicoBuscar.isVisible = showCompleto
    b.tilMaterialBuscar.isVisible = showCompleto
    b.tilMedidor.isVisible = showCompleto && tipoSeleccionado == TipoAfectacion.CLIENTE
    b.tilLocalizacion.isVisible = showCompleto
    b.tilCausa.isVisible = showCompleto
    b.tilObs.isVisible = showCompleto
    b.tilHoraLlegada.isVisible = showCompleto
    b.tilHoraFinal.isVisible = showCompleto
    b.tilKmLlegada.isVisible = showCompleto
    b.tilKmFinal.isVisible = showCompleto

    b.tilVehiculo.isVisible = showInicio
    b.tilHoraInicio.isVisible = showInicio
    b.tilKmInicio.isVisible = showInicio

    fun TextInputLayout.setEnabledDeep(enabled: Boolean) {
        isEnabled = enabled
        editText?.isEnabled = enabled
        editText?.isFocusable = enabled
        editText?.isFocusableInTouchMode = enabled
    }

    b.tilTecnicoBuscar.setEnabledDeep(puedeEditarCompleto)
    b.tilMaterialBuscar.setEnabledDeep(puedeEditarCompleto)
    b.tilMedidor.setEnabledDeep(puedeEditarCompleto && tipoSeleccionado == TipoAfectacion.CLIENTE)
    b.tilLocalizacion.setEnabledDeep(puedeEditarCompleto)
    b.tilCausa.setEnabledDeep(puedeEditarCompleto)
    b.tilObs.setEnabledDeep(puedeEditarCompleto)
    b.tilHoraLlegada.setEnabledDeep(puedeEditarCompleto)
    b.tilHoraFinal.setEnabledDeep(puedeEditarCompleto)
    b.tilKmLlegada.setEnabledDeep(puedeEditarCompleto)
    b.tilKmFinal.setEnabledDeep(puedeEditarCompleto)

    b.tilVehiculo.setEnabledDeep(puedeEditarInicio)
    b.tilHoraInicio.setEnabledDeep(puedeEditarInicio)
    b.tilKmInicio.setEnabledDeep(puedeEditarInicio)

    b.actvTecnico.isEnabled = puedeEditarCompleto
    b.actvMaterial.isEnabled = puedeEditarCompleto
    b.actvVehiculo.isEnabled = puedeEditarInicio
    b.chipGroupTecnicos.isEnabled = puedeEditarCompleto
    b.chipGroupMateriales.isEnabled = puedeEditarCompleto

    renderTecnicos()
    renderMateriales()
}

private fun configureButtonsForRules(
    estado: Estado,
    pertenece: Boolean,
    asignadaAOtro: Boolean,
    clorResuelta: Boolean,
    regionMismatch: Boolean
) {
    // Reset
    b.btnAsignar.isVisible = false
    b.btnAtender.isVisible = false
    b.btnResolver.isVisible = false
    b.btnAnular.isVisible = false
    b.btnExportar.isVisible = false
    b.btnEliminar.isVisible = false

    if (regionMismatch) return

    // ✅ Regla global: CLOR resuelta = solo lectura (solo exportar + cerrar)
    if (clorResuelta) {
        b.btnExportar.isVisible = false
        b.btnExportar.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch { PdfGenerator.exportAveria(requireContext(), item) }
        }
        return
    }

    // ✅ Asignada a otro: bottomsheet sin acciones
    if (asignadaAOtro) return

    when (estado) {
        Estado.PENDIENTE -> {
            // ✅ BottomSheet: Atender + Anular (NO asignar)
            b.btnAtender.isVisible = true
            b.btnAtender.text = getString(R.string.averia_iniciar_atencion)
            b.btnAtender.setOnClickListener {
                val data = collectFormData(ValidationContext.INICIAR) ?: return@setOnClickListener
                vm.onAtender(item, data)
                applyAtencionLocal(data)
            }

            b.btnAnular.isVisible = true
            b.btnAnular.setOnClickListener {
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

        Estado.ASIGNADA -> {
            // ✅ BottomSheet: sin edición, solo Atender + Eliminar asignación (si pertenece)
            b.btnAtender.isVisible = true
            b.btnAtender.text = getString(R.string.averia_iniciar_atencion)
            b.btnAtender.isEnabled = pertenece
            b.btnAtender.setOnClickListener {
                if (!pertenece) return@setOnClickListener
                val data = collectFormData(ValidationContext.INICIAR) ?: return@setOnClickListener
                vm.onAtender(item, data)
                applyAtencionLocal(data)
            }

            b.btnAsignar.isVisible = true
            b.btnAsignar.text = getString(R.string.averia_eliminar_asignacion)
            b.btnAsignar.isEnabled = pertenece
            b.btnAsignar.setOnClickListener {
                if (!pertenece) return@setOnClickListener
                vm.onToggleAsignacion(item)
                dismissAllowingStateLoss()
            }
        }

        Estado.EN_ATENCION -> {
            // ✅ Resolver (guardar) solo si pertenece
            b.btnResolver.isVisible = true
            b.btnResolver.text = getString(R.string.averia_guardar_resolucion)
            b.btnResolver.isEnabled = pertenece
            b.btnResolver.setOnClickListener {
                if (!pertenece) return@setOnClickListener
                val data = collectFormData(ValidationContext.RESOLVER) ?: return@setOnClickListener
                vm.onResolver(item, data)
                persistDraftOnDestroy = false
                dismissAllowingStateLoss()
            }
        }

        Estado.RESUELTA -> {
            // ✅ Resuelta por app: solo exportar
            b.btnExportar.isVisible = pertenece
b.btnExportar.isEnabled = pertenece

            b.btnExportar.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch { PdfGenerator.exportAveria(requireContext(), item) }
            }
        }

        Estado.ANULADA -> {
            b.btnAsignar.isVisible = true
            b.btnAsignar.text = getString(R.string.averia_revertir_pendiente)
            b.btnAsignar.isEnabled = pertenece
            b.btnAsignar.setOnClickListener {
                if (!pertenece) return@setOnClickListener
                vm.onRevertirAnulada(item)
                dismissAllowingStateLoss()
            }
        }
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

    private fun validarDisponibilidadMaterial(material: MaterialEntity, cantidad: Int): Boolean {
        val disponible = inventarioPorCodigo[material.codigo]?.cantidadDisponible ?: 0.0
        if (cantidad.toDouble() > disponible) {
            mostrarAlertaMaterialInsuficiente(disponible, cantidad)
            return false
        }
        return true
    }

    private fun mostrarAlertaMaterialInsuficiente(existencia: Double, solicitado: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.averia_material_existencia_titulo)
            .setMessage(
                getString(
                    R.string.averia_material_existencia_mensaje,
                    formatKilometraje(existencia),
                    solicitado
                )
            )
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun solicitarMedidorMaterial(
        material: MaterialEntity,
        existente: MaterialUso?
    ) {
        if (tipoSeleccionado != TipoAfectacion.CLIENTE && inputsEditable) {
            suppressTipoListener = true
            b.toggleTipoAfectacion.check(b.btnTipoCliente.id)
            suppressTipoListener = false
            tipoSeleccionado = TipoAfectacion.CLIENTE
            updateTipoSection()
        }
        b.tilMedidor.error = null
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
                renderMateriales()
            }
            return
        }
        val anterior = materialesSeleccionados[clave]
        val meta = metadata ?: anterior?.medidorInstalado
        val actualizado = MaterialUso(clave, material.descripcion, cantidadNormalizada, meta)
        materialesSeleccionados[clave] = actualizado
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
                if (!validarDisponibilidadMaterial(material, cantidadSeleccionada)) {
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
        val numeroPrefill = metadataInicial?.numero
            ?: medidorSeleccionado?.medidorNumber?.trim()
            ?: b.etMedidor.text?.toString()?.trim().orEmpty().ifBlank { item.numeroMedidor?.trim() }
        if (!numeroPrefill.isNullOrBlank()) {
            binding.etNumero.setText(numeroPrefill)
            binding.etNumero.setSelection(binding.etNumero.text?.length ?: 0)
        }
        val (lecturaNueva, lecturaAnterior) = parseLecturas(metadataInicial?.lectura)
        binding.etLecturaNueva.setText(lecturaNueva)
        binding.etLecturaAnterior.setText(lecturaAnterior)

        fun updateLecturasEnabled() {
            val sinCambios = binding.switchNoCambios.isChecked
            binding.tilLecturaNueva.isEnabled = !sinCambios
            binding.tilLecturaAnterior.isEnabled = !sinCambios
            if (sinCambios) {
                binding.tilLecturaNueva.error = null
                binding.tilLecturaAnterior.error = null
            }
        }

        binding.switchNoCambios.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.switchLecturaAnteriorNoVisible.isChecked = false
                binding.switchLecturasAnterioresNoVisibles.isChecked = false
            }
            updateLecturasEnabled()
        }
        binding.switchLecturaAnteriorNoVisible.setOnCheckedChangeListener { _, _ ->
            binding.tilLecturaAnterior.error = null
        }
        binding.switchLecturasAnterioresNoVisibles.setOnCheckedChangeListener { _, _ ->
            binding.tilLecturaAnterior.error = null
        }
        updateLecturasEnabled()

        fun buildMetadata(cantidad: Int): Pair<Int, MedidorInstalacion?>? {
            binding.tilCantidad.error = null
            binding.tilNumero.error = null
            binding.tilLecturaNueva.error = null
            binding.tilLecturaAnterior.error = null

            if (cantidad < 0) {
                binding.tilCantidad.error = getString(R.string.averia_material_cantidad_error)
                return null
            }
            if (cantidad == 0) {
                return 0 to null
            }
            if (!validarDisponibilidadMaterial(material, cantidad)) {
                return null
            }

            val numero = binding.etNumero.text?.toString()?.trim().orEmpty()
            val lecturaNuevaTexto = binding.etLecturaNueva.text?.toString()?.trim().orEmpty()
            val lecturaAnteriorTexto = binding.etLecturaAnterior.text?.toString()?.trim().orEmpty()
            val sinCambios = binding.switchNoCambios.isChecked
            val lecturaAnteriorNoVisible = binding.switchLecturaAnteriorNoVisible.isChecked
            val lecturasAnterioresNoVisibles = binding.switchLecturasAnterioresNoVisibles.isChecked
            if (numero.isBlank()) {
                binding.tilNumero.error = getString(R.string.averia_medidor_error_numero)
                return null
            }
            if (!sinCambios && lecturaNuevaTexto.isBlank()) {
                binding.tilLecturaNueva.error = getString(R.string.averia_medidor_error_lectura)
                return null
            }
            if (!sinCambios && lecturaAnteriorTexto.isBlank() && !lecturaAnteriorNoVisible && !lecturasAnterioresNoVisibles) {
                binding.tilLecturaAnterior.error = getString(R.string.averia_medidor_error_lectura_anterior)
                return null
            }
            val payload = if (sinCambios) null else buildLecturaPayload(lecturaNuevaTexto, lecturaAnteriorTexto)
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
                .setNegativeButton(android.R.string.ok, null)
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
        if (estadoActual != Estado.EN_ATENCION) {
            b.chipGroupTecnicos.removeAllViews()
            b.chipGroupTecnicos.isVisible = false
            b.tvTecnicosTitulo.isVisible = false
            return
        }
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
        val canShowSection = estadoActual != Estado.PENDIENTE && estadoActual != Estado.ANULADA
        b.tvMateriales.isVisible = canShowSection && (inputsEditable || materiales.isNotEmpty())
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
        b.chipGroupMateriales.isVisible = canShowSection && materiales.isNotEmpty()
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

    private fun collectFormData(contexto: ValidationContext = ValidationContext.NONE): AveriaActionData? {
        val causa = b.etCausa.text?.toString()?.trim().orEmpty()
        val obs = b.etObs.text?.toString()?.trim()
        val vehiculo = b.actvVehiculo.text?.toString()?.trim()
        val atendido = vm.nombreTecnicoActual()
        val uid = vm.usuarioActual.value?.uid ?: item.tecnicoUid

        b.tilCausa.error = null
        b.tilHoraLlegada.error = null
        b.tilHoraInicio.error = null
        b.tilHoraFinal.error = null
        b.tilKmInicio.error = null
        b.tilKmLlegada.error = null
        b.tilKmFinal.error = null
        b.tilVehiculo.error = null
        b.tilObs.error = null
        b.tilLocalizacion.error = null

        val horaLlegadaTexto = b.etHoraLlegada.text?.toString()?.trim()
        val horaInicioTexto = b.etHoraInicio.text?.toString()?.trim()
        val horaFinalTexto = b.etHoraFin.text?.toString()?.trim()
        val horaLlegada = parseHora(horaLlegadaTexto) { error -> b.tilHoraLlegada.error = error }
        val horaInicio = parseHora(horaInicioTexto) { error -> b.tilHoraInicio.error = error }
        val horaFinal = parseHora(horaFinalTexto) { error -> b.tilHoraFinal.error = error }

        if (!horaLlegadaTexto.isNullOrBlank() && horaLlegada == null) return null
        if (!horaInicioTexto.isNullOrBlank() && horaInicio == null) return null
        if (!horaFinalTexto.isNullOrBlank() && horaFinal == null) return null
        if (horaInicio != null && horaFinal != null && horaFinal <= horaInicio) {
            b.tilHoraFinal.error = getString(R.string.averia_error_hora_final_menor)
            return null
        }
        if (horaLlegada != null && horaInicio != null && horaLlegada < horaInicio) {
            b.tilHoraLlegada.error = getString(R.string.averia_error_hora_llegada_menor)
            return null
        }

        if ((contexto == ValidationContext.INICIAR || contexto == ValidationContext.RESOLVER) && horaInicioTexto.isNullOrBlank()) {
            b.tilHoraInicio.error = getString(R.string.averia_error_hora_inicio_requerida)
            return null
        }
        if (contexto == ValidationContext.RESOLVER && horaFinalTexto.isNullOrBlank()) {
            b.tilHoraFinal.error = getString(R.string.averia_error_hora_fin_requerida)
            return null
        }

        val kmLlegadaTexto = b.etKmLlegada.text?.toString()?.trim()
        val kmInicioTexto = b.etKmInicio.text?.toString()?.trim()
        val kmFinalTexto = b.etKmFinal.text?.toString()?.trim()
        val kmLlegada = kmLlegadaTexto?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
        val kmInicio = kmInicioTexto?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
        val kmFinal = kmFinalTexto?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
        if (!kmLlegadaTexto.isNullOrBlank() && kmLlegada == null) {
            b.tilKmLlegada.error = getString(R.string.averia_error_km_invalido)
            return null
        }
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
        if (kmLlegada != null && kmInicio != null && kmLlegada < kmInicio) {
            b.tilKmLlegada.error = getString(R.string.averia_error_km_llegada_menor)
            return null
        }
        if (kmInicio != null && ultimoKmRegistrado != null && kmInicio < ultimoKmRegistrado!!) {
            b.tilKmInicio.error = getString(
                R.string.averia_error_km_inicio_menor_ultimo,
                formatKilometraje(ultimoKmRegistrado!!)
            )
            return null
        }

        if ((contexto == ValidationContext.INICIAR || contexto == ValidationContext.RESOLVER) && kmInicioTexto.isNullOrBlank()) {
            b.tilKmInicio.error = getString(R.string.averia_error_km_inicio_requerido)
            return null
        }
        if (contexto == ValidationContext.RESOLVER && kmFinalTexto.isNullOrBlank()) {
            b.tilKmFinal.error = getString(R.string.averia_error_km_final_requerido)
            return null
        }

        if (contexto != ValidationContext.NONE && vehiculo.isNullOrBlank()) {
            b.tilVehiculo.error = getString(R.string.averia_error_vehiculo_requerido)
            return null
        }

        if (contexto == ValidationContext.RESOLVER && causa.isBlank()) {
            b.tilCausa.error = getString(R.string.averia_error_causa_requerida)
            return null
        }

        if (contexto == ValidationContext.RESOLVER && obs.isNullOrBlank()) {
            b.tilObs.error = getString(R.string.averia_error_observaciones_requeridas)
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
        if (contexto == ValidationContext.RESOLVER && localizacionTexto.isNullOrBlank()) {
            b.tilLocalizacion.error = getString(R.string.averia_error_localizacion_requerida)
            return null
        }

        return AveriaActionData(
            causa = causa,
            observaciones = obs,
            vehiculo = vehiculo,
            materiales = materiales,
            atendidoPorUid = uid,
            atendidoPorNombre = atendido,
            horaLlegadaMillis = horaLlegada,
            horaInicioMillis = horaInicio,
            horaFinalMillis = horaFinal,
            kilometrajeLlegada = kmLlegada,
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

    private fun buildDraft(): AveriaDraft {
        fun TextInputEditText.readText(): String? =
            text?.toString()?.trim()?.takeIf { it.isNotBlank() }

        val materiales = materialesSeleccionados.values.filter {
            it.cantidad > 0 && it.medidorInstalado == null
        }
        val tecnicos = tecnicosSeleccionados.values.toList()
        return AveriaDraft(
            localizacion = b.etLocalizacion.readText(),
            causa = b.etCausa.readText(),
            observaciones = b.etObs.readText(),
            vehiculo = b.actvVehiculo.text?.toString()?.trim()?.takeIf { it.isNotBlank() },
            horaLlegada = b.etHoraLlegada.readText(),
            horaInicio = b.etHoraInicio.readText(),
            horaFinal = b.etHoraFin.readText(),
            kmLlegada = b.etKmLlegada.readText(),
            kmInicio = b.etKmInicio.readText(),
            kmFinal = b.etKmFinal.readText(),
            tipoAfectacion = tipoSeleccionado,
            numeroMedidor = b.etMedidor.readText(),
            cliente = clienteSeleccionado,
            materiales = materiales,
            tecnicos = tecnicos
        )
    }

    private fun applyAtencionLocal(data: AveriaActionData) {
        val uid = data.atendidoPorUid ?: vm.usuarioActual.value?.uid ?: item.tecnicoUid
        val nombre = data.atendidoPorNombre ?: item.tecnico
        item = item.copy(
            estado = "En atención",
            tecnicoUid = uid,
            tecnico = nombre,
            atendidoPorUid = uid,
            atendidoPor = nombre,
            vehiculo = data.vehiculo ?: item.vehiculo,
            horaAtencionInicio = data.horaInicioMillis ?: item.horaAtencionInicio,
            horaLlegada = data.horaLlegadaMillis ?: item.horaLlegada,
            kilometrajeInicio = data.kilometrajeInicio ?: item.kilometrajeInicio,
            kilometrajeLlegada = data.kilometrajeLlegada ?: item.kilometrajeLlegada
        )
        bindHeader(Estado.EN_ATENCION)
        bindResumenes()
        bindInputs()
        renderState()
    }

    override fun onDestroyView() {
        if (persistDraftOnDestroy) {
            vm.saveDraft(item.id, buildDraft())
        } else {
            vm.clearDraft(item.id)
        }
        medidorLookupJob?.cancel()
        vm.resetMedidorEstado()
        _b = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_AVERIA = "arg_averia"

        fun newInstance(item: AveriaUI): AveriaDetalleBottomSheet {
            val bs = AveriaDetalleBottomSheet()
            bs.arguments = Bundle().apply {
                putSerializable(ARG_AVERIA, item)
            }
            bs.item = item
            return bs
        }
    }

    private fun extractItem(): AveriaUI {
        val args = arguments
            ?: throw IllegalStateException("AveriaDetalleBottomSheet requires arguments.")
        val value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            args.getSerializable(ARG_AVERIA, AveriaUI::class.java)
        } else {
            @Suppress("DEPRECATION")
            args.getSerializable(ARG_AVERIA) as? AveriaUI
        }
        return value ?: throw IllegalStateException("AveriaDetalleBottomSheet requires AveriaUI.")
    }
}
