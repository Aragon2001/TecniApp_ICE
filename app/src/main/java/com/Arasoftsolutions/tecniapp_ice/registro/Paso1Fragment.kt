package com.Arasoftsolutions.tecniapp_ice.registro

import MailSender
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
import kotlin.random.Random

class Paso1Fragment : Fragment() {

    private lateinit var viewModel: RegistroViewModel

    // UI
    private lateinit var etPhoneNumber: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var tvPhoneError: MaterialTextView
    private lateinit var tvEmailError: MaterialTextView
    private lateinit var tvPasswordError: MaterialTextView
    private lateinit var tvConfirmPasswordError: MaterialTextView
    private lateinit var checkBoxShowPassword: MaterialCheckBox
    private lateinit var btnContinue: MaterialButton

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
        etPhoneNumber = view.findViewById(R.id.etPhoneNumber)
        etEmail = view.findViewById(R.id.etEmail)
        etPassword = view.findViewById(R.id.etPassword)
        etConfirmPassword = view.findViewById(R.id.etConfirmPassword)
        tvPhoneError = view.findViewById(R.id.tvPhoneError)
        tvEmailError = view.findViewById(R.id.tvEmailError)
        tvPasswordError = view.findViewById(R.id.tvPasswordError)
        tvConfirmPasswordError = view.findViewById(R.id.tvConfirmPasswordError)
        checkBoxShowPassword = view.findViewById(R.id.ic_hide_password)
        btnContinue = view.findViewById(R.id.btnContinueToStep2)
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
                val eKey = emailKey(email)
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
                viewModel.setEmail(email)
                viewModel.setPassword(password)

                // Enviar código de verificación y pasar a Paso 2
                val code = generateCode()
                saveVerificationCode(email, code)
                sendVerificationEmail(email, code)

                Toast.makeText(requireContext(), "Te enviamos un código a $email", Toast.LENGTH_SHORT).show()
                (activity as? RegistroActivity)?.goToNextStep(1) // avanza a Paso 2

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

    // ========= Verificación por correo =========
    private fun generateCode(): String = Random.nextInt(100000, 999999).toString()

    /**
     * Guarda el código con expiración (5 min) bajo /verificationCodes/{emailKey}
     * Estructura:
     * {
     *   code: "123456",
     *   createdAt: 1690000000000,
     *   expiresAt: 1690000300000
     * }
     */
    private fun saveVerificationCode(email: String, verificationCode: String) {
        val now = System.currentTimeMillis()
        val payload = mapOf(
            "code" to verificationCode,
            "createdAt" to now,
            "expiresAt" to (now + 300_000L) // 5 minutos
        )

        FirebaseDatabase
            .getInstance("https://tecniapp-ice-user.firebaseio.com/")
            .reference.child("verificationCodes")
            .child(emailKey(email))
            .setValue(payload)
    }

    private fun sendVerificationEmail(email: String, verificationCode: String) {
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Log.e("Registro", "Correo no válido: $email")
            return
        }

        val message = """
        <html>
            <body style="margin: 0; padding: 0; font-family: 'Segoe UI', Arial, sans-serif; background-color: #f4f7fc;">
                <table align="center" width="100%" cellpadding="0" cellspacing="0" style="max-width: 600px; background-color: #ffffff; border-radius: 10px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); margin: 40px auto;">
                    <tr>
                        <td style="padding: 20px; text-align: center;">
                            <!-- Imagen del ICE (simulada con logo institucional) -->
                            <img src="https://i.imgur.com/tGUD2Vo.png" alt="ICE Logo" width="100" style="margin-bottom: 20px;">
                            <h2 style="color: #004C8C; margin-bottom: 8px;">TecniApp ICE</h2>
                            <p style="color: #555; font-size: 16px; margin-top: 0;">Verificación de cuenta</p>
                        </td>
                    </tr>
                    <tr>
                        <td style="padding: 20px;">
                            <p style="color: #333; font-size: 15px;">
                                Gracias por registrarte en <strong>TecniApp ICE</strong>. Tu código de verificación es el siguiente:
                            </p>
                            <div style="text-align: center; background-color: #0075C9; color: #ffffff; font-size: 28px; font-weight: bold; padding: 16px 0; border-radius: 8px; margin: 24px 0; letter-spacing: 3px;">
                                $verificationCode
                            </div>
                            <p style="font-size: 14px; color: #555;">Este código es válido por <strong>5 minutos</strong>. No lo compartas con nadie.</p>
                            <p style="font-size: 14px; color: #555;">Si no realizaste esta solicitud, puedes ignorar este mensaje.</p>
                        </td>
                    </tr>
                    <tr>
                        <td style="padding: 10px 20px;">
                            <hr style="border: none; border-top: 1px solid #eee;">
                            <p style="text-align: center; font-size: 12px; color: #999; margin-top: 14px;">
                                © 2025 Arasoft Solutions · Todos los derechos reservados<br>
                                Este correo fue generado automáticamente por TecniApp ICE
                            </p>
                        </td>
                    </tr>
                </table>
            </body>
        </html>
    """.trimIndent()

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
                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        "No se pudo enviar el correo. Verifica tu buzón/spam o intenta reenviar.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
