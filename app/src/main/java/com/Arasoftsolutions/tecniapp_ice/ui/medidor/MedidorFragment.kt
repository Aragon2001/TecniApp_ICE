package com.Arasoftsolutions.tecniapp_ice.ui.medidor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentMedidorBinding
import kotlinx.coroutines.launch

class MedidorFragment : Fragment() {

    private var _binding: FragmentMedidorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MedidorViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMedidorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.initialize()
        configurarUi()
        observarEstado()
    }

    private fun configurarUi() {
        binding.btnConsultar.setOnClickListener { consultarMedidor() }
        binding.inputMedidor.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                consultarMedidor()
                true
            } else {
                false
            }
        }
        binding.btnCopiar.setOnClickListener { copiarInformacion() }
        binding.btnCompartir.setOnClickListener { compartirInformacion() }
        binding.btnMostrarRegistro.setOnClickListener {
            viewModel.habilitarRegistroManual()
            binding.tilRegistroNumero.error = null
            binding.tilRegistroLocalizacion.error = null
        }
        binding.btnCancelarRegistro.setOnClickListener {
            viewModel.cancelarRegistroManual()
            limpiarFormularioManual()
        }
        binding.btnGuardarManual.setOnClickListener { registrarMedidorManual() }

        binding.inputRegistroNumero.doAfterTextChanged {
            binding.tilRegistroNumero.error = null
        }
        binding.inputRegistroLocalizacion.doAfterTextChanged {
            binding.tilRegistroLocalizacion.error = null
        }
    }

    private fun observarEstado() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { estado ->
                    binding.progressIndicator.isVisible = estado.isLoading
                    binding.btnConsultar.isEnabled = !estado.isLoading && !estado.isRegistering
                    binding.cardResultado.isVisible = estado.medidor != null
                    binding.layoutAcciones.isVisible = estado.medidor != null
                    binding.cardNoEncontrado.isVisible = estado.notFoundNumero != null && !estado.showManualForm
                    binding.cardRegistroManual.isVisible = estado.showManualForm
                    binding.btnGuardarManual.isEnabled = !estado.isRegistering
                    binding.progressRegistro.isVisible = estado.isRegistering
                    binding.btnMostrarRegistro.isEnabled = !estado.isRegistering
                    binding.btnCancelarRegistro.isEnabled = !estado.isRegistering

                    val mensaje = estado.message ?: getString(R.string.medidor_estado_listo)
                    val infoMessages = setOf(
                        getString(R.string.medidor_estado_listo),
                        getString(R.string.medidor_estado_instruccion),
                        getString(R.string.medidor_estado_cargando_cache)
                    )
                    val tituloEstado = when {
                        estado.isLoading || estado.isRegistering -> getString(R.string.medidor_estado_titulo_preparando)
                        estado.medidor != null -> getString(R.string.medidor_estado_titulo_exito)
                        estado.notFoundNumero != null -> getString(R.string.medidor_estado_titulo_no_encontrado)
                        estado.message != null && estado.message !in infoMessages -> getString(R.string.medidor_estado_titulo_error)
                        else -> getString(R.string.medidor_estado_titulo_listo)
                    }
                    binding.textEstadoTitle.text = tituloEstado
                    binding.textEstadoMessage.text = mensaje

                    val chipTexto = if (estado.isReady) {
                        estado.subregionNombre?.let {
                            getString(R.string.medidor_estado_chip_listo, it)
                        } ?: getString(R.string.medidor_estado_chip_listo_generico)
                    } else {
                        getString(R.string.medidor_estado_chip_preparando)
                    }
                    binding.chipEstado.text = chipTexto

                    estado.notFoundNumero?.let { numero ->
                        binding.textNoEncontradoDescription.text = getString(
                            R.string.medidor_no_encontrado_descripcion,
                            numero
                        )
                        if (estado.showManualForm && binding.inputRegistroNumero.text.isNullOrBlank()) {
                            binding.inputRegistroNumero.setText(numero)
                            binding.inputRegistroNumero.setSelection(numero.length)
                        }
                    } ?: run {
                        binding.textNoEncontradoDescription.text = getString(R.string.medidor_no_encontrado_descripcion_vacia)
                        if (!estado.showManualForm) {
                            limpiarFormularioManual()
                        }
                    }

                    estado.medidor?.let { medidor ->
                        binding.valueNumero.text = medidor.medidorNumber.ifBlank {
                            getString(R.string.profile_summary_placeholder)
                        }
                        binding.valueCliente.text = medidor.cliente.orEmpty().ifBlank {
                            getString(R.string.profile_summary_placeholder)
                        }
                        binding.valueCalle.text = medidor.calle.orEmpty().ifBlank {
                            getString(R.string.profile_summary_placeholder)
                        }
                        binding.valuePoste.text = medidor.poste.orEmpty().ifBlank {
                            getString(R.string.profile_summary_placeholder)
                        }
                        binding.valueMetros.text = medidor.metros.orEmpty().ifBlank {
                            getString(R.string.profile_summary_placeholder)
                        }
                        val subregionDisplay = estado.subregionNombre
                            ?: medidor.subregion.orEmpty().ifBlank {
                                getString(R.string.profile_summary_placeholder)
                            }
                        binding.valueSubregionHeader.text = subregionDisplay
                        binding.valueLocalizacion.text = medidor.localizacion?.toString()
                            ?: getString(R.string.profile_summary_placeholder)

                        viewLifecycleOwner.lifecycleScope.launch {
                            val descripcionPueblo = viewModel.obtenerDescripcionPueblo(medidor.pueblo)
                            binding.valuePueblo.text = descripcionPueblo ?: medidor.pueblo.orEmpty().ifBlank {
                                getString(R.string.profile_summary_placeholder)
                            }
                        }
                    } ?: limpiarCampos()
                }
            }
        }
    }

    private fun limpiarCampos() {
        binding.valueNumero.text = ""
        binding.valueCliente.text = ""
        binding.valueCalle.text = ""
        binding.valuePoste.text = ""
        binding.valueMetros.text = ""
        binding.valuePueblo.text = ""
        binding.valueLocalizacion.text = ""
        binding.valueSubregionHeader.text = ""
    }

    private fun consultarMedidor() {
        val numero = binding.inputMedidor.text?.toString().orEmpty().trim()
        if (numero.isEmpty()) {
            binding.layoutMedidor.error = getString(R.string.medidor_error_numero)
            return
        }
        binding.layoutMedidor.error = null
        viewModel.buscar(numero)
    }

    private fun copiarInformacion() {
        val info = buildString {
            appendLine("${getString(R.string.medidor_numero_label)}: ${binding.valueNumero.text}")
            appendLine("${getString(R.string.medidor_cliente_label)}: ${binding.valueCliente.text}")
            appendLine("${getString(R.string.medidor_calle_label)}: ${binding.valueCalle.text}")
            appendLine("${getString(R.string.medidor_poste_label)}: ${binding.valuePoste.text}")
            appendLine("${getString(R.string.medidor_metros_label)}: ${binding.valueMetros.text}")
            appendLine("${getString(R.string.medidor_pueblo_label)}: ${binding.valuePueblo.text}")
            appendLine("${getString(R.string.medidor_subregion_label)}: ${binding.valueSubregionHeader.text}")
            append("${getString(R.string.medidor_localizacion_label)}: ${binding.valueLocalizacion.text}")
        }

        val clipboard = ContextCompat.getSystemService(requireContext(), ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("Medidor", info))
    }

    private fun compartirInformacion() {
        val medidor = viewModel.obtenerMedidorActual() ?: return
        val info = buildString {
            appendLine("${getString(R.string.medidor_numero_label)}: ${medidor.medidorNumber}")
            appendLine("${getString(R.string.medidor_cliente_label)}: ${binding.valueCliente.text}")
            appendLine("${getString(R.string.medidor_calle_label)}: ${binding.valueCalle.text}")
            appendLine("${getString(R.string.medidor_poste_label)}: ${binding.valuePoste.text}")
            appendLine("${getString(R.string.medidor_metros_label)}: ${binding.valueMetros.text}")
            appendLine("${getString(R.string.medidor_pueblo_label)}: ${binding.valuePueblo.text}")
            appendLine("${getString(R.string.medidor_subregion_label)}: ${binding.valueSubregionHeader.text}")
            append("${getString(R.string.medidor_localizacion_label)}: ${binding.valueLocalizacion.text}")
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, info)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.medidor_compartir)))
    }

    private fun registrarMedidorManual() {
        val numero = binding.inputRegistroNumero.text?.toString().orEmpty().trim()
        if (numero.isEmpty()) {
            binding.tilRegistroNumero.error = getString(R.string.medidor_registro_requerido_numero)
            return
        }

        val cliente = binding.inputRegistroCliente.text?.toString()?.trim().takeIf { !it.isNullOrEmpty() }
        val calle = binding.inputRegistroCalle.text?.toString()?.trim().takeIf { !it.isNullOrEmpty() }
        val poste = binding.inputRegistroPoste.text?.toString()?.trim().takeIf { !it.isNullOrEmpty() }
        val metros = binding.inputRegistroMetros.text?.toString()?.trim().takeIf { !it.isNullOrEmpty() }
        val pueblo = binding.inputRegistroPueblo.text?.toString()?.trim().takeIf { !it.isNullOrEmpty() }

        val localizacionTexto = binding.inputRegistroLocalizacion.text?.toString()?.trim()
        val localizacion = localizacionTexto?.takeIf { it.isNotEmpty() }?.toLongOrNull()
        if (localizacionTexto?.isNotEmpty() == true && localizacion == null) {
            binding.tilRegistroLocalizacion.error = getString(R.string.medidor_registro_localizacion_error)
            return
        }
        binding.tilRegistroLocalizacion.error = null

        viewModel.registrarMedidorManual(
            numero = numero,
            cliente = cliente,
            localizacion = localizacion,
            calle = calle,
            poste = poste,
            metros = metros,
            pueblo = pueblo
        )
    }

    private fun limpiarFormularioManual() {
        binding.tilRegistroNumero.error = null
        binding.tilRegistroLocalizacion.error = null
        binding.inputRegistroNumero.setText("")
        binding.inputRegistroCliente.setText("")
        binding.inputRegistroLocalizacion.setText("")
        binding.inputRegistroCalle.setText("")
        binding.inputRegistroPoste.setText("")
        binding.inputRegistroMetros.setText("")
        binding.inputRegistroPueblo.setText("")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
