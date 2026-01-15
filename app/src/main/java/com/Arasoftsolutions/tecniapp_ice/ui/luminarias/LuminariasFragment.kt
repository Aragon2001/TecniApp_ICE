package com.Arasoftsolutions.tecniapp_ice.ui.luminarias

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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class LuminariasFragment : Fragment() {

    private var _binding: FragmentLuminariasBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LuminariasViewModel by viewModels()
    private var vehiculosAdapter: ArrayAdapter<String>? = null
    private var materialesAdapter: ArrayAdapter<String>? = null
    private var tecnicosAdapter: ArrayAdapter<String>? = null
    private var estadosAdapter: ArrayAdapter<String>? = null
    private lateinit var materialesSeleccionadosAdapter: LuminariaMaterialAdapter
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

        binding.btnRegistrarLuminaria.setOnClickListener { validarYRegistrar() }
        binding.etLocalizacion.doAfterTextChanged {
            viewModel.actualizarLocalizacion(it?.toString().orEmpty())
            binding.tilLocalizacion.error = null
        }
        binding.actEjecutorLuminaria.doAfterTextChanged {
            viewModel.actualizarEjecutor(it?.toString().orEmpty())
            binding.tilEjecutorLuminaria.error = null
        }

        observarEstado()
    }

    private fun setupAdapters() {
        materialesSeleccionadosAdapter = LuminariaMaterialAdapter { material ->
            viewModel.eliminarMaterial(material.codigo)
        }
        binding.listMaterialesLuminaria.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = materialesSeleccionadosAdapter
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }

        reparacionesPendientesAdapter = LuminariaReparacionAdapter(
            onEdit = { reparacion -> mostrarDialogoEdicion(reparacion) },
            onDelete = { reparacion -> confirmarEliminacion(reparacion) }
        )
        binding.listReparacionesPendientes.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = reparacionesPendientesAdapter
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }

        reparacionesReparadasAdapter = LuminariaReparacionAdapter(
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
                    val labels = state.vehiculos.map { "${it.placa} - ${it.tipo}" }
                    if (vehiculosAdapter == null) {
                        vehiculosAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
                        binding.spVehiculoLuminaria.setAdapter(vehiculosAdapter)
                    } else {
                        vehiculosAdapter?.clear()
                        vehiculosAdapter?.addAll(labels)
                    }
                    val seleccion = state.vehiculos.indexOfFirst { it.id == state.vehiculoSeleccionado }
                    if (seleccion >= 0 && seleccion < labels.size) {
                        binding.spVehiculoLuminaria.setText(labels[seleccion], false)
                    }
                    binding.spVehiculoLuminaria.setOnItemClickListener { _, _, position, _ ->
                        state.vehiculos.getOrNull(position)?.id?.let { viewModel.seleccionarVehiculo(it) }
                    }
                    binding.spVehiculoLuminaria.isEnabled = !state.vehiculoAutomatico

                    materialesCatalogo = state.materiales
                    val materialesLabel = state.materiales.map { "${it.codigo} - ${it.descripcion}" }
                    if (materialesAdapter == null) {
                        materialesAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, materialesLabel)
                        binding.actMaterialLuminaria.setAdapter(materialesAdapter)
                    } else {
                        materialesAdapter?.clear()
                        materialesAdapter?.addAll(materialesLabel)
                    }

                    binding.actMaterialLuminaria.setOnItemClickListener { _, _, position, _ ->
                        materialesCatalogo.getOrNull(position)?.let { material ->
                            mostrarDialogoCantidad(material) { cantidad ->
                                viewModel.agregarMaterial(material.codigo, material.descripcion, cantidad)
                                binding.actMaterialLuminaria.setText("", false)
                            }
                        }
                    }

                    tecnicosCatalogo = state.tecnicos
                    val tecnicosLabel = state.tecnicos.map { it.nombre }
                    if (tecnicosAdapter == null) {
                        tecnicosAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, tecnicosLabel)
                        binding.actEjecutorLuminaria.setAdapter(tecnicosAdapter)
                    } else {
                        tecnicosAdapter?.clear()
                        tecnicosAdapter?.addAll(tecnicosLabel)
                    }
                    if (binding.actEjecutorLuminaria.text?.toString() != state.ejecutorNombre) {
                        binding.actEjecutorLuminaria.setText(state.ejecutorNombre, false)
                    }

                    val estadosLabel = listOf("Pendiente", "Reparada")
                    if (estadosAdapter == null) {
                        estadosAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, estadosLabel)
                        binding.actEstadoLuminaria.setAdapter(estadosAdapter)
                    }
                    val estadoTexto = if (state.estadoSeleccionado == com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado.PENDIENTE) {
                        "Pendiente"
                    } else {
                        "Reparada"
                    }
                    if (binding.actEstadoLuminaria.text?.toString() != estadoTexto) {
                        binding.actEstadoLuminaria.setText(estadoTexto, false)
                    }
                    binding.actEstadoLuminaria.setOnItemClickListener { _, _, position, _ ->
                        val estado = if (position == 0) {
                            com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado.PENDIENTE
                        } else {
                            com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado.REPARADA
                        }
                        viewModel.actualizarEstado(estado)
                    }

                    materialesSeleccionadosAdapter.submitList(state.materialesSeleccionados)
                    binding.tvEmptyMateriales.isVisible = state.materialesSeleccionados.isEmpty()
                    if (state.materialesSeleccionados.isNotEmpty()) {
                        binding.tilMaterialLuminaria.error = null
                    }

                    reparacionesPendientesAdapter.submitList(state.reparacionesPendientes)
                    binding.tvEmptyReparacionesPendientes.isVisible = state.reparacionesPendientes.isEmpty()

                    reparacionesReparadasAdapter.submitList(state.reparacionesReparadas)
                    binding.tvEmptyReparaciones.isVisible = state.reparacionesReparadas.isEmpty()

                    if (binding.etLocalizacion.text?.toString() != state.localizacion) {
                        binding.etLocalizacion.setText(state.localizacion)
                    }
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

    private fun validarYRegistrar() {
        val state = viewModel.uiState.value
        var valido = true

        val localizacion = binding.etLocalizacion.text?.toString().orEmpty().trim()
        if (localizacion.isBlank()) {
            binding.tilLocalizacion.error = "Ingresa el número de localización"
            valido = false
        } else {
            binding.tilLocalizacion.error = null
        }

        if (state.materialesSeleccionados.isEmpty()) {
            binding.tilMaterialLuminaria.error = "Agrega al menos un material"
            valido = false
        } else {
            binding.tilMaterialLuminaria.error = null
        }

        val ejecutor = binding.actEjecutorLuminaria.text?.toString().orEmpty().trim()
        if (ejecutor.isBlank()) {
            binding.tilEjecutorLuminaria.error = "Indica quién ejecutó la reparación"
            valido = false
        } else {
            binding.tilEjecutorLuminaria.error = null
        }

        if (valido) {
            viewModel.registrarReparacion()
        }
    }

    private fun confirmarEliminacion(reparacion: com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaReparacionEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar reparación")
            .setMessage("¿Deseas eliminar esta reparación? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ -> viewModel.eliminarReparacion(reparacion.id) }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
