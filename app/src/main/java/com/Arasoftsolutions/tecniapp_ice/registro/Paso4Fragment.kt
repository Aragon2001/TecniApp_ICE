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
import android.widget.ProgressBar
import android.widget.Spinner
import java.text.Normalizer
import android.widget.Toast
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

// FIX: hereda de BaseRegistroFragment
class Paso4Fragment : BaseRegistroFragment() {

    private lateinit var viewModel: RegistroViewModel

    private var _progressBar: ProgressBar? = null
    private var _spinnerRegion: Spinner? = null
    private var _spinnerSubregion: Spinner? = null
    private var _spinnerAgencia: Spinner? = null
    private var _spinnerVehiculo: Spinner? = null
    private var _btnFinishRegistration: MaterialButton? = null

    private val progressBar get() = requireNotNull(_progressBar)
    private val spinnerRegion get() = requireNotNull(_spinnerRegion)
    private val spinnerSubregion get() = requireNotNull(_spinnerSubregion)
    private val spinnerAgencia get() = requireNotNull(_spinnerAgencia)
    private val spinnerVehiculo get() = requireNotNull(_spinnerVehiculo)
    private val btnFinishRegistration get() = requireNotNull(_btnFinishRegistration)

    private lateinit var regionsDatabase: DatabaseReference
    private lateinit var subregionsDatabase: DatabaseReference
    private lateinit var agenciesDatabase: DatabaseReference
    private lateinit var vehiclesDatabase: DatabaseReference

    private data class RegionItem(val id: String, val nombre: String)
    private data class SubregionItem(val id: String, val nombre: String, val regionId: String)
    private data class AgencyItem(val id: String?, val nombre: String, val regionId: String?, val subregionId: String?)

    private var regionItems: List<RegionItem> = emptyList()
    private var subregionItems: List<SubregionItem> = emptyList()
    private var filteredSubregions: List<SubregionItem> = emptyList()
    private var filteredAgencies: List<AgencyItem> = emptyList()
    private var vehicles: MutableList<String> = mutableListOf()

    private var selectedRegionItem: RegionItem? = null
    private var selectedSubregionItem: SubregionItem? = null
    private var selectedAgencyItem: AgencyItem? = null

    private fun normTag(x: String): String {
        val upper = x.trim().uppercase(Locale.ROOT)
        val noAccents = Normalizer.normalize(upper, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noAccents
            .replace(Regex("[^A-Z0-9]+"), "_")
            .replace(Regex("^_+|_+$"), "")
    }

    private fun emailKey(email: String): String =
        email.trim().lowercase(Locale.ROOT)
            .replace(".", ",").replace("#", "_").replace("$", "_")
            .replace("[", "_").replace("]", "_")

    private fun phoneKey(phone: String): String = phone.filter(Char::isDigit)
    private fun cedulaKey(id: String): String = id.filter(Char::isDigit)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_paso_4, container, false)

        setNavigationListeners(view)

        viewModel = ViewModelProvider(requireActivity())[RegistroViewModel::class.java]

        _progressBar = view.findViewById(R.id.progressBar)
        _spinnerRegion = view.findViewById(R.id.spinnerRegion)
        _spinnerSubregion = view.findViewById(R.id.spinnerSubregion)
        _spinnerAgencia = view.findViewById(R.id.spinnerAgencia)
        _spinnerVehiculo = view.findViewById(R.id.spinnerVehiculo)
        _btnFinishRegistration = view.findViewById(R.id.btnFinishRegistration)
        btnFinishRegistration.isEnabled = false

        setupInitialSpinners()

        regionsDatabase    = FirebaseDatabase.getInstance("https://tecniapp-ice-datosgenerales.firebaseio.com").getReference("regiones")
        subregionsDatabase = FirebaseDatabase.getInstance("https://tecniapp-ice-datosgenerales.firebaseio.com").getReference("subregiones")
        agenciesDatabase   = FirebaseDatabase.getInstance("https://tecniapp-ice-datosgenerales.firebaseio.com").getReference("agencias")
        vehiclesDatabase   = FirebaseDatabase.getInstance("https://tecniapp-ice-datosgenerales.firebaseio.com").getReference("vehiculos")

        loadRegions()

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
                if (!isFragmentAlive()) return
                btnFinishRegistration.isEnabled = position > 0 && vehicles.getOrNull(position).isNullOrBlank().not()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        btnFinishRegistration.setOnClickListener { finalizeRegistration() }

        return view
    }

    // FIX: animar barra al 100% al entrar al paso 4
    override fun onResume() {
        super.onResume()
        _progressBar?.let { animateProgress(it, 100) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _spinnerRegion?.onItemSelectedListener = null
        _spinnerSubregion?.onItemSelectedListener = null
        _spinnerAgencia?.onItemSelectedListener = null
        _spinnerVehiculo?.onItemSelectedListener = null
        _spinnerRegion?.adapter = null
        _spinnerSubregion?.adapter = null
        _spinnerAgencia?.adapter = null
        _spinnerVehiculo?.adapter = null
        _btnFinishRegistration?.setOnClickListener(null)
        _progressBar = null
        _spinnerRegion = null
        _spinnerSubregion = null
        _spinnerAgencia = null
        _spinnerVehiculo = null
        _btnFinishRegistration = null
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
        _btnFinishRegistration?.isEnabled = false
    }

    private fun loadRegions() {
        regionsDatabase.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(ds: DataSnapshot) {
                if (!isFragmentAlive()) return
                regionItems = ds.children.mapNotNull { snap ->
                    val id = snap.child("id").getValue(String::class.java)?.trim() ?: snap.key?.trim()
                    val nombre = snap.child("nombre").getValue(String::class.java)?.trim()
                    if (id.isNullOrBlank() || nombre.isNullOrBlank()) null
                    else RegionItem(id, nombre)
                }.sortedBy { it.nombre }
                updateRegionSpinner()
                loadSubregions()
            }

            override fun onCancelled(error: DatabaseError) {
                if (!isFragmentAlive()) return
                showToast("Error al cargar regiones: ${error.message}")
            }
        })
    }

    private fun loadSubregions() {
        subregionsDatabase.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(ds: DataSnapshot) {
                if (!isFragmentAlive()) return
                subregionItems = ds.children.mapNotNull { snap ->
                    val id = snap.child("id").getValue(String::class.java)?.trim() ?: snap.key?.trim()
                    val nombre = snap.child("nombre").getValue(String::class.java)?.trim()
                    val regionId = (snap.child("region_id").getValue(String::class.java)
                        ?: snap.child("regionId").getValue(String::class.java)
                        ?: snap.child("region").getValue(String::class.java) ?: "").trim()
                    if (id.isNullOrEmpty() || nombre.isNullOrEmpty()) null
                    else SubregionItem(id, nombre, regionId)
                }
                updateSubregionSpinner()
            }

            override fun onCancelled(error: DatabaseError) {
                if (!isFragmentAlive()) return
                showToast("Error al cargar subregiones: ${error.message}")
            }
        })
    }

    private fun loadAgencies(subregion: SubregionItem?) {
        if (subregion == null) { updateAgencySpinner(emptyList()); return }

        agenciesDatabase.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(ds: DataSnapshot) {
                if (!isFragmentAlive()) return
                val agencies = ds.children.mapNotNull { snap ->
                    val nombre = snap.child("nombre").getValue(String::class.java)?.trim()
                    if (nombre.isNullOrEmpty()) return@mapNotNull null

                    val id = snap.child("id").getValue(String::class.java)?.trim() ?: snap.key?.trim()
                    val regionId = snap.child("region_id").getValue(String::class.java)
                        ?: snap.child("regionId").getValue(String::class.java)
                        ?: snap.child("region").getValue(String::class.java)
                    val subregionValue = snap.child("subregion").getValue(String::class.java)
                        ?: snap.child("subregion_id").getValue(String::class.java)
                        ?: snap.child("subregionId").getValue(String::class.java)

                    val matchesSubregion = agencyMatchesSubregion(subregionValue, subregion)
                    val matchesRegion = selectedRegionItem?.let { region ->
                        val normalized = regionId?.trim().orEmpty()
                        normalized.isEmpty() ||
                                normalized.equals(region.id, ignoreCase = true) ||
                                normalized.equals(region.nombre, ignoreCase = true)
                    } ?: true

                    if (!matchesSubregion || !matchesRegion) null
                    else AgencyItem(id?.takeIf { it.isNotBlank() }, nombre, regionId?.trim(), subregionValue?.trim())
                }

                updateAgencySpinner(agencies)
            }

            override fun onCancelled(error: DatabaseError) {
                if (!isFragmentAlive()) return
                showToast("Error al cargar agencias: ${error.message}")
            }
        })
    }

    private fun loadVehicles(agency: AgencyItem?) {
        if (agency == null) { resetVehiclesSpinner(); return }

        vehiclesDatabase.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(ds: DataSnapshot) {
                if (!isFragmentAlive()) return
                vehicles = mutableListOf("Seleccione un Vehículo")
                for (snap in ds.children) {
                    val source = snap.child("meta").takeIf { it.exists() } ?: snap
                    val agencyValues = listOfNotNull(
                        source.child("agencia").getValue(String::class.java),
                        source.child("agencia_id").getValue(String::class.java),
                        source.child("agenciaId").getValue(String::class.java),
                        source.child("agenciaNombre").getValue(String::class.java),
                        source.child("agencia_nombre").getValue(String::class.java),
                        source.child("agenciaTag").getValue(String::class.java),
                        snap.child("agencia").getValue(String::class.java),
                        snap.child("agencia_id").getValue(String::class.java),
                        snap.child("agenciaId").getValue(String::class.java),
                        snap.child("agenciaNombre").getValue(String::class.java),
                        snap.child("agencia_nombre").getValue(String::class.java),
                        snap.child("agenciaTag").getValue(String::class.java)
                    ).map { it.trim() }.filter { it.isNotEmpty() }

                    if (agencyValues.isEmpty()) continue
                    if (!vehicleMatchesAgency(agencyValues, agency)) continue

                    val subregionValue = source.child("subregion").getValue(String::class.java)
                        ?: source.child("subregion_id").getValue(String::class.java)
                        ?: snap.child("subregion").getValue(String::class.java)
                    val matchesSubregion = selectedSubregionItem?.let { sub ->
                        val normalized = subregionValue?.trim().orEmpty()
                        normalized.isEmpty() ||
                                normalized.equals(sub.id, ignoreCase = true) ||
                                normalized.equals(sub.nombre, ignoreCase = true)
                    } ?: true

                    if (!matchesSubregion) continue

                    val placa = (source.child("placa").value ?: snap.child("placa").value)
                        ?.toString()?.trim()
                    if (!placa.isNullOrBlank()) vehicles.add(placa)
                }

                val ctx = context ?: return
                spinnerVehiculo.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, vehicles).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                spinnerVehiculo.setSelection(0)
                btnFinishRegistration.isEnabled = false
            }

            override fun onCancelled(error: DatabaseError) {
                if (!isFragmentAlive()) return
                showToast("Error al cargar vehículos: ${error.message}")
            }
        })
    }

    private fun agencyMatchesSubregion(value: String?, subregion: SubregionItem): Boolean {
        val normalized = value?.trim().orEmpty()
        if (normalized.isEmpty()) return true
        return normalized.equals(subregion.id, ignoreCase = true) ||
                normalized.equals(subregion.nombre, ignoreCase = true)
    }

    private fun vehicleMatchesAgency(values: List<String>, agency: AgencyItem): Boolean {
        if (values.isEmpty()) return false
        val agencyId = agency.id?.trim().orEmpty()
        val agencyName = agency.nombre.trim()
        val agencyTag = normTag(agencyName)
        return values.any { v ->
            val raw = v.trim()
            if (raw.isEmpty()) return@any false
            raw.equals(agencyName, ignoreCase = true) ||
                    (agencyId.isNotEmpty() && raw.equals(agencyId, ignoreCase = true)) ||
                    normTag(raw) == agencyTag
        }
    }

    private fun finalizeRegistration() {
        val regionItem = selectedRegionItem ?: run { showToast("Por favor, selecciona una región."); return }
        val subregionItem = selectedSubregionItem ?: run { showToast("Por favor, selecciona una subregión."); return }
        val agencyItem = selectedAgencyItem ?: run { showToast("Por favor, selecciona una agencia."); return }
        val selectedVehicle = (spinnerVehiculo.selectedItem as? String)
            ?.takeIf { it.isNotBlank() && it != "Seleccione un Vehículo" }
            ?: run { showToast("Por favor, selecciona un vehículo."); return }

        viewModel.setDatosAdicionales(
            regionItem.id, regionItem.nombre,
            subregionItem.id, subregionItem.nombre,
            agencyItem.id, agencyItem.nombre,
            selectedVehicle
        )

        val email = viewModel.getEmail()?.trim().orEmpty()
        val password = viewModel.getPassword().orEmpty()
        val nombre = viewModel.getNombre().orEmpty()
        val primerApellido = viewModel.getPrimerApellido().orEmpty()
        val segundoApellido = viewModel.getSegundoApellido().orEmpty()
        val apellidos = viewModel.getApellidosCompletos().orEmpty()
        val telefono = viewModel.getTelefono().orEmpty()
        val cedula = viewModel.getCedula().orEmpty()

        if (email.isBlank() || password.length < 6 || nombre.isBlank() ||
            primerApellido.isBlank() || segundoApellido.isBlank() ||
            telefono.isBlank() || cedula.isBlank()) {
            showToast("Verifica correo/clave (6+), nombre, apellidos, teléfono y cédula.")
            return
        }

        btnFinishRegistration.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val auth = FirebaseAuth.getInstance()
            val dbUsers = FirebaseDatabase
                .getInstance("https://tecniapp-ice-user.firebaseio.com")
                .reference

            try {
                val user = auth.createUserWithEmailAndPassword(email, password).await().user
                    ?: throw IllegalStateException("No se pudo crear el usuario de autenticación.")
                val uid = user.uid

                val userData = hashMapOf<String, Any?>(
                    "uid" to uid,
                    "cedula" to cedula,
                    "email" to email,
                    "email_lower" to email.lowercase(Locale.ROOT),
                    "nombre" to nombre,
                    "apellidos" to apellidos,
                    "primer_apellido" to primerApellido,
                    "segundo_apellido" to segundoApellido,
                    "telefono" to telefono,
                    "region" to regionItem.id,
                    "region_nombre" to regionItem.nombre,
                    "subregion" to subregionItem.id,
                    "subregion_nombre" to subregionItem.nombre,
                    "agencia" to agencyItem.nombre,
                    "agencia_id" to agencyItem.id,
                    "placaVehiculo" to selectedVehicle,
                    "createdAt" to ServerValue.TIMESTAMP,
                    "rol" to ""
                )

                val eKey = emailKey(email)
                val pKey = phoneKey(telefono)
                val cedKey = cedulaKey(cedula)

                val updates = hashMapOf<String, Any?>(
                    "/usuarios/$uid" to userData,
                    "/emails/$eKey" to mapOf("uid" to uid),
                    "/idcards/$cedKey" to mapOf("uid" to uid)
                )
                if (pKey.isNotBlank()) {
                    updates["/phones/$pKey"] = mapOf("uid" to uid)
                }

                dbUsers.updateChildren(updates).await()

                runCatching {
                    dbUsers.child("verificationCodes").child(eKey).removeValue().await()
                }
                auth.signOut()

                if (!isFragmentAlive()) return@launch
                showToast("Registro completado con éxito.")
                val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                requireActivity().finish()

            } catch (t: Throwable) {
                Log.e("Paso4Fragment", "finalizeRegistration error: ${t.message}", t)
                runCatching { FirebaseAuth.getInstance().currentUser?.delete()?.await() }
                if (!isFragmentAlive()) return@launch
                if (t is FirebaseAuthUserCollisionException) {
                    showToast("Ese correo ya tiene una cuenta. Inicia sesión o recupera tu contraseña.")
                } else {
                    showToast("No se pudo completar el registro: ${t.message}")
                }
                btnFinishRegistration.isEnabled = true
            }
        }
    }

    private fun showToast(message: String) {
        if (!isFragmentAlive()) return
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}