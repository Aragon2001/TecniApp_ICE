package com.Arasoftsolutions.tecniapp_ice.registro

import MailSender
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.RegistroActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import android.widget.TextView
import com.google.firebase.database.FirebaseDatabase

class Paso2Fragment : Fragment() {

    private lateinit var viewModel: RegistroViewModel
    private lateinit var etVerificationCodeEmail: TextInputEditText
    private lateinit var btnVerifyCode: MaterialButton
    private lateinit var tvResendEmail: TextView
    private lateinit var resendEmailCountDown: TextView
    private lateinit var tvVerificationError: TextView // Mensaje de error

    private var resendEmailAttempts = 0
    private val maxResendAttempts = 3

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_paso_2, container, false)


        setNavigationListeners(view) // Añadir aquí, no dentro de onCreateView.

        // Inicialización del ViewModel
        viewModel = ViewModelProvider(requireActivity()).get(RegistroViewModel::class.java)

        // Inicialización de las vistas
        etVerificationCodeEmail = view.findViewById(R.id.etVerificationCodeEmail)
        btnVerifyCode = view.findViewById(R.id.btnVerifyCode)
        tvResendEmail = view.findViewById(R.id.tvResendEmail)
        resendEmailCountDown = view.findViewById(R.id.resendEmailCountDown)
        tvVerificationError = view.findViewById(R.id.tvVerificationError) // Inicializa el mensaje de error

        btnVerifyCode.setOnClickListener {
            val emailCode = etVerificationCodeEmail.text.toString().trim()

            // Validar que el código no esté vacío
            if (TextUtils.isEmpty(emailCode)) {
                showToast("Por favor, ingresa el código de verificación.")
                tvVerificationError.visibility = View.GONE // Ocultar error si no hay código
            } else {
                verifyCode(emailCode)
            }
        }

        tvResendEmail.setOnClickListener {
            resendEmailVerificationCode()
        }

        return view
    }

    // Configuración de la navegación
    private fun setNavigationListeners(view: View) {
        view.findViewById<ImageView>(R.id.backArrow).setOnClickListener {
            // Esto simula el botón de "atrás" del sistema para volver al fragmento anterior
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }


    private fun verifyCode(emailCode: String) {
        val email = viewModel.getEmail() ?: run {
            showError("El correo electrónico no está disponible.")
            return
        }

        val databaseRef = FirebaseDatabase.getInstance("https://tecniapp-ice-user.firebaseio.com/")
            .reference.child("verificationCodes").child(email.replace(".", ","))

        // Mostrar un indicador de carga
        btnVerifyCode.isEnabled = false
        showToast("Verificando...")

        databaseRef.get().addOnSuccessListener { snapshot ->
            btnVerifyCode.isEnabled = true // Rehabilitar el botón

            if (snapshot.exists()) {
                val expectedCode = snapshot.value.toString()

                if (emailCode == expectedCode) {
                    // Código correcto, ir al siguiente paso
                    (activity as RegistroActivity).goToNextStep(2)
                    tvVerificationError.visibility = View.GONE // Ocultar el mensaje de error
                } else {
                    showError("El código de verificación es incorrecto.")
                    tvVerificationError.visibility = View.VISIBLE // Mostrar el mensaje de error
                }
            } else {
                showError("No se encontró el código de verificación.")
                tvVerificationError.visibility = View.VISIBLE // Mostrar el mensaje de error
            }
        }.addOnFailureListener {
            btnVerifyCode.isEnabled = true // Rehabilitar el botón
            showError("Error al verificar el código. Intente nuevamente.")
            tvVerificationError.visibility = View.GONE // Ocultar error si hay falla
        }
    }

    private fun resendEmailVerificationCode() {
        if (resendEmailAttempts >= maxResendAttempts) {
            showToast("Has alcanzado el límite de reenvíos.")
            return
        }

        val email = viewModel.getEmail() ?: run {
            showError("El correo electrónico no está disponible.")
            return
        }

        sendVerificationCodeToEmail(email)
        resendEmailAttempts++
        updateResendCountDown()
    }

    private fun sendVerificationCodeToEmail(email: String) {
        // Generar un nuevo código de verificación
        val newVerificationCode = (100000..999999).random().toString()

        // Guardar el nuevo código en la base de datos
        saveVerificationCode(email, newVerificationCode)

        // Crear el mensaje HTML con el código de verificación
        val message = """
        <html>
            <body style="font-family: Arial, sans-serif; padding: 20px; background-color: #f4f7fc;">
                <div style="background-color: #ffffff; padding: 20px; border-radius: 8px; box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);">
                    <h2 style="color: #004C8C; text-align: center;">TecniApp - Verificación de Cuenta</h2>
                    <p>Hola,</p>
                    <p>Tu código de verificación ha sido reenviado. Usa el siguiente código para completar tu registro:</p>
                    <div style="text-align: center; background-color: #FF6200EE; color: #ffffff; font-size: 28px; font-weight: bold; padding: 15px; border-radius: 8px; margin: 20px 0; letter-spacing: 2px;">
                        $newVerificationCode
                    </div>
                    <p>Si no solicitaste este código, por favor ignora este correo.</p>
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
        Log.d("Registro", "Correo Reenviado exitosamente.")
        if (isAdded) Toast.makeText(requireContext(), "Reenviamos el código a $email", Toast.LENGTH_SHORT).show()
    } else {
        Log.e("Registro", "Error al Reenviar el correo: $errorMessage")
        if (isAdded) Toast.makeText(requireContext(),
            "No se pudo reenviar el correo. Intenta de nuevo.", Toast.LENGTH_LONG).show()
    }
}

    }

    private fun saveVerificationCode(email: String, verificationCode: String) {
        val databaseRef = FirebaseDatabase.getInstance("https://tecniapp-ice-user.firebaseio.com/")
            .reference.child("verificationCodes").child(email.replace(".", ","))

        databaseRef.setValue(verificationCode)
    }

    private fun updateResendCountDown() {
        val remainingAttempts = maxResendAttempts - resendEmailAttempts
        resendEmailCountDown.text = "Te quedan $remainingAttempts reenvíos"
        resendEmailCountDown.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))

        // Deshabilitar el botón de reenviar si se han alcanzado los intentos máximos
        if (remainingAttempts <= 0) {
            tvResendEmail.isEnabled = false
        }
    }

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
