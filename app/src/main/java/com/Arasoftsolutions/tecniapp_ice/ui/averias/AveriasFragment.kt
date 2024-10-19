package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentAveriasBinding

class AveriasFragment : Fragment() {

    private var _binding: FragmentAveriasBinding? = null
    private val binding get() = _binding!!
    private lateinit var averiasAdapter: AveriasAdapter
    private lateinit var averiasViewModel: AveriasViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAveriasBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Inicializa el ViewModel
        averiasViewModel = ViewModelProvider(this).get(AveriasViewModel::class.java)

        // Configura el RecyclerView
        setupRecyclerView()

        // Observa las averías desde el ViewModel
        averiasViewModel.averias.observe(viewLifecycleOwner, Observer { averias ->
            // Actualiza el adaptador cuando las averías cambian
            averiasAdapter.updateAverias(averias)
        })

        return root
    }

    private fun setupRecyclerView() {
        averiasAdapter = AveriasAdapter(mutableListOf())
        binding.recyclerViewAverias.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewAverias.adapter = averiasAdapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
