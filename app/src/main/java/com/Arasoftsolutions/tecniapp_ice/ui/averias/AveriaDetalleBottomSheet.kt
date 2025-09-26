package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import androidx.fragment.app.viewModels
import com.Arasoftsolutions.tecniapp_ice.databinding.BottomsheetAveriaDetalleBinding

class AveriaDetalleBottomSheet : BottomSheetDialogFragment() {

    private var _b: BottomsheetAveriaDetalleBinding? = null
    private val b get() = _b!!
    private val vm: AveriasViewModel by viewModels({ requireParentFragment() })

    private lateinit var item: AveriaUI

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = BottomsheetAveriaDetalleBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.tvCaso.text = "Caso ${item.id}"
        b.tvNise.text = "NISE ${item.nise}"
        b.tvAgencia.text = item.agencia
        b.tvRegion.text = item.region
        b.tvEstado.text = item.estado
        b.tvCausa.text = item.causa
        b.tvObs.text = item.observaciones

        b.btnAsignar.setOnClickListener { vm.onAsignar(item); dismiss() }
        b.btnAtender.setOnClickListener { vm.onAtender(item); dismiss() }
        b.btnCerrar.setOnClickListener { vm.onCerrar(item); dismiss() }
        b.btnExportar.setOnClickListener {
    if (item.estado == "Resuelta") {
        PdfGenerator.exportAveria(requireContext(), item)
    }
}

        b.btnVerMapa.setOnClickListener {
            if (item.lat == 0.0 && item.lng == 0.0) return@setOnClickListener
            val uri = Uri.parse("geo:${item.lat},${item.lng}?q=${item.lat},${item.lng}")
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(item: AveriaUI): AveriaDetalleBottomSheet {
            val bs = AveriaDetalleBottomSheet()
            bs.item = item
            return bs
            }
    }
}
