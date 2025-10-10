package com.Arasoftsolutions.tecniapp_ice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.Arasoftsolutions.tecniapp_ice.Database.entities.UserEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.apellidosCompletos
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.databinding.ActivityMainBinding
import com.Arasoftsolutions.tecniapp_ice.databinding.NavHeaderMainBinding
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriaNotifications
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriasSyncWorker
import com.bumptech.glide.Glide
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class ActivityMain : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var repository: RoomRepository
    private lateinit var headerBinding: NavHeaderMainBinding

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val messageRes = if (granted) {
                R.string.averia_notification_permission_granted
            } else {
                R.string.averia_notification_permission_denied
            }
            Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        // ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Toolbar
        setSupportActionBar(binding.appBarMain.toolbar)

        // Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Room Repository
        repository = RoomRepository.getInstance(applicationContext)

        // Cargar datos del usuario local (Room)
        lifecycleScope.launch {
            loadUserDataFromDatabase()
        }

        AveriaNotifications.ensureChannel(this)
        requestNotificationPermissionIfNeeded()
        AveriasSyncWorker.schedule(applicationContext)
        AveriasSyncWorker.triggerNow(applicationContext)

        // Drawer + Navigation
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)

        headerBinding = NavHeaderMainBinding.bind(navView.getHeaderView(0)).also { header ->
            header.chipVehicle.isVisible = false
            header.chipVehicle.text = getString(R.string.nav_header_vehicle_placeholder)
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
                R.id.nav_reportes,
                R.id.nav_programacion,
                R.id.nav_account
            ),
            drawerLayout
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
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
            headerBinding.chipVehicle.isVisible = false
        } else {
            headerBinding.chipVehicle.isVisible = true
            headerBinding.chipVehicle.text = getString(R.string.nav_header_vehicle_format, vehiculo)
        }

        Glide.with(this)
            .load(usuario.fotoUrl)
            .placeholder(R.drawable.default_profile_picture)
            .error(R.drawable.default_profile_picture)
            .into(headerBinding.imageViewProfile)
    }

    private fun displayValue(value: String?): String =
        value?.takeIf { it.isNotBlank() } ?: getString(R.string.profile_summary_placeholder)

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                signOutAndRedirect()
                true
            }
            R.id.action_accounts -> {
                openUserFragment()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openUserFragment() {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        navController.navigate(R.id.nav_account)
        binding.drawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun signOutAndRedirect() {
        auth.signOut()

        getSharedPreferences("TecniAppPrefs", MODE_PRIVATE).edit().apply {
            clear()
            apply()
        }
        getSharedPreferences("app_preferences", MODE_PRIVATE).edit().apply {
            clear()
            apply()
        }

        if (auth.currentUser == null) {
            Toast.makeText(this, "Sesión cerrada correctamente", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Error al cerrar sesión", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { loadUserDataFromDatabase() }
    }

    fun refreshNavHeader() {
        lifecycleScope.launch { loadUserDataFromDatabase() }
    }
}
