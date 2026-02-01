package com.Arasoftsolutions.tecniapp_ice.ui.vehiculo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.Arasoftsolutions.tecniapp_ice.Database.entities.EtmRegistroEntity
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentMiVehiculoBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class MiVehiculoFragment : Fragment() {

    private var _binding: FragmentMiVehiculoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MiVehiculoViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMiVehiculoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvRegistros.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRegistros.adapter = EtmRegistroAdapter(emptyList())

        setupListeners()
        observarEstado()
    }

    private fun setupListeners() {
        binding.btnRegistrarInicial.setOnClickListener { mostrarDialogoRegistrarInicial() }
        binding.btnRegistrarFinal.setOnClickListener { mostrarDialogoRegistrarFinal() }
    }

    private fun mostrarDialogoRegistrarInicial() {
        val state = viewModel.uiState.value
        val unidad = state.tipoVehiculo.unidadTexto
        val input = TextInputEditText(requireContext()).apply {
            hint = "Valor inicial ($unidad)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.mi_vehiculo_registrar_inicial))
            .setView(input)
            .setPositiveButton(getString(android.R.string.ok)) { _, _ ->
                val v = input.text?.toString()?.toDoubleOrNull()
                if (v != null && v >= 0) viewModel.registrarInicial(v)
                else Toast.makeText(requireContext(), "Ingresa un valor válido", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun mostrarDialogoRegistrarFinal() {
        val state = viewModel.uiState.value
        val unidad = state.tipoVehiculo.unidadTexto
        val input = TextInputEditText(requireContext()).apply {
            hint = "Valor final ($unidad)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.mi_vehiculo_registrar_final))
            .setView(input)
            .setPositiveButton(getString(android.R.string.ok)) { _, _ ->
                val v = input.text?.toString()?.toDoubleOrNull()
                if (v != null && v >= 0) viewModel.registrarFinal(v)
                else Toast.makeText(requireContext(), "Ingresa un valor válido", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun observarEstado() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val vehiculo = state.vehiculo
                    binding.tvSinVehiculo.isVisible = vehiculo == null
                    binding.btnRegistrarInicial.isVisible = vehiculo != null
                    binding.btnRegistrarFinal.isVisible = false

                    if (vehiculo != null) {
                        binding.tvVehiculoAsignado.text = "Placa: ${vehiculo.placa} - ${vehiculo.tipo}"
                        binding.tvTipoControl.text = if (state.tipoVehiculo.usaKilometraje) {
                            "Control por kilometraje (km)"
                        } else {
                            "Control por orímetro (horas)"
                        }

                        val reg = state.registroHoy
                        if (reg != null) {
                            val u = state.tipoVehiculo.unidadTexto
                            binding.tvEstadoHoy.text = "Hoy: ${reg.valorInicial} $u inicial" +
                                (reg.valorFinal?.let { " • $it $u final" } ?: "")
                            binding.btnRegistrarInicial.isVisible = false
                            binding.btnRegistrarFinal.isVisible = reg.valorFinal == null
                        } else {
                            binding.tvEstadoHoy.text = "Registra el valor inicial del día"
                            binding.btnRegistrarInicial.isVisible = true
                        }
                    }

                    (binding.rvRegistros.adapter as? EtmRegistroAdapter)?.updateList(
                        state.registrosRecientes,
                        state.tipoVehiculo.unidadTexto
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class EtmRegistroAdapter(
    private var items: List<EtmRegistroEntity>,
    private var unidad: String = "km"
) : androidx.recyclerview.widget.RecyclerView.Adapter<EtmRegistroAdapter.VH>() {

    fun updateList(list: List<EtmRegistroEntity>, u: String) {
        items = list
        unidad = u
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_etm_registro, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], unidad)
    }

    override fun getItemCount() = items.size

    class VH(view: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        private val tvFecha = view.findViewById<android.widget.TextView>(R.id.tvFecha)
        private val tvValores = view.findViewById<android.widget.TextView>(R.id.tvValores)

        fun bind(item: EtmRegistroEntity, unidad: String) {
            tvFecha.text = item.fecha
            val fin = item.valorFinal?.let { " • Final: $it $unidad" } ?: ""
            val diff = item.diferencia?.let { " • Diferencia: $it $unidad" } ?: ""
            tvValores.text = "Inicial: ${item.valorInicial} $unidad$fin$diff"
        }
    }
}
