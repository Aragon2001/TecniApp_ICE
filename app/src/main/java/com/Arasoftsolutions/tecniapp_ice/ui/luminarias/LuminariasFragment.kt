package com.Arasoftsolutions.tecniapp_ice.ui.luminarias

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentLuminariasBinding
import com.Arasoftsolutions.tecniapp_ice.ui.inventario.InventarioAdapter
import kotlinx.coroutines.launch

class LuminariasFragment : Fragment() {

    private var _binding: FragmentLuminariasBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LuminariasViewModel by viewModels()
    private lateinit var inventarioAdapter: InventarioAdapter
    private var vehiculosAdapter: ArrayAdapter<String>? = null

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
        inventarioAdapter = InventarioAdapter()
        binding.listInventarioLuminaria.layoutManager = LinearLayoutManager(requireContext())
        binding.listInventarioLuminaria.adapter = inventarioAdapter

        binding.btnRegistrarLuminaria.setOnClickListener { viewModel.registrarReparacion() }
        binding.etCodigo.doAfterTextChanged { viewModel.actualizarCodigo(it?.toString().orEmpty()) }
        binding.etCantidad.doAfterTextChanged { viewModel.actualizarCantidad(it?.toString().orEmpty()) }
        binding.etLocalizacion.doAfterTextChanged { viewModel.actualizarLocalizacion(it?.toString().orEmpty()) }

        observarEstado()
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

                    inventarioAdapter.submitList(state.inventario)
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
}
