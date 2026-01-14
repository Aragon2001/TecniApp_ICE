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
    private lateinit var materialesSeleccionadosAdapter: LuminariaMaterialAdapter
    private lateinit var reparacionesAdapter: LuminariaReparacionAdapter
    private var materialesCatalogo = emptyList<com.Arasoftsolutions.tecniapp_ice.Database.entities.MaterialEntity>()

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

        binding.btnRegistrarLuminaria.setOnClickListener { viewModel.registrarReparacion() }
        binding.etLocalizacion.doAfterTextChanged { viewModel.actualizarLocalizacion(it?.toString().orEmpty()) }

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

        reparacionesAdapter = LuminariaReparacionAdapter(
            onEdit = { reparacion -> mostrarDialogoEdicion(reparacion) },
            onDelete = { reparacion -> viewModel.eliminarReparacion(reparacion.id) }
        )
        binding.listReparacionesLuminaria.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = reparacionesAdapter
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
                            mostrarDialogoCantidad(material)
                        }
                    }

                    materialesSeleccionadosAdapter.submitList(state.materialesSeleccionados)
                    binding.tvEmptyMateriales.isVisible = state.materialesSeleccionados.isEmpty()

                    reparacionesAdapter.submitList(state.reparaciones)
                    binding.tvEmptyReparaciones.isVisible = state.reparaciones.isEmpty()

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

    private fun mostrarDialogoCantidad(material: com.Arasoftsolutions.tecniapp_ice.Database.entities.MaterialEntity) {
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
                viewModel.agregarMaterial(material.codigo, material.descripcion, cantidad)
                binding.actMaterialLuminaria.setText("", false)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarDialogoEdicion(reparacion: com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaReparacionEntity) {
        val dialogView = layoutInflater.inflate(
            com.Arasoftsolutions.tecniapp_ice.R.layout.dialog_luminaria_edicion,
            null
        )
        val localizacionInput = dialogView.findViewById<AppCompatEditText>(
            com.Arasoftsolutions.tecniapp_ice.R.id.etLocalizacionReparacion
        )
        val cantidadInput = dialogView.findViewById<AppCompatEditText>(
            com.Arasoftsolutions.tecniapp_ice.R.id.etCantidadReparacion
        )
        localizacionInput.setText(reparacion.localizacion)
        cantidadInput.setText(reparacion.cantidadUtilizada.toString())
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Editar reparación")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val nuevaLocalizacion = localizacionInput.text?.toString().orEmpty()
                val nuevaCantidad = cantidadInput.text?.toString()?.toDoubleOrNull() ?: 0.0
                viewModel.actualizarReparacion(reparacion.id, nuevaLocalizacion, nuevaCantidad)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
