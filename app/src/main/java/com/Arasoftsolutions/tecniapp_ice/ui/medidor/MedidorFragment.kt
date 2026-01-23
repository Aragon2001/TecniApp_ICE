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
import com.Arasoftsolutions.tecniapp_ice.databinding.BottomsheetMedidorRegistroBinding
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentMedidorBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

class MedidorFragment : Fragment() {

    private var _binding: FragmentMedidorBinding? = null
    private val binding get() = _binding!!
    private var registroDialog: BottomSheetDialog? = null
    private var registroBinding: BottomsheetMedidorRegistroBinding? = null

    private val viewModel: MedidorViewModel by viewModels()
    private lateinit var subregionAdapter: ArrayAdapter<String>
    private lateinit var puebloAdapter: ArrayAdapter<String>
    private var currentSubregionDisplays: List<String> = emptyList()
    private var currentPuebloDisplays: List<String> = emptyList()
    private val subregionDisplayToOption = mutableMapOf<String, SubregionOption>()
    private val puebloDisplayToOption = mutableMapOf<String, PuebloOption>()

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
            registroBinding?.let { registro ->
                registro.tilRegistroNumero.error = null
                registro.tilRegistroLocalizacion.error = null
                registro.tilRegistroCliente.error = null
                registro.tilRegistroCalle.error = null
                registro.tilRegistroPoste.error = null
                registro.tilRegistroMetros.error = null
                registro.tilRegistroPueblo.error = null
                registro.tilRegistroSubregion.error = null
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
                    binding.btnMostrarRegistro.isEnabled = !estado.isRegistering

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
                    actualizarPuebloAdapter(estado.puebloOptions)

                    estado.notFoundNumero?.let { numero ->
                        binding.textNoEncontradoDescription.text = getString(
                            R.string.medidor_no_encontrado_descripcion,
                            numero
                        )
                        registroBinding?.let { registro ->
                            if (estado.showManualForm && registro.inputRegistroNumero.text.isNullOrBlank()) {
                                registro.inputRegistroNumero.setText(numero)
                                registro.inputRegistroNumero.setSelection(numero.length)
                            }
                        }
                    } ?: run {
                        binding.textNoEncontradoDescription.text = getString(R.string.medidor_no_encontrado_descripcion_vacia)
                        if (!estado.showManualForm) {
                            limpiarFormularioManual()
                        }
                    }

                    if (estado.showManualForm) {
                        showRegistroBottomSheet()
                        registroBinding?.let { updateRegistroContent(it, estado) }
                        actualizarLocalizacionSugerida()
                    } else {
                        dismissRegistroBottomSheet()
                    }

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
        val registro = registroBinding ?: return
        val calle = registro.inputRegistroCalle.text?.toString().orEmpty()
        val poste = registro.inputRegistroPoste.text?.toString().orEmpty()
        val metros = registro.inputRegistroMetros.text?.toString().orEmpty()
        val localizacion = generarLocalizacion(puebloCodigo, calle, poste, metros)
        val nuevoTexto = localizacion?.toString().orEmpty()
        if (registro.inputRegistroLocalizacion.text?.toString() != nuevoTexto) {
            registro.inputRegistroLocalizacion.setText(nuevoTexto)
        }
        if (localizacion != null) {
            registro.tilRegistroLocalizacion.error = null
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
        val registro = registroBinding ?: return

        val numero = registro.inputRegistroNumero.text?.toString().orEmpty().trim()
        val cliente = registro.inputRegistroCliente.text?.toString().orEmpty().trim()
        val calle = registro.inputRegistroCalle.text?.toString().orEmpty().trim()
        val poste = registro.inputRegistroPoste.text?.toString().orEmpty().trim()
        val metros = registro.inputRegistroMetros.text?.toString().orEmpty().trim()
        actualizarLocalizacionSugerida()
        val localizacionTexto = registro.inputRegistroLocalizacion.text?.toString()?.trim().orEmpty()
        val subregionDisplay = registro.inputRegistroSubregion.text?.toString()?.trim().orEmpty()
        val puebloDisplay = registro.inputRegistroPueblo.text?.toString()?.trim().orEmpty()

        var hasError = false
        if (numero.isEmpty()) {
            registro.tilRegistroNumero.error = getString(R.string.medidor_registro_requerido_numero)
            hasError = true
        } else {
            registro.tilRegistroNumero.error = null
        }

        val subregionOption = subregionDisplayToOption[subregionDisplay]
        if (subregionOption == null) {
            registro.tilRegistroSubregion.error = getString(R.string.medidor_registro_subregion_requerida)
            hasError = true
        } else {
            registro.tilRegistroSubregion.error = null
        }

        val puebloOption = puebloDisplayToOption[puebloDisplay]
        if (puebloOption == null) {
            registro.tilRegistroPueblo.error = getString(R.string.medidor_registro_pueblo_requerido)
            hasError = true
        } else {
            registro.tilRegistroPueblo.error = null
        }

        if (cliente.isEmpty()) {
            registro.tilRegistroCliente.error = getString(R.string.medidor_registro_cliente_requerido)
            hasError = true
        } else {
            registro.tilRegistroCliente.error = null
        }

        if (calle.isEmpty()) {
            registro.tilRegistroCalle.error = getString(R.string.medidor_registro_calle_requerida)
            hasError = true
        } else {
            registro.tilRegistroCalle.error = null
        }

        if (poste.isEmpty()) {
            registro.tilRegistroPoste.error = getString(R.string.medidor_registro_poste_requerido)
            hasError = true
        } else {
            registro.tilRegistroPoste.error = null
        }

        if (metros.isEmpty()) {
            registro.tilRegistroMetros.error = getString(R.string.medidor_registro_metros_requeridos)
            hasError = true
        } else {
            registro.tilRegistroMetros.error = null
        }

        val localizacion = localizacionTexto.toLongOrNull()
        if (localizacion == null) {
            registro.tilRegistroLocalizacion.error = getString(R.string.medidor_registro_localizacion_obligatoria)
            hasError = true
        } else {
            registro.tilRegistroLocalizacion.error = null
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
        val registro = registroBinding ?: return
        registro.tilRegistroNumero.error = null
        registro.tilRegistroLocalizacion.error = null
        registro.tilRegistroCliente.error = null
        registro.tilRegistroCalle.error = null
        registro.tilRegistroPoste.error = null
        registro.tilRegistroMetros.error = null
        registro.tilRegistroPueblo.error = null
        registro.tilRegistroSubregion.error = null
        registro.inputRegistroNumero.setText("")
        registro.inputRegistroCliente.setText("")
        registro.inputRegistroLocalizacion.setText("")
        registro.inputRegistroCalle.setText("")
        registro.inputRegistroPoste.setText("")
        registro.inputRegistroMetros.setText("")
        registro.inputRegistroPueblo.setText("", false)
        viewModel.limpiarSeleccionPueblo()
        val subregionSeleccionada = viewModel.uiState.value.selectedSubregionDisplay.orEmpty()
        if (subregionSeleccionada.isNotEmpty()) {
            registro.inputRegistroSubregion.setText(subregionSeleccionada, false)
        }
    }

    private fun showRegistroBottomSheet() {
        if (registroDialog != null) {
            registroBinding?.let { updateRegistroUi(it, viewModel.uiState.value) }
            return
        }
        val dialog = BottomSheetDialog(requireContext())
        val sheetBinding = BottomsheetMedidorRegistroBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)
        dialog.setOnDismissListener {
            registroBinding = null
            registroDialog = null
            if (viewModel.uiState.value.showManualForm) {
                viewModel.cancelarRegistroManual()
            }
        }
        registroDialog = dialog
        registroBinding = sheetBinding
        configureRegistroSheet(sheetBinding)
        updateRegistroUi(sheetBinding, viewModel.uiState.value)
        dialog.show()
    }

    private fun dismissRegistroBottomSheet() {
        registroDialog?.dismiss()
        registroDialog = null
        registroBinding = null
    }

    private fun configureRegistroSheet(registro: BottomsheetMedidorRegistroBinding) {
        registro.btnCancelarRegistro.setOnClickListener {
            viewModel.cancelarRegistroManual()
            limpiarFormularioManual()
            dismissRegistroBottomSheet()
        }
        registro.btnGuardarManual.setOnClickListener { registrarMedidorManual() }

        registro.inputRegistroNumero.doAfterTextChanged {
            registro.tilRegistroNumero.error = null
        }
        registro.inputRegistroCliente.doAfterTextChanged {
            registro.tilRegistroCliente.error = null
        }
        registro.inputRegistroCalle.doAfterTextChanged {
            registro.tilRegistroCalle.error = null
            actualizarLocalizacionSugerida()
        }
        registro.inputRegistroPoste.doAfterTextChanged {
            registro.tilRegistroPoste.error = null
            actualizarLocalizacionSugerida()
        }
        registro.inputRegistroMetros.doAfterTextChanged {
            registro.tilRegistroMetros.error = null
            actualizarLocalizacionSugerida()
        }
        registro.inputRegistroLocalizacion.doAfterTextChanged {
            registro.tilRegistroLocalizacion.error = null
        }
        registro.inputRegistroSubregion.apply {
            keyListener = null
            isFocusable = true
            isFocusableInTouchMode = true
            setAdapter(subregionAdapter)
            setOnClickListener { showDropDown() }
            setOnFocusChangeListener { _, hasFocus -> if (hasFocus) showDropDown() }
            doAfterTextChanged {
                registro.tilRegistroSubregion.error = null
            }
            setOnItemClickListener { _, _, position, _ ->
                val display = subregionAdapter.getItem(position) ?: return@setOnItemClickListener
                subregionDisplayToOption[display]?.let { option ->
                    viewModel.seleccionarSubregionParaRegistro(option)
                    registro.tilRegistroSubregion.error = null
                }
            }
        }
        registro.inputRegistroPueblo.apply {
            keyListener = null
            isFocusable = true
            isFocusableInTouchMode = true
            setAdapter(puebloAdapter)
            setOnClickListener { showDropDown() }
            setOnFocusChangeListener { _, hasFocus -> if (hasFocus) showDropDown() }
            doAfterTextChanged {
                registro.tilRegistroPueblo.error = null
            }
            setOnItemClickListener { _, _, position, _ ->
                val display = puebloAdapter.getItem(position) ?: return@setOnItemClickListener
                puebloDisplayToOption[display]?.let { option ->
                    viewModel.seleccionarPuebloParaRegistro(option)
                    registro.tilRegistroPueblo.error = null
                    actualizarLocalizacionSugerida()
                }
            }
        }
    }

    private fun updateRegistroUi(registro: BottomsheetMedidorRegistroBinding, estado: MedidorUiState) {
        registro.btnGuardarManual.isEnabled = !estado.isRegistering && !estado.isPueblosLoading
        registro.progressRegistro.isVisible = estado.isRegistering
        registro.btnCancelarRegistro.isEnabled = !estado.isRegistering
    }

    private fun updateRegistroContent(registro: BottomsheetMedidorRegistroBinding, estado: MedidorUiState) {
        estado.notFoundNumero?.let { numero ->
            if (estado.showManualForm && registro.inputRegistroNumero.text.isNullOrBlank()) {
                registro.inputRegistroNumero.setText(numero)
                registro.inputRegistroNumero.setSelection(numero.length)
            }
        }
        val subregionDisplay = estado.selectedSubregionDisplay.orEmpty()
        if (registro.inputRegistroSubregion.text?.toString() != subregionDisplay) {
            registro.inputRegistroSubregion.setText(subregionDisplay, false)
        }
        val subregionDisponible = estado.subregionOptions.isNotEmpty()
        registro.tilRegistroSubregion.isEnabled = subregionDisponible
        registro.inputRegistroSubregion.isEnabled = subregionDisponible
        registro.tilRegistroSubregion.helperText = when {
            estado.showManualForm && !subregionDisponible -> getString(R.string.medidor_registro_subregion_cargando)
            else -> null
        }

        val puebloDisplay = estado.selectedPuebloDisplay.orEmpty()
        if (registro.inputRegistroPueblo.text?.toString() != puebloDisplay) {
            registro.inputRegistroPueblo.setText(puebloDisplay, false)
        }
        val pueblosDisponibles = estado.puebloOptions.isNotEmpty()
        registro.inputRegistroPueblo.isEnabled = pueblosDisponibles && !estado.isPueblosLoading
        registro.tilRegistroPueblo.isEnabled = pueblosDisponibles || estado.isPueblosLoading
        registro.tilRegistroPueblo.helperText = when {
            estado.showManualForm && estado.isPueblosLoading -> getString(R.string.medidor_registro_pueblo_cargando)
            else -> null
        }

        updateRegistroUi(registro, estado)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dismissRegistroBottomSheet()
        _binding = null
    }
}
