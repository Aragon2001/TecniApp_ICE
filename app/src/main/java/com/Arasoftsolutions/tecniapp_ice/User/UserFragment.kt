package com.Arasoftsolutions.tecniapp_ice.User

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.Arasoftsolutions.tecniapp_ice.R

// ✅ Mantén tu sincronizador (asegúrate que internamente ya use Room y no SQLite)
import com.Arasoftsolutions.tecniapp_ice.Database.sync.Synchronizer
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriasRepository
// import com.Arasoftsolutions.tecniapp_ice.data.UserEntity  // <- si lo necesitas explícito

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch

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
    ): View {
        val view = inflater.inflate(R.layout.fragment_user, container, false)

        // ViewModel (mantengo tu manera actual; si luego quieres inyectar repo de Room, usamos Factory)
        userViewModel = ViewModelProvider(this).get(UserViewModel::class.java)

        // Vistas
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

        // Cargar datos de usuario por email desde Firebase (online-first, como quieres)
        val userEmail = FirebaseAuth.getInstance().currentUser?.email
        userEmail?.let { userViewModel.loadCurrentUser(it) }

        observeUserData()
        setupSaveButton()

        return view
    }

    private fun observeUserData() {
        userViewModel.userEntityData.observe(viewLifecycleOwner) { user ->
            user?.let {
                cedulaTextView.text = it.cedula
                nombreTextView.text = it.nombre
                emailTextView.text = it.email
                telefonoEditText.setText(it.telefono)

                // Cargar catálogos desde Firebase (tu lógica actual)
                userViewModel.loadSubregions()

                userViewModel.subregions.observe(viewLifecycleOwner) { subregions ->
                    setupSpinner(subregionSpinner, subregions) { selectedSubregion ->
                        if (selectedSubregion != "Seleccione una Subregion") {
                            userViewModel.loadAgencies(selectedSubregion)
                        }
                    }
                    // Intentar colocar en el ítem correcto si ya viene de Firebase/Room
                    val idx = subregions.indexOf(user.subregion)
                    if (idx >= 0) subregionSpinner.setSelection(idx)
                }

                userViewModel.agencies.observe(viewLifecycleOwner) { agencies ->
                    setupSpinner(agenciaSpinner, agencies) { selectedAgency ->
                        if (selectedAgency != "Seleccione una Agencia") {
                            userViewModel.loadVehicles(selectedAgency)
                        }
                    }
                    val idx = agencies.indexOf(it.agencia)
                    if (idx >= 0) agenciaSpinner.setSelection(idx)
                }

                userViewModel.vehicles.observe(viewLifecycleOwner) { vehicles ->
                    setupSpinner(placaVehiculoSpinner, vehicles, null)
                    val idx = vehicles.indexOf(it.placaVehiculo)
                    if (idx >= 0) placaVehiculoSpinner.setSelection(idx)
                }
            }
        }
    }

    private fun setupSaveButton() {
        updateButton.setOnClickListener {
            val currentUser = userViewModel.userEntityData.value ?: return@setOnClickListener

            val newPassword = passwordEditText.text.toString()
            val confirmPassword = confirmPasswordEditText.text.toString()

            // ✅ Validaciones de contraseña (opcional)
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

            // ✅ Construimos el User actualizado (mantenemos tu lógica)
            val updatedUser = currentUser.copy(
                telefono = telefonoEditText.text.toString(),
                subregion = if (subregionSpinner.selectedItem.toString() != "Seleccione una Subregion")
                    subregionSpinner.selectedItem.toString() else currentUser.subregion,
                agencia = if (agenciaSpinner.selectedItem.toString() != "Seleccione una Agencia")
                    agenciaSpinner.selectedItem.toString() else currentUser.agencia,
                placaVehiculo = if (placaVehiculoSpinner.selectedItem.toString() != "Seleccione un Vehículo")
                    placaVehiculoSpinner.selectedItem.toString() else currentUser.placaVehiculo,
                password = if (newPassword.isNotEmpty()) newPassword else currentUser.password
            )

            // 1) ✅ ACTUALIZA EN FIREBASE (fuente de verdad para verificación)
            val databaseRef = FirebaseDatabase
                .getInstance("https://tecniapp-ice-user.firebaseio.com")
                .getReference("usuarios")
                .child(currentUser.uid.toString())

            databaseRef.setValue(updatedUser)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Actualizado en Firebase", Toast.LENGTH_SHORT).show()

                    // 2) ✅ GUARDA EN ROOM (cache/offline) — SIN SQLite
                    //    Asegúrate que AppDatabase y UsuarioDao estén en el paquete 'com.Arasoftsolutions.tecniapp_ice.data'
                    //    y que tu UsuarioDao tenga un método upsert(user) con OnConflict=REPLACE.
                    viewLifecycleOwner.lifecycleScope.launch {
                        val db = AppDatabase.getInstance(requireContext())
                        val dao = db.usuarioDao()
                        dao.upsert(updatedUser)
                    }

                    // 3) ✅ REFRESCA UI (tu VM)
                    userViewModel.updateUserData(updatedUser)

                    // 4) ✅ DISPARA SINCRONIZACIÓN GLOBAL (Firebase → Room para catálogos/medidores, etc.)
                    val ctx = requireContext()
                    val db = AppDatabase.getInstance(ctx)
                    val sync = Synchronizer(
                        RoomRepository.getInstance(ctx),
                        AveriasRepository(db)
                    )
                    viewLifecycleOwner.lifecycleScope.launch {
                        sync.syncSubregion(
                            updatedUser.subregion.toString(),
                            onSyncStart = { },
                            onSyncProgress = { _, _, _ -> },
                            onSyncSuccess = {},
                            onSyncError = { e -> Log.e("UserFragment", "Sync error", e) }
                        )
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Error al actualizar en Firebase", Toast.LENGTH_LONG).show()
                    Log.e("UserFragment", "Firebase update failed: ${it.message}")
                }
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
