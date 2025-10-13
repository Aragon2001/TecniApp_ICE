/**
 * TecniApp ICE © 2025 Arasoft Solutions
 * Todos los derechos reservados.
 * Desarrollado para el Instituto Costarricense de Electricidad (ICE).
 */
package com.Arasoftsolutions.tecniapp_ice.ui.policies

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
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

        binding.textPrivacy.apply {
            movementMethod = LinkMovementMethod.getInstance()
        }

        binding.buttonViewTerms.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.terms_title)
                .setMessage(R.string.terms_and_conditions)
                .setPositiveButton(R.string.terms_accept, null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
