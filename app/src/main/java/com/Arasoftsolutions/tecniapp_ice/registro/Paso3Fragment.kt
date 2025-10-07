package com.Arasoftsolutions.tecniapp_ice.registro

import android.os.Bundle
import android.text.TextUtils
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
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class Paso3Fragment : Fragment() {

    private lateinit var viewModel: RegistroViewModel
    private lateinit var etFirstName: TextInputEditText
    private lateinit var etLastName: TextInputEditText
    private lateinit var etLastName2: TextInputEditText
    private lateinit var etID: TextInputEditText
    private lateinit var btnContinueToStep4: MaterialButton

    private lateinit var tvFirstNameError: TextView
    private lateinit var tvLastNameError: TextView
    private lateinit var tvLastNameError2: TextView
    private lateinit var tvIDError: TextView

    // RTDB users (mismo host que Paso1/2/4)
    private val usersDb: DatabaseReference by lazy {
        FirebaseDatabase.getInstance("https://tecniapp-ice-user.firebaseio.com").reference
    }

    // Solo dígitos, 9 posiciones (clave de índice)
    private fun cedulaKey(raw: String): String = raw.filter { it.isDigit() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_paso_3, container, false)

        setNavigationListeners(view)

        viewModel = ViewModelProvider(requireActivity())[RegistroViewModel::class.java]

        etFirstName = view.findViewById(R.id.etFirstName)
        etLastName  = view.findViewById(R.id.etLastName)
        etLastName2 = view.findViewById(R.id.etLastName2)
        etID        = view.findViewById(R.id.etID)
        btnContinueToStep4 = view.findViewById(R.id.btnContinueToStep4)

        tvFirstNameError = view.findViewById(R.id.tvFirstNameError)
        tvLastNameError  = view.findViewById(R.id.tvLastNameError)
        tvLastNameError2 = view.findViewById(R.id.tvLastNameError2)
        tvIDError        = view.findViewById(R.id.tvIDError)

        btnContinueToStep4.setOnClickListener { onContinue() }

        return view
    }

    private fun setNavigationListeners(view: View) {
        view.findViewById<ImageView>(R.id.backArrow).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun onContinue() {
        clearErrors()

        val firstName = etFirstName.text?.toString()?.trim().orEmpty()
        val lastName  = etLastName.text?.toString()?.trim().orEmpty()
        val lastName2  = etLastName2.text?.toString()?.trim().orEmpty()
        val cedulaRaw = etID.text?.toString()?.trim().orEmpty()
        val cedKey    = cedulaKey(cedulaRaw)

        // Validación local
        if (firstName.isBlank()) return showFieldError(tvFirstNameError, "Por favor, ingresa tu nombre.")
        if (!isValidName(firstName)) return showFieldError(tvFirstNameError, "El nombre solo debe contener letras.")
        if (lastName.isBlank()) return showFieldError(tvLastNameError, "Por favor, ingresa tu primer apellido.")
        if (!isValidName(lastName)) return showFieldError(tvLastNameError, "El apellido solo debe contener letras.")

        if (lastName2.isBlank()) return showFieldError(tvLastNameError2, "Por favor, ingresa tu segundo apellido.")
        if (!isValidName(lastName2)) return showFieldError(tvLastNameError2, "El apellido solo debe contener letras.")

        if (cedKey.isBlank()) return showFieldError(tvIDError, "Por favor, ingresa tu cédula.")
        if (!cedKey.all { it.isDigit() }) return showFieldError(tvIDError, "La cédula debe contener solo números.")
        if (cedKey.length != 9) return showFieldError(tvIDError, "La cédula debe tener exactamente 9 dígitos.")

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
                viewModel.setDatosTecnico(firstName, lastName, lastName2, cedKey)
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

    private fun isValidName(name: String): Boolean =
        name.all { it.isLetter() || it.isWhitespace() }
}
