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
import androidx.fragment.app.Fragment
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentPoliciesBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.Arasoftsolutions.tecniapp_ice.ui.legal.StructuredTextFormatter
import com.Arasoftsolutions.tecniapp_ice.ui.legal.StructuredTextParser
import com.Arasoftsolutions.tecniapp_ice.ui.legal.renderStructuredContent
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
            binding.textPoliciesSubtitle.text = privacyDocument.intro
            binding.textPoliciesBody.renderStructuredContent(
                StructuredTextFormatter.buildDocument(
                    requireContext(),
                    privacyDocument,
                    includeIntro = false,
                    includeFooter = false
                )
            )
            binding.textPoliciesFooter.renderStructuredContent(
                StructuredTextFormatter.buildBlocks(
                    requireContext(),
                    privacyDocument.footer
                )
            )
        } else {
            val fallback = getString(R.string.structured_text_parse_error)
            binding.textPoliciesSubtitle.text = fallback
            binding.textPoliciesBody.renderStructuredContent(fallback)
            binding.textPoliciesFooter.renderStructuredContent(null)
        }

        binding.buttonViewTerms.setOnClickListener {
            val consentView = layoutInflater.inflate(R.layout.dialog_terms, null)
            val termsContent = runCatching {
                val termsDocument = StructuredTextParser.parse(
                    requireContext(),
                    R.xml.terms_of_use,
                    replacements
                )
                StructuredTextFormatter.buildDocument(
                    requireContext(),
                    termsDocument
                )
            }.getOrElse { error ->
                Log.e("PoliciesFragment", "Error al cargar los términos desde políticas", error)
                getString(R.string.structured_text_parse_error)
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
