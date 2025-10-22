/**
 * TecniApp ICE © 2025 Arasoft Solutions
 * Todos los derechos reservados.
 * Desarrollado para el Instituto Costarricense de Electricidad (ICE).
 */
package com.Arasoftsolutions.tecniapp_ice.ui.policies

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentPoliciesBinding
import com.Arasoftsolutions.tecniapp_ice.ui.legal.StructuredTextFormatter
import com.Arasoftsolutions.tecniapp_ice.ui.legal.StructuredTextParser
import com.Arasoftsolutions.tecniapp_ice.ui.legal.renderStructuredContent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Calendar

class PoliciesFragment : Fragment(R.layout.fragment_policies) {

    private var _binding: FragmentPoliciesBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPoliciesBinding.bind(view)

        val currentYear = Calendar.getInstance().get(Calendar.YEAR).toString()
        val replacements = mapOf(
            "year" to currentYear,
            "supportEmail" to getString(R.string.privacy_policy_contact_email)
        )
        val privacyDocument = runCatching {
            StructuredTextParser.parse(
                requireContext(),
                R.xml.privacy_policy,
                replacements
            )
        }.onFailure { error ->
            Log.e("PoliciesFragment", "Error al cargar la política de privacidad", error)
        }.getOrNull()

        if (privacyDocument != null) {
            binding.textPoliciesSubtitle.renderStructuredContent(
                StructuredTextFormatter.buildParagraphHtml(privacyDocument.intro)
            )

            val container = binding.containerPoliciesSections
            container.removeAllViews()
            privacyDocument.sections.forEach { section ->
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
                    StructuredTextFormatter.buildSectionBodyHtml(section)
                )
                container.addView(sectionView)
            }

            val footerContent = StructuredTextFormatter.buildBlocksHtml(privacyDocument.footer)
            val hasFooter = footerContent.isNotEmpty()
            binding.policiesFooterDivider.isVisible = hasFooter
            binding.textPoliciesFooter.isVisible = hasFooter
            binding.textPoliciesFooter.renderStructuredContent(if (hasFooter) footerContent else null)
        } else {
            val fallback = getString(R.string.structured_text_parse_error)
            binding.textPoliciesSubtitle.renderStructuredContent(
                StructuredTextFormatter.buildParagraphHtml(fallback)
            )
            val container = binding.containerPoliciesSections
            container.removeAllViews()
            val fallbackView = layoutInflater.inflate(R.layout.item_structured_section, container, false)
            fallbackView.findViewById<TextView>(R.id.textSectionTitle).isVisible = false
            fallbackView.findViewById<TextView>(R.id.textSectionBody).renderStructuredContent(
                StructuredTextFormatter.buildParagraphHtml(fallback)
            )
            container.addView(fallbackView)
            binding.policiesFooterDivider.isVisible = false
            binding.textPoliciesFooter.isVisible = false
        }

        binding.buttonViewTerms.setOnClickListener {
            val consentView = layoutInflater.inflate(R.layout.dialog_terms, null)
            val termsContent: CharSequence = runCatching {
                val termsDocument = StructuredTextParser.parse(
                    requireContext(),
                    R.xml.terms_of_use,
                    replacements
                )
                StructuredTextFormatter.buildDocumentHtml(
                    termsDocument
                )
            }.getOrElse { error ->
                Log.e("PoliciesFragment", "Error al cargar los términos desde políticas", error)
                StructuredTextFormatter.buildParagraphHtml(
                    getString(R.string.structured_text_parse_error)
                ) ?: getString(R.string.structured_text_parse_error)
            }
            consentView.findViewById<TextView>(R.id.textTermsContent)
                .renderStructuredContent(termsContent)

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.terms_title)
                .setView(consentView)
                .setPositiveButton(R.string.dialog_close, null)
                .show()
        }

        binding.buttonContactSupport.setOnClickListener {
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
