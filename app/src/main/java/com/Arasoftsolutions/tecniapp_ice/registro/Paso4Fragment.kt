package com.Arasoftsolutions.tecniapp_ice.registro

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.Arasoftsolutions.tecniapp_ice.LoginActivity
import com.Arasoftsolutions.tecniapp_ice.R
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class Paso4Fragment : Fragment() {

    // ViewModel para manejar el estado compartido entre fragmentos
    private lateinit var viewModel: RegistroViewModel

    // Spinners para las subregiones, agencias y vehículos
    private lateinit var spinnerSubregion: Spinner
    private lateinit var spinnerAgencia: Spinner
    private lateinit var spinnerVehiculo: Spinner

    // Botón de finalización de registro
    private lateinit var btnFinishRegistration: MaterialButton

    // Referencias a la base de datos de Firebase
    private lateinit var subregionsDatabase: DatabaseReference
    private lateinit var agenciesDatabase: DatabaseReference
    private lateinit var vehiclesDatabase: DatabaseReference

    // Listas para almacenar las subregiones, agencias y vehículos cargados
    private lateinit var subregions: MutableList<String>
    private lateinit var agencies: MutableList<String>
    private lateinit var vehicles: MutableList<String>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflar la vista del fragmento
        val view = inflater.inflate(R.layout.fragment_paso_4, container, false)

        // Inicializar la navegación (flecha de retroceso)
        setNavigationListeners(view)

        // Inicializar el ViewModel
        viewModel = ViewModelProvider(requireActivity()).get(RegistroViewModel::class.java)

        // Encontrar los Spinners y el botón en la vista
        spinnerSubregion = view.findViewById(R.id.spinnerSubregion)
        spinnerAgencia = view.findViewById(R.id.spinnerAgencia)
        spinnerVehiculo = view.findViewById(R.id.spinnerVehiculo)
        btnFinishRegistration = view.findViewById(R.id.btnFinishRegistration)

        // Deshabilitar el botón de finalización hasta que todos los campos estén seleccionados
        btnFinishRegistration.isEnabled = false

        //
        // Inicializar las referencias de Firebase
subregionsDatabase = FirebaseDatabase.getInstance("https://tecniapp-ice-datosgenerales.firebaseio.com").getReference("subregiones")
agenciesDatabase = FirebaseDatabase.getInstance("https://tecniapp-ice-datosgenerales.firebaseio.com").getReference("agencias")
vehiclesDatabase = FirebaseDatabase.getInstance("https://tecniapp-ice-datosgenerales.firebaseio.com").getReference("vehiculos")



        // Cargar las subregiones desde Firebase
        loadSubregions()

        // Configurar el comportamiento del spinner de subregiones
        spinnerSubregion.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                if (position > 0) { // Evitar que el usuario seleccione el valor por defecto
                    val selectedSubregion = subregions[position]
                    loadAgencies(selectedSubregion) // Cargar agencias para la subregión seleccionada
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Configurar el comportamiento del spinner de agencias
        spinnerAgencia.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                if (position > 0) { // Evitar que el usuario seleccione el valor por defecto
                    val selectedAgency = agencies[position]
                    loadVehicles(selectedAgency) // Cargar vehículos para la agencia seleccionada
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Configurar el comportamiento del spinner de vehículos
        spinnerVehiculo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                if (position > 0) {
                    // Habilitar el botón de finalización solo cuando todos los campos están seleccionados
                    btnFinishRegistration.isEnabled = true
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Configurar el clic del botón de finalización de registro
        btnFinishRegistration.setOnClickListener {
            finalizeRegistration() // Finalizar el registro cuando el botón sea presionado
        }

        return view
    }

    // Método para configurar la flecha de retroceso
    private fun setNavigationListeners(view: View) {
        view.findViewById<ImageView>(R.id.backArrow).setOnClickListener {
            requireActivity().onBackPressed() // Navegar hacia atrás cuando se presione la flecha
        }
    }

    // Método para cargar las subregiones desde Firebase
    private fun loadSubregions() {
        subregionsDatabase.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                // Inicializar la lista de subregiones con un valor por defecto
                subregions = mutableListOf("Seleccione una Subregion")
                for (subregionSnapshot in dataSnapshot.children) {
                    // Obtener el nombre de la subregión y agregarlo a la lista
                    val subregionName = subregionSnapshot.child("nombre").getValue(String::class.java)
                    if (subregionName != null) {
                        subregions.add(subregionName)
                    }
                }
                // Configurar el adaptador del spinner
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, subregions)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerSubregion.adapter = adapter

                // Log para depuración
                Log.d("Paso4Fragment", "Subregiones cargadas: $subregions")
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Mostrar un mensaje de error si falla la carga de subregiones
                showToast("Error al cargar subregiones: ${databaseError.message}")
            }
        })
    }

    // Método para cargar las agencias basadas en la subregión seleccionada
    private fun loadAgencies(subregion: String) {
        agenciesDatabase.orderByChild("subregion").equalTo(subregion).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                // Inicializar la lista de agencias con un valor por defecto
                agencies = mutableListOf("Seleccione una Agencia")
                for (agencySnapshot in dataSnapshot.children) {
                    // Obtener el nombre de la agencia y agregarlo a la lista
                    val agencyName = agencySnapshot.child("nombre").getValue(String::class.java)
                    if (agencyName != null) {
                        agencies.add(agencyName)
                    }
                }
                // Configurar el adaptador del spinner
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, agencies)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerAgencia.adapter = adapter
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Mostrar un mensaje de error si falla la carga de agencias
                showToast("Error al cargar agencias: ${databaseError.message}")
            }
        })
    }

    // Método para cargar los vehículos basados en la agencia seleccionada
    private fun loadVehicles(agency: String) {
        vehiclesDatabase.orderByChild("agencia").equalTo(agency).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                // Inicializar la lista de vehículos con un valor por defecto
                vehicles = mutableListOf("Seleccione un Vehículo")
                for (vehicleSnapshot in dataSnapshot.children) {
                    // Obtener la placa del vehículo y agregarla a la lista
                    val vehiclePlate = vehicleSnapshot.child("placa").getValue(String::class.java)
                    if (vehiclePlate != null) {
                        vehicles.add(vehiclePlate)
                    }
                }
                // Configurar el adaptador del spinner
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, vehicles)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerVehiculo.adapter = adapter
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Mostrar un mensaje de error si falla la carga de vehículos
                showToast("Error al cargar vehículos: ${databaseError.message}")
            }
        })
    }

    // Método para mostrar un Toast con un mensaje
    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    // Método para finalizar el registro cuando se presiona el botón


private fun finalizeRegistration() {
    // Obtener los valores seleccionados en los spinners
    val selectedSubregion = spinnerSubregion.selectedItem as String
    val selectedAgency = spinnerAgencia.selectedItem as String
    val selectedVehicle = spinnerVehiculo.selectedItem as String

    // Validar que se haya seleccionado un valor válido en cada spinner
    if (selectedSubregion == "Seleccione una Subregion" ||
        selectedAgency == "Seleccione una Agencia" ||
        selectedVehicle == "Seleccione un Vehículo") {
        // Mostrar un mensaje si faltan campos por completar
        showToast("Por favor, completa todos los campos.")
        return
    }

    // Obtener datos del ViewModel
    val email = viewModel.getEmail()
    val password = viewModel.getPassword()
    val nombre = viewModel.getNombre()
    val apellidos = viewModel.getApellidos()
    val telefono = viewModel.getTelefono()
    val cedula = viewModel.getCedula()

    // Validar que la cédula no sea nula
    if (cedula.isNullOrEmpty()) {
        showToast("La cédula no puede estar vacía.")
        return
    }

    // Guardar el usuario en Firebase Authentication
    if (email != null && password != null) {
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Usar la cédula como ID del usuario en la base de datos
                    val userId = cedula // Aquí usamos la cédula como ID

                    // Crear el objeto con los datos del usuario (sin el password)
                    val userData = hashMapOf(
                        "nombre" to nombre,
                        "apellidos" to apellidos,
                        "email" to email,
                        "telefono" to telefono,
                        "subregion" to selectedSubregion,
                        "agencia" to selectedAgency,
                        "placa_vehiculo" to selectedVehicle, // Asegúrate de que este campo esté correctamente asignado
                        "cedula" to cedula
                    )

                    // Guardar los datos del usuario en la base de datos de Firebase
                    val userDatabase = FirebaseDatabase.getInstance("https://tecniapp-ice-user.firebaseio.com/")
                        .getReference("usuarios")
                        .child(userId) // Usar cédula como clave

                    userDatabase.setValue(userData)
                        .addOnCompleteListener { userTask ->
                            if (userTask.isSuccessful) {
                                showToast("Registro completado con éxito.")

                                // Redirigir al login tras completar el registro
                                val intent = Intent(requireContext(), LoginActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // Limpiar el historial de actividades
                                startActivity(intent)
                                requireActivity().finish() // Finalizar la actividad actual
                            } else {
                                showToast("Error al guardar los datos del usuario: ${userTask.exception?.message}")
                            }
                        }
                } else {
                    showToast("Error en la creación de la cuenta: ${task.exception?.message}")
                }
            }
    } else {
        showToast("Por favor, verifica que todos los campos están completos.")
    }
}




    }
