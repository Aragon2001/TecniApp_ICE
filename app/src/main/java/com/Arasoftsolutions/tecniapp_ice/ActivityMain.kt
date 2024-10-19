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
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.Arasoftsolutions.tecniapp_ice.User.UserViewModel
import com.Arasoftsolutions.tecniapp_ice.User.User
import com.Arasoftsolutions.tecniapp_ice.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.squareup.picasso.Picasso

class ActivityMain : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var userViewModel: UserViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflar la vista usando ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurar el Toolbar
        setSupportActionBar(binding.appBarMain.toolbar)

        // Inicializar FirebaseAuth
        auth = FirebaseAuth.getInstance()

        // Inicializar UserViewModel
        userViewModel = ViewModelProvider(this).get(UserViewModel::class.java)

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

        // Cargar datos del usuario al iniciar la actividad
        val currentUser = auth.currentUser
        currentUser?.let {
            // Llamar al ViewModel para obtener los datos del usuario
            userViewModel.userData.observe(this) { usuario ->
                // Actualizar el encabezado de la navegación con los datos del usuario
                updateNavHeader(usuario)
            }
        }
    }

    private fun updateNavHeader(usuario: User) {
        // Obtener la vista del encabezado de la navegación
        val headerView: View = binding.navView.getHeaderView(0)
        val profileImageView = headerView.findViewById<ImageView>(R.id.imageViewProfile)
        val fullNameTextView = headerView.findViewById<TextView>(R.id.textViewFullName)
        val emailTextView = headerView.findViewById<TextView>(R.id.textViewEmail)
        val vehiculoTextView = headerView.findViewById<TextView>(R.id.textViewVehiculo)

        // Cargar la imagen de perfil
        if (auth.currentUser?.photoUrl != null) {
            Picasso.get().load(auth.currentUser?.photoUrl).into(profileImageView)
        } else {
            profileImageView.setImageResource(R.drawable.default_profile_picture)
        }

        // Actualizar los TextViews con los datos del usuario del ViewModel
        fullNameTextView.text = "${usuario.nombre} ${usuario.apellidos}"
        emailTextView.text = usuario.email
        vehiculoTextView.text = "Vehículo: ${usuario.placaVehiculo ?: "No disponible"}"
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

        // Verificar si el usuario ha cerrado sesión
        if (auth.currentUser == null) {
            Toast.makeText(this, "Sesión cerrada correctamente", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Error al cerrar sesión", Toast.LENGTH_SHORT).show()
            return
        }

        // Redirigir a LoginActivity
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)

        // Cerrar la actividad actual
        Log.d("ActivityMain", "Cerrando sesión y redirigiendo a LoginActivity")
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
