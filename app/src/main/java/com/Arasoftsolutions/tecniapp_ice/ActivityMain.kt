/**
 * TecniApp ICE © 2025 Arasoft Solutions
 * Todos los derechos reservados.
 * Desarrollado para el Instituto Costarricense de Electricidad (ICE).
 */
package com.Arasoftsolutions.tecniapp_ice

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.NavOptions
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.Arasoftsolutions.tecniapp_ice.Database.entities.UserEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.apellidosCompletos
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.databinding.ActivityMainBinding
import com.Arasoftsolutions.tecniapp_ice.databinding.NavHeaderMainBinding
import com.Arasoftsolutions.tecniapp_ice.preferences.DataStoreManager
import com.Arasoftsolutions.tecniapp_ice.permissions.PermissionInitializer
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriasSyncWorker
import com.Arasoftsolutions.tecniapp_ice.ui.legal.StructuredTextFormatter
import com.Arasoftsolutions.tecniapp_ice.ui.legal.StructuredTextParser
import com.Arasoftsolutions.tecniapp_ice.ui.legal.renderStructuredContent
import com.Arasoftsolutions.tecniapp_ice.update.UpdateDialog
import com.Arasoftsolutions.tecniapp_ice.update.UpdateDownloadManager
import com.Arasoftsolutions.tecniapp_ice.update.UpdateInfo
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class ActivityMain : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var navView: NavigationView
    private lateinit var auth: FirebaseAuth
    private lateinit var repository: RoomRepository
    private lateinit var headerBinding: NavHeaderMainBinding
    private val dataStore by lazy { DataStoreManager.getInstance(applicationContext) }
    private val updateDownloadManager by lazy { UpdateDownloadManager(this) }
    private lateinit var permissionInitializer: PermissionInitializer
    private var adminRoleEligible = false
    private var adminPrivilegesEnabled = true
    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        if (firebaseAuth.currentUser == null) {
            navigateToLogin()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        // ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ensureTermsAccepted()
        checkPendingUpdate()

        // Toolbar
        setSupportActionBar(binding.appBarMain.toolbar)

        // Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Room Repository
        repository = RoomRepository.getInstance(applicationContext)
        permissionInitializer = PermissionInitializer(this, dataStore)

        // Cargar datos del usuario local (Room)
        lifecycleScope.launch {
            loadUserDataFromDatabase()
        }
        observeUserUpdates()
         val currentUser = auth.currentUser
        if (currentUser != null) {
            triggerInitialAveriasSyncIfIdle()
        }

        permissionInitializer.initializeIfNeeded()

        // Drawer + Navigation
        val drawerLayout: DrawerLayout = binding.drawerLayout
        navView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)

        headerBinding = NavHeaderMainBinding.bind(navView.getHeaderView(0)).also { header ->
            header.textViewVehicle.isVisible = false
            header.textViewVehicle.text = getString(R.string.nav_header_vehicle_placeholder)
            header.root.setOnClickListener { openUserFragment() }
        }

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home,
                R.id.nav_medidor,
                R.id.nav_localizacion,
                R.id.nav_averias,
                R.id.nav_luminarias,
                R.id.nav_inventario,
                R.id.nav_mi_vehiculo,
                R.id.nav_reportes,
                R.id.nav_planillas,
                R.id.nav_programacion,
                R.id.nav_account,
                R.id.nav_settings,
                R.id.nav_help,
                R.id.nav_privacy
            ),
            drawerLayout
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        navView.setNavigationItemSelectedListener { item ->
            val currentId = navController.currentDestination?.id
            if (currentId == item.itemId) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
                return@setNavigationItemSelectedListener true
            }
            validarEtmAntesDeNavegar(item.itemId) {
                navController.navigate(item.itemId, null, buildDrawerNavOptions(navController.graph.startDestinationId))
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        lifecycleScope.launch {
            dataStore.adminPrivilegesEnabled.collect { enabled ->
                adminPrivilegesEnabled = enabled
                applyAdminMenuVisibility()
            }
        }
    }


    private fun validarEtmAntesDeNavegar(destinationId: Int, onContinue: () -> Unit) {
        val requiereEtm = destinationId == R.id.nav_averias ||
            destinationId == R.id.nav_luminarias ||
            destinationId == R.id.nav_programacion
        if (!requiereEtm) {
            onContinue()
            return
        }
        lifecycleScope.launch {
            val uid = auth.currentUser?.uid ?: run {
                onContinue()
                return@launch
            }
            val usuario = repository.obtenerUsuario(uid)
            val placa = usuario?.placaVehiculo?.trim().orEmpty()
            val placaLong = com.Arasoftsolutions.tecniapp_ice.ui.vehiculo.VehiculoPlacaUtils.parsePlacaLong(placa)
            val vehiculo = placaLong?.let { repository.obtenerVehiculoPorPlaca(it) }
            if (vehiculo != null && !vehiculo.registroCerrado) {
                MaterialAlertDialogBuilder(this@ActivityMain)
                    .setTitle(getString(R.string.mi_vehiculo_titulo))
                    .setMessage("Debes registrar y cerrar el eTM del vehículo antes de abrir este módulo.")
                    .setPositiveButton("Ir a Mi vehículo") { _, _ ->
                        val navController = findNavController(R.id.nav_host_fragment_content_main)
                        navController.navigate(
                            R.id.nav_mi_vehiculo,
                            null,
                            buildDrawerNavOptions(navController.graph.startDestinationId)
                        )
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                return@launch
            }
            onContinue()
        }
    }

    private fun triggerInitialAveriasSyncIfIdle() {
        val workManager = WorkManager.getInstance(applicationContext)
        lifecycleScope.launch {
            val infos = workManager
                .getWorkInfosForUniqueWorkFlow(AveriasSyncWorker.UNIQUE_MANUAL_WORK)
                .first()
            val hasActive = infos.any { info ->
                info.state == WorkInfo.State.ENQUEUED ||
                    info.state == WorkInfo.State.RUNNING ||
                    info.state == WorkInfo.State.BLOCKED
            }
            if (!hasActive) {
                AveriasSyncWorker.triggerNow(applicationContext)
            }
        }
    }

    private fun checkPendingUpdate() {
        lifecycleScope.launch {
            val info = dataStore.pendingUpdateInfo.first()
            if (info == null) return@launch
            if (!info.isNewerThan(BuildConfig.VERSION_CODE)) {
                dataStore.setPendingUpdateInfo(null)
                return@launch
            }
            showUpdateDialog(info)
        }
    }

    private fun showUpdateDialog(info: UpdateInfo) {
        if (supportFragmentManager.findFragmentByTag(UPDATE_DIALOG_TAG) != null) return
        if (supportFragmentManager.isStateSaved) return
        val dialog = UpdateDialog.newInstance(info).apply {
            onConfirmUpdate = { updateDownloadManager.startDownload(this@ActivityMain, it) }
        }
        dialog.show(supportFragmentManager, UPDATE_DIALOG_TAG)
    }

    companion object {
        private const val UPDATE_DIALOG_TAG = "update_dialog"
    }

    /**
     * Carga el usuario desde Room usando el UID del usuario autenticado.
     */
    private suspend fun loadUserDataFromDatabase() {
        val uid = auth.currentUser?.uid
        val user: UserEntity? = if (uid != null) {
            repository.obtenerUsuario(uid)
        } else {
            null
        }

        user?.let {
            val nombreLog = listOfNotNull(it.nombre, it.apellidosCompletos)
                .joinToString(" ")
                .ifBlank { it.email ?: it.uid }
            Log.d("ActivityMain", "Usuario local: $nombreLog - ${it.email}")
            updateNavHeader(it)
            updateAdminMenuVisibility(it)
        } ?: run {
            Log.e("ActivityMain", "No se encontró usuario en la base local.")
        }
    }

    private fun updateNavHeader(usuario: UserEntity) {
        val fullName = listOfNotNull(usuario.nombre, usuario.apellidosCompletos)
            .joinToString(" ")
            .trim()
            .ifBlank { getString(R.string.profile_default_name) }

        headerBinding.textViewFullName.text = fullName
        headerBinding.textViewEmail.text = usuario.email ?: getString(R.string.profile_summary_placeholder)
        headerBinding.textViewCedula.text = getString(
            R.string.nav_header_cedula_format,
            displayValue(usuario.cedula)
        )
        headerBinding.textViewSubregion.text = getString(
            R.string.nav_header_subregion_format,
            displayValue(usuario.subregion)
        )
        headerBinding.textViewAgency.text = getString(
            R.string.nav_header_agency_format,
            displayValue(usuario.agencia)
        )

        val vehiculo = usuario.placaVehiculo?.takeUnless { it.isBlank() }
        if (vehiculo.isNullOrBlank()) {
            headerBinding.textViewVehicle.isVisible = false
        } else {
            headerBinding.textViewVehicle.isVisible = true
            headerBinding.textViewVehicle.text = getString(R.string.nav_header_vehicle_format, vehiculo)
        }

        Glide.with(this)
            .load(usuario.fotoUrl)
            .placeholder(R.drawable.default_profile_picture)
            .error(R.drawable.default_profile_picture)
            .into(headerBinding.imageViewProfile)
    }

    private fun updateAdminMenuVisibility(usuario: UserEntity) {
        val rol = usuario.rol?.trim()?.lowercase()
        adminRoleEligible = rol == "administrador" || rol == "supervisor"
        applyAdminMenuVisibility()
    }

    private fun applyAdminMenuVisibility() {
        if (!this::navView.isInitialized) return
        navView.menu.findItem(R.id.nav_admin)?.isVisible = adminRoleEligible && adminPrivilegesEnabled
    }

    private fun displayValue(value: String?): String =
        value?.takeIf { it.isNotBlank() } ?: getString(R.string.profile_summary_placeholder)

    private fun openUserFragment() {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        if (navController.currentDestination?.id == R.id.nav_account) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            return
        }
        navController.navigate(
            R.id.nav_account,
            null,
            buildDrawerNavOptions(navController.graph.startDestinationId)
        )
        binding.drawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun buildDrawerNavOptions(startDestinationId: Int): NavOptions =
        NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setRestoreState(true)
            .setPopUpTo(startDestinationId, inclusive = false, saveState = true)
            .build()

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onResume() {
        super.onResume()
        if (auth.currentUser == null) {
            navigateToLogin()
            return
        }
        lifecycleScope.launch { loadUserDataFromDatabase() }
    }

    override fun onStart() {
        super.onStart()
        auth.addAuthStateListener(authStateListener)
    }

    override fun onStop() {
        super.onStop()
        auth.removeAuthStateListener(authStateListener)
    }

    private fun observeUserUpdates() {
        val uid = auth.currentUser?.uid ?: return
        lifecycleScope.launch {
            repository.observarUsuario(uid).collect { usuario ->
                if (usuario != null) {
                    updateNavHeader(usuario)
                    updateAdminMenuVisibility(usuario)
                }
            }
        }
    }

    private fun navigateToLogin() {
        if (isFinishing) return
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    fun refreshNavHeader() {
        lifecycleScope.launch { loadUserDataFromDatabase() }
    }

    private fun ensureTermsAccepted() {
        val prefs = getSharedPreferences("TecniAppPrefs", MODE_PRIVATE)
        if (prefs.getBoolean("termsAccepted", false)) {
            return
        }

        val consentView = layoutInflater.inflate(R.layout.dialog_terms, null)
        val replacements = mapOf(
            "year" to Calendar.getInstance().get(Calendar.YEAR).toString(),
            "supportEmail" to getString(R.string.privacy_policy_contact_email)
        )
        val termsContent = runCatching {
            val termsDocument = StructuredTextParser.parse(
                context = this,
                xmlRes = R.xml.terms_of_use,
                replacements = replacements
            )
            StructuredTextFormatter.buildDocument(
                context = this,
                document = termsDocument,
                includeIntro = true,
                includeFooter = true
            )
        }.getOrElse { error ->
            Log.e("ActivityMain", "Error al cargar los términos", error)
            getString(R.string.structured_text_parse_error)
        }
        consentView.findViewById<TextView>(R.id.textTermsContent)
            .renderStructuredContent(termsContent)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.terms_title)
            .setView(consentView)
            .setCancelable(false)
            .setPositiveButton(R.string.terms_accept) { dialog, _ ->
                prefs.edit().putBoolean("termsAccepted", true).apply()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.terms_decline) { _, _ ->
                prefs.edit().putBoolean("termsAccepted", false).apply()
                finishAffinity()
            }
            .show()
    }
}
