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

    // FIX: eliminado el stepFragmentMap que creaba 3 instancias de Fragment
    // en onCreate (y en cada rotación). Ahora se instancian solo cuando se
    // necesitan dentro de goToNextStep.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registro)

        viewModel = ViewModelProvider(this).get(RegistroViewModel::class.java)

        if (savedInstanceState == null) {
            loadFragment(Paso1Fragment())
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        finish()
    }

    // FIX: instanciar el fragmento en el momento de navegar para que el
    // sistema de fragmentos gestione correctamente su ciclo de vida.
    fun goToNextStep(step: Int) {
        val fragment: Fragment = when (step) {
            1 -> Paso2Fragment()
            2 -> Paso3Fragment()
            3 -> Paso4Fragment()
            else -> return
        }
        loadFragment(fragment)
    }
}