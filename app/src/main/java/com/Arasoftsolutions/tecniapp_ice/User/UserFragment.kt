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
import com.Arasoftsolutions.tecniapp_ice.Database.entities.RegionEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.SubregionesEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.UserEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculosEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.apellidosCompletos
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.Database.sync.SubregionNormalizer

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
    FirebaseStorage.getInstance("gs://tecniapp-ice.firebasestorage.app")
        .reference.child("profilePictures")
}


    private var currentUser: UserEntity? = null
    private var regionItems: List<RegionEntity> = emptyList()
    private var subregionItems: List<SubregionesEntity> = emptyList()
    private var agencyItems: List<AgenciaEntity> = emptyList()
    private var vehicleItems: List<VehiculosEntity> = emptyList()
    private var filteredSubregions: List<SubregionesEntity> = emptyList()
    private var filteredAgencies: List<AgenciaEntity> = emptyList()
    private var filteredVehicles: List<VehiculosEntity> = emptyList()

    private lateinit var regionAdapter: ArrayAdapter<String>
    private lateinit var subregionAdapter: ArrayAdapter<String>
    private lateinit var agencyAdapter: ArrayAdapter<String>
    private lateinit var vehicleAdapter: ArrayAdapter<String>

    private var selectedRegion: RegionEntity? = null
    private var selectedSubregion: SubregionesEntity? = null
    private var selectedAgency: AgenciaEntity? = null
    private var selectedVehicle: VehiculosEntity? = null
    private var isChangingPassword: Boolean = false

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
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapters()
        setupListeners()
        togglePasswordEdition(false)
        collectViewModel()
        loadUser()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupAdapters() {
        val context = requireContext()
        regionAdapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, mutableListOf())
        binding.actvRegion.setAdapter(regionAdapter)

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

        binding.actvRegion.setOnItemClickListener { _, _, position, _ ->
            selectedRegion = regionItems.getOrNull(position)
            selectedRegion?.let {
                binding.actvRegion.setText(it.nombre, false)
            } ?: run {
                binding.actvRegion.setText("", false)
            }
            if (selectedSubregion?.let { !subregionMatchesRegion(it, selectedRegion) } == true) {
                selectedSubregion = null
                binding.actvSubregion.setText("", false)
            }
            selectedAgency = null
            selectedVehicle = null
            updateSubregionDropdown()
            updateAgencyDropdown()
            updateVehicleDropdown()
            updateSummary()
        }

        binding.actvRegion.setOnClickListener {
            binding.actvRegion.showDropDown()
        }

        binding.actvSubregion.setOnItemClickListener { _, _, position, _ ->
            selectedSubregion = filteredSubregions.getOrNull(position)
            selectedSubregion?.let {
                binding.actvSubregion.setText(formatSubregion(it), false)
                val regionForSubregion = findRegion(it.regionId)
                if (regionForSubregion != null && selectedRegion?.id?.equals(regionForSubregion.id, true) != true) {
                    selectedRegion = regionForSubregion
                    binding.actvRegion.setText(regionForSubregion.nombre, false)
                    updateSubregionDropdown()
                }
            } ?: run {
                binding.actvSubregion.setText("", false)
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
                binding.actvAgency.setText(formatAgency(it), false)
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

        binding.switchChangePassword.setOnCheckedChangeListener { _, isChecked ->
            togglePasswordEdition(isChecked)
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
                    viewModel.regions.collect { list ->
                        regionItems = list.sortedBy { it.nombre.lowercase() }
                        updateRegionDropdown()
                        applyUserSelections()
                    }
                }
                launch {
                    viewModel.subregions.collect { list ->
                        subregionItems = list.sortedBy { it.nombre.lowercase() }
                        updateSubregionDropdown()
                        applyUserSelections()
                    }
                }
                launch {
                    viewModel.agencies.collect { list ->
                        agencyItems = list.sortedBy { it.nombre.lowercase() }
                        updateAgencyDropdown()
                        applyUserSelections()
                    }
                }
                launch {
                    viewModel.vehicles.collect { list ->
                        vehicleItems = list.sortedBy { it.placa }
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
        val placeholder = binding.root.context.getString(R.string.profile_summary_placeholder)
        binding.textEmail.text = user.email ?: placeholder
        binding.textUserId.text = getString(
            R.string.profile_user_id_format,
            summaryValue(user.cedula, placeholder)
        )
        binding.etPhoneNumber.setText(user.telefono.orEmpty())
        binding.switchChangePassword.isChecked = false
        togglePasswordEdition(false)

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
        val userRegion = currentUserRegion()
        val userSubregion = currentUserSubregion()

        selectedRegion = selectedRegion
            ?: userRegion
            ?: selectedSubregion?.let { findRegion(it.regionId) }
            ?: userSubregion?.let { findRegion(it.regionId) }

        selectedRegion?.let {
            binding.actvRegion.setText(it.nombre, false)
        } ?: run {
            val fallback = user.regionNombre ?: user.region
            binding.actvRegion.setText(fallback.orEmpty(), false)
        }

        updateSubregionDropdown()

        if (selectedSubregion == null) {
            selectedSubregion = userSubregion
        }

        val effectiveRegion = selectedRegion ?: selectedSubregion?.let { findRegion(it.regionId) } ?: userRegion
        if (effectiveRegion != null && selectedSubregion != null && !subregionMatchesRegion(selectedSubregion!!, effectiveRegion)) {
            selectedSubregion = userSubregion?.takeIf { subregionMatchesRegion(it, effectiveRegion) }
        }

        selectedSubregion?.let {
            binding.actvSubregion.setText(formatSubregion(it), false)
        } ?: run {
            val fallback = user.subregionNombre ?: user.subregion
            binding.actvSubregion.setText(fallback.orEmpty(), false)
        }

        updateAgencyDropdown()
        if (selectedAgency == null) {
            selectedAgency = findAgency(user.agenciaId, selectedSubregion, effectiveRegion)
                ?: findAgency(user.agencia, selectedSubregion, effectiveRegion)
        }
        selectedAgency?.let {
            binding.actvAgency.setText(formatAgency(it), false)
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

        if (selectedRegion == null && effectiveRegion != null) {
            selectedRegion = effectiveRegion
            binding.actvRegion.setText(effectiveRegion.nombre, false)
        }

        updateSummary()
    }

    private fun updateRegionDropdown() {
        regionAdapter.clear()
        regionAdapter.addAll(regionItems.map { it.nombre })
        regionAdapter.notifyDataSetChanged()
        selectedRegion = selectedRegion?.let { current ->
            regionItems.firstOrNull { it.id.equals(current.id, ignoreCase = true) }
                ?: regionItems.firstOrNull { it.nombre.equals(current.nombre, ignoreCase = true) }
        }
    }

    private fun updateSubregionDropdown() {
        subregionAdapter.clear()
        val targetRegion = selectedRegion ?: currentUserRegion() ?: currentUserSubregion()?.let { findRegion(it.regionId) }
        filteredSubregions = if (targetRegion != null) {
            subregionItems.filter { subregionMatchesRegion(it, targetRegion) }
        } else {
            emptyList()
        }
        subregionAdapter.addAll(filteredSubregions.map { formatSubregion(it) })
        subregionAdapter.notifyDataSetChanged()

        if (selectedSubregion?.let { subregionMatchesRegion(it, targetRegion) } != true) {
            selectedSubregion = null
            binding.actvSubregion.setText("", false)
        }
    }

    private fun updateAgencyDropdown() {
        val targetSubregion = selectedSubregion ?: currentUserSubregion()
        val targetRegion = selectedRegion ?: currentUserRegion() ?: targetSubregion?.let { findRegion(it.regionId) }
        filteredAgencies = when {
            targetSubregion != null || targetRegion != null -> agencyItems.filter {
                agencyMatches(it, targetSubregion, targetRegion)
            }
            else -> emptyList()
        }
        agencyAdapter.clear()
        agencyAdapter.addAll(filteredAgencies.map { formatAgency(it) })
        agencyAdapter.notifyDataSetChanged()

        if (selectedAgency?.let { agencyMatches(it, targetSubregion, targetRegion) } != true) {
            selectedAgency = null
            binding.actvAgency.setText("", false)
        }
    }

    private fun updateVehicleDropdown() {
        val targetAgency = selectedAgency ?: currentUserAgency()
        filteredVehicles = if (targetAgency != null) {
            vehicleItems.filter { vehicleMatchesAgency(it, targetAgency) }
        } else {
            emptyList()
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


        val changePassword = isChangingPassword
        val newPassword = if (changePassword) binding.etPassword.text?.toString().orEmpty() else ""
        val confirmPassword = if (changePassword) binding.etConfirmPassword.text?.toString().orEmpty() else ""

        if (changePassword && newPassword.isBlank()) {
            Toast.makeText(requireContext(), R.string.profile_password_required, Toast.LENGTH_LONG).show()
            return
        }

        if (newPassword.isNotEmpty() && newPassword.length < MIN_PASSWORD_LENGTH) {
            Toast.makeText(requireContext(), R.string.profile_password_too_short, Toast.LENGTH_LONG).show()
            return
        }
        if (newPassword.isNotEmpty() && newPassword != confirmPassword) {
            Toast.makeText(requireContext(), R.string.profile_password_mismatch, Toast.LENGTH_LONG).show()
            return
        }

        val phone = binding.etPhoneNumber.text?.toString().normalizedOrNull()
        val regionEntity = selectedRegion
            ?: selectedSubregion?.let { findRegion(it.regionId) }
            ?: currentUserRegion()
        val regionId = regionEntity?.id.normalizedOrNull()
            ?: user.region.normalizedOrNull()
        val regionName = regionEntity?.nombre.normalizedOrNull()
            ?: binding.actvRegion.text?.toString().normalizedOrNull()
            ?: user.regionNombre.normalizedOrNull()

        val subregionEntity = selectedSubregion ?: currentUserSubregion()
        val subregionId = subregionEntity?.id.normalizedOrNull()
            ?: user.subregion.normalizedOrNull()
        val subregionName = subregionEntity?.nombre.normalizedOrNull()
            ?: user.subregionNombre.normalizedOrNull()

        val agencyEntity = selectedAgency
        val agencyId = agencyEntity?.id.normalizedOrNull()
            ?: user.agenciaId.normalizedOrNull()
        val agencyName = agencyEntity?.nombre.normalizedOrNull()
            ?: user.agencia.normalizedOrNull()

        val vehiclePlate = selectedVehicle?.placa?.toString()?.normalizedOrNull()
            ?: user.placaVehiculo.normalizedOrNull()

        val updatedUser = user.copy(
            telefono = phone,
            region = regionId,
            regionNombre = regionName,
            subregion = subregionId,
            subregionNombre = subregionName,
            agencia = agencyName,
            agenciaId = agencyId,
            placaVehiculo = vehiclePlate,
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
                binding.switchChangePassword.isChecked = false
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
        val binding = _binding ?: return
        if (!isAdded) return

        val user = currentUser
        val regionEntity = selectedRegion
            ?: selectedSubregion?.let { findRegion(it.regionId) }
            ?: currentUserRegion()
        val regionValue = regionEntity?.nombre ?: user?.regionNombre ?: user?.region
        val subregionValue = selectedSubregion?.let { formatSubregion(it) } ?: user?.subregionNombre ?: user?.subregion
        val agencyValue = selectedAgency?.let { formatAgency(it) } ?: user?.agencia
        val vehicleValue = selectedVehicle?.let { formatVehicle(it) } ?: user?.placaVehiculo
        val placeholder = binding.root.context.getString(R.string.profile_summary_placeholder)


    }

    private fun summaryValue(raw: String?, placeholder: String): String {
        val value = raw?.trim().orEmpty()
        return if (value.isEmpty()) placeholder else value
    }

    private fun findSubregion(value: String?): SubregionesEntity? {
        if (value.isNullOrBlank()) return null
        val normalized = value.trim().lowercase()
        val canonicalTarget = SubregionNormalizer.canonicalIdOrSelf(value)
        return subregionItems.firstOrNull {
            val idMatch = it.id.trim().lowercase() == normalized
            val nameMatch = it.nombre.trim().lowercase() == normalized
            if (idMatch || nameMatch) {
                true
            } else {
                val canonicalId = SubregionNormalizer.canonicalIdOrSelf(it.id)
                val canonicalNombre = SubregionNormalizer.canonicalIdOrSelf(it.nombre)
                canonicalTarget != null && listOfNotNull(canonicalId, canonicalNombre).any { canon ->
                    canon.equals(canonicalTarget, ignoreCase = true)
                }
            }
        }
    }

    private fun findAgency(
        value: String?,
        subregion: SubregionesEntity?,
        region: RegionEntity?
    ): AgenciaEntity? {
        if (value.isNullOrBlank()) return null
        val normalized = value.trim().lowercase()
        return filteredAgencies.firstOrNull {
            it.id.trim().lowercase() == normalized || it.nombre.trim().lowercase() == normalized
        } ?: agencyItems.firstOrNull {
            val matchesValue = it.id.trim().lowercase() == normalized || it.nombre.trim().lowercase() == normalized
            matchesValue && agencyMatches(it, subregion, region)
        }
    }

    private fun findRegion(value: String?): RegionEntity? {
        if (value.isNullOrBlank()) return null
        val normalized = value.trim().lowercase()
        return regionItems.firstOrNull {
            it.id.trim().lowercase() == normalized || it.nombre.trim().lowercase() == normalized
        }
    }

    private fun currentUserSubregion(): SubregionesEntity? {
        val user = currentUser ?: return null
        return findSubregion(user.subregion) ?: findSubregion(user.subregionNombre)
    }

    private fun currentUserRegion(): RegionEntity? {
        val user = currentUser ?: return null
        return findRegion(user.region) ?: findRegion(user.regionNombre)
    }

    private fun currentUserAgency(): AgenciaEntity? {
        val user = currentUser ?: return null
        val subregion = selectedSubregion ?: currentUserSubregion()
        val region = selectedRegion ?: subregion?.let { findRegion(it.regionId) } ?: currentUserRegion()
        return findAgency(user.agenciaId, subregion, region)
            ?: findAgency(user.agencia, subregion, region)
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
        if (agencySub.isEmpty()) return false
        val subId = subregion.id.trim()
        val subName = subregion.nombre.trim()
        val agencyCanonical = SubregionNormalizer.canonicalIdOrSelf(agencySub)
        val subregionCanonical = SubregionNormalizer.canonicalIdOrSelf(subId)
            ?: SubregionNormalizer.canonicalIdOrSelf(subName)
        if (agencyCanonical != null && subregionCanonical != null) {
            return agencyCanonical.equals(subregionCanonical, ignoreCase = true)
        }
        return agencySub.equals(subId, ignoreCase = true) || agencySub.equals(subName, ignoreCase = true)
    }

    private fun agencyMatchesRegion(agency: AgenciaEntity, region: RegionEntity?): Boolean {
        if (region == null) return true
        val agencyRegion = agency.regionId?.trim().orEmpty()
        if (agencyRegion.isEmpty()) return true
        val regionId = region.id.trim()
        val regionName = region.nombre.trim()
        return agencyRegion.equals(regionId, ignoreCase = true) || agencyRegion.equals(regionName, ignoreCase = true)
    }

    private fun agencyMatches(
        agency: AgenciaEntity,
        subregion: SubregionesEntity?,
        region: RegionEntity?
    ): Boolean {
        return agencyMatchesSubregion(agency, subregion) && agencyMatchesRegion(agency, region)
    }

    private fun subregionMatchesRegion(subregion: SubregionesEntity, region: RegionEntity?): Boolean {
        if (region == null) return true
        val target = subregion.regionId.trim()
        val regionId = region.id.trim()
        val regionName = region.nombre.trim()
        return target.equals(regionId, ignoreCase = true) || target.equals(regionName, ignoreCase = true)
    }

    private fun vehicleMatchesAgency(vehicle: VehiculosEntity, agency: AgenciaEntity): Boolean {
        val vehicleAgency = vehicle.agencia.trim()
        if (vehicleAgency.isEmpty()) return true
        val agencyId = agency.id?.trim().orEmpty()
        val agencyName = agency.nombre.trim()
        return vehicleAgency.equals(agencyName, ignoreCase = true) ||
            (agencyId.isNotEmpty() && vehicleAgency.equals(agencyId, ignoreCase = true))
    }

    private fun togglePasswordEdition(enable: Boolean) {
        isChangingPassword = enable
        binding.passwordGroup.isVisible = enable
        binding.tilPassword.isEnabled = enable
        binding.tilConfirmPassword.isEnabled = enable
        binding.etPassword.isEnabled = enable
        binding.etConfirmPassword.isEnabled = enable
        if (!enable) {
            binding.etPassword.text?.clear()
            binding.etConfirmPassword.text?.clear()
            binding.tilPassword.error = null
            binding.tilConfirmPassword.error = null
        }
    }

    private fun formatSubregion(subregion: SubregionesEntity): String {
        return subregion.nombre
    }

    private fun formatAgency(agency: AgenciaEntity): String {
        val subregionName = findSubregion(agency.subregion)?.nombre?.takeIf { it.isNotBlank() }
        return if (subregionName != null) {
            getString(R.string.profile_dropdown_agency_format, agency.nombre, subregionName)
        } else {
            agency.nombre
        }
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

    private fun String?.normalizedOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun setSaving(saving: Boolean) {
        binding.progressSaving.isVisible = saving
        binding.btnSaveChanges.isEnabled = !saving
        binding.btnChangePhoto.isEnabled = !saving && !binding.progressPhoto.isVisible
        binding.switchChangePassword.isEnabled = !saving
        binding.tilRegion.isEnabled = !saving
        binding.tilSubregion.isEnabled = !saving
        binding.tilAgency.isEnabled = !saving
        binding.tilVehicle.isEnabled = !saving
        binding.tilPhone.isEnabled = !saving
        binding.passwordGroup.isEnabled = !saving
        if (!saving) {
            togglePasswordEdition(binding.switchChangePassword.isChecked)
        }
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
