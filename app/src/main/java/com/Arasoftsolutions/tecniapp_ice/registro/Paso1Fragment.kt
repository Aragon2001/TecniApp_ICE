package com.Arasoftsolutions.tecniapp_ice.registro


import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.RegistroActivity
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale
import com.google.firebase.functions.FirebaseFunctions


class Paso1Fragment : Fragment() {

    private lateinit var viewModel: RegistroViewModel
    private val functions by lazy { FirebaseFunctions.getInstance() }
    private val prefs by lazy {
        requireContext().getSharedPreferences("registro_prefs", Context.MODE_PRIVATE)
    }


    // UI
    private var _etPhoneNumber: TextInputEditText? = null
    private var _etEmail: TextInputEditText? = null
    private var _etPassword: TextInputEditText? = null
    private var _etConfirmPassword: TextInputEditText? = null
    private var _tvPhoneError: MaterialTextView? = null
    private var _tvEmailError: MaterialTextView? = null
    private var _tvPasswordError: MaterialTextView? = null
    private var _tvConfirmPasswordError: MaterialTextView? = null
    private var _checkBoxShowPassword: MaterialCheckBox? = null
    private var _btnContinue: MaterialButton? = null

    private val etPhoneNumber get() = requireNotNull(_etPhoneNumber)
    private val etEmail get() = requireNotNull(_etEmail)
    private val etPassword get() = requireNotNull(_etPassword)
    private val etConfirmPassword get() = requireNotNull(_etConfirmPassword)
    private val tvPhoneError get() = requireNotNull(_tvPhoneError)
    private val tvEmailError get() = requireNotNull(_tvEmailError)
    private val tvPasswordError get() = requireNotNull(_tvPasswordError)
    private val tvConfirmPasswordError get() = requireNotNull(_tvConfirmPasswordError)
    private val checkBoxShowPassword get() = requireNotNull(_checkBoxShowPassword)
    private val btnContinue get() = requireNotNull(_btnContinue)

    // ---- Helpers de clave (coinciden con reglas RTDB) ----
    private fun emailKey(email: String) = email.trim().lowercase(Locale.ROOT)
        .replace(".", ",").replace("#","_").replace("$","_").replace("[","_").replace("]","_")

    private fun phoneKey(phone: String) = phone.filter(Char::isDigit)

    // RTDB raíz del proyecto de usuarios
    private val usersDb: DatabaseReference by lazy {
        FirebaseDatabase.getInstance("https://tecniapp-ice-user.firebaseio.com/").reference
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_paso_1, container, false)

        viewModel = ViewModelProvider(requireActivity())[RegistroViewModel::class.java]

        // Inicializar UI
        initViews(view)

        // Navegación
        setNavigationListeners(view)

        // Mostrar/ocultar contraseñas
        checkBoxShowPassword.setOnCheckedChangeListener { _, isChecked ->
            togglePasswordVisibility(isChecked)
        }

        // Continuar → validar y avanzar
        btnContinue.setOnClickListener { validateInputsAndGo() }

        // Back físico → volver al login
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            (activity as? RegistroActivity)?.goToLogin()
        }

        return view
    }

    private fun initViews(view: View) {
        _etPhoneNumber = view.findViewById(R.id.etPhoneNumber)
        _etEmail = view.findViewById(R.id.etEmail)
        _etPassword = view.findViewById(R.id.etPassword)
        _etConfirmPassword = view.findViewById(R.id.etConfirmPassword)
        _tvPhoneError = view.findViewById(R.id.tvPhoneError)
        _tvEmailError = view.findViewById(R.id.tvEmailError)
        _tvPasswordError = view.findViewById(R.id.tvPasswordError)
        _tvConfirmPasswordError = view.findViewById(R.id.tvConfirmPasswordError)
        _checkBoxShowPassword = view.findViewById(R.id.ic_hide_password)
        _btnContinue = view.findViewById(R.id.btnContinueToStep2)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _checkBoxShowPassword?.setOnCheckedChangeListener(null)
        _btnContinue?.setOnClickListener(null)
        _etPhoneNumber = null
        _etEmail = null
        _etPassword = null
        _etConfirmPassword = null
        _tvPhoneError = null
        _tvEmailError = null
        _tvPasswordError = null
        _tvConfirmPasswordError = null
        _checkBoxShowPassword = null
        _btnContinue = null
    }

    private fun setNavigationListeners(view: View) {
        view.findViewById<TextView>(R.id.tvLoginLink).setOnClickListener {
            (activity as? RegistroActivity)?.goToLogin()
        }
        view.findViewById<ImageView>(R.id.backArrow).setOnClickListener {
            (activity as? RegistroActivity)?.goToLogin()
        }
    }

    private fun togglePasswordVisibility(show: Boolean) {
        val method = if (show) null else PasswordTransformationMethod.getInstance()
        etPassword.transformationMethod = method
        etConfirmPassword.transformationMethod = method
        etPassword.setSelection(etPassword.text?.length ?: 0)
        etConfirmPassword.setSelection(etConfirmPassword.text?.length ?: 0)
    }

    // ========= Validaciones UI =========
    private fun clearErrorMessages() {
        tvPhoneError.visibility = View.GONE
        tvEmailError.visibility = View.GONE
        tvPasswordError.visibility = View.GONE
        tvConfirmPasswordError.visibility = View.GONE
    }

    private fun isPhoneNumberValid(phone: String): Boolean =
        phoneKey(phone).length >= 8 // solo dígitos, mínimo 8

    private fun isEmailValid(email: String): Boolean =
        !TextUtils.isEmpty(email) &&
            android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() &&
            email.lowercase(Locale.ROOT).endsWith("@ice.go.cr")

    private fun isPasswordValid(password: String): Boolean = password.length >= 8

    // ========= Flujo: Validar y avanzar =========
    private fun validateInputsAndGo() {
        clearErrorMessages()

        val phoneRaw = etPhoneNumber.text?.toString()?.trim().orEmpty()
        val email = etEmail.text?.toString()?.trim().orEmpty()
        val password = etPassword.text?.toString()?.trim().orEmpty()
        val confirmPassword = etConfirmPassword.text?.toString()?.trim().orEmpty()

        var ok = true

        if (!isPhoneNumberValid(phoneRaw)) {
            tvPhoneError.text = "El número celular debe tener al menos 8 dígitos."
            tvPhoneError.visibility = View.VISIBLE
            ok = false
        }
        if (!isEmailValid(email)) {
            tvEmailError.text = "Ingrese un correo institucional @ice.go.cr."
            tvEmailError.visibility = View.VISIBLE
            ok = false
        }
        if (!isPasswordValid(password)) {
            tvPasswordError.text = "La contraseña debe tener al menos 8 caracteres."
            tvPasswordError.visibility = View.VISIBLE
            ok = false
        }
        if (confirmPassword != password) {
            tvConfirmPasswordError.text = "Las contraseñas no coinciden."
            tvConfirmPasswordError.visibility = View.VISIBLE
            ok = false
        }

        if (!ok) return

        // Evitar doble clic mientras consultamos unicidad
        setLoading(true)

        // Verificar disponibilidad usando /emails y /phones (no /usuarios)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val normalizedEmail = email.trim()
                if (normalizedEmail.isBlank()) {
                    Toast.makeText(requireContext(), "El correo electrónico no está disponible.", Toast.LENGTH_SHORT).show()
                    setLoading(false)
                    return@launch
                }

                val eKey = emailKey(normalizedEmail)
                val pKey = phoneKey(phoneRaw)

                val emailTaken = usersDb.child("emails").child(eKey).get().await().exists()
                if (emailTaken) {
                    tvEmailError.text = "El correo ya está registrado."
                    tvEmailError.visibility = View.VISIBLE
                    setLoading(false)
                    return@launch
                }

                val phoneTaken = usersDb.child("phones").child(pKey).get().await().exists()
                if (phoneTaken) {
                    tvPhoneError.text = "El número de teléfono ya está registrado."
                    tvPhoneError.visibility = View.VISIBLE
                    setLoading(false)
                    return@launch
                }

                // Guardar en VM
                viewModel.setTelefono(phoneRaw)
                viewModel.setEmail(normalizedEmail)
                viewModel.setPassword(password)
                prefs.edit().putString("registro_email", normalizedEmail).apply()

                // Llamar Cloud Function: sendVerificationCode(email)
                Log.d("Paso1Fragment", "Voy a pedir código para: '$normalizedEmail'")
                functions
                    .getHttpsCallable("sendVerificationCode")
                    .call(normalizedEmail)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Te enviamos un código a $normalizedEmail", Toast.LENGTH_SHORT).show()
                        (activity as? RegistroActivity)?.goToNextStep(1) // Paso 2
                    }
                    .addOnFailureListener { e ->
                        Log.e("Paso1Fragment", "Error enviando correo de verificación", e)
                        Toast.makeText(requireContext(), "No se pudo enviar el código. Intenta de nuevo.", Toast.LENGTH_LONG).show()
                    }

            } catch (t: Throwable) {
                Log.e("Paso1Fragment", "Validación falló: ${t.message}", t)
                Toast.makeText(requireContext(), "No se pudo validar. Intenta de nuevo.", Toast.LENGTH_LONG).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        btnContinue.isEnabled = !loading
        // Si tienes progress en el layout, muéstralo/ocúltalo aquí.
    }


}
