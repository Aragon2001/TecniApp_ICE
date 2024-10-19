package com.Arasoftsolutions.tecniapp_ice

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.Arasoftsolutions.tecniapp_ice.registro.Paso1Fragment
import com.Arasoftsolutions.tecniapp_ice.registro.Paso2Fragment
import com.Arasoftsolutions.tecniapp_ice.registro.Paso3Fragment
import com.Arasoftsolutions.tecniapp_ice.registro.Paso4Fragment
import com.Arasoftsolutions.tecniapp_ice.registro.RegistroViewModel


class RegistroActivity : AppCompatActivity() {

    private lateinit var viewModel: RegistroViewModel

    private val stepFragmentMap = mapOf(
        1 to Paso2Fragment(),
        2 to Paso3Fragment(),
        3 to Paso4Fragment()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registro)

        // Inicialización del ViewModel
        viewModel = ViewModelProvider(this).get(RegistroViewModel::class.java)

        // Cargar el primer fragmento al iniciar la actividad
        if (savedInstanceState == null) {
            loadFragment(Paso1Fragment())
        }
    }

    // Función para cargar un fragmento
    private fun loadFragment(fragment: Fragment) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, fragment)
        transaction.addToBackStack(null) // Añadir la transacción al stack para poder navegar atrás
        transaction.commit()
    }

    fun goToLogin() {
        // Verificar si la actividad de login ya está en la pila
        val intent = Intent(this, LoginActivity::class.java)

        // Opcional: agregar flags para evitar múltiples instancias de la actividad de login
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)

        startActivity(intent)

        // Cerrar la actividad actual
        finish() // Esto cerrará la actividad actual (RegistroActivity)
    }

    // Método para ir al siguiente paso
    fun goToNextStep(step: Int) {
        stepFragmentMap[step]?.let { fragment ->
            loadFragment(fragment)
        }
    }
}
