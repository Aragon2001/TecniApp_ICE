package com.Arasoftsolutions.tecniapp_ice.ui.planillas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentActivityGroupsManagerBinding

class ActivityGroupsManagerFragment : Fragment() {
    private var _binding: FragmentActivityGroupsManagerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentActivityGroupsManagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // TODO: conectar ViewModel y cargar activity groups por fecha
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
