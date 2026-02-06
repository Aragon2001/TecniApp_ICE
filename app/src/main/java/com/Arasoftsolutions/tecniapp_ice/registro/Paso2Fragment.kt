package com.Arasoftsolutions.tecniapp_ice.registro

import android.content.Context
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
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale
import kotlin.random.Random

class Paso2Fragment : Fragment() {

    private lateinit var viewModel: RegistroViewModel
    private val functions by lazy { FirebaseFunctions.getInstance() }
    private val prefs by lazy {
        requireContext().getSharedPreferences("registro_prefs", Context.MODE_PRIVATE)
    }

    private var _etVerificationCodeEmail: TextInputEditText? = null
    private var _btnVerifyCode: MaterialButton? = null
    private var _tvResendEmail: TextView? = null
    private var _resendEmailCountDown: TextView? = null
    private var _tvVerificationError: TextView? = null

    private val etVerificationCodeEmail get() = requireNotNull(_etVerificationCodeEmail)
    private val btnVerifyCode get() = requireNotNull(_btnVerifyCode)
    private val tvResendEmail get() = requireNotNull(_tvResendEmail)
    private val resendEmailCountDown get() = requireNotNull(_resendEmailCountDown)
    private val tvVerificationError get() = requireNotNull(_tvVerificationError)

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

        _etVerificationCodeEmail = view.findViewById(R.id.etVerificationCodeEmail)
        _btnVerifyCode = view.findViewById(R.id.btnVerifyCode)
        _tvResendEmail = view.findViewById(R.id.tvResendEmail)
        _resendEmailCountDown = view.findViewById(R.id.resendEmailCountDown)
        _tvVerificationError = view.findViewById(R.id.tvVerificationError)

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
        _btnVerifyCode?.setOnClickListener(null)
        _tvResendEmail?.setOnClickListener(null)
        _etVerificationCodeEmail = null
        _btnVerifyCode = null
        _tvResendEmail = null
        _resendEmailCountDown = null
        _tvVerificationError = null
    }

    private fun setNavigationListeners(view: View) {
        view.findViewById<ImageView>(R.id.backArrow).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    // ========= Verificación =========
    private fun verifyCode(emailCode: String) {
        val email = getEmailOrShowError() ?: return

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

        val email = getEmailOrShowError() ?: return

        resendEmailAttempts++
        val remainingAttempts = maxResendAttempts - resendEmailAttempts
        resendEmailCountDown.text = "Te quedan $remainingAttempts reenvíos"

        // Cooldown de 60s entre reenvíos
        startResendCooldown()

        functions
            .getHttpsCallable("sendVerificationCode")
            .call(email)
            .addOnSuccessListener {
                if (isAdded) {
                    Toast.makeText(requireContext(), "Reenviamos un código a $email", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("Paso2Fragment", "Error reenviando código", e)
                if (isAdded) {
                    Toast.makeText(requireContext(), "No se pudo reenviar el código.", Toast.LENGTH_LONG).show()
                }
            }

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


    // ========= UI helpers =========
    private fun showError(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun showToast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun getEmailOrShowError(): String? {
        val fromViewModel = viewModel.getEmail()?.trim().orEmpty()
        val email = if (fromViewModel.isNotBlank()) {
            fromViewModel
        } else {
            prefs.getString("registro_email", "")?.trim().orEmpty()
        }

        return if (email.isNotBlank()) {
            if (fromViewModel.isBlank()) {
                viewModel.setEmail(email)
            }
            email
        } else {
            showError("El correo electrónico no está disponible.")
            null
        }
    }
}
