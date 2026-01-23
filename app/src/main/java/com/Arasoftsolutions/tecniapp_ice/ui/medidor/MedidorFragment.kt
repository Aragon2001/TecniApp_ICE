package com.Arasoftsolutions.tecniapp_ice.ui.medidor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class MedidorFragment : Fragment() {

    private var _binding: FragmentMedidorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MedidorViewModel by viewModels()
    private lateinit var subregionAdapter: ArrayAdapter<String>
    private lateinit var puebloAdapter: ArrayAdapter<String>
    private var currentSubregionDisplays: List<String> = emptyList()
    private var currentPuebloDisplays: List<String> = emptyList()
    private val subregionDisplayToOption = mutableMapOf<String, SubregionOption>()
    private val puebloDisplayToOption = mutableMapOf<String, PuebloOption>()
    private var wasManualVisible = false

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
        val context = requireContext()
        subregionAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, mutableListOf())
        puebloAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, mutableListOf())

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
            binding.tilRegistroCliente.error = null
            binding.tilRegistroCalle.error = null
            binding.tilRegistroPoste.error = null
            binding.tilRegistroMetros.error = null
            binding.tilRegistroPueblo.error = null
            binding.tilRegistroSubregion.error = null
        }
        binding.btnCancelarRegistro.setOnClickListener {
            viewModel.cancelarRegistroManual()
            limpiarFormularioManual()
        }
        binding.btnGuardarManual.setOnClickListener { registrarMedidorManual() }

        binding.inputRegistroNumero.doAfterTextChanged {
            binding.tilRegistroNumero.error = null
        }
        binding.inputRegistroCliente.doAfterTextChanged {
            binding.tilRegistroCliente.error = null
        }
        binding.inputRegistroCalle.doAfterTextChanged {
            binding.tilRegistroCalle.error = null
            actualizarLocalizacionSugerida()
        }
        binding.inputRegistroPoste.doAfterTextChanged {
            binding.tilRegistroPoste.error = null
            actualizarLocalizacionSugerida()
        }
        binding.inputRegistroMetros.doAfterTextChanged {
            binding.tilRegistroMetros.error = null
            actualizarLocalizacionSugerida()
        }
        binding.inputRegistroLocalizacion.doAfterTextChanged {
            binding.tilRegistroLocalizacion.error = null
        }
        binding.inputRegistroSubregion.apply {
            keyListener = null
            isFocusable = true
            isFocusableInTouchMode = true
            setAdapter(subregionAdapter)
            setOnClickListener { showDropDown() }
            setOnFocusChangeListener { _, hasFocus -> if (hasFocus) showDropDown() }
            doAfterTextChanged {
                binding.tilRegistroSubregion.error = null
            }
            setOnItemClickListener { _, _, position, _ ->
                val display = subregionAdapter.getItem(position) ?: return@setOnItemClickListener
                subregionDisplayToOption[display]?.let { option ->
                    viewModel.seleccionarSubregionParaRegistro(option)
                    binding.tilRegistroSubregion.error = null
                }
            }
        }
        binding.inputRegistroPueblo.apply {
            keyListener = null
            isFocusable = true
            isFocusableInTouchMode = true
            setAdapter(puebloAdapter)
            setOnClickListener { showDropDown() }
            setOnFocusChangeListener { _, hasFocus -> if (hasFocus) showDropDown() }
            doAfterTextChanged {
                binding.tilRegistroPueblo.error = null
            }
            setOnItemClickListener { _, _, position, _ ->
                val display = puebloAdapter.getItem(position) ?: return@setOnItemClickListener
                puebloDisplayToOption[display]?.let { option ->
                    viewModel.seleccionarPuebloParaRegistro(option)
                    binding.tilRegistroPueblo.error = null
                    actualizarLocalizacionSugerida()
                }
            }
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
                    binding.btnGuardarManual.isEnabled = !estado.isRegistering && !estado.isPueblosLoading
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
                    binding.textEstadoMessage.isVisible = mensaje.isNotBlank() && mensaje !in infoMessages

                    actualizarSubregionAdapter(estado.subregionOptions)
                    val subregionDisplay = estado.selectedSubregionDisplay.orEmpty()
                    if (binding.inputRegistroSubregion.text?.toString() != subregionDisplay) {
                        binding.inputRegistroSubregion.setText(subregionDisplay, false)
                    }
                    val subregionDisponible = estado.subregionOptions.isNotEmpty()
                    binding.tilRegistroSubregion.isEnabled = subregionDisponible
                    binding.inputRegistroSubregion.isEnabled = subregionDisponible
                    binding.tilRegistroSubregion.helperText = when {
                        estado.showManualForm && !subregionDisponible -> getString(R.string.medidor_registro_subregion_cargando)
                        else -> null
                    }

                    actualizarPuebloAdapter(estado.puebloOptions)
                    val puebloDisplay = estado.selectedPuebloDisplay.orEmpty()
                    if (binding.inputRegistroPueblo.text?.toString() != puebloDisplay) {
                        binding.inputRegistroPueblo.setText(puebloDisplay, false)
                    }
                    val pueblosDisponibles = estado.puebloOptions.isNotEmpty()
                    binding.inputRegistroPueblo.isEnabled = pueblosDisponibles && !estado.isPueblosLoading
                    binding.tilRegistroPueblo.isEnabled = pueblosDisponibles || estado.isPueblosLoading
                    binding.tilRegistroPueblo.helperText = when {
                        estado.showManualForm && estado.isPueblosLoading -> getString(R.string.medidor_registro_pueblo_cargando)
                        else -> null
                    }

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

                    if (!estado.showManualForm) {
                        binding.tilRegistroSubregion.helperText = null
                        binding.tilRegistroPueblo.helperText = null
                    }

                    if (estado.showManualForm) {
                        actualizarLocalizacionSugerida()
                    }

                    if (estado.showManualForm && !wasManualVisible) {
                        binding.scrollMedidor.post {
                            binding.scrollMedidor.smoothScrollTo(0, binding.cardRegistroManual.top)
                        }
                    }
                    wasManualVisible = estado.showManualForm

                    if (estado.showNotFoundDialog && estado.notFoundNumero != null) {
                        viewModel.onNotFoundDialogMostrado()
                        val dialogMessage = if (estado.notFoundOffline) {
                            getString(R.string.medidor_no_registrado_dialog_offline, estado.notFoundNumero)
                        } else {
                            getString(R.string.medidor_no_registrado_dialog_message, estado.notFoundNumero)
                        }
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.medidor_no_registrado_dialog_title)
                            .setMessage(dialogMessage)
                            .setPositiveButton(R.string.medidor_no_registrado_dialog_positive) { _, _ ->
                                viewModel.habilitarRegistroManual()
                            }
                            .setNegativeButton(R.string.medidor_no_registrado_dialog_negative, null)
                            .show()
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

    private fun actualizarSubregionAdapter(opciones: List<SubregionOption>) {
        if (!::subregionAdapter.isInitialized) return
        val displays = opciones.map { it.displayName }
        if (displays == currentSubregionDisplays) return
        currentSubregionDisplays = displays
        subregionDisplayToOption.clear()
        opciones.forEachIndexed { index, option ->
            val display = displays.getOrElse(index) { option.displayName }
            subregionDisplayToOption[display] = option
        }
        subregionAdapter.clear()
        subregionAdapter.addAll(displays)
        subregionAdapter.notifyDataSetChanged()
    }

    private fun actualizarPuebloAdapter(opciones: List<PuebloOption>) {
        if (!::puebloAdapter.isInitialized) return
        val displays = opciones.map { it.displayName }
        if (displays == currentPuebloDisplays) return
        currentPuebloDisplays = displays
        puebloDisplayToOption.clear()
        opciones.forEachIndexed { index, option ->
            val display = displays.getOrElse(index) { option.displayName }
            puebloDisplayToOption[display] = option
        }
        puebloAdapter.clear()
        puebloAdapter.addAll(displays)
        puebloAdapter.notifyDataSetChanged()
    }

    private fun actualizarLocalizacionSugerida() {
        val estadoActual = viewModel.uiState.value
        val puebloCodigo = estadoActual.selectedPuebloId?.toString()
        val calle = binding.inputRegistroCalle.text?.toString().orEmpty()
        val poste = binding.inputRegistroPoste.text?.toString().orEmpty()
        val metros = binding.inputRegistroMetros.text?.toString().orEmpty()
        val localizacion = generarLocalizacion(puebloCodigo, calle, poste, metros)
        val nuevoTexto = localizacion?.toString().orEmpty()
        if (binding.inputRegistroLocalizacion.text?.toString() != nuevoTexto) {
            binding.inputRegistroLocalizacion.setText(nuevoTexto)
        }
        if (localizacion != null) {
            binding.tilRegistroLocalizacion.error = null
        }
    }

    private fun generarLocalizacion(
        puebloCodigo: String?,
        calle: String,
        poste: String,
        metros: String,
    ): Long? {
        val codigo = puebloCodigo?.takeIf { it.isNotBlank() } ?: return null
        if (calle.isBlank() || poste.isBlank() || metros.isBlank()) return null
        val puebloSegmento = codigo.filter(Char::isDigit)
        if (puebloSegmento.isEmpty()) return null
        val calleSegmento = calle.filter(Char::isDigit).padStart(3, '0')
        val posteSegmento = poste.filter(Char::isDigit).padStart(3, '0')
        val metrosSegmento = metros.filter(Char::isDigit).padStart(2, '0')
        val localizacion = puebloSegmento + calleSegmento + posteSegmento + metrosSegmento
        return localizacion.toLongOrNull()
    }

    private fun registrarMedidorManual() {
        if (viewModel.uiState.value.isPueblosLoading) return

        val numero = binding.inputRegistroNumero.text?.toString().orEmpty().trim()
        val cliente = binding.inputRegistroCliente.text?.toString().orEmpty().trim()
        val calle = binding.inputRegistroCalle.text?.toString().orEmpty().trim()
        val poste = binding.inputRegistroPoste.text?.toString().orEmpty().trim()
        val metros = binding.inputRegistroMetros.text?.toString().orEmpty().trim()
        actualizarLocalizacionSugerida()
        val localizacionTexto = binding.inputRegistroLocalizacion.text?.toString()?.trim().orEmpty()
        val subregionDisplay = binding.inputRegistroSubregion.text?.toString()?.trim().orEmpty()
        val puebloDisplay = binding.inputRegistroPueblo.text?.toString()?.trim().orEmpty()

        var hasError = false
        if (numero.isEmpty()) {
            binding.tilRegistroNumero.error = getString(R.string.medidor_registro_requerido_numero)
            hasError = true
        } else {
            binding.tilRegistroNumero.error = null
        }

        val subregionOption = subregionDisplayToOption[subregionDisplay]
        if (subregionOption == null) {
            binding.tilRegistroSubregion.error = getString(R.string.medidor_registro_subregion_requerida)
            hasError = true
        } else {
            binding.tilRegistroSubregion.error = null
        }

        val puebloOption = puebloDisplayToOption[puebloDisplay]
        if (puebloOption == null) {
            binding.tilRegistroPueblo.error = getString(R.string.medidor_registro_pueblo_requerido)
            hasError = true
        } else {
            binding.tilRegistroPueblo.error = null
        }

        if (cliente.isEmpty()) {
            binding.tilRegistroCliente.error = getString(R.string.medidor_registro_cliente_requerido)
            hasError = true
        } else {
            binding.tilRegistroCliente.error = null
        }

        if (calle.isEmpty()) {
            binding.tilRegistroCalle.error = getString(R.string.medidor_registro_calle_requerida)
            hasError = true
        } else {
            binding.tilRegistroCalle.error = null
        }

        if (poste.isEmpty()) {
            binding.tilRegistroPoste.error = getString(R.string.medidor_registro_poste_requerido)
            hasError = true
        } else {
            binding.tilRegistroPoste.error = null
        }

        if (metros.isEmpty()) {
            binding.tilRegistroMetros.error = getString(R.string.medidor_registro_metros_requeridos)
            hasError = true
        } else {
            binding.tilRegistroMetros.error = null
        }

        val localizacion = localizacionTexto.toLongOrNull()
        if (localizacion == null) {
            binding.tilRegistroLocalizacion.error = getString(R.string.medidor_registro_localizacion_obligatoria)
            hasError = true
        } else {
            binding.tilRegistroLocalizacion.error = null
        }

        if (hasError) return

        subregionOption?.let { viewModel.seleccionarSubregionParaRegistro(it) }
        puebloOption?.let { viewModel.seleccionarPuebloParaRegistro(it) }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.medidor_registro_confirm_title)
            .setMessage(R.string.medidor_registro_confirm_message)
            .setPositiveButton(R.string.medidor_registro_confirm_positive) { _, _ ->
                if (localizacion != null) {
                    viewModel.registrarMedidorManual(
                        numero = numero,
                        cliente = cliente,
                        localizacion = localizacion,
                        calle = calle,
                        poste = poste,
                        metros = metros
                    )
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun limpiarFormularioManual() {
        binding.tilRegistroNumero.error = null
        binding.tilRegistroLocalizacion.error = null
        binding.tilRegistroCliente.error = null
        binding.tilRegistroCalle.error = null
        binding.tilRegistroPoste.error = null
        binding.tilRegistroMetros.error = null
        binding.tilRegistroPueblo.error = null
        binding.tilRegistroSubregion.error = null
        binding.inputRegistroNumero.setText("")
        binding.inputRegistroCliente.setText("")
        binding.inputRegistroLocalizacion.setText("")
        binding.inputRegistroCalle.setText("")
        binding.inputRegistroPoste.setText("")
        binding.inputRegistroMetros.setText("")
        binding.inputRegistroPueblo.setText("", false)
        viewModel.limpiarSeleccionPueblo()
        val subregionSeleccionada = viewModel.uiState.value.selectedSubregionDisplay.orEmpty()
        if (subregionSeleccionada.isNotEmpty()) {
            binding.inputRegistroSubregion.setText(subregionSeleccionada, false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
