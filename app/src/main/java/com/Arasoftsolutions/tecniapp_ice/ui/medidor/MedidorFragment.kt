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
    }

    private fun observarEstado() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { estado ->
                    binding.progressIndicator.isVisible = estado.isLoading
                    binding.btnConsultar.isEnabled = !estado.isLoading
                    binding.cardResultado.isVisible = estado.medidor != null
                    binding.layoutAcciones.isVisible = estado.medidor != null

                    val mensaje = when {
                        estado.message != null -> estado.message
                        estado.medidor != null -> getString(R.string.medidor_result_title)
                        else -> getString(R.string.medidor_estado_listo)
                    }
                    binding.textStatus.text = mensaje

                    estado.medidor?.let { medidor ->
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
        binding.valueCliente.text = ""
        binding.valueCalle.text = ""
        binding.valuePoste.text = ""
        binding.valueMetros.text = ""
        binding.valuePueblo.text = ""
        binding.valueLocalizacion.text = ""
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
            appendLine("${getString(R.string.medidor_cliente_label)}: ${binding.valueCliente.text}")
            appendLine("${getString(R.string.medidor_calle_label)}: ${binding.valueCalle.text}")
            appendLine("${getString(R.string.medidor_poste_label)}: ${binding.valuePoste.text}")
            appendLine("${getString(R.string.medidor_metros_label)}: ${binding.valueMetros.text}")
            appendLine("${getString(R.string.medidor_pueblo_label)}: ${binding.valuePueblo.text}")
            append("${getString(R.string.medidor_localizacion_label)}: ${binding.valueLocalizacion.text}")
        }

        val clipboard = ContextCompat.getSystemService(requireContext(), ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("Medidor", info))
    }

    private fun compartirInformacion() {
        val medidor = viewModel.obtenerMedidorActual() ?: return
        val info = buildString {
            appendLine("${getString(R.string.medidor_numero_hint)}: ${medidor.medidorNumber}")
            appendLine("${getString(R.string.medidor_cliente_label)}: ${binding.valueCliente.text}")
            appendLine("${getString(R.string.medidor_calle_label)}: ${binding.valueCalle.text}")
            appendLine("${getString(R.string.medidor_poste_label)}: ${binding.valuePoste.text}")
            appendLine("${getString(R.string.medidor_metros_label)}: ${binding.valueMetros.text}")
            appendLine("${getString(R.string.medidor_pueblo_label)}: ${binding.valuePueblo.text}")
            append("${getString(R.string.medidor_localizacion_label)}: ${binding.valueLocalizacion.text}")
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, info)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.medidor_compartir)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
