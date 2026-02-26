package com.Arasoftsolutions.tecniapp_ice.ui.legal

import androidx.annotation.StringRes
import androidx.annotation.XmlRes
import com.Arasoftsolutions.tecniapp_ice.R

enum class LegalDocType(
    @StringRes val titleRes: Int,
    @XmlRes val documentRes: Int,
    val supportsContact: Boolean = true
) {
    PRIVACY(
        titleRes = R.string.privacy_policy_title,
        documentRes = R.xml.privacy_policy
    ),
    TERMS(
        titleRes = R.string.terms_title,
        documentRes = R.xml.terms_of_use,
        supportsContact = false
    ),
    ABOUT(
        titleRes = R.string.about_screen_title,
        documentRes = R.xml.about_overview
    ),
    HELP(
        titleRes = R.string.legal_help_title,
        documentRes = R.xml.support_help
    );

    companion object {
        const val ARG_KEY = "docType"

        fun fromRaw(raw: String?): LegalDocType = entries.firstOrNull { it.name == raw } ?: PRIVACY
    }
}
