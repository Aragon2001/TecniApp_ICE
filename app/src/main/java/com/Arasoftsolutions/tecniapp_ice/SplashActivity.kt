package com.Arasoftsolutions.tecniapp_ice

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.Arasoftsolutions.tecniapp_ice.preferences.DataStoreManager
import com.Arasoftsolutions.tecniapp_ice.ui.onboarding.OnboardingActivity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val dataStore by lazy { DataStoreManager.getInstance(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val centerGroup = findViewById<View>(R.id.splashCenterGroup)
        val bottomGroup = findViewById<View>(R.id.splashBottomGroup)

        playEntranceAnimation(centerGroup, bottomGroup)

        lifecycleScope.launch {
            delay(2200)
            routeFromSplash()
        }
    }

    private fun playEntranceAnimation(centerGroup: View, bottomGroup: View) {
        // Grupo central: escala desde 0.88 + fade in
        val centerScaleX = ObjectAnimator.ofFloat(centerGroup, View.SCALE_X, 0.88f, 1f).apply {
            duration = 750
            interpolator = OvershootInterpolator(1.1f)
        }
        val centerScaleY = ObjectAnimator.ofFloat(centerGroup, View.SCALE_Y, 0.88f, 1f).apply {
            duration = 750
            interpolator = OvershootInterpolator(1.1f)
        }
        val centerFade = ObjectAnimator.ofFloat(centerGroup, View.ALPHA, 0f, 1f).apply {
            duration = 600
        }
        val centerTranslate = ObjectAnimator.ofFloat(centerGroup, View.TRANSLATION_Y, 24f, 0f).apply {
            duration = 750
            interpolator = DecelerateInterpolator()
        }

        // Grupo inferior: fade in retardado
        val bottomFade = ObjectAnimator.ofFloat(bottomGroup, View.ALPHA, 0f, 1f).apply {
            duration = 500
            startDelay = 700
        }

        AnimatorSet().apply {
            playTogether(centerScaleX, centerScaleY, centerFade, centerTranslate, bottomFade)
            start()
        }
    }

    private suspend fun routeFromSplash() {
        val onboardingCompleted = dataStore.onboardingCompleted.first()
        if (!onboardingCompleted) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        val nextActivity = if (auth.currentUser == null) {
            Intent(this, LoginActivity::class.java)
        } else {
            Intent(this, ActivityMain::class.java)
        }
        startActivity(nextActivity)
        finish()
    }
}
