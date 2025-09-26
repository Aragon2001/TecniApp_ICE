package com.Arasoftsolutions.tecniapp_ice.registro

import MailSender
import android.os.Bundle
import android.os.CountDownTimer
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.RegistroActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale
import kotlin.random.Random

class Paso2Fragment : Fragment() {

    private lateinit var viewModel: RegistroViewModel

    private lateinit var etVerificationCodeEmail: TextInputEditText
    private lateinit var btnVerifyCode: MaterialButton
    private lateinit var tvResendEmail: TextView
    private lateinit var resendEmailCountDown: TextView
    private lateinit var tvVerificationError: TextView

    private var resendEmailAttempts = 0
    private val maxResendAttempts = 3

    private var cooldownTimer: CountDownTimer? = null
    private val resendCooldownMillis = 60_000L // 60s

    // ===== Helpers: mismas claves que Paso1/Reglas =====
    private fun emailKey(email: String): String =
        email.trim().lowercase(Locale.ROOT)
            .replace(".", ",")
            .replace("#", "_")
            .replace("$", "_")
            .replace("[", "_")
            .replace("]", "_")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_paso_2, container, false)

        setNavigationListeners(view)

        viewModel = ViewModelProvider(requireActivity())[RegistroViewModel::class.java]

        etVerificationCodeEmail = view.findViewById(R.id.etVerificationCodeEmail)
        btnVerifyCode = view.findViewById(R.id.btnVerifyCode)
        tvResendEmail = view.findViewById(R.id.tvResendEmail)
        resendEmailCountDown = view.findViewById(R.id.resendEmailCountDown)
        tvVerificationError = view.findViewById(R.id.tvVerificationError)

        btnVerifyCode.setOnClickListener {
            val emailCode = etVerificationCodeEmail.text?.toString()?.trim().orEmpty()
            if (TextUtils.isEmpty(emailCode)) {
                showToast("Por favor, ingresa el código de verificación.")
                tvVerificationError.visibility = View.GONE
            } else {
                verifyCode(emailCode)
            }
        }

        tvResendEmail.setOnClickListener { resendEmailVerificationCode() }

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cooldownTimer?.cancel()
    }

    private fun setNavigationListeners(view: View) {
        view.findViewById<ImageView>(R.id.backArrow).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    // ========= Verificación =========
    private fun verifyCode(emailCode: String) {
        val email = viewModel.getEmail() ?: run {
            showError("El correo electrónico no está disponible.")
            return
        }

        val ref = FirebaseDatabase
            .getInstance("https://tecniapp-ice-user.firebaseio.com/")
            .reference.child("verificationCodes")
            .child(emailKey(email))

        btnVerifyCode.isEnabled = false
        showToast("Verificando...")

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val snap = ref.get().await()

                if (!snap.exists()) {
                    tvVerificationError.text = "No se encontró el código de verificación."
                    tvVerificationError.visibility = View.VISIBLE
                    return@launch
                }

                // INTENTO 1: nuevo formato con objeto { code, createdAt, expiresAt }
                val now = System.currentTimeMillis()
                val hasCodeChild = snap.child("code").exists()
                val serverCode: String?
                val expiresAt: Long

                if (hasCodeChild) {
                    serverCode = snap.child("code").getValue(String::class.java)
                    expiresAt = snap.child("expiresAt").getValue(Long::class.java) ?: Long.MAX_VALUE
                } else {
                    // INTENTO 2: legacy string plano. Protégete contra DatabaseException (HashMap→String).
                    serverCode = runCatching { snap.getValue(String::class.java) }.getOrNull()
                    expiresAt = Long.MAX_VALUE
                }

                if (serverCode.isNullOrBlank()) {
                    tvVerificationError.text = "Código de verificación inválido."
                    tvVerificationError.visibility = View.VISIBLE
                    return@launch
                }

                if (now > expiresAt) {
                    tvVerificationError.text = "El código ha expirado. Reenviá el código."
                    tvVerificationError.visibility = View.VISIBLE
                    return@launch
                }

                if (emailCode == serverCode) {
                    // Limpia el código y avanza
                    ref.setValue(null)
                    tvVerificationError.visibility = View.GONE
                    (activity as RegistroActivity).goToNextStep(2)
                } else {
                    tvVerificationError.text = "El código de verificación es incorrecto."
                    tvVerificationError.visibility = View.VISIBLE
                }
            } catch (t: Throwable) {
                Log.e("Paso2Fragment", "Error verificando código", t)
                showError("Error al verificar el código. Intente nuevamente.")
                tvVerificationError.visibility = View.GONE
            } finally {
                btnVerifyCode.isEnabled = true
            }
        }
    }

    // ========= Reenvío =========
    private fun resendEmailVerificationCode() {
        if (resendEmailAttempts >= maxResendAttempts) {
            showToast("Has alcanzado el límite de reenvíos.")
            return
        }

        val email = viewModel.getEmail()?.trim()
        if (email.isNullOrBlank()) {
            showError("El correo electrónico no está disponible.")
            return
        }

        resendEmailAttempts++
        val remainingAttempts = maxResendAttempts - resendEmailAttempts
        resendEmailCountDown.text = "Te quedan $remainingAttempts reenvíos"

        // Cooldown de 60s entre reenvíos
        startResendCooldown()

        val newCode = generateCode()
        saveVerificationCode(email, newCode)
        sendVerificationEmail(email, newCode)
    }

    private fun startResendCooldown() {
        tvResendEmail.isEnabled = false
        cooldownTimer?.cancel()
        cooldownTimer = object : CountDownTimer(resendCooldownMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val s = (millisUntilFinished / 1000).toInt()
                resendEmailCountDown.text = "Puedes reenviar en ${s}s"
            }
            override fun onFinish() {
                resendEmailCountDown.text = ""
                tvResendEmail.isEnabled = resendEmailAttempts < maxResendAttempts
            }
        }.start()
    }

    private fun generateCode(): String = Random.nextInt(100000, 999999).toString()

    /**
     * Guarda { code, createdAt, expiresAt } bajo /verificationCodes/{emailKey}
     * Expira en 5 minutos.
     */
    private fun saveVerificationCode(email: String, verificationCode: String) {
        val now = System.currentTimeMillis()
        val payload = mapOf(
            "code" to verificationCode,
            "createdAt" to now,
            "expiresAt" to (now + 5 * 60_000)
        )
        FirebaseDatabase.getInstance("https://tecniapp-ice-user.firebaseio.com/")
            .reference.child("verificationCodes")
            .child(emailKey(email))
            .setValue(payload)
            .addOnFailureListener {
                Log.e("Paso2Fragment", "No se pudo guardar el nuevo código: ${it.message}", it)
            }
    }

    private fun sendVerificationEmail(email: String, verificationCode: String) {
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
                Log.d("Registro", "Correo reenviado/exitoso.")
                if (isAdded) Toast.makeText(requireContext(), "Enviamos un código a $email", Toast.LENGTH_SHORT).show()
            } else {
                Log.e("Registro", "Error al enviar correo: $errorMessage")
                if (isAdded) Toast.makeText(requireContext(), "No se pudo enviar el correo. Intenta de nuevo.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ========= UI helpers =========
    private fun showError(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun showToast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}
