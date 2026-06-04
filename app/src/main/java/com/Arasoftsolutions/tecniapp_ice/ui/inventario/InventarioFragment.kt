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
import com.Arasoftsolutions.tecniapp_ice.Database.entities.InventarioConVehiculo
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentInventarioBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class InventarioFragment : Fragment() {

    private var _binding: FragmentInventarioBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InventarioViewModel by viewModels()
    private lateinit var adapter: InventarioAdapter
    private var vehiculosAdapter: ArrayAdapter<String>? = null
    private var fabExpanded = false

    private val csvLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.procesarCsv(it) }
    }

    private val pdfLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.procesarPdf(it) }
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
        adapter = InventarioAdapter(
            showActions = true,
            onIncrease = { viewModel.ajustarCantidad(it, 1.0) },
            onDecrease = { viewModel.ajustarCantidad(it, -1.0) },
            onDelete = { mostrarConfirmacionEliminar(it) }
        )
        binding.listInventario.layoutManager = LinearLayoutManager(requireContext())
        binding.listInventario.adapter = adapter
        setupListeners()
        observarEstado()
    }

    private fun setupListeners() {
        binding.btnFabMain.setOnClickListener { toggleFab() }
        binding.btnFabCsv.setOnClickListener {
            collapseFab()
            csvLauncher.launch("text/csv")
        }
        binding.btnFabPdf.setOnClickListener {
            collapseFab()
            pdfLauncher.launch("application/pdf")
        }
    }

    private fun toggleFab() {
        fabExpanded = !fabExpanded
        binding.layoutFabMiniGroup.isVisible = fabExpanded
        binding.btnFabMain.setImageResource(
            if (fabExpanded) R.drawable.ic_close_sheet else R.drawable.ic_add
        )
    }

    private fun collapseFab() {
        fabExpanded = false
        binding.layoutFabMiniGroup.isVisible = false
        binding.btnFabMain.setImageResource(R.drawable.ic_add)
    }

    private fun observarEstado() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressInventario.isVisible = state.isProcessing
                    val labels = mutableListOf(getString(R.string.inventario_filtro_todos))
                    labels.addAll(state.vehiculos.map { "${it.placa} - ${it.tipo}" })
                    if (vehiculosAdapter == null) {
                        vehiculosAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
                        binding.spVehiculos.setAdapter(vehiculosAdapter)
                    } else {
                        vehiculosAdapter?.clear()
                        vehiculosAdapter?.addAll(labels)
                    }
                    val seleccion = state.vehiculoSeleccionado?.let { id ->
                        state.vehiculos.indexOfFirst { it.id == id }
                    } ?: -1
                    val index = if (seleccion >= 0) seleccion + 1 else 0
                    if (index in labels.indices) {
                        binding.spVehiculos.setText(labels[index], false)
                    }
                    binding.spVehiculos.setOnItemClickListener { _, _, position, _ ->
                        if (position == 0) {
                            viewModel.seleccionarVehiculo(null)
                        } else {
                            state.vehiculos.getOrNull(position - 1)?.id?.let { viewModel.seleccionarVehiculo(it) }
                        }
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

    private fun mostrarConfirmacionEliminar(item: InventarioConVehiculo) {
        val descripcion = item.item.descripcionMaterial.ifBlank { item.item.codigoMaterial }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.inventario_confirmar_eliminar_titulo))
            .setMessage(getString(R.string.inventario_confirmar_eliminar_mensaje, descripcion))
            .setPositiveButton(R.string.inventario_confirmar_eliminar_si) { _, _ ->
                viewModel.eliminarItem(item)
            }
            .setNegativeButton(R.string.inventario_confirmar_eliminar_no, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
