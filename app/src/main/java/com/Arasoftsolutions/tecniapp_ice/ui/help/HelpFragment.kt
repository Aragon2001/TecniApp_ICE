/**
 * TecniApp ICE © 2025 Arasoft Solutions
 * Todos los derechos reservados.
 * Desarrollado para el Instituto Costarricense de Electricidad (ICE).
 */
package com.Arasoftsolutions.tecniapp_ice.ui.help

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentHelpBinding
import com.Arasoftsolutions.tecniapp_ice.ui.legal.StructuredTextFormatter
import com.Arasoftsolutions.tecniapp_ice.ui.legal.StructuredTextParser
import com.Arasoftsolutions.tecniapp_ice.ui.legal.renderStructuredContent
import java.util.Calendar
import kotlin.math.roundToInt

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

        binding.buttonAboutContact.setOnClickListener {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:" + getString(R.string.privacy_policy_contact_email))
            }
            runCatching { startActivity(emailIntent) }
                .onFailure {
                    Toast.makeText(
                        requireContext(),
                        R.string.privacy_policy_contact_error,
                        Toast.LENGTH_LONG
                    ).show()
                }
        }

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
            binding.textHelpSubtitle.renderStructuredContent(aboutDocument.intro)

            val container = binding.containerAboutSections
            container.removeAllViews()
            aboutDocument.sections.forEach { section ->
                val sectionView = layoutInflater.inflate(R.layout.item_structured_section, container, false)
                val titleView = sectionView.findViewById<TextView>(R.id.textSectionTitle)
                if (section.title.isBlank()) {
                    titleView.isVisible = false
                } else {
                    titleView.text = section.title
                    titleView.isVisible = true
                }
                val bodyView = sectionView.findViewById<TextView>(R.id.textSectionBody)
                bodyView.renderStructuredContent(
                    StructuredTextFormatter.buildSectionBody(requireContext(), section)
                )
                container.addView(sectionView)
            }

            val footerContent = StructuredTextFormatter.buildBlocks(
                requireContext(),
                aboutDocument.footer
            )
            val hasFooter = footerContent.isNotEmpty()
            binding.aboutFooterDivider.isVisible = hasFooter
            binding.textHelpFooter.isVisible = hasFooter
            binding.textHelpFooter.renderStructuredContent(
                if (hasFooter) footerContent else null
            )
        } else {
            val fallback = getString(R.string.structured_text_parse_error)
            binding.textHelpSubtitle.renderStructuredContent(fallback)
            binding.containerAboutSections.removeAllViews()
            val padding = (16 * resources.displayMetrics.density).roundToInt()
            val fallbackView = TextView(requireContext()).apply {
                TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Material3_BodyLarge)
                text = fallback
                setPadding(0, padding, 0, padding)
            }
            binding.containerAboutSections.addView(fallbackView)
            binding.aboutFooterDivider.isVisible = false
            binding.textHelpFooter.isVisible = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
