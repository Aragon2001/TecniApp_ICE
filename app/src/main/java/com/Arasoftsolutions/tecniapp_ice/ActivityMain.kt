package com.Arasoftsolutions.tecniapp_ice

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.Arasoftsolutions.tecniapp_ice.Database.entities.UserEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ActivityMain : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var repository: RoomRepository

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
        repository = RoomRepository(applicationContext)

        // Cargar datos del usuario local (Room)
        lifecycleScope.launch {
            loadUserDataFromDatabase()
        }

        // Drawer + Navigation
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)

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
            Log.d("ActivityMain", "Usuario local: ${it.nombre} ${it.apellidos} - ${it.email}")
            updateNavHeader(it)
        } ?: run {
            Log.e("ActivityMain", "No se encontró usuario en la base local.")
        }
    }

    private fun updateNavHeader(usuario: UserEntity) {
        val headerView: View = binding.navView.getHeaderView(0)
        val profileImageView = headerView.findViewById<ImageView>(R.id.imageViewProfile)
        val fullNameTextView = headerView.findViewById<TextView>(R.id.textViewFullName)
        val emailTextView = headerView.findViewById<TextView>(R.id.textViewEmail)
        val vehiculoTextView = headerView.findViewById<TextView>(R.id.textViewVehiculo)

        fullNameTextView.text = "${usuario.nombre} ${usuario.apellidos}"
        emailTextView.text = usuario.email
        vehiculoTextView.text = "Vehículo: ${usuario.placaVehiculo ?: "No disponible"}"
        // Si en el futuro agregas foto de perfil, úsala en profileImageView.
    }

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
    }

    private fun signOutAndRedirect() {
        auth.signOut()

        getSharedPreferences("TecniAppPrefs", MODE_PRIVATE).edit().apply {
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

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
