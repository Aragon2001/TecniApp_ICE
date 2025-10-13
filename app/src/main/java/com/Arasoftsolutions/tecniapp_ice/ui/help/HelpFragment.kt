/**
 * TecniApp ICE © 2025 Arasoft Solutions
 * Todos los derechos reservados.
 * Desarrollado para el Instituto Costarricense de Electricidad (ICE).
 */
package com.Arasoftsolutions.tecniapp_ice.ui.help

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentHelpBinding
import com.Arasoftsolutions.tecniapp_ice.ui.legal.StructuredTextFormatter
import com.Arasoftsolutions.tecniapp_ice.ui.legal.StructuredTextParser
import com.Arasoftsolutions.tecniapp_ice.ui.legal.renderStructuredContent
import java.util.Calendar

class HelpFragment : Fragment(R.layout.fragment_help) {

    private var _binding: FragmentHelpBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHelpBinding.bind(view)

        val currentYear = Calendar.getInstance().get(Calendar.YEAR).toString()
        val replacements = mapOf(
            "year" to currentYear,
            "supportEmail" to getString(R.string.privacy_policy_contact_email)
        )
        val aboutDocument = runCatching {
            StructuredTextParser.parse(
                requireContext(),
                R.xml.about_overview,
                replacements
            )
        }.onFailure { error ->
            Log.e("HelpFragment", "Error al cargar la información de Acerca de", error)
        }.getOrNull()

        if (aboutDocument != null) {
            binding.textHelpSubtitle.text = aboutDocument.intro

            val aboutSections = aboutDocument.sections
            aboutSections.getOrNull(0)?.let { section ->
                binding.textAboutMissionBody.renderStructuredContent(
                    StructuredTextFormatter.buildSectionBody(requireContext(), section)
                )
            }

            aboutSections.getOrNull(1)?.let { section ->
                binding.textAboutHighlightsBody.renderStructuredContent(
                    StructuredTextFormatter.buildSectionBody(requireContext(), section)
                )
            }

            aboutSections.getOrNull(2)?.let { section ->
                val supportBody = StructuredTextFormatter.buildSectionBody(requireContext(), section)
                val footer = StructuredTextFormatter.buildBlocks(requireContext(), aboutDocument.footer)
                binding.textAboutSupportBody.renderStructuredContent(
                    if (footer.isNotEmpty()) {
                        SpannableStringBuilder(supportBody).apply {
                            append("\n\n")
                            append(footer)
                        }
                    } else {
                        supportBody
                    }
                )
            }
        } else {
            val fallback = getString(R.string.structured_text_parse_error)
            binding.textHelpSubtitle.text = fallback
            binding.textAboutMissionBody.renderStructuredContent(fallback)
            binding.textAboutHighlightsBody.renderStructuredContent(fallback)
            binding.textAboutSupportBody.renderStructuredContent(fallback)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
