package com.Arasoftsolutions.tecniapp_ice.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.Arasoftsolutions.tecniapp_ice.ActivityMain
import com.Arasoftsolutions.tecniapp_ice.LoginActivity
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.ActivityOnboardingBinding
import com.Arasoftsolutions.tecniapp_ice.preferences.DataStoreManager
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private val dataStore by lazy { DataStoreManager.getInstance(applicationContext) }
    private val returnToCaller by lazy { intent.getBooleanExtra(EXTRA_RETURN_TO_CALLER, false) }
    private var finishingFlow = false

    private val pages by lazy {
        listOf(
            OnboardingPage(
                illustrationRes = R.drawable.ic_menu_home,
                titleRes = R.string.onboarding_title_welcome,
                descriptionRes = R.string.onboarding_desc_welcome
            ),
            OnboardingPage(
                illustrationRes = R.drawable.ic_menu_localizacion,
                titleRes = R.string.onboarding_title_location,
                descriptionRes = R.string.onboarding_desc_location
            ),
            OnboardingPage(
                illustrationRes = R.drawable.ic_menu_medidor,
                titleRes = R.string.onboarding_title_field_ops,
                descriptionRes = R.string.onboarding_desc_field_ops
            ),
            OnboardingPage(
                illustrationRes = R.drawable.ic_menu_averias,
                titleRes = R.string.onboarding_title_incidents,
                descriptionRes = R.string.onboarding_desc_incidents
            ),
            OnboardingPage(
                illustrationRes = R.drawable.ic_menu_programacion,
                titleRes = R.string.onboarding_title_planning,
                descriptionRes = R.string.onboarding_desc_planning
            )
        )
    }

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)
            updateButtonState(position)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupViewPager()
        setupButtons()

        updateButtonState(binding.viewPager.currentItem)
    }

    override fun onDestroy() {
        binding.viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
        super.onDestroy()
    }

    private fun setupToolbar() {
        if (returnToCaller) {
            binding.toolbar.navigationIcon = AppCompatResources.getDrawable(this, R.drawable.ic_arrow_back)
            binding.toolbar.setNavigationOnClickListener { finish() }
        } else {
            binding.toolbar.navigationIcon = null
        }
    }

    private fun setupViewPager() {
        val adapter = OnboardingAdapter(pages)
        binding.viewPager.adapter = adapter
        binding.viewPager.registerOnPageChangeCallback(pageChangeCallback)

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, _ ->
            tab.text = " "
        }.attach()
    }

    private fun setupButtons() {
        binding.buttonSkip.text = getString(R.string.onboarding_action_skip)
        binding.buttonSkip.setOnClickListener { finishOnboarding() }

        binding.buttonNext.setOnClickListener {
            val nextIndex = binding.viewPager.currentItem + 1
            if (nextIndex < pages.size) {
                binding.viewPager.currentItem = nextIndex
            } else {
                finishOnboarding()
            }
        }
    }

    private fun updateButtonState(position: Int) {
        val isLastPage = position == pages.lastIndex
        binding.buttonNext.text = if (isLastPage) {
            getString(R.string.onboarding_action_start)
        } else {
            getString(R.string.onboarding_action_next)
        }
        binding.buttonSkip.isVisible = !isLastPage
    }

    private fun finishOnboarding() {
        if (finishingFlow) return
        finishingFlow = true
        binding.buttonNext.isEnabled = false
        binding.buttonSkip.isEnabled = false

        lifecycleScope.launch {
            dataStore.setOnboardingCompleted(true)

            if (returnToCaller) {
                finish()
            } else {
                navigateToEntryPoint()
            }
        }
    }

    private fun navigateToEntryPoint() {
        val nextIntent = if (FirebaseAuth.getInstance().currentUser == null) {
            Intent(this, LoginActivity::class.java)
        } else {
            Intent(this, ActivityMain::class.java)
        }
        startActivity(nextIntent)
        finish()
    }

    companion object {
        const val EXTRA_RETURN_TO_CALLER = "extra_return_to_caller"
    }
}
