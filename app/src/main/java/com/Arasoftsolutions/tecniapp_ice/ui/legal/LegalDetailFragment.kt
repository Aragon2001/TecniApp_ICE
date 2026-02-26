package com.Arasoftsolutions.tecniapp_ice.ui.legal

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentLegalDetailBinding
import java.util.Calendar

class LegalDetailFragment : Fragment(R.layout.fragment_legal_detail) {

    private var _binding: FragmentLegalDetailBinding? = null
    private val binding get() = _binding!!
    private val sectionsAdapter = LegalSectionsAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLegalDetailBinding.bind(view)

        val docType = LegalDocType.fromRaw(arguments?.getString(LegalDocType.ARG_KEY))
        binding.legalDetailToolbar.title = getString(docType.titleRes)
        binding.legalDetailToolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.legalDetailRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.legalDetailRecycler.adapter = sectionsAdapter

        binding.buttonContactSupport.isVisible = docType.supportsContact
        binding.buttonContactSupport.setOnClickListener { openSupportEmail() }

        renderDocument(docType)
    }

    private fun renderDocument(docType: LegalDocType) {
        val replacements = mapOf(
            "year" to Calendar.getInstance().get(Calendar.YEAR).toString(),
            "supportEmail" to getString(R.string.privacy_policy_contact_email)
        )

        val sections = runCatching {
            val document = StructuredTextParser.parse(
                context = requireContext(),
                xmlRes = docType.documentRes,
                replacements = replacements
            )
            buildSectionModels(document)
        }.onFailure { error ->
            Log.e("LegalDetailFragment", "No se pudo cargar el documento ${docType.name}", error)
        }.getOrElse {
            listOf(
                LegalSectionUiModel(
                    title = "",
                    body = StructuredTextFormatter.buildParagraphHtml(
                        getString(R.string.structured_text_parse_error)
                    ) ?: getString(R.string.structured_text_parse_error)
                )
            )
        }

        sectionsAdapter.submitList(sections)
    }

    private fun buildSectionModels(document: StructuredDocument): List<LegalSectionUiModel> {
        val values = mutableListOf<LegalSectionUiModel>()

        document.intro?.takeIf { it.isNotBlank() }?.let { intro ->
            values += LegalSectionUiModel(
                title = getString(R.string.legal_intro_title),
                body = StructuredTextFormatter.buildParagraphHtml(intro) ?: intro
            )
        }

        document.sections.forEach { section ->
            values += LegalSectionUiModel(
                title = section.title,
                body = StructuredTextFormatter.buildSectionBodyHtml(section)
            )
        }

        val footerContent = StructuredTextFormatter.buildBlocksHtml(document.footer)
        if (footerContent.isNotEmpty()) {
            values += LegalSectionUiModel(
                title = getString(R.string.legal_footer_title),
                body = footerContent
            )
        }

        return values.ifEmpty {
            listOf(
                LegalSectionUiModel(
                    title = "",
                    body = getString(R.string.structured_text_parse_error)
                )
            )
        }
    }

    private fun openSupportEmail() {
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
