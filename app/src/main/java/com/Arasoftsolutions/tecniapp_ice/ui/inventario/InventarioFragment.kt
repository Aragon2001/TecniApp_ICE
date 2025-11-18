package com.Arasoftsolutions.tecniapp_ice.ui.inventario

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentInventarioBinding
import kotlinx.coroutines.launch

class InventarioFragment : Fragment() {

    private var _binding: FragmentInventarioBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InventarioViewModel by viewModels()
    private lateinit var adapter: InventarioAdapter
    private var vehiculosAdapter: ArrayAdapter<String>? = null

    private val csvLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.procesarCsv(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentInventarioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = InventarioAdapter()
        binding.listInventario.layoutManager = LinearLayoutManager(requireContext())
        binding.listInventario.adapter = adapter
        setupListeners()
        observarEstado()
    }

    private fun setupListeners() {
        binding.btnAgregar.setOnClickListener { ajustarCantidad(true) }
        binding.btnRetirar.setOnClickListener { ajustarCantidad(false) }
        binding.btnImportarCsv.setOnClickListener { csvLauncher.launch("text/csv") }
    }

    private fun ajustarCantidad(esEntrada: Boolean) {
        val codigo = binding.etCodigoMaterial.text?.toString().orEmpty()
        val descripcion = binding.etDescripcion.text?.toString().orEmpty()
        val cantidad = binding.etCantidad.text?.toString()?.toDoubleOrNull() ?: 0.0
        if (codigo.isBlank() || cantidad <= 0) {
            Toast.makeText(requireContext(), "Ingresa código y cantidad válida", Toast.LENGTH_SHORT).show()
            return
        }
        val delta = if (esEntrada) cantidad else -cantidad
        viewModel.ajustarCantidadManual(codigo, descripcion, delta)
    }

    private fun observarEstado() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressInventario.isVisible = state.isProcessing
                    val labels = state.vehiculos.map { "${it.placa} - ${it.tipo}" }
                    if (vehiculosAdapter == null) {
                        vehiculosAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
                        binding.spVehiculos.setAdapter(vehiculosAdapter)
                    } else {
                        vehiculosAdapter?.clear()
                        vehiculosAdapter?.addAll(labels)
                    }
                    val seleccion = state.vehiculos.indexOfFirst { it.id == state.vehiculoSeleccionado }
                    if (seleccion >= 0 && seleccion < labels.size) {
                        binding.spVehiculos.setText(labels[seleccion], false)
                    }
                    binding.spVehiculos.setOnItemClickListener { _, _, position, _ ->
                        state.vehiculos.getOrNull(position)?.id?.let { viewModel.seleccionarVehiculo(it) }
                    }

                    adapter.submitList(state.inventario)
                    binding.tvInventarioVacio.isVisible = state.inventario.isEmpty()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.mensajes.collect { mensaje ->
                    mensaje?.let {
                        val texto = when (it) {
                            is InventarioMensaje.Exito -> it.texto
                            is InventarioMensaje.Error -> it.texto
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
