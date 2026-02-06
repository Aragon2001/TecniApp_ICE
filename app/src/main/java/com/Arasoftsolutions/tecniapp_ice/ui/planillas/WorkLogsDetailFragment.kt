package com.Arasoftsolutions.tecniapp_ice.ui.planillas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentWorklogsDetailBinding

class WorkLogsDetailFragment : Fragment() {
    private var _binding: FragmentWorklogsDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorklogsDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // TODO: cargar worklogs del groupId y permitir edición
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
