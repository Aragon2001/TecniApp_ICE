package com.Arasoftsolutions.tecniapp_ice.registro

import MailSender
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
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

class Paso1Fragment : Fragment() {

    private lateinit var viewModel: RegistroViewModel
    private lateinit var etPhoneNumber: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var tvPhoneError: MaterialTextView
    private lateinit var tvEmailError: MaterialTextView
    private lateinit var tvPasswordError: MaterialTextView
    private lateinit var tvConfirmPasswordError: MaterialTextView
    private lateinit var checkBoxShowPassword: MaterialCheckBox

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_paso_1, container, false)

        viewModel = ViewModelProvider(requireActivity()).get(RegistroViewModel::class.java)

        // Inicializar campos
        initViews(view)

        // Configurar enlaces de navegación
        setNavigationListeners(view)

        // Configurar el CheckBox para mostrar/ocultar contraseñas
        checkBoxShowPassword.setOnCheckedChangeListener { _, isChecked ->
            togglePasswordVisibility(
                isChecked
            )
        }

        // Botón continuar
        view.findViewById<MaterialButton>(R.id.btnContinueToStep2).setOnClickListener {
            validateInputs()
        }

        // Manejar el gesto de volver
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            (activity as? RegistroActivity)?.goToLogin()
        }

        return view
    }

    private fun initViews(view: View) {
        etPhoneNumber = view.findViewById(R.id.etPhoneNumber)
        etEmail = view.findViewById(R.id.etEmail)
        etPassword = view.findViewById(R.id.etPassword)
        etConfirmPassword = view.findViewById(R.id.etConfirmPassword)
        tvPhoneError = view.findViewById(R.id.tvPhoneError)
        tvEmailError = view.findViewById(R.id.tvEmailError)
        tvPasswordError = view.findViewById(R.id.tvPasswordError)
        tvConfirmPasswordError = view.findViewById(R.id.tvConfirmPasswordError)
        checkBoxShowPassword = view.findViewById(R.id.ic_hide_password)
    }

    private fun setNavigationListeners(view: View) {
        view.findViewById<TextView>(R.id.tvLoginLink).setOnClickListener {
            (activity as? RegistroActivity)?.goToLogin()
        }

        view.findViewById<ImageView>(R.id.backArrow).setOnClickListener {
            (activity as? RegistroActivity)?.goToLogin()
        }
    }

    private fun togglePasswordVisibility(isChecked: Boolean) {
        val transformationMethod =
            if (isChecked) null else android.text.method.PasswordTransformationMethod.getInstance()
        etPassword.transformationMethod = transformationMethod
        etConfirmPassword.transformationMethod = transformationMethod
        etPassword.setSelection(etPassword.text?.length ?: 0)
        etConfirmPassword.setSelection(etConfirmPassword.text?.length ?: 0)
    }

    private fun validateInputs() {
        clearErrorMessages()

        val phoneNumber = etPhoneNumber.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val confirmPassword = etConfirmPassword.text.toString().trim()

        var isValid = true

        // Validar el número de teléfono
        if (!isPhoneNumberValid(phoneNumber)) {
            tvPhoneError.text = "El número celular debe tener al menos 8 dígitos."
            tvPhoneError.visibility = View.VISIBLE
            isValid = false
        }

        // Validar el correo electrónico
        if (!isEmailValid(email)) {
            tvEmailError.text = "Ingrese un correo institucional @ice.go.cr."
            tvEmailError.visibility = View.VISIBLE
            isValid = false
        }

        // Validar la contraseña
        if (!isPasswordValid(password)) {
            tvPasswordError.text = "La contraseña debe tener al menos 8 caracteres."
            tvPasswordError.visibility = View.VISIBLE
            isValid = false
        }

        // Validar la confirmación de contraseña
        if (confirmPassword != password) {
            tvConfirmPasswordError.text = "Las contraseñas no coinciden."
            tvConfirmPasswordError.visibility = View.VISIBLE
            isValid = false
        }

        // Validar si el correo y el teléfono ya existen en la base de datos
        if (isValid) {
            checkIfUserExists(email, phoneNumber)
        }
    }

    private fun clearErrorMessages() {
        tvPhoneError.visibility = View.GONE
        tvEmailError.visibility = View.GONE
        tvPasswordError.visibility = View.GONE
        tvConfirmPasswordError.visibility = View.GONE
    }

    private fun isPhoneNumberValid(phoneNumber: String) = phoneNumber.length >= 8

    private fun isEmailValid(email: String): Boolean {
        return !TextUtils.isEmpty(email) && android.util.Patterns.EMAIL_ADDRESS.matcher(email)
            .matches() && email.endsWith("@ice.go.cr")
    }

    private fun isPasswordValid(password: String) = password.length >= 8

    private fun checkIfUserExists(email: String, phoneNumber: String) {
        val databaseRef =
            FirebaseDatabase.getInstance("https://tecniapp-ice-user.firebaseio.com/").reference.child(
                "usuarios"
            )

        databaseRef.orderByChild("email").equalTo(email).get().addOnCompleteListener { emailTask ->
            if (view != null && isAdded) { // Asegurarse de que el fragmento está activo
                if (emailTask.isSuccessful) {
                    if (emailTask.result.exists()) {
                        tvEmailError.text = "El correo ya está registrado."
                        tvEmailError.visibility = View.VISIBLE
                    } else {
                        checkPhoneNumber(databaseRef, phoneNumber)
                    }
                } else {
                    Log.e(
                        "Firebase",
                        "Error al verificar el correo: ${emailTask.exception?.message}"
                    )
                }
            }
        }
    }

    private fun checkPhoneNumber(databaseRef: DatabaseReference, phoneNumber: String) {
        databaseRef.orderByChild("telefono").equalTo(phoneNumber).get()
            .addOnCompleteListener { phoneTask ->
                if (view != null && isAdded) { // Asegurarse de que el fragmento está activo
                    if (phoneTask.isSuccessful) {
                        if (phoneTask.result.exists()) {
                            tvPhoneError.text = "El número de teléfono ya está registrado."
                            tvPhoneError.visibility = View.VISIBLE
                        } else {
                            continueToNextStep(etEmail.text.toString().trim(), phoneNumber)
                        }
                    } else {
                        Log.e(
                            "Firebase",
                            "Error al verificar el teléfono: ${phoneTask.exception?.message}"
                        )
                    }
                }
            }
    }

    private fun continueToNextStep(email: String, phoneNumber: String) {
        // Guardar los datos en el ViewModel
        viewModel.setTelefono(phoneNumber)
        viewModel.setEmail(email)
        viewModel.setPassword(etPassword.text.toString().trim())

        // Enviar el código de verificación
        sendVerificationCode(email)

        // Navegar al siguiente paso
        (activity as? RegistroActivity)?.goToNextStep(1)
    }

    private fun sendVerificationCode(email: String) {
        val verificationCode = (100000..999999).random().toString()
        saveVerificationCode(email, verificationCode)

        // Ejecutar la tarea de envío de correo en el lifecycleScope para asegurar que no se ejecute fuera del ciclo de vida del fragmento
        lifecycleScope.launch {
            sendVerificationEmail(email, verificationCode)
        }
    }

    private fun saveVerificationCode(email: String, verificationCode: String) {
        val databaseRef =
            FirebaseDatabase.getInstance("https://tecniapp-ice-user.firebaseio.com/").reference.child(
                "verificationCodes"
            ).child(email.replace(".", ","))
        databaseRef.setValue(verificationCode)
    }

    private fun sendVerificationEmail(email: String, verificationCode: String) {
        // Validar correo antes de enviar
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Log.e("Registro", "Correo no válido: $email")
            return
        }

        // Crear el mensaje en formato HTML con un diseño más atractivo
        val message = """
    <html>
        <body style="font-family: Arial, sans-serif; padding: 20px; background-color: #f4f7fc;">
            <div style="background-color: #ffffff; padding: 20px; border-radius: 8px; box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);">
                <h2 style="color: #004C8C; text-align: center;">TecniApp - Verificación de Cuenta</h2>
                <p>Gracias por registrarte en <strong>TecniApp</strong>. Tu código de verificación es:</p>
                <div style="text-align: center; background-color: #FF6200EE; color: #ffffff; font-size: 28px; font-weight: bold; padding: 15px; border-radius: 8px; margin: 20px 0; letter-spacing: 2px;">
                    $verificationCode
                </div>
                <p>Introduce este código en la aplicación para continuar con tu registro.</p>
                <p>Si no realizaste esta solicitud, por favor ignora este correo.</p>
                <p>Gracias,</p>
                <p>El equipo de TecniApp</p>
                <hr />
                <footer style="text-align: center; font-size: 12px; color: #777;">
                    <p>TecniApp Soluciones Integrales</p>
                    <p>© 2024 Arasoft Solutions</p>
                </footer>
            </div>
        </body>
    </html>
    """.trimIndent()

        // Crear el objeto MailSender y enviar el correo
        val mailSender = MailSender()
        mailSender.sendFormattedMail(
            subject = "Verificación de cuenta TecniApp",
            body = message,
            to = email
        ) { success, errorMessage ->
            if (success) {
                Log.d("Registro", "Correo enviado exitosamente.")
            } else {
                Log.e("Registro", "Error al enviar el correo: $errorMessage")
            }
        }
    }


}



