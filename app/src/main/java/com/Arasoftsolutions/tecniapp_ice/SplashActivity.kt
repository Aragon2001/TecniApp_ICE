package com.Arasoftsolutions.tecniapp_ice

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        auth = FirebaseAuth.getInstance()

        // Obtener el ImageView del logo
        val logo = findViewById<ImageView>(R.id.imageViewLogo)

        // Añadir animación fadeIn al logo
        val fadeIn = ObjectAnimator.ofFloat(logo, "alpha", 0f, 1f)
        fadeIn.duration = 1500
        fadeIn.start()

        // Retrasar la transición a la siguiente actividad
        Handler(Looper.getMainLooper()).postDelayed({
            checkAuthentication()
        }, 2000) // 2 segundos de pausa para mostrar la animación
    }

    // Método para verificar la autenticación del usuario
    private fun checkAuthentication() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // Si no está autenticado, redirigir al LoginActivity
            startActivity(Intent(this, LoginActivity::class.java))
        } else {
            // Si ya está autenticado, redirigir a la actividad principal
            startActivity(Intent(this, ActivityMain::class.java))
        }
        finish() // Finaliza SplashActivity
    }
}
