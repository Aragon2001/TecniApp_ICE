package com.Arasoftsolutions.tecniapp_ice.ui.legal

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.Arasoftsolutions.tecniapp_ice.R

data class LegalEntryUiModel(
    val type: LegalDocType,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val iconRes: Int
)

data class LegalSectionUiModel(
    val title: String,
    val body: CharSequence
)

object LegalUiModelFactory {
    fun legalEntries(): List<LegalEntryUiModel> = listOf(
        LegalEntryUiModel(
            type = LegalDocType.PRIVACY,
            titleRes = R.string.privacy_policy_title,
            descriptionRes = R.string.legal_privacy_description,
            iconRes = R.drawable.ic_menu_policy
        ),
        LegalEntryUiModel(
            type = LegalDocType.TERMS,
            titleRes = R.string.terms_title,
            descriptionRes = R.string.legal_terms_description,
            iconRes = R.drawable.ic_menu_policy
        ),
        LegalEntryUiModel(
            type = LegalDocType.ABOUT,
            titleRes = R.string.about_screen_title,
            descriptionRes = R.string.legal_about_description,
            iconRes = R.drawable.ic_menu_help
        ),
        LegalEntryUiModel(
            type = LegalDocType.HELP,
            titleRes = R.string.legal_help_title,
            descriptionRes = R.string.legal_help_description,
            iconRes = R.drawable.ic_menu_help
        )
    )
}
