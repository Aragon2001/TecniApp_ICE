package com.Arasoftsolutions.tecniapp_ice.ui.legal

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentLegalCenterBinding

class LegalCenterFragment : Fragment(R.layout.fragment_legal_center) {

    private var _binding: FragmentLegalCenterBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLegalCenterBinding.bind(view)

        val adapter = LegalAdapter(
            entries = LegalUiModelFactory.legalEntries(),
            onClick = { docType ->
                findNavController().navigate(
                    R.id.nav_legal_detail,
                    Bundle().apply { putString(LegalDocType.ARG_KEY, docType.name) }
                )
            }
        )

        binding.legalCenterRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.legalCenterRecycler.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
