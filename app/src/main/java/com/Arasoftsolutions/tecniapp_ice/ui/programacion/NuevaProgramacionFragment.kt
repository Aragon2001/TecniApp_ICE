package com.Arasoftsolutions.tecniapp_ice.ui.programacion

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentNuevaProgramacionBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class NuevaProgramacionFragment : Fragment() {
    private var _binding: FragmentNuevaProgramacionBinding? = null
    private val binding get() = requireNotNull(_binding)
    private val viewModel: ProgramacionViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNuevaProgramacionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupLocalizacionValidation()

        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getInstance(requireContext())
            val vehiculos = db.vehiculoDao().getAll()

            binding.spinnerVehiculo.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                vehiculos.map { "${it.placaRaw.ifBlank { it.placa.toString() }} — ${it.vehiculoId}" }
            )

            binding.btnAsignar.setOnClickListener {
                val locRaw = binding.etLocalizacion.text?.toString().orEmpty()
                val locResult = LocalizacionUtils.validarYNormalizar(locRaw)
                if (locResult.isFailure) {
                    binding.tilLocalizacion.error = locResult.exceptionOrNull()?.message
                    return@setOnClickListener
                }
                binding.tilLocalizacion.error = null

                val detalle = binding.etDetalleTrabajo.text?.toString().orEmpty()
                if (detalle.isBlank()) {
                    binding.tilDetalleTrabajo.error = "El detalle del trabajo es obligatorio."
                    return@setOnClickListener
                }
                binding.tilDetalleTrabajo.error = null

                val vehiculo = vehiculos.getOrNull(binding.spinnerVehiculo.selectedItemPosition)
                    ?: return@setOnClickListener

                viewModel.crearOrden(
                    NuevaOrdenInput(
                        vehiculoId = vehiculo.vehiculoId,
                        localizacionRaw = locRaw,
                        detalleTrabajo = detalle,
                        lat = binding.etLat.text?.toString()?.toDoubleOrNull(),
                        lng = binding.etLng.text?.toString()?.toDoubleOrNull(),
                        fotos = binding.etFotos.text?.toString().orEmpty()
                            .split(',').map { it.trim() }.filter { it.isNotBlank() }
                    )
                )
                findNavController().navigateUp()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.message != null) {
                        Snackbar.make(binding.root, state.message, Snackbar.LENGTH_SHORT).show()
                        viewModel.clearMessage()
                    }
                }
            }
        }
    }

    private fun setupLocalizacionValidation() {
        binding.etLocalizacion.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val raw = s?.toString().orEmpty()
                val result = LocalizacionUtils.validarYNormalizar(raw)
                if (result.isSuccess) {
                    binding.tilLocalizacion.error = null
                    val fmt = LocalizacionUtils.formatearParaMostrar(result.getOrThrow())
                    binding.tvLocNormalizada.text = "→ $fmt"
                    binding.tvLocNormalizada.isVisible = true
                } else {
                    binding.tvLocNormalizada.isVisible = false
                    if (raw.filter { it.isDigit() }.length >= 9) {
                        binding.tilLocalizacion.error = result.exceptionOrNull()?.message
                    } else {
                        binding.tilLocalizacion.error = null
                    }
                }
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
