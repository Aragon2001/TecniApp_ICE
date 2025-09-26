package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.BottomsheetAveriaDetalleBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
        val estado = Estado.fromLabel(item.estado)

        b.tvCaso.text = getString(R.string.averia_caso_format, item.id)
        b.tvNise.text = getString(R.string.averia_nise_format, item.nise)
        b.tvAgencia.text = item.agencia
        b.tvRegion.text = item.region
        b.tvEstado.text = item.estado
        b.tvAsignado.text = getString(R.string.averia_asignado_a, item.tecnico.ifBlank { getString(R.string.averia_sin_asignar) })
        b.tvAtendido.text = getString(R.string.averia_atendido_por_format, item.atendidoPor.ifBlank { "—" })
        b.tvVehiculo.text = getString(R.string.averia_vehiculo_format, item.vehiculo ?: "—")
        if (item.materiales.isNotBlank()) {
            b.tvMateriales.visibility = View.VISIBLE
            b.tvMateriales.text = getString(R.string.averia_materiales_label, item.materiales)
        } else {
            b.tvMateriales.visibility = View.GONE
        }

        val nombreActual = vm.nombreTecnicoActual()
        val vehiculoActual = item.vehiculo ?: vm.vehiculoPreferido()

        b.etCausa.setText(item.causa.takeIf { it.isNotBlank() } ?: "")
        b.etObs.setText(item.observaciones.takeIf { it.isNotBlank() } ?: "")
        b.etAtendido.setText(item.atendidoPor.takeIf { it.isNotBlank() } ?: nombreActual.orEmpty())
        b.actvVehiculo.setText(vehiculoActual.orEmpty(), false)
        b.etMateriales.setText(item.materiales.takeIf { it.isNotBlank() } ?: "")

        b.tilCausa.error = null
        b.etCausa.doAfterTextChanged { b.tilCausa.error = null }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.vehiculosDisponibles.collectLatest { vehiculos ->
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, vehiculos)
                b.actvVehiculo.setAdapter(adapter)
            }
        }

        b.btnAsignar.text = when (estado) {
            Estado.PENDIENTE -> getString(R.string.averia_asignar)
            Estado.ASIGNADA -> getString(R.string.averia_eliminar_asignacion)
            else -> getString(R.string.averia_asignar)
        }
        b.btnAsignar.isEnabled = estado == Estado.PENDIENTE || estado == Estado.ASIGNADA
        b.btnAsignar.setOnClickListener {
            vm.onToggleAsignacion(item)
            dismissAllowingStateLoss()
        }

        when (estado) {
            Estado.ASIGNADA -> {
                b.btnAtender.visibility = View.VISIBLE
                b.btnAtender.text = getString(R.string.averia_guardar_en_atencion)
                b.btnAtender.setOnClickListener {
                    val data = collectFormData()
                    if (data.causa.isBlank()) {
                        b.tilCausa.error = getString(R.string.averia_error_causa_requerida)
                        return@setOnClickListener
                    }
                    vm.onAtender(item, data)
                    dismissAllowingStateLoss()
                }
            }
            Estado.EN_ATENCION -> {
                b.btnAtender.visibility = View.VISIBLE
                b.btnAtender.text = getString(R.string.averia_cancelar_atencion)
                b.btnAtender.setOnClickListener {
                    vm.onCancelarAtencion(item)
                    dismissAllowingStateLoss()
                }
            }
            else -> {
                b.btnAtender.visibility = if (estado == Estado.RESUELTA || estado == Estado.PENDIENTE) View.GONE else View.VISIBLE
                b.btnAtender.isEnabled = false
            }
        }

        if (estado == Estado.EN_ATENCION) {
            b.btnResolver.visibility = View.VISIBLE
            b.btnResolver.setOnClickListener {
                val data = collectFormData()
                if (data.causa.isBlank()) {
                    b.tilCausa.error = getString(R.string.averia_error_causa_requerida)
                    return@setOnClickListener
                }
                vm.onResolver(item, data)
                dismissAllowingStateLoss()
            }
        } else {
            b.btnResolver.visibility = View.GONE
        }

        if (estado == Estado.RESUELTA) {
            b.btnExportar.visibility = View.VISIBLE
            b.btnExportar.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    PdfGenerator.generarPDF(requireContext(), item)

                }
            }
        } else {
            b.btnExportar.visibility = View.GONE
        }

        b.btnVerMapa.setOnClickListener {
            if (item.lat == 0.0 && item.lng == 0.0) return@setOnClickListener
            val uri = Uri.parse("geo:${item.lat},${item.lng}?q=${item.lat},${item.lng}")
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    private fun collectFormData(): AveriaActionData {
        val causa = b.etCausa.text?.toString()?.trim().orEmpty()
        val obs = b.etObs.text?.toString()?.trim().takeIf { !it.isNullOrBlank() }
        val vehiculo = b.actvVehiculo.text?.toString()?.trim().takeIf { !it.isNullOrBlank() }
        val materiales = b.etMateriales.text?.toString()?.trim().takeIf { !it.isNullOrBlank() }
        val atendido = b.etAtendido.text?.toString()?.trim().takeIf { !it.isNullOrBlank() }
        val uid = vm.usuarioActual.value?.uid
        return AveriaActionData(
            causa = causa,
            observaciones = obs,
            vehiculo = vehiculo,
            materiales = materiales,
            atendidoPorUid = uid,
            atendidoPorNombre = atendido
        )
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
