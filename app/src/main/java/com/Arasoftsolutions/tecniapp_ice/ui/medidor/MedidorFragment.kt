package com.Arasoftsolutions.tecniapp_ice.ui.medidor

import android.animation.ObjectAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Toast
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
import com.Arasoftsolutions.tecniapp_ice.databinding.DialogMedidorConsultaNubeBinding
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentMedidorBinding
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    private var wasManualVisible = false
    private var consultaNubeDialog: AlertDialog? = null
    private var wasManualInfoShown = false

    /** Controla si la tarjeta de resultado ya estaba visible (para no re-animar) */
    private var resultadoYaVisible = false

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
        iniciarAnimacionHero()
        observarEstado()
    }

    // ─────────────────────────────────────────────────────────────
    //  UI Principal
    // ─────────────────────────────────────────────────────────────

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

        binding.btnCopiarInline.setOnClickListener { copiarInformacion() }
        binding.btnCompartirInline.setOnClickListener { compartirInformacion() }

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

    // ─────────────────────────────────────────────────────────────
    //  Animación del ícono hero (pulso suave e infinito)
    // ─────────────────────────────────────────────────────────────

    private fun iniciarAnimacionHero() {
        val iconContainer = binding.heroIconContainer
        val scaleX = ObjectAnimator.ofFloat(iconContainer, "scaleX", 1f, 1.06f, 1f).apply {
            duration = 2400
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val scaleY = ObjectAnimator.ofFloat(iconContainer, "scaleY", 1f, 1.06f, 1f).apply {
            duration = 2400
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        scaleX.start()
        scaleY.start()
    }

    // ─────────────────────────────────────────────────────────────
    //  Animación de entrada para la tarjeta de resultado
    // ─────────────────────────────────────────────────────────────

    private fun mostrarResultadoConAnimacion() {
        if (resultadoYaVisible) return
        resultadoYaVisible = true
        binding.cardResultado.apply {
            alpha = 0f
            translationY = 48f
            isVisible = true
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(350)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
    }

    private fun ocultarResultado() {
        resultadoYaVisible = false
        binding.cardResultado.isVisible = false
    }

    // ─────────────────────────────────────────────────────────────
    //  Observar estado del ViewModel
    // ─────────────────────────────────────────────────────────────

    private fun observarEstado() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { estado ->
                    binding.btnConsultar.isEnabled = !estado.isLoading && !estado.isRegistering
                    binding.progressBusqueda.isVisible = estado.isLoading
                    binding.btnMostrarRegistro.isEnabled = !estado.isRegistering

                    val hayMedidor = estado.medidor != null
                    if (hayMedidor) {
                        mostrarResultadoConAnimacion()
                    } else {
                        ocultarResultado()
                    }

                    binding.cardNoEncontrado.isVisible = estado.notFoundNumero != null && !estado.showManualForm

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
                        mostrarAvisoRegistroManual()
                    } else {
                        dismissRegistroBottomSheet()
                    }

                    wasManualVisible = estado.showManualForm

                    if (estado.showCloudLookupDialog) {
                        mostrarDialogoConsultaNube(estado.cloudLookupNumero.orEmpty())
                    } else {
                        ocultarDialogoConsultaNube()
                    }

                    if (estado.showNotFoundDialog && estado.notFoundNumero != null) {
                        viewModel.onNotFoundDialogMostrado()
                        if (estado.notFoundOffline) {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle(R.string.medidor_dialog_offline_title)
                                .setMessage(getString(R.string.medidor_dialog_offline_message, estado.notFoundNumero))
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                        } else {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle(R.string.medidor_no_registrado_dialog_title)
                                .setMessage(getString(R.string.medidor_no_registrado_dialog_message, estado.notFoundNumero))
                                .setPositiveButton(R.string.medidor_no_registrado_dialog_positive) { _, _ ->
                                    viewModel.habilitarRegistroManual()
                                }
                                .setNegativeButton(R.string.medidor_no_registrado_dialog_negative, null)
                                .show()
                        }
                    }

                    estado.medidor?.let { medidor ->
                        val placeholder = getString(R.string.profile_summary_placeholder)
                        binding.chipNumero.text = getString(
                            R.string.medidor_chip_numero_format,
                            medidor.medidorNumber.ifBlank { placeholder }
                        )
                        binding.chipCliente.text = getString(
                            R.string.medidor_chip_cliente_format,
                            medidor.cliente.orEmpty().ifBlank { placeholder }
                        )
                        binding.chipCalle.text = getString(
                            R.string.medidor_chip_calle_format,
                            medidor.calle.orEmpty().ifBlank { placeholder }
                        )
                        binding.chipPoste.text = getString(
                            R.string.medidor_chip_poste_format,
                            medidor.poste.orEmpty().ifBlank { placeholder }
                        )
                        binding.chipMetros.text = getString(
                            R.string.medidor_chip_metros_format,
                            medidor.metros.orEmpty().ifBlank { placeholder }
                        )
                        val subregionDisplay = estado.subregionNombre
                            ?: medidor.subregion.orEmpty().ifBlank { placeholder }
                        binding.valueSubregionHeader.text = subregionDisplay
                        binding.chipLocalizacion.text = getString(
                            R.string.medidor_chip_localizacion_format,
                            medidor.localizacion?.toString() ?: placeholder
                        )

                        viewLifecycleOwner.lifecycleScope.launch {
                            val descripcionPueblo = viewModel.obtenerDescripcionPueblo(medidor.pueblo)
                            val pueblo = descripcionPueblo ?: medidor.pueblo.orEmpty()
                            binding.chipPueblo.text = getString(
                                R.string.medidor_chip_pueblo_format,
                                pueblo.ifBlank { placeholder }
                            )
                        }
                    } ?: limpiarCampos()
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Búsqueda
    // ─────────────────────────────────────────────────────────────

    private fun consultarMedidor() {
        val numero = binding.inputMedidor.text?.toString().orEmpty().trim()
        if (numero.isEmpty()) {
            binding.layoutMedidor.error = getString(R.string.medidor_error_numero)
            return
        }
        binding.layoutMedidor.error = null
        // Reiniciar animación de entrada para el nuevo resultado
        resultadoYaVisible = false
        viewModel.buscar(numero)
    }

    // ─────────────────────────────────────────────────────────────
    //  Copiar / Compartir
    // ─────────────────────────────────────────────────────────────

    private fun construirTextoMedidor(): String? {
        val medidor = viewModel.obtenerMedidorActual() ?: return null
        val estado = viewModel.uiState.value
        val subregion = estado.subregionNombre
            ?: medidor.subregion.orEmpty().ifBlank { "-" }
        val pueblo = binding.chipPueblo.text.toString().trim().ifBlank {
            medidor.pueblo.orEmpty().ifBlank { "-" }
        }

        return buildString {
            appendLine("DATOS DEL MEDIDOR")
            appendLine()
            appendLine("Medidor      : ${medidor.medidorNumber.ifBlank { "-" }}")
            appendLine("Cliente      : ${medidor.cliente.orEmpty().ifBlank { "-" }}")
            appendLine("Localización : ${medidor.localizacion?.toString() ?: "-"}")
            appendLine("Calle        : ${medidor.calle.orEmpty().ifBlank { "-" }}")
            appendLine("Poste        : ${medidor.poste.orEmpty().ifBlank { "-" }}")
            appendLine("Metros       : ${medidor.metros.orEmpty().ifBlank { "-" }}")
            appendLine("Pueblo       : $pueblo")
            appendLine("Subregión    : $subregion")
            appendLine()
            append("Generado desde TecniApp ICE")
        }
    }

    private fun copiarInformacion() {
        val texto = construirTextoMedidor() ?: return
        val clipboard = ContextCompat.getSystemService(requireContext(), ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("Medidor", texto))
        Toast.makeText(requireContext(), R.string.medidor_copiado_exito, Toast.LENGTH_SHORT).show()

        // Feedback visual en el botón
        binding.btnCopiarInline.animate().scaleX(0.93f).scaleY(0.93f).setDuration(80)
            .withEndAction {
                binding.btnCopiarInline.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }.start()
    }

    private fun compartirInformacion() {
        val texto = construirTextoMedidor() ?: return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, texto)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.medidor_compartir)))
    }

    // ─────────────────────────────────────────────────────────────
    //  Adaptadores de subregión / pueblo
    // ─────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────
    //  Bottom Sheet de registro manual
    // ─────────────────────────────────────────────────────────────

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

        registro.inputRegistroNumero.doAfterTextChanged { registro.tilRegistroNumero.error = null }
        registro.inputRegistroCliente.doAfterTextChanged { registro.tilRegistroCliente.error = null }
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
        registro.inputRegistroLocalizacion.doAfterTextChanged { registro.tilRegistroLocalizacion.error = null }

        registro.inputRegistroSubregion.apply {
            keyListener = null
            isFocusable = true
            isFocusableInTouchMode = true
            setAdapter(subregionAdapter)
            setOnClickListener { showDropDown() }
            setOnFocusChangeListener { _, hasFocus -> if (hasFocus) showDropDown() }
            doAfterTextChanged { registro.tilRegistroSubregion.error = null }
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
            doAfterTextChanged { registro.tilRegistroPueblo.error = null }
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

    // ─────────────────────────────────────────────────────────────
    //  Cálculo de localización sugerida
    // ─────────────────────────────────────────────────────────────

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
        if (localizacion != null) registro.tilRegistroLocalizacion.error = null
    }

    private fun generarLocalizacion(
        puebloCodigo: String?,
        calle: String,
        poste: String,
        metros: String,
    ): Long? {
        val codigo = puebloCodigo?.takeIf { it.isNotBlank() } ?: return null
        if (calle.isBlank() || poste.isBlank() || metros.isBlank()) return null
        val puebloSegmento = codigo.filter(Char::isDigit).takeIf { it.isNotEmpty() } ?: return null
        val calleSegmento = calle.filter(Char::isDigit).padStart(3, '0')
        val posteSegmento = poste.filter(Char::isDigit).padStart(3, '0')
        val metrosSegmento = metros.filter(Char::isDigit).padStart(2, '0')
        return (puebloSegmento + calleSegmento + posteSegmento + metrosSegmento).toLongOrNull()
    }

    // ─────────────────────────────────────────────────────────────
    //  Registrar medidor manual
    // ─────────────────────────────────────────────────────────────

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
        if (numero.isEmpty()) { registro.tilRegistroNumero.error = getString(R.string.medidor_registro_requerido_numero); hasError = true }
        else registro.tilRegistroNumero.error = null

        val subregionOption = subregionDisplayToOption[subregionDisplay]
        if (subregionOption == null) { registro.tilRegistroSubregion.error = getString(R.string.medidor_registro_subregion_requerida); hasError = true }
        else registro.tilRegistroSubregion.error = null

        val puebloOption = puebloDisplayToOption[puebloDisplay]
        if (puebloOption == null) { registro.tilRegistroPueblo.error = getString(R.string.medidor_registro_pueblo_requerido); hasError = true }
        else registro.tilRegistroPueblo.error = null

        if (cliente.isEmpty()) { registro.tilRegistroCliente.error = getString(R.string.medidor_registro_cliente_requerido); hasError = true }
        else registro.tilRegistroCliente.error = null

        if (calle.isEmpty()) { registro.tilRegistroCalle.error = getString(R.string.medidor_registro_calle_requerida); hasError = true }
        else registro.tilRegistroCalle.error = null

        if (poste.isEmpty()) { registro.tilRegistroPoste.error = getString(R.string.medidor_registro_poste_requerido); hasError = true }
        else registro.tilRegistroPoste.error = null

        if (metros.isEmpty()) { registro.tilRegistroMetros.error = getString(R.string.medidor_registro_metros_requeridos); hasError = true }
        else registro.tilRegistroMetros.error = null

        val localizacion = localizacionTexto.toLongOrNull()
        if (localizacion == null) { registro.tilRegistroLocalizacion.error = getString(R.string.medidor_registro_localizacion_obligatoria); hasError = true }
        else registro.tilRegistroLocalizacion.error = null

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

    // ─────────────────────────────────────────────────────────────
    //  Utilidades de limpieza
    // ─────────────────────────────────────────────────────────────

    private fun limpiarCampos() {
        binding.chipNumero.text = ""
        binding.chipCliente.text = ""
        binding.chipCalle.text = ""
        binding.chipPoste.text = ""
        binding.chipMetros.text = ""
        binding.chipPueblo.text = ""
        binding.chipLocalizacion.text = ""
        binding.valueSubregionHeader.text = ""
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

    // ─────────────────────────────────────────────────────────────
    //  Diálogo de consulta a la nube
    // ─────────────────────────────────────────────────────────────

    private fun mostrarDialogoConsultaNube(numero: String) {
        if (consultaNubeDialog?.isShowing == true) return
        val bindingDialog = DialogMedidorConsultaNubeBinding.inflate(layoutInflater)
        bindingDialog.textConsultaMensaje.text = getString(R.string.medidor_dialog_consulta_nube_message, numero)
        consultaNubeDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(bindingDialog.root)
            .setCancelable(false)
            .show()
    }

    private fun ocultarDialogoConsultaNube() {
        consultaNubeDialog?.dismiss()
        consultaNubeDialog = null
    }

    private fun mostrarAvisoRegistroManual() {
        if (wasManualInfoShown) return
        wasManualInfoShown = true
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.medidor_registro_aviso_title)
            .setMessage(R.string.medidor_registro_aviso_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ─────────────────────────────────────────────────────────────
    //  Ciclo de vida
    // ─────────────────────────────────────────────────────────────

    override fun onDestroyView() {
        super.onDestroyView()
        dismissRegistroBottomSheet()
        ocultarDialogoConsultaNube()
        _binding = null
    }
}
