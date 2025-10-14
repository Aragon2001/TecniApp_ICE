package com.Arasoftsolutions.tecniapp_ice

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.Arasoftsolutions.tecniapp_ice.preferences.DataStoreManager
import com.Arasoftsolutions.tecniapp_ice.ui.onboarding.OnboardingActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val dataStore by lazy { DataStoreManager.getInstance(applicationContext) }

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

        lifecycleScope.launch {
            delay(2000) // 2 segundos de pausa para mostrar la animación
            routeFromSplash()
        }
    }

    private suspend fun routeFromSplash() {
        val onboardingCompleted = dataStore.onboardingCompleted.first()
        if (onboardingCompleted) {
            navigateAfterSplash()
        } else {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
        }
    }

    private fun navigateAfterSplash() {
        val nextActivity = if (auth.currentUser == null) {
            Intent(this, LoginActivity::class.java)
        } else {
            Intent(this, ActivityMain::class.java)
        }
        startActivity(nextActivity)
        finish()
    }
}
