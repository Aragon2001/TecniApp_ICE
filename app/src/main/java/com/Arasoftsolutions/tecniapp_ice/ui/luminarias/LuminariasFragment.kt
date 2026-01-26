package com.Arasoftsolutions.tecniapp_ice.ui.luminarias

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentLuminariasBinding
import com.Arasoftsolutions.tecniapp_ice.databinding.BottomSheetLuminariaReparacionBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.launch

class LuminariasFragment : Fragment() {

    private var _binding: FragmentLuminariasBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LuminariasViewModel by viewModels()
    private lateinit var reparacionesPendientesAdapter: LuminariaReparacionAdapter
    private lateinit var reparacionesReparadasAdapter: LuminariaReparacionAdapter
    private var materialesCatalogo = emptyList<com.Arasoftsolutions.tecniapp_ice.Database.entities.MaterialEntity>()
    private var tecnicosCatalogo = emptyList<com.Arasoftsolutions.tecniapp_ice.Database.entities.TecnicoEntity>()

    private val excelLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.procesarExcel(it) }
    }

    private var pendingMachoteVehiculos: List<com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculosEntity>? = null
    private val machoteLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument(com.Arasoftsolutions.tecniapp_ice.ui.reportes.ExcelReportExporter.MIME_TYPE_XLSX)) { uri ->
            val vehiculos = pendingMachoteVehiculos
            pendingMachoteVehiculos = null
            if (uri == null || vehiculos.isNullOrEmpty()) {
                return@registerForActivityResult
            }
            exportarMachote(uri, vehiculos)
        }

    private var vehiculosFilterCache: List<Int> = emptyList()
    private var isUpdatingVehiculoFilter = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLuminariasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapters()

        binding.btnRegistrarLuminaria.setOnClickListener { mostrarRegistroBottomSheet() }
        binding.btnImportarLuminariasExcel.setOnClickListener {
            excelLauncher.launch(
                arrayOf(
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                )
            )
        }
        binding.btnDescargarMachote.setOnClickListener { prepararMachote() }
        binding.etBuscarLocalizacion.doAfterTextChanged {
            viewModel.actualizarBusquedaLocalizacion(it?.toString().orEmpty())
        }
        binding.chipGroupEstado.isSingleSelection = true
        binding.chipPendientes.isChecked = true
        binding.chipReparadas.isChecked = false
        binding.chipPendientes.setOnCheckedChangeListener { _, _ -> actualizarVisibilidadListas() }
        binding.chipReparadas.setOnCheckedChangeListener { _, _ -> actualizarVisibilidadListas() }

        binding.toggleGroupVehiculos.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (!isChecked || isUpdatingVehiculoFilter) return@addOnButtonCheckedListener
            val button = group.findViewById<com.google.android.material.button.MaterialButton>(checkedId)
            val vehiculoId = button?.tag as? Int
            viewModel.actualizarVehiculoFiltro(vehiculoId)
        }

        observarEstado()
        actualizarVisibilidadListas()
    }

    private fun setupAdapters() {
        reparacionesPendientesAdapter = LuminariaReparacionAdapter(
            showEdit = false,
            showDelete = false,
            onEdit = { reparacion -> mostrarDialogoEdicion(reparacion) },
            onDelete = { reparacion -> confirmarEliminacion(reparacion) },
            onSelect = { reparacion -> mostrarDetallePendiente(reparacion) }
        )
        binding.listReparacionesPendientes.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = reparacionesPendientesAdapter
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }

        reparacionesReparadasAdapter = LuminariaReparacionAdapter(
            showEdit = true,
            showDelete = true,
            onEdit = { reparacion -> mostrarDialogoEdicion(reparacion) },
            onDelete = { reparacion -> confirmarEliminacion(reparacion) }
        )
        binding.listReparacionesLuminaria.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = reparacionesReparadasAdapter
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }
    }

    private fun observarEstado() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressLuminaria.isVisible = state.isProcessing
                    materialesCatalogo = state.materiales
                    tecnicosCatalogo = state.tecnicos
                    binding.btnRegistrarLuminaria.isVisible = state.puedeRegistrarReparacion
                    binding.btnImportarLuminariasExcel.isVisible = state.puedeImportarExcel
                    binding.btnDescargarMachote.isVisible = state.puedeDescargarMachote
                    actualizarModoChip()
                    renderVehiculoFilters(state)

                    reparacionesPendientesAdapter.updatePermissions(
                        showEdit = state.puedeReasignarVehiculo,
                        showDelete = state.puedeEliminarLuminarias
                    )
                    reparacionesPendientesAdapter.submitList(state.reparacionesPendientes)
                    binding.tvEmptyReparacionesPendientes.isVisible = state.reparacionesPendientes.isEmpty()

                    reparacionesReparadasAdapter.updatePermissions(
                        showEdit = !state.esSupervisor,
                        showDelete = state.puedeEliminarLuminarias && !state.esSupervisor
                    )
                    reparacionesReparadasAdapter.submitList(state.reparacionesReparadas)
                    binding.tvEmptyReparaciones.isVisible = state.reparacionesReparadas.isEmpty()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.mensaje.collect { mensaje ->
                    mensaje?.let {
                        val texto = when (it) {
                            is LuminariaMensaje.Exito -> it.texto
                            is LuminariaMensaje.Error -> it.texto
                        }
                        Toast.makeText(requireContext(), texto, Toast.LENGTH_SHORT).show()
                        viewModel.consumirMensaje()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun mostrarDialogoCantidad(
        material: com.Arasoftsolutions.tecniapp_ice.Database.entities.MaterialEntity,
        onConfirm: (Double) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(
            com.Arasoftsolutions.tecniapp_ice.R.layout.dialog_luminaria_cantidad,
            null
        )
        val cantidadInput = dialogView.findViewById<AppCompatEditText>(
            com.Arasoftsolutions.tecniapp_ice.R.id.etCantidadMaterial
        )
        cantidadInput.setText("1")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cantidad usada")
            .setView(dialogView)
            .setPositiveButton("Agregar") { _, _ ->
                val cantidad = cantidadInput.text?.toString()?.toDoubleOrNull() ?: 0.0
                onConfirm(cantidad)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarDialogoEdicion(reparacion: com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaReparacionEntity) {
        val sheetBinding = BottomSheetLuminariaReparacionBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(sheetBinding.root)
        configurarFormularioBottomSheet(
            binding = sheetBinding,
            titulo = "Editar reparación",
            mostrarDetalle = false,
            reparacion = reparacion
        )
        dialog.show()
    }

    private fun confirmarEliminacion(reparacion: com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaReparacionEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar reparación")
            .setMessage("¿Deseas eliminar esta reparación? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ -> viewModel.eliminarReparacion(reparacion.id) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarRegistroBottomSheet() {
        viewModel.prepararFormularioRegistro()
        val sheetBinding = BottomSheetLuminariaReparacionBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(sheetBinding.root)
        configurarFormularioBottomSheet(
            binding = sheetBinding,
            titulo = "Registrar reparación",
            mostrarDetalle = false,
            reparacion = null
        )
        dialog.show()
    }

    private fun mostrarDetallePendiente(reparacion: com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaReparacionEntity) {
        val sheetBinding = BottomSheetLuminariaReparacionBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(sheetBinding.root)
        configurarFormularioBottomSheet(
            binding = sheetBinding,
            titulo = "Detalle de lámpara pendiente",
            mostrarDetalle = true,
            reparacion = reparacion
        )
        dialog.show()
        cargarDetalleCliente(sheetBinding, reparacion)
    }

    private fun cargarDetalleCliente(
        sheetBinding: BottomSheetLuminariaReparacionBinding,
        reparacion: com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaReparacionEntity
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val medidor = viewModel.buscarMedidorPorLocalizacion(reparacion.localizacion)
            val cliente = reparacion.cliente?.trim().orEmpty().ifBlank {
                medidor?.cliente?.trim().orEmpty()
            }
            val contacto = reparacion.contacto?.trim().orEmpty()
            val observaciones = reparacion.observaciones?.trim().orEmpty()
            sheetBinding.tvClienteDetalle.text = cliente.ifBlank { "Sin datos" }
            sheetBinding.tvContactoDetalle.text = formatContactos(contacto).ifBlank { "Sin datos" }
            sheetBinding.tvObservacionesDetalle.text = observaciones.ifBlank { "Sin datos" }
            sheetBinding.btnLlamarContacto.isEnabled = obtenerContactos(contacto).isNotEmpty()
        }
    }

    private fun configurarFormularioBottomSheet(
        binding: BottomSheetLuminariaReparacionBinding,
        titulo: String,
        mostrarDetalle: Boolean,
        reparacion: com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaReparacionEntity?
    ) {
        val estadoUi = viewModel.uiState.value
        binding.tvTituloBottomSheet.text = titulo
        binding.groupDetalle.isVisible = mostrarDetalle || reparacion != null
        binding.tvLocalizacionDetalle.text = reparacion?.localizacion?.let { "Localización #$it" } ?: "-"
        binding.btnGuardarReparacion.text = if (reparacion == null) {
            "Registrar reparación"
        } else {
            "Actualizar reparación"
        }
        binding.btnLlamarContacto.setOnClickListener {
            val telefonoRaw = binding.tvContactoDetalle.text?.toString().orEmpty()
            if (telefonoRaw.isBlank()) return@setOnClickListener
            val contactos = obtenerContactos(telefonoRaw)
            if (contactos.isEmpty()) return@setOnClickListener
            if (contactos.size == 1) {
                marcarContacto(contactos.first())
            } else {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Selecciona un número")
                    .setItems(contactos.toTypedArray()) { _, which ->
                        marcarContacto(contactos[which])
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }

        val materialesSeleccionados = reparacion?.let {
            com.Arasoftsolutions.tecniapp_ice.ui.luminarias.LuminariaMaterialSerializer
                .fromJson(it.materialesJson)
                .map { material -> LuminariaMaterialSeleccionado(material.codigo, material.descripcion, material.cantidad) }
                .toMutableList()
        } ?: mutableListOf()

        lateinit var adapterMateriales: LuminariaMaterialAdapter
        adapterMateriales = LuminariaMaterialAdapter { material ->
            materialesSeleccionados.removeAll { it.codigo == material.codigo }
            adapterMateriales.submitList(materialesSeleccionados.toList())
            binding.tvEmptyMateriales.isVisible = materialesSeleccionados.isEmpty()
            if (reparacion == null) {
                viewModel.actualizarMaterialesSeleccionados(materialesSeleccionados.toList())
            }
        }
        binding.listMaterialesLuminaria.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = adapterMateriales
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }
        adapterMateriales.submitList(materialesSeleccionados.toList())
        binding.tvEmptyMateriales.isVisible = materialesSeleccionados.isEmpty()
        if (reparacion == null) {
            viewModel.actualizarMaterialesSeleccionados(materialesSeleccionados.toList())
        }

        val materialesLabel = materialesCatalogo.map { "${it.codigo} - ${it.descripcion}" }
        val materialesAdapterDialog = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, materialesLabel)
        binding.actMaterialLuminaria.setAdapter(materialesAdapterDialog)
        binding.actMaterialLuminaria.setOnItemClickListener { _, _, position, _ ->
            val seleccion = materialesAdapterDialog.getItem(position).orEmpty()
            val codigo = seleccion.substringBefore(" - ").trim()
            val material = materialesCatalogo.firstOrNull { it.codigo == codigo }
            material?.let {
                mostrarDialogoCantidad(material) { cantidad ->
                    val index = materialesSeleccionados.indexOfFirst { it.codigo == material.codigo }
                    if (index >= 0) {
                        val actual = materialesSeleccionados[index]
                        materialesSeleccionados[index] = actual.copy(cantidad = actual.cantidad + cantidad)
                    } else {
                        materialesSeleccionados.add(LuminariaMaterialSeleccionado(material.codigo, material.descripcion, cantidad))
                    }
                    adapterMateriales.submitList(materialesSeleccionados.toList())
                    binding.tvEmptyMateriales.isVisible = materialesSeleccionados.isEmpty()
                    if (reparacion == null) {
                        viewModel.actualizarMaterialesSeleccionados(materialesSeleccionados.toList())
                    }
                    binding.actMaterialLuminaria.setText("", false)
                }
            }
        }

        val tecnicosLabel = tecnicosCatalogo.map { it.nombre }
        val tecnicosAdapterDialog = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, tecnicosLabel)
        binding.actEjecutorLuminaria.setAdapter(tecnicosAdapterDialog)

        val vehiculosDisponibles = estadoUi.vehiculosAgencia
        val vehiculosLabel = vehiculosDisponibles.map { it.placa.toString() }
        val vehiculosAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, vehiculosLabel)
        binding.actVehiculoLuminaria.setAdapter(vehiculosAdapter)
        binding.actVehiculoLuminaria.keyListener = null

        val estadosLabel = listOf("Pendiente", "Reparada")
        val estadosAdapterDialog = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, estadosLabel)
        binding.actEstadoLuminaria.setAdapter(estadosAdapterDialog)
        binding.actEstadoLuminaria.keyListener = null

        val estadoInicial = reparacion?.let {
            if (com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado.fromRaw(it.estado) ==
                com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado.PENDIENTE
            ) {
                "Pendiente"
            } else {
                "Reparada"
            }
        } ?: "Reparada"
        binding.actEstadoLuminaria.setText(estadoInicial, false)

        binding.etLocalizacion.setText(reparacion?.localizacion ?: viewModel.uiState.value.localizacion)
        binding.actEjecutorLuminaria.setText(
            reparacion?.ejecutorNombre ?: viewModel.uiState.value.ejecutorNombre,
            false
        )
        val vehiculoActualId = reparacion?.vehiculoId ?: estadoUi.vehiculoUsuarioId
        val vehiculoActual = vehiculosDisponibles.firstOrNull { it.id == vehiculoActualId }
        binding.actVehiculoLuminaria.setText(vehiculoActual?.placa?.toString().orEmpty(), false)
        val puedeEditarVehiculo = estadoUi.puedeReasignarVehiculo && reparacion != null
        binding.tilVehiculoLuminaria.isVisible = puedeEditarVehiculo
        binding.actVehiculoLuminaria.isEnabled = puedeEditarVehiculo

        binding.etLocalizacion.doAfterTextChanged {
            if (reparacion == null) {
                viewModel.actualizarLocalizacion(it?.toString().orEmpty())
            }
            binding.tilLocalizacion.error = null
        }
        binding.actEjecutorLuminaria.doAfterTextChanged {
            if (reparacion == null) {
                viewModel.actualizarEjecutor(it?.toString().orEmpty())
            }
            binding.tilEjecutorLuminaria.error = null
        }

        if (estadoUi.esSupervisor) {
            binding.tilMaterialLuminaria.isEnabled = false
            binding.actMaterialLuminaria.isEnabled = false
            binding.tilEjecutorLuminaria.isEnabled = false
            binding.actEjecutorLuminaria.isEnabled = false
            binding.actEstadoLuminaria.isEnabled = false
            binding.tilMaterialLuminaria.isVisible = false
            binding.listMaterialesLuminaria.isVisible = false
            binding.tvEmptyMateriales.isVisible = false
            binding.tilEjecutorLuminaria.isVisible = false
            binding.tilEstadoLuminaria.isVisible = false
        } else {
            binding.tilMaterialLuminaria.isVisible = true
            binding.listMaterialesLuminaria.isVisible = true
            binding.tvEmptyMateriales.isVisible = materialesSeleccionados.isEmpty()
            binding.tilEjecutorLuminaria.isVisible = true
            binding.tilEstadoLuminaria.isVisible = true
        }

        binding.btnGuardarReparacion.setOnClickListener {
            if (reparacion == null) {
                val estado = obtenerEstado(binding.actEstadoLuminaria)
                val valido = validarFormularioRegistro(
                    binding,
                    materialesSeleccionados,
                    binding.actEjecutorLuminaria.text?.toString().orEmpty(),
                    estado
                )
                if (valido) {
                    viewModel.actualizarEstado(estado)
                    viewModel.registrarReparacion()
                }
            } else {
                if (estadoUi.esSupervisor) {
                    val placaSeleccionada = binding.actVehiculoLuminaria.text?.toString().orEmpty()
                    val vehiculoSeleccionado = vehiculosDisponibles.firstOrNull {
                        it.placa.toString() == placaSeleccionada
                    }
                    if (vehiculoSeleccionado == null) {
                        viewModel.enviarMensaje("Selecciona un camión válido", esError = true)
                    } else {
                        viewModel.reasignarVehiculo(reparacion.id, vehiculoSeleccionado.id)
                    }
                } else {
                    val estado = obtenerEstado(binding.actEstadoLuminaria)
                    val ejecutorNombre = binding.actEjecutorLuminaria.text?.toString().orEmpty()
                    val ejecutorCedula = tecnicosCatalogo.firstOrNull {
                        it.nombre.equals(ejecutorNombre, ignoreCase = true)
                    }?.cedula
                    val localizacion = binding.etLocalizacion.text?.toString().orEmpty()
                    val placaSeleccionada = binding.actVehiculoLuminaria.text?.toString().orEmpty()
                    val vehiculoSeleccionado = vehiculosDisponibles.firstOrNull {
                        it.placa.toString() == placaSeleccionada
                    }?.id
                    if (validarFormularioRegistro(binding, materialesSeleccionados, ejecutorNombre, estado)) {
                        viewModel.actualizarReparacion(
                            reparacion.id,
                            localizacion,
                            materialesSeleccionados.toList(),
                            estado,
                            ejecutorNombre,
                            ejecutorCedula,
                            vehiculoSeleccionado
                        )
                    }
                }
            }
        }
    }

    private fun validarFormularioRegistro(
        binding: BottomSheetLuminariaReparacionBinding,
        materiales: List<LuminariaMaterialSeleccionado>,
        ejecutor: String,
        estado: com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado
    ): Boolean {
        var valido = true
        val localizacion = binding.etLocalizacion.text?.toString().orEmpty().trim()
        if (localizacion.isBlank()) {
            binding.tilLocalizacion.error = "Ingresa el número de localización"
            valido = false
        } else {
            binding.tilLocalizacion.error = null
        }

        if (estado == com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado.REPARADA && materiales.isEmpty()) {
            binding.tilMaterialLuminaria.error = "Agrega al menos un material"
            valido = false
        } else {
            binding.tilMaterialLuminaria.error = null
        }

        if (estado == com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado.REPARADA && ejecutor.trim().isBlank()) {
            binding.tilEjecutorLuminaria.error = "Indica quién ejecutó la reparación"
            valido = false
        } else {
            binding.tilEjecutorLuminaria.error = null
        }

        return valido
    }

    private fun obtenerEstado(input: MaterialAutoCompleteTextView): com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado {
        return if (input.text?.toString().orEmpty().equals("Pendiente", ignoreCase = true)) {
            com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado.PENDIENTE
        } else {
            com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado.REPARADA
        }
    }

    private fun actualizarVisibilidadListas() {
        val pendientesActivas = binding.chipPendientes.isChecked
        val reparadasActivas = binding.chipReparadas.isChecked
        binding.cardPendientes.isVisible = pendientesActivas
        binding.cardReparadas.isVisible = reparadasActivas
    }

    private fun actualizarModoChip() {
        binding.chipGroupEstado.isSingleSelection = true
        if (binding.chipPendientes.isChecked && binding.chipReparadas.isChecked) {
            binding.chipReparadas.isChecked = false
        }
    }

    private fun renderVehiculoFilters(state: LuminariaUiState) {
        binding.groupVehiculosFilter.isVisible = state.puedeFiltrarVehiculo
        if (!state.puedeFiltrarVehiculo) return
        val vehiculos = state.vehiculosAgencia
        val ids = vehiculos.map { it.id }
        if (ids != vehiculosFilterCache) {
            vehiculosFilterCache = ids
            rebuildVehiculoFilters(vehiculos, state.vehiculoFiltroId)
        } else {
            updateVehiculoSelection(state.vehiculoFiltroId)
        }
    }

    private fun rebuildVehiculoFilters(
        vehiculos: List<com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculosEntity>,
        selectedId: Int?
    ) {
        isUpdatingVehiculoFilter = true
        binding.toggleGroupVehiculos.removeAllViews()
        val context = requireContext()
        val todosButton = com.google.android.material.button.MaterialButton(
            context,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            id = View.generateViewId()
            text = getString(com.Arasoftsolutions.tecniapp_ice.R.string.luminarias_camion_todos)
            tag = null
            isCheckable = true
        }
        binding.toggleGroupVehiculos.addView(todosButton)
        vehiculos.forEach { vehiculo ->
            val button = com.google.android.material.button.MaterialButton(
                context,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                id = View.generateViewId()
                text = vehiculo.placa.toString()
                tag = vehiculo.id
                isCheckable = true
            }
            binding.toggleGroupVehiculos.addView(button)
        }
        updateVehiculoSelection(selectedId)
        isUpdatingVehiculoFilter = false
    }

    private fun updateVehiculoSelection(selectedId: Int?) {
        isUpdatingVehiculoFilter = true
        val group = binding.toggleGroupVehiculos
        val toSelect = group.children.firstOrNull { child ->
            (child.tag as? Int) == selectedId
        } ?: group.children.firstOrNull { child ->
            child.tag == null
        }
        val buttonId = toSelect?.id ?: View.NO_ID
        if (buttonId != View.NO_ID && group.checkedButtonId != buttonId) {
            group.check(buttonId)
        }
        isUpdatingVehiculoFilter = false
    }

    private fun prepararMachote() {
        val vehiculos = viewModel.uiState.value.vehiculosAgencia
        if (vehiculos.isEmpty()) {
            viewModel.enviarMensaje("No hay camiones disponibles para generar el machote", esError = true)
            return
        }
        pendingMachoteVehiculos = vehiculos
        val fileName = viewModel.obtenerNombreMachote()
        machoteLauncher.launch(fileName)
    }

    private fun exportarMachote(
        uri: Uri,
        vehiculos: List<com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculosEntity>
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val resolver = requireContext().contentResolver
            try {
                val workbook = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    LuminariaMachoteExporter.buildWorkbook(requireContext(), vehiculos)
                }
                resolver.openOutputStream(uri)?.use { output ->
                    workbook.use { wb -> wb.write(output) }
                }
                viewModel.enviarMensaje("Machote descargado")
            } catch (t: Throwable) {
                viewModel.enviarMensaje("No se pudo generar el machote de luminarias", esError = true)
            }
        }
    }

    private fun obtenerContactos(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val normalized = raw.replace("\n", " ")
        val contactos = normalized.split(Regex("[,;/]"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.replace("\\s+".toRegex(), "") }
            .map { it.replace("[^0-9+]".toRegex(), "") }
            .filter { it.isNotBlank() }
        return if (contactos.isEmpty()) emptyList() else contactos.distinct()
    }

    private fun formatContactos(raw: String): String {
        val contactos = obtenerContactos(raw)
        return if (contactos.isEmpty()) "" else contactos.joinToString(" / ")
    }

    private fun marcarContacto(contacto: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$contacto"))
        startActivity(intent)
    }
}
