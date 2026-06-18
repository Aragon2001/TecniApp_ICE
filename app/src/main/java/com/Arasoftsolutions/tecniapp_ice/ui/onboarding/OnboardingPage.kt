package com.Arasoftsolutions.tecniapp_ice.ui.onboarding

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.Arasoftsolutions.tecniapp_ice.R

data class OnboardingPage(
    @DrawableRes val illustrationRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @ColorRes val accentColorRes: Int = R.color.ice_blue
)
