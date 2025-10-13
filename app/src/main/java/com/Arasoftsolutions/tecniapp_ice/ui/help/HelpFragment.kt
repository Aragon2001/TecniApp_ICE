/**
 * TecniApp ICE © 2025 Arasoft Solutions
 * Todos los derechos reservados.
 * Desarrollado para el Instituto Costarricense de Electricidad (ICE).
 */
package com.Arasoftsolutions.tecniapp_ice.ui.help

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentHelpBinding

class HelpFragment : Fragment(R.layout.fragment_help) {

    private var _binding: FragmentHelpBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHelpBinding.bind(view)

        binding.textHelpSubtitle.text = HtmlCompat.fromHtml(
            getString(R.string.about_screen_intro_html),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )

        binding.textAboutMissionBody.text = HtmlCompat.fromHtml(
            getString(R.string.about_card_mission_body_html),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )

        binding.textAboutHighlightsBody.text = HtmlCompat.fromHtml(
            getString(R.string.about_card_highlights_body_html),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )

        binding.textAboutSupportBody.apply {
            text = HtmlCompat.fromHtml(
                getString(R.string.about_card_support_body_html),
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )
            movementMethod = LinkMovementMethod.getInstance()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
