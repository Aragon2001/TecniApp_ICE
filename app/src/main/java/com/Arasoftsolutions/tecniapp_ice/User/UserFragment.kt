package com.Arasoftsolutions.tecniapp_ice.User

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.Arasoftsolutions.tecniapp_ice.R

class UserFragment : Fragment() {

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
    private lateinit var updateButton: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_user, container, false)

        cedulaTextView = view.findViewById(R.id.tvId)
        nombreTextView = view.findViewById(R.id.tvNombre)
        emailTextView = view.findViewById(R.id.tvEmail)
        telefonoEditText = view.findViewById(R.id.etPhoneNumber)
        agenciaSpinner = view.findViewById(R.id.spinnerAgencia)
        subregionSpinner = view.findViewById(R.id.spinnerSubregion)
        placaVehiculoSpinner = view.findViewById(R.id.spinnerVehiculo)
        passwordEditText = view.findViewById(R.id.etPassword)
        updateButton = view.findViewById(R.id.btnSaveChanges)

        userViewModel = ViewModelProvider(this).get(UserViewModel::class.java)

        // Cargar datos del usuario
        userViewModel.userData.observe(viewLifecycleOwner) { user ->
            user?.let {
                displayUserData(it)
            } ?: run {
                Toast.makeText(requireContext(), "No se encontró el usuario.", Toast.LENGTH_SHORT).show()
            }
        }

        // Cargar Spinners
        userViewModel.loadSubregions()

        userViewModel.subregions.observe(viewLifecycleOwner) { subregions ->
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, subregions)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            subregionSpinner.adapter = adapter

            // Cuando se carguen las subregiones, carga las agencias
            val selectedSubregion = subregionSpinner.selectedItem.toString()
            userViewModel.loadAgencies(selectedSubregion)
        }

        userViewModel.agencies.observe(viewLifecycleOwner) { agencies ->
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, agencies)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            agenciaSpinner.adapter = adapter

            // Cuando se carguen las agencias, carga los vehículos
            val selectedAgency = agenciaSpinner.selectedItem.toString()
            userViewModel.loadVehicles(selectedAgency)
        }

        userViewModel.vehicles.observe(viewLifecycleOwner) { vehicles ->
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, vehicles)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            placaVehiculoSpinner.adapter = adapter
        }

        updateButton.setOnClickListener {
            updateUser()
        }

        return view
    }

    private fun displayUserData(user: User) {
        cedulaTextView.text = user.cedula
        nombreTextView.text = user.nombre
        apellidosTextView.text = user.apellidos
        emailTextView.text = user.email
        telefonoEditText.setText(user.telefono)
        setSpinnerSelection(agenciaSpinner, user.agencia)
        setSpinnerSelection(subregionSpinner, user.subregion)
        setSpinnerSelection(placaVehiculoSpinner, user.placaVehiculo)
    }

    private fun setSpinnerSelection(spinner: Spinner, value: String) {
        val adapter = spinner.adapter
        for (i in 0 until adapter.count) {
            if (adapter.getItem(i).toString() == value) {
                spinner.setSelection(i)
                break
            }
        }
    }

    private fun updateUser() {
        if (telefonoEditText.text.isEmpty()) {
            Toast.makeText(requireContext(), "Por favor, complete todos los campos.", Toast.LENGTH_SHORT).show()
            return
        }

        val user = User(
            cedula = cedulaTextView.text.toString(),
            nombre = nombreTextView.text.toString(),
            apellidos = apellidosTextView.text.toString(),
            email = emailTextView.text.toString(),
            telefono = telefonoEditText.text.toString(),
            agencia = agenciaSpinner.selectedItem.toString(),
            subregion = subregionSpinner.selectedItem.toString(),
            placaVehiculo = placaVehiculoSpinner.selectedItem.toString()
        )

        userViewModel.updateUserData(user)

        val newPassword = passwordEditText.text.toString()
        if (newPassword.isNotEmpty()) {
            userViewModel.updatePassword(newPassword)
        }

        Toast.makeText(requireContext(), "Datos actualizados correctamente", Toast.LENGTH_SHORT).show()
    }
}
