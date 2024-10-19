package com.Arasoftsolutions.tecniapp_ice.registro

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.RegistroActivity
import com.Arasoftsolutions.tecniapp_ice.registro.RegistroViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class Paso3Fragment : Fragment() {

    private lateinit var viewModel: RegistroViewModel
    private lateinit var etFirstName: TextInputEditText
    private lateinit var etLastName: TextInputEditText
    private lateinit var etID: TextInputEditText
    private lateinit var btnContinueToStep4: MaterialButton

    // TextViews para los mensajes de error
    private lateinit var tvFirstNameError: TextView
    private lateinit var tvLastNameError: TextView
    private lateinit var tvIDError: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_paso_3, container, false)

         setNavigationListeners(view) // Añadir aquí, no dentro de onCreateView.

        // Inicialización del ViewModel
        viewModel = ViewModelProvider(requireActivity()).get(RegistroViewModel::class.java)

        // Inicialización de las vistas
        etFirstName = view.findViewById(R.id.etFirstName)
        etLastName = view.findViewById(R.id.etLastName)
        etID = view.findViewById(R.id.etID)
        btnContinueToStep4 = view.findViewById(R.id.btnContinueToStep4)

        // Inicialización de los mensajes de error
        tvFirstNameError = view.findViewById(R.id.tvFirstNameError)
        tvLastNameError = view.findViewById(R.id.tvLastNameError)
        tvIDError = view.findViewById(R.id.tvIDError)

        // Configurar el botón para continuar al siguiente paso
        btnContinueToStep4.setOnClickListener {
            if (validateInputs()) {
                // Obtener los datos ingresados
                val firstName = etFirstName.text.toString().trim()
                val lastName = etLastName.text.toString().trim()
                val cedula = etID.text.toString().trim()

                // Almacenar los datos en el ViewModel
                viewModel.setDatosTecnico(firstName, lastName, cedula)

                // Cambiar al siguiente paso
                (activity as RegistroActivity).goToNextStep(3)
            }
        }

        return view
    }

       // Configuración de la navegación
    private fun setNavigationListeners(view: View) {
        view.findViewById<ImageView>(R.id.backArrow).setOnClickListener {
            // Esto simula el botón de "atrás" del sistema para volver al fragmento anterior
            requireActivity().onBackPressed()
        }
    }

    // Método para validar los campos de entrada
    private fun validateInputs(): Boolean {
        val firstName = etFirstName.text.toString().trim()
        val lastName = etLastName.text.toString().trim()
        val cedula = etID.text.toString().trim()

        clearErrors()

        // Validar que los campos no estén vacíos y contengan caracteres válidos
        return when {
            TextUtils.isEmpty(firstName) -> {
                tvFirstNameError.text = "Por favor, ingresa tu nombre."
                tvFirstNameError.visibility = View.VISIBLE
                false
            }
            !isValidName(firstName) -> {
                tvFirstNameError.text = "El nombre solo debe contener letras."
                tvFirstNameError.visibility = View.VISIBLE
                false
            }
            TextUtils.isEmpty(lastName) -> {
                tvLastNameError.text = "Por favor, ingresa tu apellido."
                tvLastNameError.visibility = View.VISIBLE
                false
            }
            !isValidName(lastName) -> {
                tvLastNameError.text = "El apellido solo debe contener letras."
                tvLastNameError.visibility = View.VISIBLE
                false
            }
            TextUtils.isEmpty(cedula) -> {
                tvIDError.text = "Por favor, ingresa tu cédula."
                tvIDError.visibility = View.VISIBLE
                false
            }
            !isValidID(cedula) -> {
                tvIDError.text = "La cédula debe contener solo números."
                tvIDError.visibility = View.VISIBLE
                false
            }
            cedula.length != 9 -> {
                tvIDError.text = "La cédula debe tener exactamente 9 dígitos."
                tvIDError.visibility = View.VISIBLE
                false
            }
            else -> true
        }
    }

    // Método para validar que un nombre solo contenga letras
    private fun isValidName(name: String): Boolean {
        return name.all { it.isLetter() || it.isWhitespace() }
    }

    // Método para validar que la cédula contenga solo números
    private fun isValidID(id: String): Boolean {
        return id.all { it.isDigit() }
    }

    // Método para limpiar los errores
    private fun clearErrors() {
        tvFirstNameError.visibility = View.GONE
        tvLastNameError.visibility = View.GONE
        tvIDError.visibility = View.GONE
    }
}
