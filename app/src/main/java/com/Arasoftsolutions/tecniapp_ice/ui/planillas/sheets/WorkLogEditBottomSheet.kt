package com.Arasoftsolutions.tecniapp_ice.ui.planillas.sheets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.Arasoftsolutions.tecniapp_ice.databinding.SheetWorklogEditBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class WorkLogEditBottomSheet : BottomSheetDialogFragment() {
    private var _binding: SheetWorklogEditBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetWorklogEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.saveButton.setOnClickListener {
            // TODO: validar y guardar trazabilidad updatedAt/updatedBy
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
