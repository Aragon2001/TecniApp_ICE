package com.Arasoftsolutions.tecniapp_ice.User

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.Arasoftsolutions.tecniapp_ice.ActivityMain
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AgenciaEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.SubregionesEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.UserEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculosEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.apellidosCompletos
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository

import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentUserBinding
import com.bumptech.glide.Glide

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class UserFragment : Fragment() {

    private var _binding: FragmentUserBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: UserViewModel
    private lateinit var repository: RoomRepository

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val usersRef by lazy {
        FirebaseDatabase.getInstance(USERS_DB_URL).reference.child("usuarios")
    }
    private val storageRef by lazy {
        FirebaseStorage.getInstance().reference.child("profilePictures")
    }

    private var currentUser: UserEntity? = null
    private var subregionItems: List<SubregionesEntity> = emptyList()
    private var agencyItems: List<AgenciaEntity> = emptyList()
    private var vehicleItems: List<VehiculosEntity> = emptyList()
    private var filteredAgencies: List<AgenciaEntity> = emptyList()
    private var filteredVehicles: List<VehiculosEntity> = emptyList()

    private lateinit var subregionAdapter: ArrayAdapter<String>
    private lateinit var agencyAdapter: ArrayAdapter<String>
    private lateinit var vehicleAdapter: ArrayAdapter<String>

    private var selectedSubregion: SubregionesEntity? = null
    private var selectedAgency: AgenciaEntity? = null
    private var selectedVehicle: VehiculosEntity? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { uploadProfilePhoto(it) }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        repository = RoomRepository.getInstance(context.applicationContext)
        viewModel = ViewModelProvider(
            this,
            UserViewModel.Factory(repository)
        )[UserViewModel::class.java]
    }

   override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
): View {
    _binding = FragmentUserBinding.inflate(inflater, container, false)
    val view = binding.root
    return view
}


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapters()
        setupListeners()
        collectViewModel()
        loadUser()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupAdapters() {
        val context = requireContext()
        subregionAdapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, mutableListOf())
        binding.actvSubregion.setAdapter(subregionAdapter)

        agencyAdapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, mutableListOf())
        binding.actvAgency.setAdapter(agencyAdapter)

        vehicleAdapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, mutableListOf())
        binding.actvVehicle.setAdapter(vehicleAdapter)
    }

    private fun setupListeners() {
        binding.btnChangePhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.actvSubregion.setOnItemClickListener { _, _, position, _ ->
            selectedSubregion = subregionItems.getOrNull(position)
            if (selectedSubregion == null) {
                binding.actvSubregion.setText("", false)
            } else {
                binding.actvSubregion.setText(selectedSubregion!!.nombre, false)
            }
            selectedAgency = null
            selectedVehicle = null
            updateAgencyDropdown()
            updateVehicleDropdown()
            updateSummary()
        }

        binding.actvSubregion.setOnClickListener {
            binding.actvSubregion.showDropDown()
        }

        binding.actvAgency.setOnItemClickListener { _, _, position, _ ->
            selectedAgency = filteredAgencies.getOrNull(position)
            selectedAgency?.let {
                binding.actvAgency.setText(it.nombre, false)
            }
            updateSummary()
        }

        binding.actvAgency.setOnClickListener {
            binding.actvAgency.showDropDown()
        }

        binding.actvVehicle.setOnItemClickListener { _, _, position, _ ->
            selectedVehicle = filteredVehicles.getOrNull(position)
            selectedVehicle?.let {
                binding.actvVehicle.setText(formatVehicle(it), false)
            }
            updateSummary()
        }

        binding.actvVehicle.setOnClickListener {
            binding.actvVehicle.showDropDown()
        }

        binding.btnSaveChanges.setOnClickListener {
            onSaveChanges()
        }
    }

    private fun collectViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    viewModel.loading.collect { loading ->
                        binding.progressLoading.isVisible = loading
                        binding.contentGroup.isVisible = !loading
                    }
                }
                launch {
                    viewModel.subregions.collect { list ->
                        subregionItems = list
                        updateSubregionDropdown()
                        applyUserSelections()
                    }
                }
                launch {
                    viewModel.agencies.collect { list ->
                        agencyItems = list
                        updateAgencyDropdown()
                        applyUserSelections()
                    }
                }
                launch {
                    viewModel.vehicles.collect { list ->
                        vehicleItems = list
                        updateVehicleDropdown()
                        applyUserSelections()
                    }
                }
                launch {
                    viewModel.user.collect { user ->
                        user?.let { bindUser(it) }
                    }
                }
                launch {
                    viewModel.error.collect { error ->
                        if (!error.isNullOrBlank()) {
                            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                            viewModel.clearError()
                        }
                    }
                }
            }
        }
    }

    private fun loadUser() {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            Toast.makeText(requireContext(), R.string.profile_error_user_not_found, Toast.LENGTH_LONG).show()
            return
        }
        viewModel.loadUser(uid)
    }

    private fun bindUser(user: UserEntity) {
        currentUser = user
        val fullName = listOfNotNull(user.nombre, user.apellidosCompletos)
            .joinToString(" ")
            .trim()
            .ifBlank { getString(R.string.profile_default_name) }
        binding.textDisplayName.text = fullName
        binding.textEmail.text = user.email ?: getString(R.string.profile_summary_placeholder)
        binding.textUserId.text = getString(
            R.string.profile_user_id_format,
            summaryValue(user.cedula)
        )
        binding.etPhoneNumber.setText(user.telefono.orEmpty())

        Glide.with(this)
            .load(user.fotoUrl)
            .placeholder(R.drawable.default_profile_picture)
            .error(R.drawable.default_profile_picture)
            .into(binding.imageProfile)

        applyUserSelections()
        updateSummary()
    }

    private fun applyUserSelections() {
        val user = currentUser ?: return
        if (selectedSubregion == null) {
            selectedSubregion = findSubregion(user.subregion)
        }
        selectedSubregion?.let {
            binding.actvSubregion.setText(it.nombre, false)
        } ?: run {
            binding.actvSubregion.setText(user.subregion.orEmpty(), false)
        }

        updateAgencyDropdown()
        if (selectedAgency == null) {
            selectedAgency = findAgency(user.agencia, selectedSubregion)
        }
        selectedAgency?.let {
            binding.actvAgency.setText(it.nombre, false)
        } ?: run {
            binding.actvAgency.setText(user.agencia.orEmpty(), false)
        }

        updateVehicleDropdown()
        if (selectedVehicle == null) {
            selectedVehicle = findVehicle(user.placaVehiculo)
        }
        selectedVehicle?.let {
            binding.actvVehicle.setText(formatVehicle(it), false)
        } ?: run {
            binding.actvVehicle.setText(user.placaVehiculo.orEmpty(), false)
        }

        updateSummary()
    }

    private fun updateSubregionDropdown() {
        subregionAdapter.clear()
        subregionAdapter.addAll(subregionItems.map { it.nombre })
        subregionAdapter.notifyDataSetChanged()
    }

    private fun updateAgencyDropdown() {
        val targetSubregion = selectedSubregion ?: findSubregion(currentUser?.subregion)
        filteredAgencies = if (targetSubregion != null) {
            agencyItems.filter { agencyMatchesSubregion(it, targetSubregion) }
        } else {
            agencyItems
        }
        agencyAdapter.clear()
        agencyAdapter.addAll(filteredAgencies.map { it.nombre })
        agencyAdapter.notifyDataSetChanged()

        if (selectedAgency?.let { agencyMatchesSubregion(it, targetSubregion) } != true) {
            selectedAgency = null
            binding.actvAgency.setText("", false)
        }
    }

    private fun updateVehicleDropdown() {
        val targetSubregion = selectedSubregion ?: findSubregion(currentUser?.subregion)
        filteredVehicles = if (targetSubregion != null) {
            vehicleItems.filter { vehicleMatchesSubregion(it, targetSubregion) }
        } else {
            vehicleItems
        }
        vehicleAdapter.clear()
        vehicleAdapter.addAll(filteredVehicles.map { formatVehicle(it) })
        vehicleAdapter.notifyDataSetChanged()

        selectedVehicle?.let { vehicle ->
            if (!filteredVehicles.any { it.id == vehicle.id }) {
                selectedVehicle = null
                binding.actvVehicle.setText("", false)
            }
        }
    }

    private fun onSaveChanges() {
        val user = currentUser ?: run {
            Toast.makeText(requireContext(), R.string.profile_error_user_not_found, Toast.LENGTH_LONG).show()
            return
        }


        val newPassword = binding.etPassword.text?.toString().orEmpty()
        val confirmPassword = binding.etConfirmPassword.text?.toString().orEmpty()
        if (newPassword.isNotEmpty() && newPassword.length < MIN_PASSWORD_LENGTH) {
            Toast.makeText(requireContext(), R.string.profile_password_too_short, Toast.LENGTH_LONG).show()
            return
        }
        if (newPassword.isNotEmpty() && newPassword != confirmPassword) {
            Toast.makeText(requireContext(), R.string.profile_password_mismatch, Toast.LENGTH_LONG).show()
            return
        }

        val updatedUser = user.copy(
            telefono = binding.etPhoneNumber.text?.toString()?.trim().takeUnless { it.isNullOrBlank() },
            region = (selectedSubregion?.regionId ?: user.region).takeUnless { it.isNullOrBlank() },
            subregion = (selectedSubregion?.id ?: user.subregion).takeUnless { it.isNullOrBlank() },
            subregionNombre = (selectedSubregion?.nombre ?: user.subregionNombre).takeUnless { it.isNullOrBlank() },
            agencia = (selectedAgency?.nombre ?: user.agencia).takeUnless { it.isNullOrBlank() },
            agenciaId = (selectedAgency?.id ?: user.agenciaId).takeUnless { it.isNullOrBlank() },
            placaVehiculo = (selectedVehicle?.placa?.toString() ?: user.placaVehiculo).takeUnless { it.isNullOrBlank() },
            password = if (newPassword.isNotEmpty()) newPassword else user.password
        )

        setSaving(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (newPassword.isNotEmpty()) {
                    auth.currentUser?.updatePassword(newPassword)?.await()

                }
                persistUserRemote(updatedUser)
                viewModel.updateCachedUser(updatedUser, persist = true)
                currentUser = updatedUser
                binding.etPassword.text?.clear()
                binding.etConfirmPassword.text?.clear()
                applyUserSelections()
                updateSummary()
                Toast.makeText(requireContext(), R.string.profile_save_success, Toast.LENGTH_SHORT).show()
                (activity as? ActivityMain)?.refreshNavHeader()
            } catch (e: Exception) {
                val errorDetail = e.localizedMessage?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
                Toast.makeText(
                    requireContext(),
                    getString(R.string.profile_save_error, errorDetail),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                setSaving(false)
            }
        }
    }

    private suspend fun persistUserRemote(user: UserEntity) = withContext(Dispatchers.IO) {
        require(user.uid.isNotBlank()) { "UID vacío" }
        usersRef.child(user.uid).setValue(user).await()
    }

    private fun uploadProfilePhoto(uri: Uri) {
        val user = currentUser ?: run {
            Toast.makeText(requireContext(), R.string.profile_error_user_not_found, Toast.LENGTH_LONG).show()
            return
        }
        setPhotoUploading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val photoRef = storageRef.child("${user.uid}.jpg")
                photoRef.putFile(uri).await()
                val downloadUrl = photoRef.downloadUrl.await()
                val updatedUser = user.copy(fotoUrl = downloadUrl.toString())
                persistUserRemote(updatedUser)
                viewModel.updateCachedUser(updatedUser, persist = true)
                currentUser = updatedUser
                Glide.with(this@UserFragment)
                    .load(downloadUrl)
                    .placeholder(R.drawable.default_profile_picture)
                    .error(R.drawable.default_profile_picture)
                    .into(binding.imageProfile)
                applyUserSelections()
                Toast.makeText(requireContext(), R.string.profile_photo_updated, Toast.LENGTH_SHORT).show()
                (activity as? ActivityMain)?.refreshNavHeader()
            } catch (e: Exception) {
                val errorDetail = e.localizedMessage?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
                Toast.makeText(
                    requireContext(),
                    getString(R.string.profile_photo_update_error, errorDetail),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                setPhotoUploading(false)
            }
        }
    }

    private fun updateSummary() {
        val user = currentUser
        val regionValue = selectedSubregion?.nombre ?: user?.subregion
        val subregionValue = selectedSubregion?.nombre ?: user?.subregion
        val agencyValue = selectedAgency?.nombre ?: user?.agencia
        val vehicleValue = selectedVehicle?.let { formatVehicle(it) } ?: user?.placaVehiculo

        binding.textSummaryRegion.text = getString(
            R.string.profile_summary_subregion,
            summaryValue(subregionValue)
        )
        binding.textSummarySubregion.text = getString(
            R.string.profile_summary_subregion,
            summaryValue(subregionValue)
        )
        binding.textSummaryAgency.text = getString(
            R.string.profile_summary_agency,
            summaryValue(agencyValue)
        )
        binding.textSummaryVehicle.text = getString(
            R.string.profile_summary_vehicle,
            summaryValue(vehicleValue)
        )
    }

    private fun summaryValue(raw: String?): String {
        val value = raw?.trim().orEmpty()
        return if (value.isEmpty()) {
            getString(R.string.profile_summary_placeholder)
        } else {
            value
        }
    }

    private fun findSubregion(value: String?): SubregionesEntity? {
        if (value.isNullOrBlank()) return null
        val normalized = value.trim().lowercase()
        return subregionItems.firstOrNull {
            it.id.trim().lowercase() == normalized || it.nombre.trim().lowercase() == normalized
        }
    }

    private fun findAgency(value: String?, subregion: SubregionesEntity?): AgenciaEntity? {
        if (value.isNullOrBlank()) return null
        val normalized = value.trim().lowercase()
        return filteredAgencies.firstOrNull {
            it.nombre.trim().lowercase() == normalized
        } ?: agencyItems.firstOrNull {
            it.nombre.trim().lowercase() == normalized && (subregion == null || agencyMatchesSubregion(it, subregion))
        }
    }

    private fun findVehicle(value: String?): VehiculosEntity? {
        if (value.isNullOrBlank()) return null
        val normalizedDigits = value.filter { it.isDigit() }
        val numeric = normalizedDigits.toLongOrNull()
        return filteredVehicles.firstOrNull { vehicleMatchesPlate(it, numeric, value) }
            ?: vehicleItems.firstOrNull { vehicleMatchesPlate(it, numeric, value) }
    }

    private fun vehicleMatchesPlate(
        vehicle: VehiculosEntity,
        numeric: Long?,
        original: String
    ): Boolean {
        return when {
            numeric != null -> vehicle.placa == numeric
            else -> vehicle.placa.toString().equals(original.trim(), ignoreCase = true)
        }
    }

    private fun agencyMatchesSubregion(agency: AgenciaEntity, subregion: SubregionesEntity?): Boolean {
        if (subregion == null) return true
        val agencySub = agency.subregion?.trim().orEmpty()
        if (agencySub.isEmpty()) return true
        val subId = subregion.id.trim()
        val subName = subregion.nombre.trim()
        return agencySub.equals(subId, ignoreCase = true) || agencySub.equals(subName, ignoreCase = true)
    }

    private fun vehicleMatchesSubregion(vehicle: VehiculosEntity, subregion: SubregionesEntity?): Boolean {
        if (subregion == null) return true
        val vehicleSub = vehicle.subregion?.trim().orEmpty()
        if (vehicleSub.isEmpty()) return true
        val subId = subregion.id.trim()
        val subName = subregion.nombre.trim()
        return vehicleSub.equals(subId, ignoreCase = true) || vehicleSub.equals(subName, ignoreCase = true)
    }

    private fun formatVehicle(vehicle: VehiculosEntity): String {
        val placa = vehicle.placa.toString()
        val tipo = vehicle.tipo.takeIf { !it.isNullOrBlank() }?.trim().orEmpty()
        return if (tipo.isNotEmpty()) {
            getString(R.string.profile_vehicle_format, placa, tipo)
        } else {
            placa
        }
    }

    private fun setSaving(saving: Boolean) {
        binding.progressSaving.isVisible = saving
        binding.btnSaveChanges.isEnabled = !saving
        binding.btnChangePhoto.isEnabled = !saving && !binding.progressPhoto.isVisible
    }

    private fun setPhotoUploading(uploading: Boolean) {
        binding.progressPhoto.isVisible = uploading
        binding.btnChangePhoto.isEnabled = !uploading && !binding.progressSaving.isVisible
    }

    companion object {
        private const val USERS_DB_URL = "https://tecniapp-ice-user.firebaseio.com"
        private const val MIN_PASSWORD_LENGTH = 8
    }
}
