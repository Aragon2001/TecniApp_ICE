package com.Arasoftsolutions.tecniapp_ice.registro

import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.RegistroActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class Paso3Fragment : Fragment() {

    private lateinit var viewModel: RegistroViewModel
    private var _etFirstName: TextInputEditText? = null
    private var _etLastName: TextInputEditText? = null
    private var _etLastName2: TextInputEditText? = null
    private var _etID: TextInputEditText? = null
    private var _btnContinueToStep4: MaterialButton? = null
    private var _chipCedula: Chip? = null
    private var _chipDimex: Chip? = null
    private var _chipGroupTipoId: ChipGroup? = null

    private var _tvFirstNameError: TextView? = null
    private var _tvLastNameError: TextView? = null
    private var _tvLastNameError2: TextView? = null
    private var _tvIDError: TextView? = null

    private val etFirstName get() = requireNotNull(_etFirstName)
    private val etLastName get() = requireNotNull(_etLastName)
    private val etLastName2 get() = requireNotNull(_etLastName2)
    private val etID get() = requireNotNull(_etID)
    private val btnContinueToStep4 get() = requireNotNull(_btnContinueToStep4)
    private val chipCedula get() = requireNotNull(_chipCedula)
    private val chipDimex get() = requireNotNull(_chipDimex)
    private val chipGroupTipoId get() = requireNotNull(_chipGroupTipoId)

    private val tvFirstNameError get() = requireNotNull(_tvFirstNameError)
    private val tvLastNameError get() = requireNotNull(_tvLastNameError)
    private val tvLastNameError2 get() = requireNotNull(_tvLastNameError2)
    private val tvIDError get() = requireNotNull(_tvIDError)

    private val CEDULA_DIGITS = 9
    private val DIMEX_DIGITS = 12

    private fun isCedulaSelected(): Boolean = chipCedula.isChecked

    private data class PersonalData(
        val nombre: String,
        val primerApellido: String,
        val segundoApellido: String
    )

    // RTDB users (mismo host que Paso1/2/4)
    private val usersDb: DatabaseReference by lazy {
        FirebaseDatabase.getInstance("https://tecniapp-ice-user.firebaseio.com").reference
    }

    private val personalDb: DatabaseReference by lazy {
        FirebaseDatabase.getInstance("https://tecniapp-ice-personal.firebaseio.com").reference
    }

    // Solo dígitos, 9 posiciones (clave de índice)
    private fun cedulaKey(raw: String): String = raw.filter { it.isDigit() }

    private var personalData: PersonalData? = null
    private var lastLookupCedula: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_paso_3, container, false)

        setNavigationListeners(view)

        viewModel = ViewModelProvider(requireActivity())[RegistroViewModel::class.java]

        _etFirstName = view.findViewById(R.id.etFirstName)
        _etLastName = view.findViewById(R.id.etLastName)
        _etLastName2 = view.findViewById(R.id.etLastName2)
        _etID = view.findViewById(R.id.etID)
        _btnContinueToStep4 = view.findViewById(R.id.btnContinueToStep4)
        _chipCedula = view.findViewById(R.id.chipCedula)
        _chipDimex = view.findViewById(R.id.chipDimex)
        _chipGroupTipoId = view.findViewById(R.id.chipGroupTipoId)

        _tvFirstNameError = view.findViewById(R.id.tvFirstNameError)
        _tvLastNameError = view.findViewById(R.id.tvLastNameError)
        _tvLastNameError2 = view.findViewById(R.id.tvLastNameError2)
        _tvIDError = view.findViewById(R.id.tvIDError)

        applyTipoIdSelection()
        chipGroupTipoId.setOnCheckedStateChangeListener { _, _ ->
            applyTipoIdSelection()
        }

        lockNameFields()

        etID.doAfterTextChanged { text ->
            val digits = cedulaKey(text?.toString().orEmpty())
            val expectedLen = if (isCedulaSelected()) CEDULA_DIGITS else DIMEX_DIGITS
            when {
                digits.length == expectedLen -> {
                    lockNameFields()
                    lookupPersonalData(digits)
                }
                else -> {
                    personalData = null
                    lastLookupCedula = null
                    clearNameFields()
                    lockNameFields()
                }
            }
        }

        btnContinueToStep4.setOnClickListener { onContinue() }

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _btnContinueToStep4?.setOnClickListener(null)
        _chipGroupTipoId?.setOnCheckedStateChangeListener(null)
        _etFirstName = null
        _etLastName = null
        _etLastName2 = null
        _etID = null
        _btnContinueToStep4 = null
        _chipCedula = null
        _chipDimex = null
        _chipGroupTipoId = null
        _tvFirstNameError = null
        _tvLastNameError = null
        _tvLastNameError2 = null
        _tvIDError = null
    }

    private fun applyTipoIdSelection() {
        val isCedula = isCedulaSelected()
        val maxLen = if (isCedula) CEDULA_DIGITS else DIMEX_DIGITS
        etID.filters = arrayOf(InputFilter.LengthFilter(maxLen))
        etID.hint = if (isCedula) getString(R.string.registro_paso3_hint_cedula) else getString(R.string.registro_paso3_hint_dimex)
        etID.setText("")
        personalData = null
        lastLookupCedula = null
        clearNameFields()
        lockNameFields()
        clearErrors()
    }

    private fun setNavigationListeners(view: View) {
        view.findViewById<ImageView>(R.id.backArrow).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun onContinue() {
        clearErrors()

        val cedulaRaw = etID.text?.toString()?.trim().orEmpty()
        val cedKey    = cedulaKey(cedulaRaw)
        val expectedLen = if (isCedulaSelected()) CEDULA_DIGITS else DIMEX_DIGITS

        // Validación local
        if (cedKey.isBlank()) return showFieldError(tvIDError, getString(R.string.registro_paso3_error_vacio))
        if (!cedKey.all { it.isDigit() }) return showFieldError(tvIDError, getString(R.string.registro_paso3_error_solo_numeros))
        if (cedKey.length != expectedLen) {
            val msg = if (isCedulaSelected()) getString(R.string.registro_paso3_error_cedula_9) else getString(R.string.registro_paso3_error_dimex_12)
            return showFieldError(tvIDError, msg)
        }

        if (personalData == null || lastLookupCedula != cedKey) {
            val tipo = if (isCedulaSelected()) "cédula" else "DIMEX"
            showFieldError(tvIDError, "Primero valida el $tipo para completar los datos.")
            return
        }

        // Verificar unicidad de cédula usando /idcards/{cedulaKey} (patrón PRO)
        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val exists = usersDb.child("idcards").child(cedKey).get().await().exists()
                if (exists) {
                    showFieldError(tvIDError, "Esta cédula ya está registrada.")
                    return@launch
                }

                // OK → guarda en VM y avanza a Paso 4
                val data = personalData
                if (data == null) {
                    showFieldError(tvIDError, "No se encontraron datos del personal para esa cédula.")
                    return@launch
                }
                viewModel.setDatosTecnico(data.nombre, data.primerApellido, data.segundoApellido, cedKey)
                (activity as? RegistroActivity)?.goToNextStep(3)

            } catch (_: Throwable) {
                Toast.makeText(requireContext(), "No se pudo validar la cédula. Intenta de nuevo.", Toast.LENGTH_SHORT).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(b: Boolean) {
        btnContinueToStep4.isEnabled = !b
    }

    private fun showFieldError(tv: TextView, msg: String) {
        tv.text = msg
        tv.visibility = View.VISIBLE
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun clearErrors() {
        tvFirstNameError.visibility = View.GONE
        tvLastNameError.visibility = View.GONE
        tvLastNameError2.visibility = View.GONE
        tvIDError.visibility = View.GONE
    }

    private fun lockNameFields() {
        etFirstName.isEnabled = false
        etLastName.isEnabled = false
        etLastName2.isEnabled = false
        etFirstName.hint = getString(R.string.registro_paso3_hint_auto)
        etLastName.hint = getString(R.string.registro_paso3_hint_auto)
        etLastName2.hint = getString(R.string.registro_paso3_hint_auto)
    }

    private fun unlockNameFields() {
        etFirstName.isEnabled = true
        etLastName.isEnabled = true
        etLastName2.isEnabled = true
        etFirstName.hint = getString(R.string.registro_paso3_hint_nombre_manual)
        etLastName.hint = getString(R.string.registro_paso3_hint_apellido1_manual)
        etLastName2.hint = getString(R.string.registro_paso3_hint_apellido2_manual)
    }

    private fun clearNameFields() {
        etFirstName.setText("")
        etLastName.setText("")
        etLastName2.setText("")
    }

    private fun lookupPersonalData(cedula: String) {
        if (cedula == lastLookupCedula && personalData != null) return
        lastLookupCedula = cedula
        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val root = personalDb.get().await()
                val base = if (root.hasChild("personal")) root.child("personal") else root
                val person = base.child(cedula)
                if (!person.exists()) {
                    personalData = null
                    clearNameFields()
                    showFieldError(tvIDError, "La cédula no existe en el personal.")
                    return@launch
                }

                val parsed = parsePersonalData(person)
                if (parsed == null) {
                    personalData = null
                    clearNameFields()
                    showFieldError(tvIDError, "No se encontraron nombre y apellidos en el personal.")
                    return@launch
                }

                personalData = parsed
                etFirstName.setText(parsed.nombre)
                etLastName.setText(parsed.primerApellido)
                etLastName2.setText(parsed.segundoApellido)
                clearErrors()
            } catch (_: Throwable) {
                personalData = null
                clearNameFields()
                showFieldError(tvIDError, "No se pudo validar la cédula. Intenta de nuevo.")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun parsePersonalData(snapshot: com.google.firebase.database.DataSnapshot): PersonalData? {
        fun childValue(vararg keys: String): String? {
            keys.forEach { key ->
                val value = snapshot.child(key).getValue(String::class.java)?.trim()
                if (!value.isNullOrBlank()) return value
            }
            return null
        }

        var nombre = childValue("nombre", "Nombre")?.trim().orEmpty()
        var primerApellido = childValue("primer_apellido", "primerApellido", "apellido1", "primerapellido").orEmpty()
        var segundoApellido = childValue("segundo_apellido", "segundoApellido", "apellido2", "segundoapellido").orEmpty()
        val apellidos = childValue("apellidos", "Apellidos").orEmpty()

        if (primerApellido.isBlank() && apellidos.isNotBlank()) {
            val parts = apellidos.split(Regex("\\s+")).filter { it.isNotBlank() }
            primerApellido = parts.getOrNull(0).orEmpty()
            segundoApellido = parts.drop(1).joinToString(" ").trim()
        }

        if ((primerApellido.isBlank() || segundoApellido.isBlank()) && nombre.isNotBlank()) {
            val parts = nombre.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (parts.size >= 3) {
                nombre = parts.dropLast(2).joinToString(" ")
                primerApellido = primerApellido.ifBlank { parts[parts.size - 2] }
                segundoApellido = segundoApellido.ifBlank { parts.last() }
            }
        }

        if (nombre.isBlank() || primerApellido.isBlank() || segundoApellido.isBlank()) return null

        return PersonalData(nombre, primerApellido, segundoApellido)
    }
}
