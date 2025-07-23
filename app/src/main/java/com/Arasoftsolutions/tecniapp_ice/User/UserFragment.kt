package com.Arasoftsolutions.tecniapp_ice.User

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.Arasoftsolutions.tecniapp_ice.R
import com.google.firebase.auth.FirebaseAuth

class FragmentUser : Fragment() {

    private lateinit var userViewModel: UserViewModel

    private lateinit var cedulaTextView: TextView
    private lateinit var nombreTextView: TextView
    private lateinit var apellidosTextView: TextView
    private lateinit var emailTextView: TextView

    private lateinit var telefonoEditText: EditText
    private lateinit var agenciaSpinner: Spinner
    private lateinit var subregionSpinner: Spinner
    private lateinit var placaVehiculoSpinner: Spinner
    private lateinit var passwordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var updateButton: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_user, container, false)

        // Inicialización del ViewModel
        userViewModel = ViewModelProvider(this).get(UserViewModel::class.java)

        // Inicialización de vistas
        cedulaTextView = view.findViewById(R.id.tvId)
        nombreTextView = view.findViewById(R.id.tvNombre)
        emailTextView = view.findViewById(R.id.tvEmail)
        telefonoEditText = view.findViewById(R.id.etPhoneNumber)
        agenciaSpinner = view.findViewById(R.id.spinnerAgencia)
        subregionSpinner = view.findViewById(R.id.spinnerSubregion)
        placaVehiculoSpinner = view.findViewById(R.id.spinnerVehiculo)
        passwordEditText = view.findViewById(R.id.etPassword)
        confirmPasswordEditText = view.findViewById(R.id.etConfirmPassword)
        updateButton = view.findViewById(R.id.btnSaveChanges)

        // Cargar los datos del usuario actual
        val userEmail = FirebaseAuth.getInstance().currentUser?.email
        userEmail?.let {
            userViewModel.loadCurrentUser(it)
        }

        // Configuración del ViewModel y observación de datos
        observeUserData()
        setupSaveButton()

        return view
    }

    private fun observeUserData() {
        userViewModel.userData.observe(viewLifecycleOwner) { user ->
            user?.let {
                cedulaTextView.text = it.cedula
                nombreTextView.text = it.nombre
                emailTextView.text = it.email
                telefonoEditText.setText(it.telefono)

                // Cargar subregiones, agencias y vehículos
                userViewModel.loadSubregions()
                userViewModel.subregions.observe(viewLifecycleOwner) { subregions ->
                    setupSpinner(subregionSpinner, subregions) { selectedSubregion ->
                        if (selectedSubregion != "Seleccione una Subregion") {
                            userViewModel.loadAgencies(selectedSubregion)
                        }
                    }
                    subregionSpinner.setSelection(subregions.indexOf(user.subregion))
                }

                userViewModel.agencies.observe(viewLifecycleOwner) { agencies ->
                    setupSpinner(agenciaSpinner, agencies) { selectedAgency ->
                        if (selectedAgency != "Seleccione una Agencia") {
                            userViewModel.loadVehicles(selectedAgency)
                        }
                    }
                    agenciaSpinner.setSelection(agencies.indexOf(it.agencia))
                }

                userViewModel.vehicles.observe(viewLifecycleOwner) { vehicles ->
                    setupSpinner(placaVehiculoSpinner, vehicles, null)
                    placaVehiculoSpinner.setSelection(vehicles.indexOf(it.placaVehiculo))
                }
            }
        }
    }

    private fun setupSaveButton() {
        updateButton.setOnClickListener {
            val currentUser = userViewModel.userData.value ?: return@setOnClickListener

            // Validación de contraseñas
            val newPassword = passwordEditText.text.toString()
            val confirmPassword = confirmPasswordEditText.text.toString()

            if (newPassword.isNotEmpty() || confirmPassword.isNotEmpty()) {
                if (newPassword.length < 8) {
                    Toast.makeText(requireContext(), "La contraseña debe tener al menos 8 caracteres.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (newPassword != confirmPassword) {
                    Toast.makeText(requireContext(), "Las contraseñas no coinciden.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            // Actualización de campos
            val updatedUser = currentUser.copy(
                subregion = if (subregionSpinner.selectedItem.toString() != "Seleccione una Subregion")
                    subregionSpinner.selectedItem.toString() else currentUser.subregion,
                agencia = if (agenciaSpinner.selectedItem.toString() != "Seleccione una Agencia")
                    agenciaSpinner.selectedItem.toString() else currentUser.agencia,
                placaVehiculo = if (placaVehiculoSpinner.selectedItem.toString() != "Seleccione un Vehículo")
                    placaVehiculoSpinner.selectedItem.toString() else currentUser.placaVehiculo,
                password = if (newPassword.isNotEmpty()) newPassword else currentUser.password
            )

            userViewModel.updateUserData(updatedUser)
            Toast.makeText(requireContext(), "Datos actualizados correctamente.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSpinner(
        spinner: Spinner,
        items: List<String>,
        onItemSelected: ((String) -> Unit)?
    ) {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedItem = items[position]
                onItemSelected?.invoke(selectedItem)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
}
