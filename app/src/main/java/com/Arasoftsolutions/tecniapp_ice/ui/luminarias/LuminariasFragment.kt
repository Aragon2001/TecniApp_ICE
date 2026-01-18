package com.Arasoftsolutions.tecniapp_ice.ui.luminarias

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.widget.AppCompatEditText
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
        binding.etBuscarLocalizacion.doAfterTextChanged {
            viewModel.actualizarBusquedaLocalizacion(it?.toString().orEmpty())
        }
        binding.chipPendientes.setOnCheckedChangeListener { _, _ -> actualizarVisibilidadListas() }
        binding.chipReparadas.setOnCheckedChangeListener { _, _ -> actualizarVisibilidadListas() }

        observarEstado()
        actualizarVisibilidadListas()
    }

    private fun setupAdapters() {
        reparacionesPendientesAdapter = LuminariaReparacionAdapter(
            showActions = false,
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
            showActions = true,
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

                    reparacionesPendientesAdapter.submitList(state.reparacionesPendientes)
                    binding.tvEmptyReparacionesPendientes.isVisible = state.reparacionesPendientes.isEmpty()

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
        val dialogView = layoutInflater.inflate(
            com.Arasoftsolutions.tecniapp_ice.R.layout.dialog_luminaria_edicion,
            null
        )
        val localizacionInput = dialogView.findViewById<AppCompatEditText>(com.Arasoftsolutions.tecniapp_ice.R.id.etLocalizacionReparacion)
        val estadoInput = dialogView.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(
            com.Arasoftsolutions.tecniapp_ice.R.id.actEstadoReparacion
        )
        val ejecutorInput = dialogView.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(
            com.Arasoftsolutions.tecniapp_ice.R.id.actEjecutorReparacion
        )
        val materialInput = dialogView.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(
            com.Arasoftsolutions.tecniapp_ice.R.id.actMaterialReparacion
        )
        val materialesList = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(
            com.Arasoftsolutions.tecniapp_ice.R.id.listMaterialesReparacion
        )
        val emptyMateriales = dialogView.findViewById<android.widget.TextView>(
            com.Arasoftsolutions.tecniapp_ice.R.id.tvEmptyMaterialesReparacion
        )

        localizacionInput.setText(reparacion.localizacion)
        val estadoAdapterDialog = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, listOf("Pendiente", "Reparada"))
        estadoInput.setAdapter(estadoAdapterDialog)
        estadoInput.keyListener = null
        val estadoTexto = if (com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado.fromRaw(reparacion.estado) ==
            com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado.PENDIENTE
        ) {
            "Pendiente"
        } else {
            "Reparada"
        }
        estadoInput.setText(estadoTexto, false)

        val tecnicosLabel = tecnicosCatalogo.map { it.nombre }
        val tecnicosAdapterDialog = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, tecnicosLabel)
        ejecutorInput.setAdapter(tecnicosAdapterDialog)
        ejecutorInput.setText(reparacion.ejecutorNombre, false)

        val materialesLabel = materialesCatalogo.map { "${it.codigo} - ${it.descripcion}" }
        val materialesAdapterDialog = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, materialesLabel)
        materialInput.setAdapter(materialesAdapterDialog)

        val materialesSeleccionados = com.Arasoftsolutions.tecniapp_ice.ui.luminarias.LuminariaMaterialSerializer
            .fromJson(reparacion.materialesJson)
            .map { LuminariaMaterialSeleccionado(it.codigo, it.descripcion, it.cantidad) }
            .toMutableList()
        lateinit var adapterDialog: LuminariaMaterialAdapter
        adapterDialog = LuminariaMaterialAdapter { material ->
            materialesSeleccionados.removeAll { it.codigo == material.codigo }
            adapterDialog.submitList(materialesSeleccionados.toList())
            emptyMateriales.isVisible = materialesSeleccionados.isEmpty()
        }
        materialesList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = adapterDialog
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }
        adapterDialog.submitList(materialesSeleccionados.toList())
        emptyMateriales.isVisible = materialesSeleccionados.isEmpty()

        materialInput.setOnItemClickListener { _, _, position, _ ->
            materialesCatalogo.getOrNull(position)?.let { material ->
                mostrarDialogoCantidad(material) { cantidad ->
                    val index = materialesSeleccionados.indexOfFirst { it.codigo == material.codigo }
                    if (index >= 0) {
                        val actual = materialesSeleccionados[index]
                        materialesSeleccionados[index] = actual.copy(cantidad = actual.cantidad + cantidad)
                    } else {
                        materialesSeleccionados.add(LuminariaMaterialSeleccionado(material.codigo, material.descripcion, cantidad))
                    }
                    adapterDialog.submitList(materialesSeleccionados.toList())
                    emptyMateriales.isVisible = materialesSeleccionados.isEmpty()
                    materialInput.setText("", false)
                }
            }
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Editar reparación")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val nuevaLocalizacion = localizacionInput.text?.toString().orEmpty()
                val estado = if (estadoInput.text?.toString().orEmpty().equals("Pendiente", ignoreCase = true)) {
                    com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado.PENDIENTE
                } else {
                    com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado.REPARADA
                }
                val ejecutorNombre = ejecutorInput.text?.toString().orEmpty()
                val ejecutorCedula = tecnicosCatalogo.firstOrNull {
                    it.nombre.equals(ejecutorNombre, ignoreCase = true)
                }?.cedula
                viewModel.actualizarReparacion(
                    reparacion.id,
                    nuevaLocalizacion,
                    materialesSeleccionados.toList(),
                    estado,
                    ejecutorNombre,
                    ejecutorCedula
                )
            }
            .setNegativeButton("Cancelar", null)
            .show()
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
        viewLifecycleOwner.lifecycleScope.launch {
            val medidor = viewModel.buscarMedidorPorLocalizacion(reparacion.localizacion)
            sheetBinding.tvClienteDetalle.text = medidor?.cliente?.ifBlank { "Sin datos" } ?: "Sin datos"
            sheetBinding.tvContactoDetalle.text = "Sin datos"
            sheetBinding.btnLlamarContacto.isEnabled = false
        }
    }

    private fun configurarFormularioBottomSheet(
        binding: BottomSheetLuminariaReparacionBinding,
        titulo: String,
        mostrarDetalle: Boolean,
        reparacion: com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaReparacionEntity?
    ) {
        binding.tvTituloBottomSheet.text = titulo
        binding.groupDetalle.isVisible = mostrarDetalle
        binding.tvLocalizacionDetalle.text = reparacion?.localizacion?.let { "Localización #$it" } ?: "-"
        binding.btnGuardarReparacion.text = if (reparacion == null) {
            "Registrar reparación"
        } else {
            "Actualizar reparación"
        }
        binding.btnLlamarContacto.setOnClickListener {
            val telefono = binding.tvContactoDetalle.text?.toString().orEmpty()
            if (telefono.isNotBlank()) {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$telefono"))
                startActivity(intent)
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
            materialesCatalogo.getOrNull(position)?.let { material ->
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

        binding.btnGuardarReparacion.setOnClickListener {
            if (reparacion == null) {
                val valido = validarFormularioRegistro(
                    binding,
                    materialesSeleccionados,
                    binding.actEjecutorLuminaria.text?.toString().orEmpty()
                )
                if (valido) {
                    viewModel.actualizarEstado(obtenerEstado(binding.actEstadoLuminaria))
                    viewModel.registrarReparacion()
                }
            } else {
                val estado = obtenerEstado(binding.actEstadoLuminaria)
                val ejecutorNombre = binding.actEjecutorLuminaria.text?.toString().orEmpty()
                val ejecutorCedula = tecnicosCatalogo.firstOrNull {
                    it.nombre.equals(ejecutorNombre, ignoreCase = true)
                }?.cedula
                val localizacion = binding.etLocalizacion.text?.toString().orEmpty()
                if (validarFormularioRegistro(binding, materialesSeleccionados, ejecutorNombre)) {
                    viewModel.actualizarReparacion(
                        reparacion.id,
                        localizacion,
                        materialesSeleccionados.toList(),
                        estado,
                        ejecutorNombre,
                        ejecutorCedula
                    )
                }
            }
        }
    }

    private fun validarFormularioRegistro(
        binding: BottomSheetLuminariaReparacionBinding,
        materiales: List<LuminariaMaterialSeleccionado>,
        ejecutor: String
    ): Boolean {
        var valido = true
        val localizacion = binding.etLocalizacion.text?.toString().orEmpty().trim()
        if (localizacion.isBlank()) {
            binding.tilLocalizacion.error = "Ingresa el número de localización"
            valido = false
        } else {
            binding.tilLocalizacion.error = null
        }

        if (materiales.isEmpty()) {
            binding.tilMaterialLuminaria.error = "Agrega al menos un material"
            valido = false
        } else {
            binding.tilMaterialLuminaria.error = null
        }

        if (ejecutor.trim().isBlank()) {
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
}
