package com.Arasoftsolutions.tecniapp_ice.ui.planillas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentPlanillaDetailBinding
import com.Arasoftsolutions.tecniapp_ice.ui.planillas.model.SyncStatusUi

class PlanillaDetailFragment : Fragment() {
    private var _binding: FragmentPlanillaDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlanillaDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.retrySyncButton.setOnClickListener {
            // TODO: reintentar sync
        }
        binding.shareButton.setOnClickListener {
            // TODO: compartir PDF
        }
        binding.viewPdfButton.setOnClickListener {
            // TODO: abrir visor PDF
        }
    }

    fun renderSyncStatus(status: SyncStatusUi) {
        binding.syncStatusChip.text = status.name
    }

    fun setPdfPermission(canView: Boolean, pdfAvailable: Boolean) {
        binding.viewPdfButton.isVisible = canView
        binding.lockedState.isVisible = !canView
        binding.viewPdfButton.isEnabled = pdfAvailable
        binding.pdfUnavailableMessage.isVisible = canView && !pdfAvailable
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
