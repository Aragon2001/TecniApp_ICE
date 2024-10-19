package com.Arasoftsolutions.tecniapp_ice.ui.reportes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentReportesBinding

class ReportesFragment : Fragment() {

    private var _binding: FragmentReportesBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val reportesViewModel =
            ViewModelProvider(this).get(ReportesViewModel::class.java)

        _binding = FragmentReportesBinding.inflate(inflater, container, false)
        val root: View = binding.root


        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}