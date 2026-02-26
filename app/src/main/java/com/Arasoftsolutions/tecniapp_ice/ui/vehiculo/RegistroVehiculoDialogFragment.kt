package com.Arasoftsolutions.tecniapp_ice.ui.vehiculo

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.Arasoftsolutions.tecniapp_ice.databinding.DialogRegistroVehiculoBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.Locale


class RegistroVehiculoDialogFragment : DialogFragment() {

    private var _binding: DialogRegistroVehiculoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MiVehiculoViewModel by activityViewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogRegistroVehiculoBinding.inflate(LayoutInflater.from(requireContext()))
        setupUi()
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
        dialog.setCanceledOnTouchOutside(false)
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun setupUi() {
        binding.chipOtros.isChecked = true
        binding.btnCancelar.setOnClickListener { dismiss() }
        binding.btnGuardar.setOnClickListener { guardarRegistro() }

        binding.chipGroupMantenimiento.setOnCheckedStateChangeListener { _, _ ->
            actualizarProximoMantenimiento()
        }

        binding.etValorActual.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                actualizarProximoMantenimiento()
            }
        })

        viewModel.uiState.onEach { state ->
            binding.tilValorActual.hint = getString(
                com.Arasoftsolutions.tecniapp_ice.R.string.mi_vehiculo_dialog_valor_hint_format,
                state.unidad
            )
            actualizarProximoMantenimiento()
        }.launchIn(lifecycleScope)
    }

    private fun guardarRegistro() {
        val valor = binding.etValorActual.text?.toString()?.replace(",", ".")?.toDoubleOrNull()
        if (valor == null || valor < 0) {
            binding.tilValorActual.error = getString(
                com.Arasoftsolutions.tecniapp_ice.R.string.mi_vehiculo_valor_invalido
            )
            return
        }
        binding.tilValorActual.error = null
        val tipo = tipoMantenimientoSeleccionado()
        val observaciones = binding.etObservaciones.text?.toString()?.trim()?.ifBlank { null }

        viewModel.registrarMantenimiento(valor, tipo, observaciones)
        dismiss()
    }

    private fun actualizarProximoMantenimiento() {
        val state = viewModel.uiState.value
        val valor = binding.etValorActual.text?.toString()?.replace(",", ".")?.toDoubleOrNull()
        val intervalo = viewModel.obtenerIntervaloMantenimiento(
            tipoMantenimientoSeleccionado(),
            state.tipoVehiculo.usaKilometraje
        )
        val proximo = valor?.let { it + intervalo }
        val label = if (proximo != null) {
            getString(
                com.Arasoftsolutions.tecniapp_ice.R.string.mi_vehiculo_dialog_proximo_format,
                String.format(Locale.getDefault(), "%.0f", proximo),
                state.unidad
            )
        } else {
            getString(com.Arasoftsolutions.tecniapp_ice.R.string.mi_vehiculo_dialog_proximo_placeholder)
        }
        binding.tvProximoMantenimiento.text = label
    }


    private fun tipoMantenimientoSeleccionado(): String {
        return when (binding.chipGroupMantenimiento.checkedChipId) {
            binding.chipAceite.id -> binding.chipAceite.text.toString()
            binding.chipFrenos.id -> binding.chipFrenos.text.toString()
            binding.chipRevision.id -> binding.chipRevision.text.toString()
            binding.chipLlantas.id -> binding.chipLlantas.text.toString()
            binding.chipFiltros.id -> binding.chipFiltros.text.toString()
            binding.chipOtros.id -> binding.chipOtros.text.toString()
            else -> binding.chipOtros.text.toString()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "RegistroVehiculoDialog"
    }
}
