package com.Arasoftsolutions.tecniapp_ice.ui.programacion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentNuevaProgramacionBinding
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
        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getInstance(requireContext())
            val vehiculos = db.vehiculoDao().getAll()
            val tecnicos = db.usuarioDao().searchTecnicos()

            binding.spinnerVehiculo.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                vehiculos.map { "${it.placa} - ${it.vehiculoId}" }
            )
            binding.spinnerTecnico.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                tecnicos.map { "${it.nombre.orEmpty()} (${it.uid})" }
            )

            binding.btnAsignar.setOnClickListener {
                val vehiculo = vehiculos.getOrNull(binding.spinnerVehiculo.selectedItemPosition) ?: return@setOnClickListener
                val tecnico = tecnicos.getOrNull(binding.spinnerTecnico.selectedItemPosition) ?: return@setOnClickListener
                viewModel.crearProgramacion(
                    NuevaProgramacionInput(
                        vehiculoId = vehiculo.vehiculoId,
                        tecnicoId = tecnico.uid,
                        localizacion = binding.etLocalizacion.text?.toString().orEmpty(),
                        circuito = binding.etCircuito.text?.toString().orEmpty(),
                        cuenta = binding.etCuenta.text?.toString().orEmpty(),
                        actividad = binding.etActividad.text?.toString().orEmpty(),
                        descripcion = binding.etDescripcion.text?.toString(),
                        lat = binding.etLat.text?.toString()?.toDoubleOrNull(),
                        lng = binding.etLng.text?.toString()?.toDoubleOrNull(),
                        fotosAsignacion = binding.etFotos.text?.toString().orEmpty().split(',').map { it.trim() }.filter { it.isNotBlank() }
                    )
                )
                findNavController().navigateUp()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
