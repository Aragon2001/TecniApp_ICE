/**
 * TecniApp ICE © 2025 Arasoft Solutions
 * Todos los derechos reservados.
 * Desarrollado para el Instituto Costarricense de Electricidad (ICE).
 */
package com.Arasoftsolutions.tecniapp_ice.ui.policies

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentPoliciesBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PoliciesFragment : Fragment(R.layout.fragment_policies) {

    private var _binding: FragmentPoliciesBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPoliciesBinding.bind(view)

        binding.textPoliciesSubtitle.text = getString(R.string.privacy_policy_intro)

        binding.textPoliciesBody.apply {
            text = HtmlCompat.fromHtml(
                getString(R.string.privacy_policy_body_html),
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )
            movementMethod = LinkMovementMethod.getInstance()
        }

        binding.textPoliciesFooter.text = HtmlCompat.fromHtml(
            getString(R.string.privacy_policy_footer_html),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
        binding.textPoliciesFooter.movementMethod = LinkMovementMethod.getInstance()

        binding.buttonViewTerms.setOnClickListener {
            val consentView = layoutInflater.inflate(R.layout.dialog_terms, null)
            consentView.findViewById<TextView>(R.id.textTermsContent).apply {
                text = HtmlCompat.fromHtml(
                    getString(R.string.terms_body_html),
                    HtmlCompat.FROM_HTML_MODE_LEGACY
                )
                movementMethod = LinkMovementMethod.getInstance()
            }

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
