package com.Arasoftsolutions.tecniapp_ice.ui.vehiculo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentMiVehiculoBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MiVehiculoFragment : Fragment() {

    private var _binding: FragmentMiVehiculoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MiVehiculoViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMiVehiculoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvRegistros.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRegistros.adapter = EtmRegistroAdapter(emptyList())

        setupListeners()
        observarEstado()
    }

    private fun setupListeners() {
        binding.btnRegistrarInicial.setOnClickListener { registrarInicialDesdeCampo() }
        binding.btnRegistrarFinal.setOnClickListener { mostrarDialogoRegistrarFinal() }
        binding.btnNoVehiculo.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(getString(R.string.mi_vehiculo_sin_vehiculo_info))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun registrarInicialDesdeCampo() {
        val input = binding.etRegistroInicial.text?.toString()?.trim()
        val valor = input?.replace(",", ".")?.toDoubleOrNull()
        if (valor == null || valor < 0) {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(getString(R.string.mi_vehiculo_valor_invalido))
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val horasLaboradas = binding.etRegistroHoras.text?.toString()?.trim()
            ?.toIntOrNull()
            ?.takeIf { it in 1..10 }
        if (binding.etRegistroHoras.text?.isNotBlank() == true && horasLaboradas == null) {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(getString(R.string.mi_vehiculo_horas_invalidas))
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        viewModel.registrarInicial(
            valor = valor,
            circuito = binding.etRegistroCircuito.text?.toString()?.trim().orEmpty().ifBlank { null },
            actividad = binding.etRegistroActividad.text?.toString()?.trim().orEmpty().ifBlank { null },
            cuenta = binding.etRegistroCuenta.text?.toString()?.trim().orEmpty().ifBlank { null },
            numeroCaso = binding.etRegistroCaso.text?.toString()?.trim().orEmpty().ifBlank { null },
            lugar = binding.etRegistroLugar.text?.toString()?.trim().orEmpty().ifBlank { null },
            horasLaboradas = horasLaboradas
        )
    }

    private fun mostrarDialogoRegistrarFinal() {
        val state = viewModel.uiState.value
        val unidad = state.tipoVehiculo.unidadTexto
        val input = TextInputEditText(requireContext()).apply {
            hint = "Valor final ($unidad)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.mi_vehiculo_registrar_final))
            .setView(input)
            .setPositiveButton(getString(android.R.string.ok)) { _, _ ->
                val v = input.text?.toString()?.toDoubleOrNull()
                if (v != null && v >= 0) viewModel.registrarFinal(v)
                else MaterialAlertDialogBuilder(requireContext())
                    .setMessage(getString(R.string.mi_vehiculo_valor_invalido))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun observarEstado() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.eventos.collect { mensaje ->
                        MaterialAlertDialogBuilder(requireContext())
                            .setMessage(mensaje)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                }
                launch {
                    viewModel.uiState.collect { state ->
                    val vehiculo = state.vehiculo
                    binding.tvSinVehiculo.isVisible = vehiculo == null
                    binding.cardRegistroPendiente.isVisible = vehiculo != null && state.registroHoy == null
                    binding.cardRegistroHoy.isVisible = vehiculo != null
                    binding.btnRegistrarInicial.isVisible = vehiculo != null && state.registroHoy == null
                    binding.btnRegistrarFinal.isVisible = false

                    if (vehiculo != null) {
                        val tipoLabel = when (state.tipoVehiculo) {
                            TipoVehiculo.CAMION_GRUA -> getString(R.string.mi_vehiculo_tipo_grua)
                            TipoVehiculo.MAQUINARIA_PESADA -> getString(R.string.mi_vehiculo_tipo_maquinaria)
                            TipoVehiculo.LIVIANO -> getString(R.string.mi_vehiculo_tipo_liviano)
                        }
                        binding.tvVehiculoPlaca.text = getString(
                            R.string.mi_vehiculo_placa_format,
                            vehiculo.placa
                        )
                        binding.tvVehiculoTipo.text = tipoLabel
                        binding.tvVehiculoAgencia.text = vehiculo.agencia
                        binding.ivVehiculoTipo.setImageResource(
                            when (state.tipoVehiculo) {
                                TipoVehiculo.CAMION_GRUA -> R.drawable.grua
                                TipoVehiculo.MAQUINARIA_PESADA -> R.drawable.maquinaria
                                TipoVehiculo.LIVIANO -> R.drawable.liviano
                            }
                        )
                        binding.chipEstadoRegistro.text = if (state.registroHoy != null) {
                            getString(R.string.mi_vehiculo_estado_al_dia)
                        } else {
                            getString(R.string.mi_vehiculo_estado_pendiente)
                        }

                        binding.tvValorActualLabel.text = if (state.tipoVehiculo.usaKilometraje) {
                            getString(R.string.mi_vehiculo_kilometraje_actual)
                        } else {
                            getString(R.string.mi_vehiculo_orimetro_actual)
                        }
                        binding.tilRegistroInicial.hint = getString(
                            R.string.mi_vehiculo_registro_inicial_hint_format,
                            state.tipoVehiculo.unidadTexto
                        )

                        val reg = state.registroHoy
                        val ultimo = state.ultimoRegistro
                        val valorActual = reg?.valorFinal ?: reg?.valorInicial ?: ultimo?.valorFinal ?: ultimo?.valorInicial
                        binding.tvValorActual.text = valorActual?.let {
                            getString(
                                R.string.mi_vehiculo_valor_actual_format,
                                it,
                                state.tipoVehiculo.unidadTexto
                            )
                        } ?: getString(R.string.mi_vehiculo_valor_actual_placeholder)

                        if (reg != null) {
                            val u = state.tipoVehiculo.unidadTexto
                            binding.tvEstadoHoy.text = getString(
                                R.string.mi_vehiculo_registro_hoy_format,
                                reg.valorInicial,
                                u,
                                reg.valorFinal?.let { "$it $u" } ?: getString(R.string.mi_vehiculo_valor_pendiente)
                            )
                            binding.btnRegistrarInicial.isVisible = false
                            binding.btnRegistrarFinal.isVisible = reg.valorFinal == null
                            binding.etRegistroInicial.setText("")
                        } else {
                            binding.tvEstadoHoy.text = getString(R.string.mi_vehiculo_registro_pendiente)
                            binding.btnRegistrarInicial.isVisible = true
                            if (binding.etRegistroInicial.text.isNullOrBlank()) {
                                val sugerido = ultimo?.valorFinal ?: ultimo?.valorInicial
                                binding.etRegistroInicial.setText(
                                    sugerido?.toString().orEmpty()
                                )
                            }
                        }
                    }

                    (binding.rvRegistros.adapter as? EtmRegistroAdapter)?.updateList(
                        state.registrosRecientes,
                        state.tipoVehiculo.unidadTexto,
                        state.nombreUsuario
                    )
                }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class EtmRegistroAdapter(
    private var items: List<RegistroDiarioVehiculo>,
    private var unidad: String = "km",
    private var nombreUsuario: String = ""
) : androidx.recyclerview.widget.RecyclerView.Adapter<EtmRegistroAdapter.VH>() {

    fun updateList(list: List<RegistroDiarioVehiculo>, u: String, nombre: String) {
        items = list
        unidad = u
        nombreUsuario = nombre
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_etm_registro, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], unidad, nombreUsuario)
    }

    override fun getItemCount() = items.size

    class VH(view: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        private val tvFecha = view.findViewById<android.widget.TextView>(R.id.tvFecha)
        private val tvMeta = view.findViewById<android.widget.TextView>(R.id.tvMeta)
        private val tvValores = view.findViewById<android.widget.TextView>(R.id.tvValores)
        private val tvDetalle = view.findViewById<android.widget.TextView>(R.id.tvDetalle)
        private val formatoHora = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun bind(item: RegistroDiarioVehiculo, unidad: String, nombreUsuario: String) {
            tvFecha.text = item.fecha
            val nombre = item.registradoPor?.takeIf { it.isNotBlank() }
                ?: nombreUsuario.takeIf { it.isNotBlank() }
            val hora = item.registradoEn.takeIf { it > 0 }
                ?.let { formatoHora.format(Date(it)) }
            val meta = listOfNotNull(
                nombre?.let { "Registrado por: $it" },
                hora?.let { "Hora: $it" }
            ).joinToString(" • ")
            tvMeta.text = meta
            tvMeta.isVisible = meta.isNotBlank()
            val fin = item.valorFinal?.let { " • Final: $it $unidad" } ?: ""
            val diff = item.diferencia?.let { " • Diferencia: $it $unidad" } ?: ""
            tvValores.text = "Inicial: ${item.valorInicial} $unidad$fin$diff"
            val detalle = listOfNotNull(
                item.circuito?.let { "Circuito: $it" },
                item.actividad?.let { "Actividad: $it" },
                item.cuenta?.let { "Cuenta: $it" },
                item.numeroCaso?.let { "Caso: $it" },
                item.lugar?.let { "Lugar: $it" },
                item.horasLaboradas?.let { "Horas: $it" }
            ).joinToString(" • ")
            tvDetalle.text = detalle
            tvDetalle.isVisible = detalle.isNotBlank()
        }
    }
}
