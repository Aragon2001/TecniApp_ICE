package com.Arasoftsolutions.tecniapp_ice.ui.programacion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase
import com.Arasoftsolutions.tecniapp_ice.databinding.BottomsheetProgramacionDetalleBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProgramacionDetalleBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomsheetProgramacionDetalleBinding? = null
    private val binding get() = requireNotNull(_binding)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomsheetProgramacionDetalleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val programacionId = arguments?.getString(ARG_ID).orEmpty()
        if (programacionId.isBlank()) return

        CoroutineScope(Dispatchers.Main).launch {
            val db = AppDatabase.getInstance(requireContext())
            val item = db.programacionDao().getById(programacionId) ?: return@launch
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            val isTecnicoAsignado = currentUid == item.tecnicoId

            binding.tvTitulo.text = item.actividad
            binding.tvDetalle.text = "${item.localizacion} · ${item.cuenta}\n${item.descripcion.orEmpty()}"
            binding.chipEstado.text = item.estado

            binding.btnIniciar.isEnabled = isTecnicoAsignado && item.estado == ProgramacionRepository.ESTADO_PENDIENTE
            binding.btnFinalizar.isEnabled = isTecnicoAsignado && item.estado == ProgramacionRepository.ESTADO_EN_PROCESO

            binding.btnIniciar.setOnClickListener {
                launch {
                    ProgramacionRepository(db).actualizarEstado(
                        item.programacionId,
                        item.subregion,
                        item.vehiculoId,
                        ProgramacionRepository.ESTADO_EN_PROCESO,
                        binding.etObservaciones.text?.toString(),
                        emptyList()
                    )
                    dismissAllowingStateLoss()
                }
            }

            binding.btnFinalizar.setOnClickListener {
                launch {
                    val fotos = binding.etFotosCierre.text?.toString().orEmpty()
                        .split(',')
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                    ProgramacionRepository(db).actualizarEstado(
                        item.programacionId,
                        item.subregion,
                        item.vehiculoId,
                        ProgramacionRepository.ESTADO_EJECUTADA,
                        binding.etObservaciones.text?.toString(),
                        fotos
                    )
                    dismissAllowingStateLoss()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_ID = "arg_programacion_id"

        fun newInstance(programacionId: String): ProgramacionDetalleBottomSheet =
            ProgramacionDetalleBottomSheet().apply {
                arguments = Bundle().apply { putString(ARG_ID, programacionId) }
            }
    }
}
