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
import com.Arasoftsolutions.tecniapp_ice.Database.Synchronizer
import com.Arasoftsolutions.tecniapp_ice.Database.TecniAppDatabaseHelper
import com.Arasoftsolutions.tecniapp_ice.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch


class ActivityMain : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var dbHelper: TecniAppDatabaseHelper
    private lateinit var synchronizer: Synchronizer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflar la vista usando ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurar el Toolbar
        setSupportActionBar(binding.appBarMain.toolbar)

        // Inicializar FirebaseAuth
        auth = FirebaseAuth.getInstance()

       // updateNavHeader()

        // Inicializar el helper de base de datos
        dbHelper = TecniAppDatabaseHelper(this)

        // Inicializar el Synchronizer
        synchronizer = Synchronizer(applicationContext)

        // Iniciar la sincronización de datos
        lifecycleScope.launch {
            synchronizer.initialize() // Esto llamará a la sincronización de datos
        }

        // Configurar DrawerLayout y NavigationView
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)

        // Definir destinos de nivel superior
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_medidor, R.id.nav_localizacion,
                R.id.nav_averias, R.id.nav_luminarias, R.id.nav_inventario,
                R.id.nav_reportes, R.id.nav_programacion, R.id.nav_account
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // Cargar los datos del usuario desde la base de datos local
        loadUserDataFromDatabase()
    }

    private fun loadUserDataFromDatabase() {
        // Obtener los datos del usuario desde la base de datos local
        val usuario = dbHelper.getUser() // Asegúrate de que esta función esté implementada en tu helper de base de datos

        // Si el usuario existe, actualizar el nav header con sus datos
        usuario?.let {
            Log.d("ActivityMain", "Datos del usuario: ${it.nombre}, ${it.apellidos}, ${it.email}")
            updateNavHeader(it)
        } ?: run {
            Log.e("ActivityMain", "Usuario no encontrado en la base de datos local.")
        }
    }

    private fun updateNavHeader(usuario: com.Arasoftsolutions.tecniapp_ice.Database.Tablas.User) {
        runOnUiThread {
            val headerView: View = binding.navView.getHeaderView(0)
            val profileImageView = headerView.findViewById<ImageView>(R.id.imageViewProfile)
            val fullNameTextView = headerView.findViewById<TextView>(R.id.textViewFullName)
            val emailTextView = headerView.findViewById<TextView>(R.id.textViewEmail)
            val vehiculoTextView = headerView.findViewById<TextView>(R.id.textViewVehiculo)

            // Actualizar los TextViews con los datos del usuario
            fullNameTextView.text = "${usuario.nombre} ${usuario.apellidos}"
            emailTextView.text = usuario.email
            vehiculoTextView.text = "Vehículo: ${usuario.placaVehiculo ?: "No disponible"}"
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflar el menú; esto añade elementos a la barra de acción si está presente.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                signOutAndRedirect() // Manejar la acción de cierre de sesión
                true
            }
            R.id.action_accounts -> {
                // Abrir el UserFragment
                openUserFragment()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openUserFragment() {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        navController.navigate(R.id.nav_account) // Asegúrate de que el ID sea correcto en tu nav_graph.xml
    }

    private fun signOutAndRedirect() {
        // Cerrar sesión de Firebase
        auth.signOut()

        // Limpiar datos de SharedPreferences
        getSharedPreferences("TecniAppPrefs", MODE_PRIVATE).edit().apply {
            clear()
            apply()
        }

        // Verificar si el usuario ha cerrado sesión correctamente
        if (auth.currentUser == null) {
            Toast.makeText(this, "Sesión cerrada correctamente", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Error al cerrar sesión", Toast.LENGTH_SHORT).show()
            return
        }

        // Redirigir a LoginActivity y limpiar la pila de actividades
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)

        Log.d("ActivityMain", "Cerrando sesión y redirigiendo a LoginActivity")
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
