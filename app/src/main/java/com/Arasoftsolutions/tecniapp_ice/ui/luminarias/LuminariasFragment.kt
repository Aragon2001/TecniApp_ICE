package com.Arasoftsolutions.tecniapp_ice.ui.luminarias

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentLuminariasBinding

class LuminariasFragment : Fragment() {

    private var _binding: FragmentLuminariasBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val luminariasViewModel =
            ViewModelProvider(this).get(LuminariasViewModel::class.java)

        _binding = FragmentLuminariasBinding.inflate(inflater, container, false)
        val root: View = binding.root


        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}