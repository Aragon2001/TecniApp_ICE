package com.Arasoftsolutions.tecniapp_ice.ui.luminarias

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentLuminariasBinding
import com.Arasoftsolutions.tecniapp_ice.databinding.BottomSheetLuminariaReparacionBinding
import com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado
import com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaReparacionEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.google.firebase.auth.FirebaseAuth
import com.Arasoftsolutions.tecniapp_ice.ui.vehiculo.obtenerEstadoEtmVehiculo
import com.Arasoftsolutions.tecniapp_ice.ui.vehiculo.showRegistroVehiculoPendienteDialog
import com.Arasoftsolutions.tecniapp_ice.ui.materiales.MaterialMetadataRules
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.apache.poi.xssf.usermodel.XSSFWorkbook

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
    private var isFabOpen = false

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

        binding.etBuscarLocalizacion.doAfterTextChanged {
            viewModel.actualizarBusquedaLocalizacion(it?.toString().orEmpty())
        }
        binding.toggleGroupEstado.check(R.id.btnTogglePendientes)
        updateToggleEstadoColors()
        binding.toggleGroupEstado.addOnButtonCheckedListener { _, _, _ ->
            updateToggleEstadoColors()
            actualizarVisibilidadListas()
        }
        setupFab()

        binding.toggleGroupVehiculos.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (!isChecked || isUpdatingVehiculoFilter) return@addOnButtonCheckedListener
            val button = group.findViewById<com.google.android.material.button.MaterialButton>(checkedId)
            val vehiculoId = button?.tag as? Int
            viewModel.actualizarVehiculoFiltro(vehiculoId)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val repo = RoomRepository.getInstance(requireContext())
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            val estadoEtm = uid?.let { repo.obtenerEstadoEtmVehiculo(it) }
            if (estadoEtm != null && (estadoEtm.registroPendienteCierre != null || !estadoEtm.tieneRegistroHoy)) {
                showRegistroVehiculoPendienteDialog(
                    onRegistroGuardado = { },
                    onNoVehiculo = { findNavController().popBackStack(R.id.nav_home, false) }
                )
            }
        }

        observarEstado()
        actualizarVisibilidadListas()
    }

    // ─── Adapters ─────────────────────────────────────────────────────────────

    private fun setupAdapters() {
        reparacionesPendientesAdapter = LuminariaReparacionAdapter(
            actionMode = LuminariaActionMode.VIEW,
            showDelete = false,
            onEdit = { reparacion ->
                val state = viewModel.uiState.value
                when {
                    state.puedeAtenderPendientes -> mostrarAtenderLuminaria(reparacion)
                    state.puedeEditarPendienteAdmin -> mostrarEditarReportePendiente(reparacion)
                }
            },
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
            actionMode = LuminariaActionMode.VIEW,
            showDelete = false,
            onEdit = { reparacion ->
                val state = viewModel.uiState.value
                if (state.puedeEditarReparadaTecnico) {
                    mostrarEditarReparadaTecnico(reparacion)
                }
            },
            onDelete = { reparacion -> confirmarEliminacion(reparacion) },
            onSelect = { reparacion -> mostrarDetalleReparada(reparacion) }
        )
        binding.listReparacionesLuminaria.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = reparacionesReparadasAdapter
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }
    }

    // ─── Observadores ─────────────────────────────────────────────────────────

    private fun observarEstado() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressLuminaria.isVisible = state.isProcessing
                    materialesCatalogo = state.materiales
                    tecnicosCatalogo = state.tecnicos
                    val pueblosById = state.pueblos.associate { it.id to it.nombre }
                    val vehiculosById = state.vehiculosAgencia.associateBy { it.id }
                    reparacionesPendientesAdapter.updateCatalogs(pueblosById, vehiculosById)
                    reparacionesReparadasAdapter.updateCatalogs(pueblosById, vehiculosById)

                    binding.rowFabRegistrar.isVisible = state.puedeRegistrarReparacion
                    binding.rowFabImportar.isVisible = state.puedeImportarExcel
                    binding.rowFabDescargar.isVisible = state.puedeDescargarMachote
                    binding.rowFabEmail.isVisible = state.puedeEnviarMachote
                    renderVehiculoFilters(state)

                    // Pendientes: técnico ve botón "Atender", gestor ve "Editar"
                    reparacionesPendientesAdapter.updatePermissions(
                        actionMode = when {
                            state.puedeAtenderPendientes -> LuminariaActionMode.ATTEND
                            state.puedeEditarPendienteAdmin -> LuminariaActionMode.EDIT
                            else -> LuminariaActionMode.VIEW
                        },
                        showDelete = false
                    )
                    reparacionesPendientesAdapter.submitList(state.reparacionesPendientes)
                    binding.tvEmptyReparacionesPendientes.isVisible = state.reparacionesPendientes.isEmpty()

                    // Reparadas: técnico puede editar las suyas; gestor solo lee
                    reparacionesReparadasAdapter.updatePermissions(
                        actionMode = if (state.puedeEditarReparadaTecnico) LuminariaActionMode.EDIT
                                     else LuminariaActionMode.VIEW,
                        showDelete = state.puedeEliminarLuminarias
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

    override fun onResume() {
        super.onResume()
        val luminariaId = LuminariaDeepLink.consume() ?: return
        val state = viewModel.uiState.value
        val reparacion = (state.reparacionesPendientes + state.reparacionesReparadas)
            .firstOrNull { it.id.toInt() == luminariaId } ?: return
        if (state.reparacionesPendientes.contains(reparacion)) {
            mostrarDetallePendiente(reparacion)
        } else {
            mostrarDetalleReparada(reparacion)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ─── Puntos de entrada al BottomSheet ─────────────────────────────────────

    /** Técnico presiona botón "Atender" en una luminaria pendiente */
    private fun mostrarAtenderLuminaria(reparacion: LuminariaReparacionEntity) {
        val sheetBinding = BottomSheetLuminariaReparacionBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(sheetBinding.root)
        configurarFormularioBottomSheet(
            binding = sheetBinding,
            titulo = "Atender luminaria",
            soloVer = false,
            reparacion = reparacion,
            onDone = { dialog.dismiss() }
        )
        mostrarBottomSheet(dialog)
        cargarDetalleCliente(sheetBinding, reparacion)
    }

    /** Supervisor/Admin presiona "Editar" en una luminaria pendiente */
    private fun mostrarEditarReportePendiente(reparacion: LuminariaReparacionEntity) {
        val sheetBinding = BottomSheetLuminariaReparacionBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(sheetBinding.root)
        configurarFormularioBottomSheet(
            binding = sheetBinding,
            titulo = "Actualizar reporte",
            soloVer = false,
            reparacion = reparacion,
            onDone = { dialog.dismiss() }
        )
        mostrarBottomSheet(dialog)
        cargarDetalleCliente(sheetBinding, reparacion)
    }

    /** Técnico presiona "Editar" en una luminaria reparada propia */
    private fun mostrarEditarReparadaTecnico(reparacion: LuminariaReparacionEntity) {
        val sheetBinding = BottomSheetLuminariaReparacionBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(sheetBinding.root)
        configurarFormularioBottomSheet(
            binding = sheetBinding,
            titulo = "Editar reparación",
            soloVer = false,
            reparacion = reparacion,
            onDone = { dialog.dismiss() }
        )
        mostrarBottomSheet(dialog)
    }

    /** Cualquier rol toca el card de una luminaria pendiente (solo lectura) */
    private fun mostrarDetallePendiente(reparacion: LuminariaReparacionEntity) {
        val sheetBinding = BottomSheetLuminariaReparacionBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(sheetBinding.root)
        configurarFormularioBottomSheet(
            binding = sheetBinding,
            titulo = "Detalle de lámpara pendiente",
            soloVer = true,
            reparacion = reparacion,
            onDone = { dialog.dismiss() }
        )
        mostrarBottomSheet(dialog)
        cargarDetalleCliente(sheetBinding, reparacion)
    }

    /** Cualquier rol toca el card de una luminaria reparada (solo lectura) */
    private fun mostrarDetalleReparada(reparacion: LuminariaReparacionEntity) {
        val sheetBinding = BottomSheetLuminariaReparacionBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(sheetBinding.root)
        configurarFormularioBottomSheet(
            binding = sheetBinding,
            titulo = "Detalle de lámpara reparada",
            soloVer = true,
            reparacion = reparacion,
            onDone = { dialog.dismiss() }
        )
        mostrarBottomSheet(dialog)
        cargarDetalleCliente(sheetBinding, reparacion)
    }

    /** FAB: registrar nueva entrada */
    private fun mostrarRegistroBottomSheet() {
        viewModel.prepararFormularioRegistro()
        val sheetBinding = BottomSheetLuminariaReparacionBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(sheetBinding.root)
        val esGestor = viewModel.uiState.value.esSupervisor || viewModel.uiState.value.esAdministrador
        configurarFormularioBottomSheet(
            binding = sheetBinding,
            titulo = if (esGestor) "Registrar nuevo caso" else "Registrar reparación",
            soloVer = false,
            reparacion = null,
            onDone = { dialog.dismiss() }
        )
        mostrarBottomSheet(dialog)
    }

    // ─── Formulario BottomSheet ────────────────────────────────────────────────

    private fun configurarFormularioBottomSheet(
        binding: BottomSheetLuminariaReparacionBinding,
        titulo: String,
        soloVer: Boolean,
        reparacion: LuminariaReparacionEntity?,
        onDone: () -> Unit
    ) {
        val estadoUi = viewModel.uiState.value
        val esGestor = estadoUi.esSupervisor || estadoUi.esAdministrador
        val estadoActual = reparacion?.let { LuminariaEstado.fromRaw(it.estado) }
        val esRegistro = reparacion == null

        // Modos derivados
        val esAtenderPendiente = !soloVer && !esGestor && estadoActual == LuminariaEstado.PENDIENTE
        val esEditarGestorPendiente = !soloVer && esGestor && estadoActual == LuminariaEstado.PENDIENTE
        val esEditarTecnicoReparada = !soloVer && !esGestor && estadoActual == LuminariaEstado.REPARADA

        // ── Encabezado ────────────────────────────────────────────────────────
        binding.tvTituloBottomSheet.text = titulo

        // Chip de estado en el header
        binding.chipEstadoReparacion.text = estadoActual?.let {
            if (it == LuminariaEstado.PENDIENTE) "Pendiente" else "Reparada"
        } ?: if (!esGestor) "Reparada" else "Pendiente"

        // Menú de reportes: solo gestores (técnico no tiene acciones de export)
        binding.btnMenuReporteLuminarias.isVisible = esGestor
        binding.btnMenuReporteLuminarias.setOnClickListener { mostrarMenuReportesLuminarias(it) }

        // ── Sección detalle del cliente ────────────────────────────────────────
        binding.groupDetalle.isVisible = soloVer
        binding.tvLocalizacionDetalle.text = reparacion?.localizacion?.let {
            "Localización ${viewModel.normalizarLocalizacion(it)}"
        } ?: "-"

        binding.btnToggleDetalle.setOnClickListener {
            val expandido = !binding.groupDetalle.isVisible
            binding.groupDetalle.isVisible = expandido
            binding.btnToggleDetalle.setIconResource(
                if (expandido) R.drawable.ic_expand_less else R.drawable.ic_expand_more
            )
        }
        binding.btnToggleDetalle.setIconResource(
            if (binding.groupDetalle.isVisible) R.drawable.ic_expand_less else R.drawable.ic_expand_more
        )

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
                    .setItems(contactos.toTypedArray()) { _, which -> marcarContacto(contactos[which]) }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }

        // ── Materiales seleccionados ───────────────────────────────────────────
        val materialesSeleccionados = reparacion?.let {
            LuminariaMaterialSerializer.fromJson(it.materialesJson)
                .map { m -> LuminariaMaterialSeleccionado(m.codigo, m.descripcion, m.cantidad, m.selloNumero) }
                .toMutableList()
        } ?: mutableListOf()

        lateinit var adapterMateriales: LuminariaMaterialAdapter
        adapterMateriales = LuminariaMaterialAdapter { material ->
            if (!soloVer) {
                materialesSeleccionados.removeAll { it.codigo == material.codigo }
                adapterMateriales.submitList(materialesSeleccionados.toList())
                if (esRegistro) viewModel.actualizarMaterialesSeleccionados(materialesSeleccionados.toList())
            }
        }
        binding.listMaterialesLuminaria.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = adapterMateriales
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }
        adapterMateriales.submitList(materialesSeleccionados.toList())

        if (esRegistro) viewModel.actualizarMaterialesSeleccionados(materialesSeleccionados.toList())

        // Selector de materiales: solo para técnico en modo edición
        val puedeEditarMateriales = !soloVer && !esGestor
        binding.tilMaterialLuminaria.isVisible = puedeEditarMateriales
        binding.actMaterialLuminaria.isEnabled = puedeEditarMateriales
        // Lista siempre visible si hay materiales, o si puede agregar
        binding.listMaterialesLuminaria.isVisible = puedeEditarMateriales || materialesSeleccionados.isNotEmpty()

        if (puedeEditarMateriales) {
            val materialesLabel = materialesCatalogo.map { "${it.codigo} - ${it.descripcion}" }
            val materialesAdapterDialog = ArrayAdapter(
                requireContext(), android.R.layout.simple_list_item_1, materialesLabel
            )
            binding.actMaterialLuminaria.setAdapter(materialesAdapterDialog)
            binding.actMaterialLuminaria.setOnItemClickListener { _, _, position, _ ->
                val seleccion = materialesAdapterDialog.getItem(position).orEmpty()
                val codigo = seleccion.substringBefore(" - ").trim()
                val material = materialesCatalogo.firstOrNull { it.codigo == codigo }
                material?.let {
                    val existente = materialesSeleccionados.firstOrNull { it.codigo == material.codigo }
                    mostrarDialogoCantidad(material, existente?.selloNumero) { cantidad, selloNumero ->
                        val index = materialesSeleccionados.indexOfFirst { it.codigo == material.codigo }
                        if (index >= 0) {
                            val actual = materialesSeleccionados[index]
                            materialesSeleccionados[index] = actual.copy(
                                cantidad = actual.cantidad + cantidad,
                                selloNumero = selloNumero ?: actual.selloNumero
                            )
                        } else {
                            materialesSeleccionados.add(
                                LuminariaMaterialSeleccionado(material.codigo, material.descripcion, cantidad, selloNumero)
                            )
                        }
                        adapterMateriales.submitList(materialesSeleccionados.toList())
                        if (esRegistro) viewModel.actualizarMaterialesSeleccionados(materialesSeleccionados.toList())
                        binding.actMaterialLuminaria.setText("", false)
                    }
                }
            }
        }

        // ── Catálogo técnicos ──────────────────────────────────────────────────
        val tecnicosLabel = tecnicosCatalogo.map { it.nombre }
        val tecnicosAdapterDialog = ArrayAdapter(
            requireContext(), android.R.layout.simple_list_item_1, tecnicosLabel
        )
        binding.actEjecutorLuminaria.setAdapter(tecnicosAdapterDialog)

        // ── Campo estado ───────────────────────────────────────────────────────
        // Solo el técnico puede cambiar el estado
        val puedeEditarEstado = !soloVer && !esGestor
        binding.tilEstadoLuminaria.isVisible = puedeEditarEstado
        binding.actEstadoLuminaria.isEnabled = puedeEditarEstado

        val estadosLabel = listOf("Pendiente", "Reparada")
        val estadosAdapterDialog = ArrayAdapter(
            requireContext(), android.R.layout.simple_list_item_1, estadosLabel
        )
        binding.actEstadoLuminaria.setAdapter(estadosAdapterDialog)
        binding.actEstadoLuminaria.keyListener = null

        // Estado inicial: para técnico atendiendo pendiente → Reparada por defecto
        val estadoInicial = when {
            estadoActual != null ->
                if (estadoActual == LuminariaEstado.PENDIENTE) "Pendiente" else "Reparada"
            esAtenderPendiente || (!esGestor && esRegistro) -> "Reparada"
            else -> "Pendiente"
        }
        binding.actEstadoLuminaria.setText(estadoInicial, false)

        // ── Campo ejecutor ─────────────────────────────────────────────────────
        // Solo el técnico puede ver/editar el ejecutor
        val puedeEditarEjecutor = !soloVer && !esGestor
        binding.tilEjecutorLuminaria.isVisible = puedeEditarEjecutor
        binding.actEjecutorLuminaria.isEnabled = puedeEditarEjecutor

        binding.actEjecutorLuminaria.setText(
            reparacion?.ejecutorNombre ?: estadoUi.ejecutorNombre, false
        )
        binding.actEjecutorLuminaria.doAfterTextChanged {
            if (esRegistro) viewModel.actualizarEjecutor(it?.toString().orEmpty())
            binding.tilEjecutorLuminaria.error = null
        }

        // ── Localización ──────────────────────────────────────────────────────
        binding.etLocalizacion.isEnabled = !soloVer
        val localizacionInicial = viewModel.normalizarLocalizacion(
            reparacion?.localizacion ?: estadoUi.localizacion
        )
        binding.etLocalizacion.setText(localizacionInicial)

        var isFormattingLocalizacion = false
        binding.etLocalizacion.doAfterTextChanged {
            if (isFormattingLocalizacion) return@doAfterTextChanged
            val rawText = it?.toString().orEmpty()
            val formatted = viewModel.normalizarLocalizacion(rawText)
            if (formatted.isNotBlank() && formatted != rawText) {
                isFormattingLocalizacion = true
                binding.etLocalizacion.setText(formatted)
                binding.etLocalizacion.setSelection(formatted.length)
                isFormattingLocalizacion = false
            }
            if (esRegistro) viewModel.actualizarLocalizacion(formatted.ifBlank { rawText })
            binding.tilLocalizacion.error = null
        }

        // ── Camión ─────────────────────────────────────────────────────────────
        // Solo gestor puede asignar camión en registro o edición de pendiente
        val vehiculosDisponibles = estadoUi.vehiculosAgencia
        val vehiculosLabel = vehiculosDisponibles.map { it.placa.toString() }
        val vehiculosAdapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_list_item_1, vehiculosLabel
        )
        binding.actVehiculoLuminaria.setAdapter(vehiculosAdapter)
        binding.actVehiculoLuminaria.keyListener = null

        val puedeEditarCamion = !soloVer && esGestor &&
            (esRegistro || estadoActual == LuminariaEstado.PENDIENTE)
        binding.tilVehiculoLuminaria.isVisible = puedeEditarCamion
        binding.actVehiculoLuminaria.isEnabled = puedeEditarCamion

        val vehiculoActualId = reparacion?.vehiculoId
            ?: estadoUi.vehiculoRegistroId ?: estadoUi.vehiculoUsuarioId
        val vehiculoActual = vehiculosDisponibles.firstOrNull { it.id == vehiculoActualId }
        binding.actVehiculoLuminaria.setText(vehiculoActual?.placa?.toString().orEmpty(), false)

        if (puedeEditarCamion) {
            binding.actVehiculoLuminaria.setOnItemClickListener { _, _, position, _ ->
                val placa = vehiculosAdapter.getItem(position).orEmpty()
                val vehiculoSeleccionado = vehiculosDisponibles.firstOrNull { it.placa.toString() == placa }
                viewModel.actualizarVehiculoRegistro(vehiculoSeleccionado?.id)
            }
        }

        // ── Texto y comportamiento del botón guardar ───────────────────────────
        binding.btnGuardarReparacion.text = when {
            soloVer -> "Cerrar"
            esRegistro && !esGestor -> "Registrar atención"
            esRegistro && esGestor -> "Registrar caso"
            esAtenderPendiente -> "Guardar atención"
            esEditarGestorPendiente -> "Actualizar reporte"
            esEditarTecnicoReparada -> "Actualizar reparación"
            else -> "Guardar"
        }

        binding.btnGuardarReparacion.setOnClickListener {
            if (soloVer) {
                onDone()
                return@setOnClickListener
            }

            // Determinar estado seleccionado
            val estadoSeleccionado = when {
                puedeEditarEstado -> obtenerEstado(binding.actEstadoLuminaria)
                else -> LuminariaEstado.PENDIENTE // gestor siempre guarda como PENDIENTE
            }

            // Validación crítica: técnico NO puede guardar dejando en PENDIENTE
            if (!esGestor && estadoSeleccionado == LuminariaEstado.PENDIENTE && !esEditarTecnicoReparada) {
                mostrarDialogoEstadoPendiente()
                return@setOnClickListener
            }

            val localizacionNormalizada = viewModel.normalizarLocalizacion(
                binding.etLocalizacion.text?.toString().orEmpty()
            )
            binding.etLocalizacion.setText(localizacionNormalizada)

            if (esRegistro) {
                // Nuevo registro
                if (esGestor && binding.actVehiculoLuminaria.text?.toString().isNullOrBlank()) {
                    viewModel.enviarMensaje("Selecciona un camión válido", esError = true)
                    return@setOnClickListener
                }
                val ejecutorNombre = binding.actEjecutorLuminaria.text?.toString().orEmpty()
                if (!validarCamposObligatorios(binding, ejecutorNombre, estadoSeleccionado, esGestor)) return@setOnClickListener
                viewModel.actualizarEstado(estadoSeleccionado)
                viewModel.registrarReparacion()
                onDone()
            } else {
                // Actualización de existente
                val ejecutorNombre = if (!esGestor) {
                    binding.actEjecutorLuminaria.text?.toString().orEmpty()
                } else {
                    // Gestor no toca ejecutor — preservar el existente
                    reparacion!!.ejecutorNombre
                }
                val ejecutorCedula = if (!esGestor) {
                    tecnicosCatalogo.firstOrNull {
                        it.nombre.equals(ejecutorNombre, ignoreCase = true)
                    }?.cedula ?: reparacion?.ejecutorCedula
                } else {
                    reparacion!!.ejecutorCedula
                }
                val materialesFinales = if (!esGestor) {
                    materialesSeleccionados.toList()
                } else {
                    // Gestor no toca materiales — preservar los existentes
                    reparacion!!.let { r ->
                        LuminariaMaterialSerializer.fromJson(r.materialesJson)
                            .map { m -> LuminariaMaterialSeleccionado(m.codigo, m.descripcion, m.cantidad, m.selloNumero) }
                    }
                }
                if (!validarCamposObligatorios(binding, ejecutorNombre, estadoSeleccionado, esGestor)) return@setOnClickListener
                val placaSeleccionada = binding.actVehiculoLuminaria.text?.toString().orEmpty()
                val vehiculoSeleccionado = vehiculosDisponibles
                    .firstOrNull { it.placa.toString() == placaSeleccionada }?.id
                viewModel.actualizarReparacion(
                    reparacion!!.id,
                    localizacionNormalizada,
                    materialesFinales,
                    estadoSeleccionado,
                    ejecutorNombre,
                    ejecutorCedula,
                    vehiculoSeleccionado
                )
                onDone()
            }
        }
    }

    /** Diálogo moderno: bloquea al técnico si intenta guardar sin marcar Reparada */
    private fun mostrarDialogoEstadoPendiente() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Aún está pendiente")
            .setMessage(
                "Para guardar la atención técnica, cambie el estado de la luminaria a Reparada."
            )
            .setIcon(R.drawable.ic_warning)
            .setPositiveButton("Entendido", null)
            .show()
    }

    private fun validarCamposObligatorios(
        binding: BottomSheetLuminariaReparacionBinding,
        ejecutor: String,
        estado: LuminariaEstado,
        esGestor: Boolean
    ): Boolean {
        var valido = true
        val localizacion = binding.etLocalizacion.text?.toString().orEmpty().trim()
        if (localizacion.isBlank()) {
            binding.tilLocalizacion.error = "Ingresa el número de localización"
            valido = false
        } else {
            binding.tilLocalizacion.error = null
        }
        // Ejecutor requerido solo para técnico marcando como REPARADA
        if (!esGestor && estado == LuminariaEstado.REPARADA && ejecutor.trim().isBlank()) {
            binding.tilEjecutorLuminaria.error = "Indica quién ejecutó la reparación"
            valido = false
        } else {
            binding.tilEjecutorLuminaria.error = null
        }
        return valido
    }

    // ─── Diálogo cantidad material ─────────────────────────────────────────────

    private fun mostrarDialogoCantidad(
        material: com.Arasoftsolutions.tecniapp_ice.Database.entities.MaterialEntity,
        selloInicial: String?,
        onConfirm: (Double, String?) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_luminaria_cantidad, null)
        val cantidadInput = dialogView.findViewById<AppCompatEditText>(R.id.etCantidadMaterial)
        val btnMenos = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCantidadMenos)
        val btnMas = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCantidadMas)
        val btnConfirmar = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirmarCantidad)
        val tvDisponible = dialogView.findViewById<android.widget.TextView>(R.id.tvDisponibleCantidad)
        val cardWarning = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardWarningCantidad)

        tvDisponible.text = "Material: ${material.codigo} - ${material.descripcion}"
        cardWarning.isVisible = false
        cantidadInput.setText("1")
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cantidad usada")
            .setView(dialogView)
            .setNegativeButton("Cancelar", null)
            .show()

        fun ajustar(delta: Double) {
            val actual = cantidadInput.text?.toString()?.toDoubleOrNull() ?: 1.0
            val nuevo = (actual + delta).coerceAtLeast(0.5)
            val texto = if (nuevo % 1.0 == 0.0) nuevo.toInt().toString() else nuevo.toString()
            cantidadInput.setText(texto)
            cantidadInput.setSelection(texto.length)
        }

        btnMenos.setOnClickListener { ajustar(-0.5) }
        btnMas.setOnClickListener { ajustar(0.5) }
        btnConfirmar.setOnClickListener {
            val cantidad = cantidadInput.text?.toString()?.toDoubleOrNull() ?: 0.0
            if (cantidad <= 0.0) {
                cantidadInput.error = "Ingresa una cantidad válida"
                return@setOnClickListener
            }
            val requiereSello = MaterialMetadataRules.requiresSealNumber(material.codigo, material.descripcion)
            if (requiereSello) {
                dialog.dismiss()
                solicitarNumeroSello(selloInicial) { numeroSello -> onConfirm(cantidad, numeroSello) }
                return@setOnClickListener
            }
            onConfirm(cantidad, null)
            dialog.dismiss()
        }
    }

    private fun solicitarNumeroSello(numeroInicial: String?, onConfirm: (String) -> Unit) {
        val input = AppCompatEditText(requireContext()).apply {
            setText(numeroInicial.orEmpty())
            setSelection(text?.length ?: 0)
            hint = getString(R.string.material_sello_numero_hint)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.material_sello_numero_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val valor = input.text?.toString()?.trim().orEmpty()
                if (valor.isNotBlank()) onConfirm(valor)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ─── Confirmación eliminación ──────────────────────────────────────────────

    private fun confirmarEliminacion(reparacion: LuminariaReparacionEntity) {
        val estado = LuminariaEstado.fromRaw(reparacion.estado)
        val mensaje = if (estado == LuminariaEstado.REPARADA) {
            "¿Deseas pasar esta reparación nuevamente a Pendiente?"
        } else {
            "¿Deseas eliminar esta reparación? Esta acción no se puede deshacer."
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (estado == LuminariaEstado.REPARADA) "Devolver a pendiente" else "Eliminar reparación")
            .setMessage(mensaje)
            .setPositiveButton(if (estado == LuminariaEstado.REPARADA) "Devolver" else "Eliminar") { _, _ ->
                viewModel.eliminarReparacion(reparacion.id)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ─── BottomSheet helpers ───────────────────────────────────────────────────

    private fun mostrarBottomSheet(dialog: BottomSheetDialog) {
        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
                sheet.layoutParams = sheet.layoutParams.apply { height = ViewGroup.LayoutParams.MATCH_PARENT }
            }
            dialog.behavior.skipCollapsed = true
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        dialog.show()
    }

    private fun cargarDetalleCliente(
        sheetBinding: BottomSheetLuminariaReparacionBinding,
        reparacion: LuminariaReparacionEntity
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

    private fun obtenerEstado(input: MaterialAutoCompleteTextView): LuminariaEstado {
        return if (input.text?.toString().orEmpty().equals("Pendiente", ignoreCase = true)) {
            LuminariaEstado.PENDIENTE
        } else {
            LuminariaEstado.REPARADA
        }
    }

    // ─── Toggle de estado y visibilidad ───────────────────────────────────────

    private fun actualizarVisibilidadListas() {
        binding.cardPendientes.isVisible =
            binding.toggleGroupEstado.checkedButtonId == R.id.btnTogglePendientes
        binding.cardReparadas.isVisible =
            binding.toggleGroupEstado.checkedButtonId == R.id.btnToggleReparadas
    }

    private fun updateToggleEstadoColors() {
        val ctx = requireContext()
        val redColor = ContextCompat.getColor(ctx, R.color.chip_pendiente)
        val greenColor = ContextCompat.getColor(ctx, R.color.chip_resuelta)
        val pendienteChecked = binding.toggleGroupEstado.checkedButtonId == R.id.btnTogglePendientes
        val reparadaChecked = binding.toggleGroupEstado.checkedButtonId == R.id.btnToggleReparadas

        binding.btnTogglePendientes.backgroundTintList =
            ColorStateList.valueOf(if (pendienteChecked) redColor else Color.TRANSPARENT)
        binding.btnTogglePendientes.setTextColor(if (pendienteChecked) Color.WHITE else redColor)
        binding.btnTogglePendientes.strokeColor = ColorStateList.valueOf(redColor)

        binding.btnToggleReparadas.backgroundTintList =
            ColorStateList.valueOf(if (reparadaChecked) greenColor else Color.TRANSPARENT)
        binding.btnToggleReparadas.setTextColor(if (reparadaChecked) Color.WHITE else greenColor)
        binding.btnToggleReparadas.strokeColor = ColorStateList.valueOf(greenColor)
    }

    // ─── FAB speed dial ───────────────────────────────────────────────────────

    private fun setupFab() {
        binding.fabPrincipal.setOnClickListener { toggleFab() }
        binding.fabScrim.setOnClickListener { closeFab() }
        binding.fabRegistrar.setOnClickListener {
            closeFab()
            mostrarRegistroBottomSheet()
        }
        binding.fabImportar.setOnClickListener {
            closeFab()
            excelLauncher.launch(
                arrayOf(
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                )
            )
        }
        binding.fabDescargar.setOnClickListener {
            closeFab()
            prepararMachote()
        }
        binding.fabEmail.setOnClickListener {
            closeFab()
            prepararMachoteCorreo()
        }
    }

    private fun toggleFab() {
        if (isFabOpen) closeFab() else openFab()
    }

    private fun openFab() {
        isFabOpen = true
        binding.fabSpeedDialContainer.isVisible = true
        binding.fabScrim.isVisible = true
        binding.fabPrincipal.animate().rotation(45f).setDuration(200).start()
        binding.fabSpeedDialContainer.alpha = 0f
        binding.fabSpeedDialContainer.translationY = 40f
        binding.fabSpeedDialContainer.animate().alpha(1f).translationY(0f).setDuration(200).start()
    }

    private fun closeFab() {
        isFabOpen = false
        binding.fabPrincipal.animate().rotation(0f).setDuration(200).start()
        binding.fabSpeedDialContainer.animate()
            .alpha(0f).translationY(40f).setDuration(200)
            .withEndAction {
                binding.fabSpeedDialContainer.isVisible = false
                binding.fabScrim.isVisible = false
            }.start()
    }

    // ─── Filtro por vehículo (solo gestor) ────────────────────────────────────

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
            context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            id = View.generateViewId()
            text = getString(R.string.luminarias_camion_todos)
            tag = null
            isCheckable = true
        }
        binding.toggleGroupVehiculos.addView(todosButton)
        vehiculos.forEach { vehiculo ->
            val button = com.google.android.material.button.MaterialButton(
                context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
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
        } ?: group.children.firstOrNull { child -> child.tag == null }
        val buttonId = toSelect?.id ?: View.NO_ID
        if (buttonId != View.NO_ID && group.checkedButtonId != buttonId) {
            group.check(buttonId)
        }
        isUpdatingVehiculoFilter = false
    }

    // ─── Menú de reportes (solo gestor, en BottomSheet) ───────────────────────

    private fun mostrarMenuReportesLuminarias(anchor: View) {
        val puedeImportar = viewModel.uiState.value.puedeImportarExcel
        val puedeEnviar = viewModel.uiState.value.puedeEnviarMachote
        val puedeDescargar = viewModel.uiState.value.puedeDescargarMachote
        PopupMenu(requireContext(), anchor).apply {
            if (puedeImportar) menu.add(0, 1, 1, getString(R.string.luminarias_menu_cargar))
            if (puedeEnviar) menu.add(0, 2, 2, getString(R.string.luminarias_menu_enviar))
            if (puedeDescargar) menu.add(0, 3, 3, getString(R.string.luminarias_menu_descargar))
            menu.add(0, 4, 4, getString(R.string.luminarias_menu_reporte_pdf))
            menu.add(0, 5, 5, getString(R.string.luminarias_menu_reporte_excel))
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        excelLauncher.launch(
                            arrayOf(
                                "application/vnd.ms-excel",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            )
                        )
                        true
                    }
                    2 -> { prepararMachoteCorreo(); true }
                    3 -> { prepararMachote(); true }
                    4 -> { exportarReporteGeneralPdf(); true }
                    5 -> { exportarReporteGeneralExcel(); true }
                    else -> false
                }
            }
            show()
        }
    }

    // ─── Machote (solo gestor) ─────────────────────────────────────────────────

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

    private fun prepararMachoteCorreo() {
        val vehiculos = viewModel.uiState.value.vehiculosAgencia
        if (vehiculos.isEmpty()) {
            viewModel.enviarMensaje("No hay camiones disponibles para generar el machote", esError = true)
            return
        }
        exportarMachoteParaCorreo(vehiculos)
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

    private fun exportarMachoteParaCorreo(
        vehiculos: List<com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculosEntity>
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val context = requireContext()
            try {
                val workbook = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    LuminariaMachoteExporter.buildWorkbook(context, vehiculos)
                }
                val parentDir = context.getExternalFilesDir(null) ?: context.filesDir
                val reportsDir = java.io.File(parentDir, "TecniApp/Reportes")
                if (!reportsDir.exists()) reportsDir.mkdirs()
                val file = java.io.File(reportsDir, viewModel.obtenerNombreMachote())
                java.io.FileOutputStream(file).use { output ->
                    workbook.use { wb -> wb.write(output) }
                }
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context, context.packageName + ".fileprovider", file
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Machote de luminarias")
                    putExtra(Intent.EXTRA_TEXT, "Adjunto machote de luminarias para su revisión.")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Enviar machote"))
                viewModel.enviarMensaje("Machote listo para enviar")
            } catch (t: Throwable) {
                viewModel.enviarMensaje("No se pudo generar el machote de luminarias", esError = true)
            }
        }
    }

    // ─── Reportes generales ────────────────────────────────────────────────────

    private fun exportarReporteGeneralExcel() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val context = requireContext()
                val reporteDir = crearDirectorioReportes(context)
                val file = File(reporteDir, "Reporte_General_Luminarias_${timestampArchivo()}.xlsx")
                val state = viewModel.uiState.value
                val reparaciones = (state.reparacionesPendientes + state.reparacionesReparadas)
                    .sortedByDescending { it.fechaRegistro }
                val vehiculosById = state.vehiculosAgencia.associateBy { it.id }

                val workbook = XSSFWorkbook()
                workbook.use { wb ->
                    val sheet = wb.createSheet("Luminarias")
                    val headers = listOf(
                        "ID", "Estado", "Localización", "Cliente", "Contacto", "Observaciones",
                        "Ejecutor", "Cédula", "Vehículo", "Agencia", "Fecha registro", "Fecha reparación"
                    )
                    val headerRow = sheet.createRow(0)
                    headers.forEachIndexed { index, value -> headerRow.createCell(index).setCellValue(value) }
                    val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    reparaciones.forEachIndexed { i, reparacion ->
                        val row = sheet.createRow(i + 1)
                        val vehiculo = vehiculosById[reparacion.vehiculoId]
                        row.createCell(0).setCellValue(reparacion.id.toDouble())
                        row.createCell(1).setCellValue(reparacion.estado)
                        row.createCell(2).setCellValue(reparacion.localizacion)
                        row.createCell(3).setCellValue(reparacion.cliente.orEmpty())
                        row.createCell(4).setCellValue(reparacion.contacto.orEmpty())
                        row.createCell(5).setCellValue(reparacion.observaciones.orEmpty())
                        row.createCell(6).setCellValue(reparacion.ejecutorNombre)
                        row.createCell(7).setCellValue(reparacion.ejecutorCedula.orEmpty())
                        row.createCell(8).setCellValue(vehiculo?.placa?.toString().orEmpty())
                        row.createCell(9).setCellValue(vehiculo?.agencia.orEmpty())
                        row.createCell(10).setCellValue(format.format(Date(reparacion.fechaRegistro)))
                        row.createCell(11).setCellValue(
                            reparacion.fechaReparacion?.let { format.format(Date(it)) }.orEmpty()
                        )
                    }
                    headers.indices.forEach { sheet.autoSizeColumn(it) }
                    FileOutputStream(file).use { output -> wb.write(output) }
                }
                compartirArchivo(
                    file = file,
                    mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    subject = "Reporte general de luminarias (Excel)",
                    chooser = "Compartir reporte Excel"
                )
                viewModel.enviarMensaje(getString(R.string.luminarias_reporte_generado))
            } catch (_: Throwable) {
                viewModel.enviarMensaje(getString(R.string.luminarias_reporte_error), esError = true)
            }
        }
    }

    private fun exportarReporteGeneralPdf() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val context = requireContext()
                val reporteDir = crearDirectorioReportes(context)
                val file = File(reporteDir, "Reporte_General_Luminarias_${timestampArchivo()}.pdf")
                val state = viewModel.uiState.value
                val reparaciones = (state.reparacionesPendientes + state.reparacionesReparadas)
                    .sortedByDescending { it.fechaRegistro }
                val vehiculosById = state.vehiculosAgencia.associateBy { it.id }
                val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

                val pdf = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(1200, 1800, 1).create()
                var page = pdf.startPage(pageInfo)
                var canvas = page.canvas
                val paint = Paint().apply { textSize = 28f }
                val bodyPaint = Paint().apply { textSize = 20f }
                var y = 60f

                canvas.drawText("Reporte general de luminarias", 40f, y, paint)
                y += 40f
                canvas.drawText("Total registros: ${reparaciones.size}", 40f, y, bodyPaint)
                y += 40f

                reparaciones.forEach { reparacion ->
                    if (y > 1720f) {
                        pdf.finishPage(page)
                        page = pdf.startPage(pageInfo)
                        canvas = page.canvas
                        y = 60f
                    }
                    val vehiculo = vehiculosById[reparacion.vehiculoId]
                    val linea = "#${reparacion.id} | ${reparacion.estado} | Loc ${reparacion.localizacion} | Veh ${vehiculo?.placa ?: "-"} | ${format.format(Date(reparacion.fechaRegistro))}"
                    canvas.drawText(linea.take(130), 40f, y, bodyPaint)
                    y += 28f
                    val detalle = "Cliente: ${reparacion.cliente.orEmpty()} | Obs: ${reparacion.observaciones.orEmpty()}"
                    canvas.drawText(detalle.take(130), 40f, y, bodyPaint)
                    y += 34f
                }
                pdf.finishPage(page)
                FileOutputStream(file).use { output -> pdf.writeTo(output) }
                pdf.close()
                compartirArchivo(
                    file = file,
                    mimeType = "application/pdf",
                    subject = "Reporte general de luminarias (PDF)",
                    chooser = "Compartir reporte PDF"
                )
                viewModel.enviarMensaje(getString(R.string.luminarias_reporte_generado))
            } catch (_: Throwable) {
                viewModel.enviarMensaje(getString(R.string.luminarias_reporte_error), esError = true)
            }
        }
    }

    // ─── Utilidades ───────────────────────────────────────────────────────────

    private fun crearDirectorioReportes(context: android.content.Context): File {
        val parentDir = context.getExternalFilesDir(null) ?: context.filesDir
        val reportesDir = File(parentDir, "TecniApp/Reportes")
        if (!reportesDir.exists()) reportesDir.mkdirs()
        return reportesDir
    }

    private fun timestampArchivo(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

    private fun compartirArchivo(file: File, mimeType: String, subject: String, chooser: String) {
        val context = requireContext()
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, context.packageName + ".fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, chooser))
    }

    private fun obtenerContactos(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val normalized = raw.replace("\n", " ")
        return normalized.split(Regex("[,;/]"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.replace("\\s+".toRegex(), "") }
            .map { it.replace("[^0-9+]".toRegex(), "") }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun formatContactos(raw: String): String {
        val contactos = obtenerContactos(raw)
        return if (contactos.isEmpty()) "" else contactos.joinToString(" / ")
    }

    private fun marcarContacto(contacto: String) {
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$contacto")))
    }
}
