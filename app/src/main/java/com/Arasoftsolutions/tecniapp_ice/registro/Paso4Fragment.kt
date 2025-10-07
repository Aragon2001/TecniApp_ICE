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
    private lateinit var spinnerRegion: Spinner
    private lateinit var spinnerSubregion: Spinner
    private lateinit var spinnerAgencia: Spinner
    private lateinit var spinnerVehiculo: Spinner
    private lateinit var btnFinishRegistration: MaterialButton

    // Firebase (datos generales)
    private lateinit var regionsDatabase: DatabaseReference
    private lateinit var subregionsDatabase: DatabaseReference
    private lateinit var agenciesDatabase: DatabaseReference
    private lateinit var vehiclesDatabase: DatabaseReference

    private data class RegionItem(val id: String, val nombre: String)
    private data class SubregionItem(val id: String, val nombre: String, val regionId: String)
    private data class AgencyItem(
        val id: String?,
        val nombre: String,
        val regionId: String?,
        val subregionId: String?
    )

    private var regionItems: List<RegionItem> = emptyList()
    private var subregionItems: List<SubregionItem> = emptyList()
    private var filteredSubregions: List<SubregionItem> = emptyList()
    private var filteredAgencies: List<AgencyItem> = emptyList()
    private var vehicles: MutableList<String> = mutableListOf()

    private var selectedRegionItem: RegionItem? = null
    private var selectedSubregionItem: SubregionItem? = null
    private var selectedAgencyItem: AgencyItem? = null

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
        spinnerRegion   = view.findViewById(R.id.spinnerRegion)
        spinnerSubregion = view.findViewById(R.id.spinnerSubregion)
        spinnerAgencia   = view.findViewById(R.id.spinnerAgencia)
        spinnerVehiculo  = view.findViewById(R.id.spinnerVehiculo)
        btnFinishRegistration = view.findViewById(R.id.btnFinishRegistration)
        btnFinishRegistration.isEnabled = false

        setupInitialSpinners()

        // Refs Firebase (datos generales)
        regionsDatabase    = FirebaseDatabase.getInstance("https://tecniapp-ice-datosgenerales.firebaseio.com").getReference("regiones")
        subregionsDatabase = FirebaseDatabase.getInstance("https://tecniapp-ice-datosgenerales.firebaseio.com").getReference("subregiones")
        agenciesDatabase   = FirebaseDatabase.getInstance("https://tecniapp-ice-datosgenerales.firebaseio.com").getReference("agencias")
        vehiclesDatabase   = FirebaseDatabase.getInstance("https://tecniapp-ice-datosgenerales.firebaseio.com").getReference("vehiculos")

        // Carga inicial
        loadRegions()

        // Listeners de cascada
        spinnerRegion.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, position: Int, id: Long) {
                selectedRegionItem = if (position > 0) regionItems.getOrNull(position - 1) else null
                selectedSubregionItem = null
                selectedAgencyItem = null
                updateSubregionSpinner()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        spinnerSubregion.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, position: Int, id: Long) {
                try {
                    selectedSubregionItem = if (position > 0) filteredSubregions.getOrNull(position - 1) else null
                    selectedAgencyItem = null
                    updateAgencySpinner(emptyList())
                    loadAgencies(selectedSubregionItem)
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
                    selectedAgencyItem = if (position > 0) filteredAgencies.getOrNull(position - 1) else null
                    loadVehicles(selectedAgencyItem)
                } catch (e: Exception) {
                    Log.e("Paso4Fragment", "onItemSelected agencia: ${e.message}", e)
                    showToast("No se pudieron cargar vehículos. Intenta de nuevo.")
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        spinnerVehiculo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, position: Int, id: Long) {
                btnFinishRegistration.isEnabled = position > 0 && vehicles.getOrNull(position).isNullOrBlank().not()
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

    private fun setupInitialSpinners() {
        updateRegionSpinner()
        updateSubregionSpinner()
        updateAgencySpinner(emptyList())
        resetVehiclesSpinner()
    }

    private fun updateRegionSpinner() {
        val ctx = context ?: return
        val labels = mutableListOf("Seleccione una Región")
        labels += regionItems.map { it.nombre }
        spinnerRegion.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, labels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerRegion.setSelection(0)
    }

    private fun updateSubregionSpinner() {
        val ctx = context ?: return
        filteredSubregions = selectedRegionItem?.let { region ->
            subregionItems.filter { it.regionId.equals(region.id, ignoreCase = true) }
        }?.sortedBy { it.nombre } ?: emptyList()

        val labels = mutableListOf("Seleccione una Subregión")
        labels += filteredSubregions.map { it.nombre }

        spinnerSubregion.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, labels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerSubregion.setSelection(0)
        updateAgencySpinner(emptyList())
    }

    private fun updateAgencySpinner(newItems: List<AgencyItem>) {
        val ctx = context ?: return
        filteredAgencies = newItems.sortedBy { it.nombre }
        val labels = mutableListOf("Seleccione una Agencia")
        labels += filteredAgencies.map { it.nombre }
        spinnerAgencia.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, labels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerAgencia.setSelection(0)
        resetVehiclesSpinner()
    }

    private fun resetVehiclesSpinner() {
        val ctx = context ?: return
        vehicles = mutableListOf("Seleccione un Vehículo")
        spinnerVehiculo.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, vehicles).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerVehiculo.setSelection(0)
        btnFinishRegistration.isEnabled = false
    }

    private fun loadRegions() {
        regionsDatabase.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(ds: DataSnapshot) {
                if (!isAdded) return
                regionItems = ds.children.mapNotNull { snap ->
                    val id = snap.child("id").getValue(String::class.java)?.trim()
                        ?: snap.key?.trim()
                    val nombre = snap.child("nombre").getValue(String::class.java)?.trim()
                    if (id.isNullOrBlank() || nombre.isNullOrBlank()) {
                        null
                    } else {
                        RegionItem(id, nombre)
                    }
                }.sortedBy { it.nombre }
                updateRegionSpinner()
                loadSubregions()
            }

            override fun onCancelled(error: DatabaseError) {
                if (!isAdded) return
                showToast("Error al cargar regiones: ${error.message}")
                Log.e("Paso4Fragment", "loadRegions cancelled: ${error.toException()}")
            }
        })
    }

    private fun loadSubregions() {
        subregionsDatabase.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(ds: DataSnapshot) {
                if (!isAdded) return
                subregionItems = ds.children.mapNotNull { snap ->
                    val id = snap.child("id").getValue(String::class.java)?.trim()
                        ?: snap.key?.trim()
                    val nombre = snap.child("nombre").getValue(String::class.java)?.trim()
                    val regionId = snap.child("region_id").getValue(String::class.java)
                        ?: snap.child("regionId").getValue(String::class.java)
                        ?: snap.child("region").getValue(String::class.java)
                        ?: ""
                    val trimmedId = id
                    val trimmedNombre = nombre
                    val trimmedRegion = regionId.trim()
                    if (trimmedId.isNullOrEmpty() || trimmedNombre.isNullOrEmpty()) {
                        null
                    } else {
                        SubregionItem(trimmedId, trimmedNombre, trimmedRegion)
                    }
                }
                updateSubregionSpinner()
            }

            override fun onCancelled(error: DatabaseError) {
                if (!isAdded) return
                showToast("Error al cargar subregiones: ${error.message}")
                Log.e("Paso4Fragment", "loadSubregions cancelled: ${error.toException()}")
            }
        })
    }

    private fun loadAgencies(subregion: SubregionItem?) {
        if (subregion == null) {
            updateAgencySpinner(emptyList())
            return
        }

        agenciesDatabase.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(ds: DataSnapshot) {
                if (!isAdded) return
                val agencies = ds.children.mapNotNull { snap ->
                    val nombre = snap.child("nombre").getValue(String::class.java)?.trim()
                    if (nombre.isNullOrEmpty()) return@mapNotNull null

                    val id = snap.child("id").getValue(String::class.java)?.trim()
                        ?: snap.key?.trim()
                    val regionId = snap.child("region_id").getValue(String::class.java)
                        ?: snap.child("regionId").getValue(String::class.java)
                        ?: snap.child("region").getValue(String::class.java)
                    val subregionValue = snap.child("subregion").getValue(String::class.java)
                        ?: snap.child("subregion_id").getValue(String::class.java)
                        ?: snap.child("subregionId").getValue(String::class.java)

                    val matchesSubregion = agencyMatchesSubregion(subregionValue, subregion)
                    val matchesRegion = regionId.isNullOrBlank() || subregion.regionId.equals(regionId.trim(), ignoreCase = true)

                    if (!matchesSubregion && !matchesRegion) {
                        null
                    } else {
                        AgencyItem(id?.takeIf { it.isNotBlank() }, nombre, regionId?.trim(), subregionValue?.trim())
                    }
                }

                if (agencies.isEmpty()) {
                    updateAgencySpinner(emptyList())
                    return
                }

                updateAgencySpinner(agencies)
            }

            override fun onCancelled(error: DatabaseError) {
                if (!isAdded) return
                showToast("Error al cargar agencias: ${error.message}")
                Log.e("Paso4Fragment", "loadAgencies cancelled: ${error.toException()}")
            }
        })
    }

    private fun loadVehicles(agency: AgencyItem?) {
        if (agency == null) {
            resetVehiclesSpinner()
            return
        }

        vehiclesDatabase.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(ds: DataSnapshot) {
                if (!isAdded) return
                vehicles = mutableListOf("Seleccione un Vehículo")
                for (snap in ds.children) {
                    val agencyValues = listOfNotNull(
                        snap.child("agencia").getValue(String::class.java)?.trim(),
                        snap.child("agencia_id").getValue(String::class.java)?.trim(),
                        snap.child("agenciaId").getValue(String::class.java)?.trim()
                    ).filter { it.isNotEmpty() }

                    if (agencyValues.isEmpty()) continue

                    if (!vehicleMatchesAgency(agencyValues, agency)) continue

                    val subregionValue = snap.child("subregion").getValue(String::class.java)
                        ?: snap.child("subregion_id").getValue(String::class.java)
                        ?: snap.child("subregionId").getValue(String::class.java)
                    val matchesSubregion = selectedSubregionItem?.let { sub ->
                        val normalized = subregionValue?.trim().orEmpty()
                        normalized.isEmpty() ||
                                normalized.equals(sub.id, ignoreCase = true) ||
                                normalized.equals(sub.nombre, ignoreCase = true)
                    } ?: true

                    if (!matchesSubregion) continue

                    val placa = snap.child("placa").value?.toString()?.trim()
                    if (!placa.isNullOrBlank()) {
                        vehicles.add(placa)
                    }
                }

                val ctx = context ?: return
                spinnerVehiculo.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, vehicles).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                spinnerVehiculo.setSelection(0)
                btnFinishRegistration.isEnabled = false
            }

            override fun onCancelled(error: DatabaseError) {
                if (!isAdded) return
                showToast("Error al cargar vehículos: ${error.message}")
                Log.e("Paso4Fragment", "loadVehicles cancelled: ${error.toException()}")
            }
        })
    }

    private fun agencyMatchesSubregion(value: String?, subregion: SubregionItem): Boolean {
        val normalized = value?.trim().orEmpty()
        if (normalized.isEmpty()) return false
        return normalized.equals(subregion.id, ignoreCase = true) ||
                normalized.equals(subregion.nombre, ignoreCase = true)
    }

    private fun vehicleMatchesAgency(values: List<String>, agency: AgencyItem): Boolean {
        if (values.isEmpty()) return false
        val agencyId = agency.id?.trim()
        val agencyName = agency.nombre.trim()
        return values.any { value ->
            value.equals(agencyName, ignoreCase = true) ||
                    (agencyId != null && value.equals(agencyId, ignoreCase = true))
        }
    }

    // ---------- 4) Finalizar registro ----------
    private fun finalizeRegistration() {
        val regionItem = selectedRegionItem ?: run {
            showToast("Por favor, selecciona una región.")
            return
        }
        val subregionItem = selectedSubregionItem ?: run {
            showToast("Por favor, selecciona una subregión.")
            return
        }
        val agencyItem = selectedAgencyItem ?: run {
            showToast("Por favor, selecciona una agencia.")
            return
        }
        val selectedVehicle = (spinnerVehiculo.selectedItem as? String)?.takeIf { it.isNotBlank() && it != "Seleccione un Vehículo" }
            ?: run {
                showToast("Por favor, selecciona un vehículo.")
                return
            }

        // Persistir en el VM (por si lo necesitas después)
        viewModel.setDatosAdicionales(
            regionItem.id,
            regionItem.nombre,
            subregionItem.id,
            subregionItem.nombre,
            agencyItem.id,
            agencyItem.nombre,
            selectedVehicle
        )

        val email     = viewModel.getEmail()?.trim().orEmpty()
        val password  = viewModel.getPassword().orEmpty()
        val nombre    = viewModel.getNombre().orEmpty()
        val primerApellido = viewModel.getPrimerApellido().orEmpty()
        val segundoApellido = viewModel.getSegundoApellido().orEmpty()
        val apellidos = viewModel.getApellidosCompletos().orEmpty()
        val telefono  = viewModel.getTelefono().orEmpty()
        val cedula    = viewModel.getCedula().orEmpty()

        if (email.isBlank() || password.length < 6 || nombre.isBlank() ||
            primerApellido.isBlank() || segundoApellido.isBlank() || apellidos.isBlank() ||
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
                    "primer_apellido" to primerApellido,
                    "segundo_apellido" to segundoApellido,
                    "telefono"      to telefono,
                    "region"        to regionItem.id,
                    "region_nombre" to regionItem.nombre,
                    "subregion"     to subregionItem.id,
                    "subregion_nombre" to subregionItem.nombre,
                    "agencia"       to agencyItem.nombre,
                    "agencia_id"    to agencyItem.id,
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
