package com.Arasoftsolutions.tecniapp_ice.ui.programacion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.Arasoftsolutions.tecniapp_ice.Database.entities.ProgramacionEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase
import com.Arasoftsolutions.tecniapp_ice.databinding.BottomsheetProgramacionDetalleBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
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
            val repo = ProgramacionRepository(db)
            val item = db.programacionDao().getById(programacionId) ?: return@launch
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            val currentUser = db.usuarioDao().getByUid(currentUid)
            val rawRol = currentUser?.rol.orEmpty()
            val isSupervisor = rawRol.contains("super", ignoreCase = true) || rawRol.contains("admin", ignoreCase = true)
            val isTecnicoPrincipal = currentUid == item.tecnicoPrincipalUid || currentUid == item.tecnicoId

            renderHeader(item)
            configurarBotones(item, isSupervisor, isTecnicoPrincipal, repo)
        }
    }

    private fun renderHeader(item: ProgramacionEntity) {
        val titulo = item.actividad.ifBlank { item.descripcion.orEmpty() }
        binding.tvTitulo.text = titulo.ifBlank { "Sin título" }

        val locDisplay = if (item.localizacionNormalizada.isNotBlank())
            LocalizacionUtils.formatearParaMostrar(item.localizacionNormalizada)
        else item.localizacion
        binding.tvDetalle.text = buildString {
            append("Localización: $locDisplay")
            if (!item.descripcion.isNullOrBlank()) append("\n${item.descripcion}")
            if (!item.descripcionAtencion.isNullOrBlank()) append("\nAtención: ${item.descripcionAtencion}")
        }

        binding.tvVehiculo.text = "Camión: ${item.placa.ifBlank { item.vehiculoId }}"

        val estadoLabel = when (item.estado) {
            ProgramacionRepository.ESTADO_PENDIENTE -> "Pendiente"
            ProgramacionRepository.ESTADO_EN_ATENCION -> "En atención"
            ProgramacionRepository.ESTADO_ATENDIDA -> "Atendida"
            ProgramacionRepository.ESTADO_REABIERTA -> "Reabierta"
            ProgramacionRepository.ESTADO_CANCELADA -> "Cancelada"
            ProgramacionRepository.ESTADO_ELIMINADA -> "Eliminada"
            else -> item.estado
        }
        binding.chipEstado.text = estadoLabel
    }

    private fun configurarBotones(
        item: ProgramacionEntity,
        isSupervisor: Boolean,
        isTecnicoPrincipal: Boolean,
        repo: ProgramacionRepository
    ) {
        val esAtendible = item.estado in listOf(
            ProgramacionRepository.ESTADO_PENDIENTE,
            ProgramacionRepository.ESTADO_EN_ATENCION,
            ProgramacionRepository.ESTADO_REABIERTA
        )

        // ─── Botones técnico ─────────────────────────────────────────────────
        binding.layoutBotonesTecnico.isVisible = !isSupervisor

        binding.btnIniciar.isEnabled = !isSupervisor && esAtendible &&
            item.estado in listOf(ProgramacionRepository.ESTADO_PENDIENTE, ProgramacionRepository.ESTADO_REABIERTA)

        binding.btnFinalizar.isEnabled = !isSupervisor && isTecnicoPrincipal &&
            item.estado in listOf(ProgramacionRepository.ESTADO_EN_ATENCION, ProgramacionRepository.ESTADO_REABIERTA)

        binding.btnReabrir.isEnabled = !isSupervisor && isTecnicoPrincipal &&
            item.estado == ProgramacionRepository.ESTADO_ATENDIDA

        binding.tilMotivoReapertura.isVisible = binding.btnReabrir.isEnabled

        // Mostrar sección de atención solo si el técnico puede interactuar
        val puedeAtender = !isSupervisor && esAtendible
        binding.checkGastoMateriales.isVisible = puedeAtender
        binding.layoutMateriales.isVisible = false

        binding.checkGastoMateriales.setOnCheckedChangeListener { _, checked ->
            binding.layoutMateriales.isVisible = checked
        }

        // ─── Botones supervisor ───────────────────────────────────────────────
        binding.layoutBotonesSupervisor.isVisible = isSupervisor &&
            item.estado !in listOf(ProgramacionRepository.ESTADO_CANCELADA, ProgramacionRepository.ESTADO_ELIMINADA)
        binding.tilMotivoCancelacion.isVisible = isSupervisor
        binding.tilMotivoEliminacion.isVisible = isSupervisor

        // ─── Acciones técnico ─────────────────────────────────────────────────
        binding.btnIniciar.setOnClickListener {
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            CoroutineScope(Dispatchers.Main).launch {
                val db = AppDatabase.getInstance(requireContext())
                val user = db.usuarioDao().getByUid(currentUid)
                val nombre = listOfNotNull(user?.nombre, user?.apellidos).joinToString(" ").ifBlank { currentUid }
                repo.iniciarAtencion(item.programacionId, currentUid, nombre)
                    .onSuccess { dismissAllowingStateLoss() }
                    .onFailure { mostrarError(it.message) }
            }
        }

        binding.btnFinalizar.setOnClickListener {
            val descripcion = binding.etDescripcionAtencion.text?.toString().orEmpty()
            if (descripcion.isBlank()) {
                mostrarError("La descripción de atención es obligatoria.")
                return@setOnClickListener
            }
            val gastoMateriales = binding.checkGastoMateriales.isChecked
            val materialesRaw = binding.etMateriales.text?.toString().orEmpty()
            if (gastoMateriales && materialesRaw.isBlank()) {
                mostrarError("Debe agregar al menos un material.")
                return@setOnClickListener
            }
            val materiales = parsearMateriales(materialesRaw)
            if (gastoMateriales && materiales.isEmpty()) {
                mostrarError("No se pudieron leer los materiales. Verifique el formato.")
                return@setOnClickListener
            }

            val fotos = binding.etFotosAtencion.text?.toString().orEmpty()
                .split(',').map { it.trim() }.filter { it.isNotBlank() }
            val tecnicosRaw = binding.etTecnicosParticipantes.text?.toString().orEmpty()
            val tecnicos = tecnicosRaw.split(',').map { it.trim() }.filter { it.isNotBlank() }
                .map { TecnicoParticipante(uid = it, nombre = it) }

            val currentUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            CoroutineScope(Dispatchers.Main).launch {
                repo.finalizarAtencion(item.programacionId, currentUid, descripcion, fotos, tecnicos, gastoMateriales, materiales)
                    .onSuccess { dismissAllowingStateLoss() }
                    .onFailure { mostrarError(it.message) }
            }
        }

        binding.btnReabrir.setOnClickListener {
            val motivo = binding.etMotivoReapertura.text?.toString()
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            CoroutineScope(Dispatchers.Main).launch {
                repo.reabrirOrden(item.programacionId, currentUid, motivo)
                    .onSuccess { dismissAllowingStateLoss() }
                    .onFailure { mostrarError(it.message) }
            }
        }

        // ─── Acciones supervisor ──────────────────────────────────────────────
        binding.btnCancelar.setOnClickListener {
            val motivo = binding.etMotivoCancelacion.text?.toString()
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            CoroutineScope(Dispatchers.Main).launch {
                repo.cancelarOrden(item.programacionId, currentUid, motivo)
                    .onSuccess { dismissAllowingStateLoss() }
                    .onFailure { mostrarError(it.message) }
            }
        }

        binding.btnEliminar.setOnClickListener {
            val motivo = binding.etMotivoEliminacion.text?.toString()
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            CoroutineScope(Dispatchers.Main).launch {
                repo.eliminarOrden(item.programacionId, currentUid, motivo)
                    .onSuccess { dismissAllowingStateLoss() }
                    .onFailure { mostrarError(it.message) }
            }
        }
    }

    private fun parsearMateriales(raw: String): List<MaterialGastado> {
        if (raw.isBlank()) return emptyList()
        return raw.lines()
            .mapNotNull { linea ->
                val partes = linea.split(',').map { it.trim() }
                if (partes.size >= 4) {
                    val cant = partes[2].toIntOrNull() ?: return@mapNotNull null
                    MaterialGastado(partes[0], partes[1], cant, partes[3])
                } else null
            }
    }

    private fun mostrarError(msg: String?) {
        val root = view ?: return
        Snackbar.make(root, msg ?: "Error", Snackbar.LENGTH_SHORT).show()
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
