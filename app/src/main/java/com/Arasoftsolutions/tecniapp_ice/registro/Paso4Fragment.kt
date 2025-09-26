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
import androidx.lifecycle.lifecycleScope
import com.Arasoftsolutions.tecniapp_ice.LoginActivity
import com.Arasoftsolutions.tecniapp_ice.R
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.database.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

class Paso4Fragment : Fragment() {

    // ViewModel compartido del flujo de registro
    private lateinit var viewModel: RegistroViewModel

    // UI
    private lateinit var spinnerSubregion: Spinner
    private lateinit var spinnerAgencia: Spinner
    private lateinit var spinnerVehiculo: Spinner
    private lateinit var btnFinishRegistration: MaterialButton

    // Firebase (datos generales)
    private lateinit var subregionsDatabase: DatabaseReference
    private lateinit var agenciesDatabase: DatabaseReference
    private lateinit var vehiclesDatabase: DatabaseReference

    // Listas
    private lateinit var subregions: MutableList<String>
    private lateinit var agencies: MutableList<String>
    private lateinit var vehicles: MutableList<String>

    // ---------- Helpers para claves seguras ----------
    private fun emailKey(email: String): String =
        email.trim().lowercase(Locale.ROOT)
            .replace(".", ",")
            .replace("#", "_")
            .replace("$", "_")
            .replace("[", "_")
            .replace("]", "_")

    private fun phoneKey(phone: String): String =
        phone.filter(Char::isDigit)

    private fun cedulaKey(id: String): String =
        id.filter(Char::isDigit) // solo dígitos, p.ej. "102030405"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_paso_4, container, false)

        // Back arrow
        setNavigationListeners(view)

        // VM compartido
        viewModel = ViewModelProvider(requireActivity())[RegistroViewModel::class.java]

        // Bind UI
        spinnerSubregion = view.findViewById(R.id.spinnerSubregion)
        spinnerAgencia   = view.findViewById(R.id.spinnerAgencia)
        spinnerVehiculo  = view.findViewById(R.id.spinnerVehiculo)
        btnFinishRegistration = view.findViewById(R.id.btnFinishRegistration)
        btnFinishRegistration.isEnabled = false

        // Refs Firebase (datos generales)
        subregionsDatabase = FirebaseDatabase.getInstance("https://tecniapp-ice-datosgenerales.firebaseio.com").getReference("subregiones")
        agenciesDatabase   = FirebaseDatabase.getInstance("https://tecniapp-ice-datosgenerales.firebaseio.com").getReference("agencias")
        vehiclesDatabase   = FirebaseDatabase.getInstance("https://tecniapp-ice-datosgenerales.firebaseio.com").getReference("vehiculos")

        // Carga inicial
        loadSubregions()

        // Listeners de cascada
        spinnerSubregion.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, position: Int, id: Long) {
                try {
                    if (position > 0) {
                        val selectedSubregion = subregions.getOrNull(position) ?: return
                        loadAgencies(selectedSubregion)
                    } else {
                        loadAgencies("Seleccione una Subregion") // reset seguro
                    }
                } catch (e: Exception) {
                    Log.e("Paso4Fragment", "onItemSelected subregión: ${e.message}", e)
                    showToast("No se pudieron cargar agencias. Intenta de nuevo.")
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        spinnerAgencia.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, position: Int, id: Long) {
                try {
                    if (position > 0) {
                        val selectedAgency = agencies.getOrNull(position) ?: return
                        loadVehicles(selectedAgency)
                    } else {
                        loadVehicles("Seleccione una Agencia") // reset
                    }
                } catch (e: Exception) {
                    Log.e("Paso4Fragment", "onItemSelected agencia: ${e.message}", e)
                    showToast("No se pudieron cargar vehículos. Intenta de nuevo.")
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        spinnerVehiculo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, position: Int, id: Long) {
                btnFinishRegistration.isEnabled = position > 0
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Finalizar registro
        btnFinishRegistration.setOnClickListener { finalizeRegistration() }

        return view
    }

    private fun setNavigationListeners(view: View) {
        view.findViewById<ImageView>(R.id.backArrow).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    // ---------- 1) Cargar subregiones ----------
    private fun loadSubregions() {
        subregionsDatabase.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(ds: DataSnapshot) {
                if (!isAdded) return
                subregions = mutableListOf("Seleccione una Subregion")
                for (snap in ds.children) {
                    val nombre = snap.child("nombre").getValue(String::class.java)
                    if (!nombre.isNullOrBlank()) subregions.add(nombre)
                }
                val ctx = context ?: return
                val adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, subregions).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                spinnerSubregion.adapter = adapter
                Log.d("Paso4Fragment", "Subregiones: $subregions")
            }
            override fun onCancelled(error: DatabaseError) {
                if (!isAdded) return
                showToast("Error al cargar subregiones: ${error.message}")
                Log.e("Paso4Fragment", "loadSubregions cancelled: ${error.toException()}")
            }
        })
    }

    // ---------- 2) Cargar agencias según subregión ----------
    private fun loadAgencies(subregion: String) {
        if (subregion == "Seleccione una Subregion") {
            val ctx = context ?: return
            agencies = mutableListOf("Seleccione una Agencia")
            spinnerAgencia.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, agencies).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            // reset vehículos
            vehicles = mutableListOf("Seleccione un Vehículo")
            spinnerVehiculo.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, vehicles).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            btnFinishRegistration.isEnabled = false
            return
        }

        agenciesDatabase.orderByChild("subregion").equalTo(subregion)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(ds: DataSnapshot) {
                    if (!isAdded) return
                    agencies = mutableListOf("Seleccione una Agencia")
                    for (snap in ds.children) {
                        val nombre = snap.child("nombre").getValue(String::class.java)
                        if (!nombre.isNullOrBlank()) agencies.add(nombre)
                    }
                    val ctx = context ?: return
                    spinnerAgencia.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, agencies).apply {
                        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    }

                    if (agencies.size == 1) {
                        Log.w("Paso4Fragment", "Sin agencias para subregión: $subregion")
                        vehicles = mutableListOf("Seleccione un Vehículo")
                        spinnerVehiculo.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, vehicles).apply {
                            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        }
                        btnFinishRegistration.isEnabled = false
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    if (!isAdded) return
                    showToast("Error al cargar agencias: ${error.message}")
                    Log.e("Paso4Fragment", "loadAgencies cancelled: ${error.toException()}")
                }
            })
    }

    // ---------- 3) Cargar vehículos según agencia ----------
    private fun loadVehicles(agency: String) {
        if (agency == "Seleccione una Agencia") {
            val ctx = context ?: return
            vehicles = mutableListOf("Seleccione un Vehículo")
            spinnerVehiculo.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, vehicles).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            btnFinishRegistration.isEnabled = false
            return
        }

        vehiclesDatabase.orderByChild("agencia").equalTo(agency)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(ds: DataSnapshot) {
                    if (!isAdded) return
                    vehicles = mutableListOf("Seleccione un Vehículo")
                    for (snap in ds.children) {
                    val placa = snap.child("placa").value?.toString()
                    if (!placa.isNullOrBlank()) vehicles.add(placa)
}
                    val ctx = context ?: return
                    spinnerVehiculo.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, vehicles).apply {
                        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    }
                    btnFinishRegistration.isEnabled = false // se habilita al elegir
                }
                override fun onCancelled(error: DatabaseError) {
                    if (!isAdded) return
                    showToast("Error al cargar vehículos: ${error.message}")
                    Log.e("Paso4Fragment", "loadVehicles cancelled: ${error.toException()}")
                }
            })
    }

    // ---------- 4) Finalizar registro ----------
    private fun finalizeRegistration() {
        val selectedSubregion = spinnerSubregion.selectedItem as String
        val selectedAgency    = spinnerAgencia.selectedItem as String
        val selectedVehicle   = spinnerVehiculo.selectedItem as String

        if (selectedSubregion == "Seleccione una Subregion" ||
            selectedAgency    == "Seleccione una Agencia" ||
            selectedVehicle   == "Seleccione un Vehículo") {
            showToast("Por favor, completa todos los campos.")
            return
        }

        // Persistir en el VM (por si lo necesitas después)
        viewModel.setDatosAdicionales(selectedSubregion, selectedAgency, selectedVehicle)

        val email     = viewModel.getEmail()?.trim().orEmpty()
        val password  = viewModel.getPassword().orEmpty()
        val nombre    = viewModel.getNombre().orEmpty()
        val apellidos = viewModel.getApellidos().orEmpty()
        val telefono  = viewModel.getTelefono().orEmpty()
        val cedula    = viewModel.getCedula().orEmpty()

        if (email.isBlank() || password.length < 6 || nombre.isBlank() || apellidos.isBlank() ||
            telefono.isBlank() || cedula.isBlank()) {
            showToast("Verifica correo/clave (6+), nombre, apellidos, teléfono y cédula.")
            return
        }

        // Deshabilita para evitar doble click
        btnFinishRegistration.isEnabled = false

        // Crear Auth + multi-escritura atómica (usuarios + emails + phones + idcards)
        viewLifecycleOwner.lifecycleScope.launch {
            val auth = FirebaseAuth.getInstance()
            val dbUsers = FirebaseDatabase
                .getInstance("https://tecniapp-ice-user.firebaseio.com")
                .reference

            try {
                // A) Crear credenciales en Auth
                val user = auth.createUserWithEmailAndPassword(email, password).await().user
                    ?: throw IllegalStateException("No se pudo crear el usuario de autenticación.")
                val uid = user.uid

                // B) Armar perfil (NO guardar password en RTDB). Usa timestamp de servidor.
                val userData = hashMapOf<String, Any?>(
                    "uid"           to uid,
                    "cedula"        to cedula,
                    "email"         to email,
                    "email_lower"   to email.lowercase(Locale.ROOT),
                    "nombre"        to nombre,
                    "apellidos"     to apellidos,
                    "telefono"      to telefono,
                    "subregion"     to selectedSubregion,  // si luego manejas IDs, guarda también subregionId
                    "agencia"       to selectedAgency,     // idem con agenciaId
                    "placaVehiculo" to selectedVehicle,
                    "createdAt"     to ServerValue.TIMESTAMP
                )

                // C) Multi-location update atómico
                val eKey   = emailKey(email)
                val pKey   = phoneKey(telefono)
                val cedKey = cedulaKey(cedula)

                val updates = hashMapOf<String, Any?>(
                    "/usuarios/$uid"    to userData,
                    "/emails/$eKey"     to mapOf("uid" to uid),
                    "/idcards/$cedKey"  to mapOf("uid" to uid) // reclamo de cédula
                )
                if (pKey.isNotBlank()) {
                    updates["/phones/$pKey"] = mapOf("uid" to uid)
                }

                // Si alguno ya existe según reglas, falla todo (consistente)
                dbUsers.updateChildren(updates).await()

                // D) Limpia el código de verificación (si existe) y cierra sesión
                runCatching {
                    dbUsers.child("verificationCodes").child(eKey).removeValue().await()
                }
                auth.signOut()

                // E) OK → navegar a Login
                if (!isAdded) return@launch
                showToast("Registro completado con éxito.")
                val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                requireActivity().finish()

            } catch (t: Throwable) {
                Log.e("Paso4Fragment", "finalizeRegistration error: ${t.message}", t)

                // Mensaje específico si el correo ya existe en Auth
                if (t is FirebaseAuthUserCollisionException) {
                    if (isAdded) showToast("Ese correo ya tiene una cuenta. Inicia sesión o recupera tu contraseña.")
                } else {
                    if (isAdded) showToast("No se pudo completar el registro: ${t.message}")
                }

                // Rollback de Auth para no dejar usuario huérfano si llegó a crearse
                runCatching { FirebaseAuth.getInstance().currentUser?.delete()?.await() }

                if (isAdded) btnFinishRegistration.isEnabled = true
            }
        }
    }


    private fun showToast(message: String) {
        if (!isAdded) return
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
