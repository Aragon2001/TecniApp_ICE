package com.Arasoftsolutions.tecniapp_ice.ui.admin

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.Database.entities.LocalizacionesEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.MedidorEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.PueblosEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.SubregionesEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculosEntity
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentAdminManagementBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class AdminManagementFragment : Fragment(R.layout.fragment_admin_management) {

    private var _binding: FragmentAdminManagementBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminManagementViewModel by viewModels()

    private lateinit var medidorAdapter: ArrayAdapter<String>
    private lateinit var vehiculoAdapter: ArrayAdapter<String>
    private lateinit var localizacionAdapter: ArrayAdapter<String>
    private lateinit var puebloAdapter: ArrayAdapter<String>
    private lateinit var subregionAdapter: ArrayAdapter<String>

    private var pueblosDisponibles: List<PueblosEntity> = emptyList()
    private var subregionesDisponibles: List<SubregionesEntity> = emptyList()
    private var medidoresDisponibles: List<MedidorEntity> = emptyList()
    private var vehiculosDisponibles: List<VehiculosEntity> = emptyList()
    private var localizacionesDisponibles: List<LocalizacionesEntity> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAdminManagementBinding.bind(view)

        setupAdapters()
        setupToggle()
        setupListeners()
        observeViewModel()
    }

    private fun setupAdapters() {
        medidorAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, mutableListOf())
        binding.actvAdminMedidorBuscar.setAdapter(medidorAdapter)

        vehiculoAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, mutableListOf())
        binding.actvAdminVehiculoBuscar.setAdapter(vehiculoAdapter)

        localizacionAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, mutableListOf())
        binding.actvAdminLocalizacionBuscar.setAdapter(localizacionAdapter)

        puebloAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, mutableListOf())
        binding.actvAdminMedidorPueblo.setAdapter(puebloAdapter)
        binding.actvAdminLocalizacionPueblo.setAdapter(puebloAdapter)

        subregionAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, mutableListOf())
        binding.actvAdminMedidorSubregion.setAdapter(subregionAdapter)
        binding.actvAdminVehiculoSubregion.setAdapter(subregionAdapter)
        binding.actvAdminLocalizacionSubregion.setAdapter(subregionAdapter)
    }

    private fun setupToggle() {
        binding.toggleAdminSections.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            binding.cardMedidores.isVisible = checkedId == R.id.btnAdminMedidores
            binding.cardVehiculos.isVisible = checkedId == R.id.btnAdminVehiculos
            binding.cardLocalizaciones.isVisible = checkedId == R.id.btnAdminLocalizaciones
        }
        binding.toggleAdminSections.check(R.id.btnAdminMedidores)
    }

    private fun setupListeners() {
        binding.actvAdminMedidorBuscar.setOnItemClickListener { parent, _, position, _ ->
            val numero = parent.getItemAtPosition(position) as? String ?: return@setOnItemClickListener
            viewModel.seleccionarMedidor(numero)
        }

        binding.btnAdminMedidorCargar.setOnClickListener {
            viewModel.seleccionarMedidor(binding.actvAdminMedidorBuscar.text?.toString().orEmpty())
        }

        binding.btnAdminMedidorLimpiar.setOnClickListener {
            limpiarFormularioMedidor()
            viewModel.limpiarMedidor()
        }

        binding.btnAdminMedidorGuardar.setOnClickListener { guardarMedidor() }
        binding.btnAdminMedidorEliminar.setOnClickListener { eliminarMedidor() }

        binding.actvAdminVehiculoBuscar.setOnItemClickListener { parent, _, position, _ ->
            val item = parent.getItemAtPosition(position) as? String ?: return@setOnItemClickListener
            viewModel.seleccionarVehiculoPorId(parseVehiculoId(item))
        }

        binding.btnAdminVehiculoCargar.setOnClickListener {
            viewModel.seleccionarVehiculoPorId(parseVehiculoId(binding.actvAdminVehiculoBuscar.text?.toString()))
        }

        binding.btnAdminVehiculoLimpiar.setOnClickListener {
            limpiarFormularioVehiculo()
            viewModel.limpiarVehiculo()
        }

        binding.btnAdminVehiculoGuardar.setOnClickListener { guardarVehiculo() }
        binding.btnAdminVehiculoEliminar.setOnClickListener { eliminarVehiculo() }

        binding.actvAdminLocalizacionBuscar.setOnItemClickListener { parent, _, position, _ ->
            val item = parent.getItemAtPosition(position) as? String ?: return@setOnItemClickListener
            viewModel.seleccionarLocalizacion(item.substringBefore(" - ").trim().toIntOrNull())
        }

        binding.btnAdminLocalizacionCargar.setOnClickListener {
            viewModel.seleccionarLocalizacion(binding.actvAdminLocalizacionBuscar.text?.toString()?.trim()?.toIntOrNull())
        }

        binding.btnAdminLocalizacionLimpiar.setOnClickListener {
            limpiarFormularioLocalizacion()
            viewModel.limpiarLocalizacion()
        }

        binding.btnAdminLocalizacionGuardar.setOnClickListener { guardarLocalizacion() }
        binding.btnAdminLocalizacionEliminar.setOnClickListener { eliminarLocalizacion() }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.medidores.collect { actualizarMedidores(it) } }
                launch { viewModel.vehiculos.collect { actualizarVehiculos(it) } }
                launch { viewModel.localizaciones.collect { actualizarLocalizaciones(it) } }
                launch { viewModel.pueblos.collect { actualizarPueblos(it) } }
                launch { viewModel.subregiones.collect { actualizarSubregiones(it) } }
                launch { viewModel.medidorSeleccionado.collect { mostrarMedidor(it) } }
                launch { viewModel.vehiculoSeleccionado.collect { mostrarVehiculo(it) } }
                launch { viewModel.localizacionSeleccionada.collect { mostrarLocalizacion(it) } }
                launch {
                    viewModel.eventos.collect { evento ->
                        val mensaje = when (evento) {
                            is AdminEvent.Error -> evento.message
                            is AdminEvent.Success -> evento.message
                        }
                        Snackbar.make(binding.root, mensaje, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun actualizarMedidores(lista: List<MedidorEntity>) {
        medidoresDisponibles = lista
        val datos = lista.map { it.medidorNumber }.sorted()
        medidorAdapter.clear()
        medidorAdapter.addAll(datos)
        medidorAdapter.notifyDataSetChanged()
    }

    private fun actualizarVehiculos(lista: List<VehiculosEntity>) {
        vehiculosDisponibles = lista
        val datos = lista.sortedBy { it.id }.map { "${it.id} - ${it.placa}" }
        vehiculoAdapter.clear()
        vehiculoAdapter.addAll(datos)
        vehiculoAdapter.notifyDataSetChanged()
    }

    private fun actualizarLocalizaciones(lista: List<LocalizacionesEntity>) {
        localizacionesDisponibles = lista
        val datos = lista.sortedBy { it.id }.map { "${it.id} - ${it.direccion}" }
        localizacionAdapter.clear()
        localizacionAdapter.addAll(datos)
        localizacionAdapter.notifyDataSetChanged()
    }

    private fun actualizarPueblos(lista: List<PueblosEntity>) {
        pueblosDisponibles = lista
        val datos = lista.sortedBy { it.id }.map { "${it.id} - ${it.nombre}" }
        puebloAdapter.clear()
        puebloAdapter.addAll(datos)
        puebloAdapter.notifyDataSetChanged()
    }

    private fun actualizarSubregiones(lista: List<SubregionesEntity>) {
        subregionesDisponibles = lista
        val datos = lista.sortedBy { it.id }.map { "${it.id} - ${it.nombre}" }
        subregionAdapter.clear()
        subregionAdapter.addAll(datos)
        subregionAdapter.notifyDataSetChanged()
    }

    private fun mostrarMedidor(entidad: MedidorEntity?) {
        limpiarErroresMedidor()
        if (entidad == null) {
            limpiarFormularioMedidor()
            return
        }

        binding.actvAdminMedidorBuscar.setText(entidad.medidorNumber, false)
        binding.inputAdminMedidorNumero.setText(entidad.medidorNumber)
        binding.inputAdminMedidorCliente.setText(entidad.cliente.orEmpty())
        binding.inputAdminMedidorLocalizacion.setText(entidad.localizacion?.toString().orEmpty())
        binding.inputAdminMedidorCalle.setText(entidad.calle.orEmpty())
        binding.inputAdminMedidorPoste.setText(entidad.poste.orEmpty())
        binding.inputAdminMedidorMetros.setText(entidad.metros.orEmpty())
        binding.actvAdminMedidorPueblo.setText(formatPueblo(entidad.pueblo), false)
        binding.actvAdminMedidorSubregion.setText(formatSubregion(entidad.subregion), false)
    }

    private fun mostrarVehiculo(entidad: VehiculosEntity?) {
        limpiarErroresVehiculo()
        if (entidad == null) {
            limpiarFormularioVehiculo()
            return
        }

        binding.actvAdminVehiculoBuscar.setText("${entidad.id} - ${entidad.placa}", false)
        binding.inputAdminVehiculoId.setText(entidad.id.toString())
        binding.inputAdminVehiculoPlaca.setText(entidad.placa.toString())
        binding.inputAdminVehiculoAgencia.setText(entidad.agencia)
        binding.inputAdminVehiculoTipo.setText(entidad.tipo)
        binding.actvAdminVehiculoSubregion.setText(formatSubregion(entidad.subregion), false)
    }

    private fun mostrarLocalizacion(entidad: LocalizacionesEntity?) {
        limpiarErroresLocalizacion()
        if (entidad == null) {
            limpiarFormularioLocalizacion()
            return
        }

        binding.actvAdminLocalizacionBuscar.setText(entidad.id.toString(), false)
        binding.inputAdminLocalizacionId.setText(entidad.id.toString())
        binding.actvAdminLocalizacionPueblo.setText(formatPueblo(entidad.pueblo.toString()), false)
        binding.inputAdminLocalizacionCalle.setText(entidad.calle.toString())
        binding.inputAdminLocalizacionDireccion.setText(entidad.direccion)
        binding.inputAdminLocalizacionLatitud.setText(entidad.latitud.takeUnless { it == 0.0 }?.toString().orEmpty())
        binding.inputAdminLocalizacionLongitud.setText(entidad.longitud.takeUnless { it == 0.0 }?.toString().orEmpty())
        binding.inputAdminLocalizacionDelPoste.setText(entidad.delPoste.toString())
        binding.inputAdminLocalizacionAlPoste.setText(entidad.alPoste.toString())
        binding.actvAdminLocalizacionSubregion.setText(formatSubregion(entidad.subregion), false)
    }

    private fun guardarMedidor() {
        limpiarErroresMedidor()
        val numero = binding.inputAdminMedidorNumero.text?.toString().orEmpty().trim()
        if (numero.isEmpty()) {
            binding.tilAdminMedidorNumero.error = getString(R.string.admin_medidor_error_numero)
            return
        }

        val cliente = binding.inputAdminMedidorCliente.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val localizacionTexto = binding.inputAdminMedidorLocalizacion.text?.toString()?.trim()
        val localizacion = if (localizacionTexto.isNullOrEmpty()) {
            null
        } else {
            localizacionTexto.toLongOrNull() ?: run {
                binding.tilAdminMedidorLocalizacion.error = getString(R.string.admin_medidor_error_localizacion)
                return
            }
        }

        val calle = binding.inputAdminMedidorCalle.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val poste = binding.inputAdminMedidorPoste.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val metros = binding.inputAdminMedidorMetros.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val puebloCodigo = resolvePuebloCode(binding.actvAdminMedidorPueblo.text?.toString())
        if (puebloCodigo == null) {
            binding.tilAdminMedidorPueblo.error = getString(R.string.admin_medidor_error_pueblo)
            return
        }

        val subregionId = resolveSubregionId(binding.actvAdminMedidorSubregion.text?.toString())
        if (subregionId.isNullOrEmpty()) {
            binding.tilAdminMedidorSubregion.error = getString(R.string.admin_medidor_error_subregion)
            return
        }

        viewModel.guardarMedidor(
            numero = numero,
            cliente = cliente,
            localizacion = localizacion,
            calle = calle,
            poste = poste,
            metros = metros,
            puebloCodigo = puebloCodigo,
            subregionId = subregionId
        )
    }

    private fun eliminarMedidor() {
        val numero = binding.inputAdminMedidorNumero.text?.toString()?.trim()
        if (numero.isNullOrEmpty()) {
            binding.tilAdminMedidorNumero.error = getString(R.string.admin_medidor_error_numero)
            return
        }
        val subregionId = resolveSubregionId(binding.actvAdminMedidorSubregion.text?.toString())
        if (subregionId.isNullOrEmpty()) {
            binding.tilAdminMedidorSubregion.error = getString(R.string.admin_medidor_error_subregion)
            return
        }
        viewModel.eliminarMedidor(numero, subregionId)
    }

    private fun guardarVehiculo() {
        limpiarErroresVehiculo()
        val id = binding.inputAdminVehiculoId.text?.toString()?.trim()?.toIntOrNull()
        if (id == null) {
            binding.tilAdminVehiculoId.error = getString(R.string.admin_vehiculo_error_id)
            return
        }

        val placa = binding.inputAdminVehiculoPlaca.text?.toString()?.trim()?.toLongOrNull()
        if (placa == null) {
            binding.tilAdminVehiculoPlaca.error = getString(R.string.admin_vehiculo_error_placa)
            return
        }

        val agencia = binding.inputAdminVehiculoAgencia.text?.toString()?.trim()
        if (agencia.isNullOrEmpty()) {
            binding.tilAdminVehiculoAgencia.error = getString(R.string.admin_vehiculo_error_agencia)
            return
        }

        val tipo = binding.inputAdminVehiculoTipo.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val subregion = resolveSubregionId(binding.actvAdminVehiculoSubregion.text?.toString())

        viewModel.guardarVehiculo(
            id = id,
            placa = placa,
            agencia = agencia,
            tipo = tipo,
            subregionId = subregion
        )
    }

    private fun eliminarVehiculo() {
        val id = binding.inputAdminVehiculoId.text?.toString()?.trim()?.toIntOrNull()
        if (id == null) {
            binding.tilAdminVehiculoId.error = getString(R.string.admin_vehiculo_error_id)
            return
        }
        val etiqueta = binding.inputAdminVehiculoPlaca.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        viewModel.eliminarVehiculo(id, etiqueta)
    }

    private fun guardarLocalizacion() {
        limpiarErroresLocalizacion()
        val id = binding.inputAdminLocalizacionId.text?.toString()?.trim()?.toIntOrNull()
        if (id == null) {
            binding.tilAdminLocalizacionId.error = getString(R.string.admin_localizacion_error_id)
            return
        }

        val puebloId = resolvePuebloId(binding.actvAdminLocalizacionPueblo.text?.toString())
        if (puebloId == null) {
            binding.tilAdminLocalizacionPueblo.error = getString(R.string.admin_localizacion_error_pueblo)
            return
        }

        val calle = binding.inputAdminLocalizacionCalle.text?.toString()?.trim()?.toIntOrNull()
        if (calle == null) {
            binding.tilAdminLocalizacionCalle.error = getString(R.string.admin_localizacion_error_calle)
            return
        }

        val direccion = binding.inputAdminLocalizacionDireccion.text?.toString()?.trim()
        if (direccion.isNullOrEmpty()) {
            binding.tilAdminLocalizacionDireccion.error = getString(R.string.admin_localizacion_error_direccion)
            return
        }

        val latitudTexto = binding.inputAdminLocalizacionLatitud.text?.toString()?.trim()
        val latitud = if (latitudTexto.isNullOrEmpty()) {
            null
        } else {
            latitudTexto.toDoubleOrNull() ?: run {
                binding.tilAdminLocalizacionLatitud.error = getString(R.string.admin_localizacion_error_latitud)
                return
            }
        }

        val longitudTexto = binding.inputAdminLocalizacionLongitud.text?.toString()?.trim()
        val longitud = if (longitudTexto.isNullOrEmpty()) {
            null
        } else {
            longitudTexto.toDoubleOrNull() ?: run {
                binding.tilAdminLocalizacionLongitud.error = getString(R.string.admin_localizacion_error_longitud)
                return
            }
        }

        val delPoste = binding.inputAdminLocalizacionDelPoste.text?.toString()?.trim()?.toIntOrNull()
        if (delPoste == null) {
            binding.tilAdminLocalizacionDelPoste.error = getString(R.string.admin_localizacion_error_del_poste)
            return
        }

        val alPoste = binding.inputAdminLocalizacionAlPoste.text?.toString()?.trim()?.toIntOrNull()
        if (alPoste == null) {
            binding.tilAdminLocalizacionAlPoste.error = getString(R.string.admin_localizacion_error_al_poste)
            return
        }

        val subregion = resolveSubregionId(binding.actvAdminLocalizacionSubregion.text?.toString())

        viewModel.guardarLocalizacion(
            id = id,
            puebloId = puebloId,
            calleId = calle,
            direccion = direccion,
            latitud = latitud,
            longitud = longitud,
            delPoste = delPoste,
            alPoste = alPoste,
            subregionId = subregion
        )
    }

    private fun eliminarLocalizacion() {
        val id = binding.inputAdminLocalizacionId.text?.toString()?.trim()?.toIntOrNull()
        if (id == null) {
            binding.tilAdminLocalizacionId.error = getString(R.string.admin_localizacion_error_id)
            return
        }
        viewModel.eliminarLocalizacion(id)
    }

    private fun limpiarFormularioMedidor() {
        limpiarErroresMedidor()
        binding.actvAdminMedidorBuscar.setText("", false)
        binding.inputAdminMedidorNumero.setText("")
        binding.inputAdminMedidorCliente.setText("")
        binding.inputAdminMedidorLocalizacion.setText("")
        binding.inputAdminMedidorCalle.setText("")
        binding.inputAdminMedidorPoste.setText("")
        binding.inputAdminMedidorMetros.setText("")
        binding.actvAdminMedidorPueblo.setText("", false)
        binding.actvAdminMedidorSubregion.setText("", false)
    }

    private fun limpiarFormularioVehiculo() {
        limpiarErroresVehiculo()
        binding.actvAdminVehiculoBuscar.setText("", false)
        binding.inputAdminVehiculoId.setText("")
        binding.inputAdminVehiculoPlaca.setText("")
        binding.inputAdminVehiculoAgencia.setText("")
        binding.inputAdminVehiculoTipo.setText("")
        binding.actvAdminVehiculoSubregion.setText("", false)
    }

    private fun limpiarFormularioLocalizacion() {
        limpiarErroresLocalizacion()
        binding.actvAdminLocalizacionBuscar.setText("", false)
        binding.inputAdminLocalizacionId.setText("")
        binding.actvAdminLocalizacionPueblo.setText("", false)
        binding.inputAdminLocalizacionCalle.setText("")
        binding.inputAdminLocalizacionDireccion.setText("")
        binding.inputAdminLocalizacionLatitud.setText("")
        binding.inputAdminLocalizacionLongitud.setText("")
        binding.inputAdminLocalizacionDelPoste.setText("")
        binding.inputAdminLocalizacionAlPoste.setText("")
        binding.actvAdminLocalizacionSubregion.setText("", false)
    }

    private fun limpiarErroresMedidor() {
        binding.tilAdminMedidorNumero.error = null
        binding.tilAdminMedidorLocalizacion.error = null
        binding.tilAdminMedidorPueblo.error = null
        binding.tilAdminMedidorSubregion.error = null
    }

    private fun limpiarErroresVehiculo() {
        binding.tilAdminVehiculoId.error = null
        binding.tilAdminVehiculoPlaca.error = null
        binding.tilAdminVehiculoAgencia.error = null
    }

    private fun limpiarErroresLocalizacion() {
        binding.tilAdminLocalizacionId.error = null
        binding.tilAdminLocalizacionPueblo.error = null
        binding.tilAdminLocalizacionCalle.error = null
        binding.tilAdminLocalizacionDireccion.error = null
        binding.tilAdminLocalizacionLatitud.error = null
        binding.tilAdminLocalizacionLongitud.error = null
        binding.tilAdminLocalizacionDelPoste.error = null
        binding.tilAdminLocalizacionAlPoste.error = null
    }

    private fun resolvePuebloCode(display: String?): String? {
        val raw = display?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val candidate = raw.split(" - ").firstOrNull()?.trim().takeUnless { it.isNullOrEmpty() }
            ?: raw
        val match = pueblosDisponibles.firstOrNull { it.id.toString() == candidate }
        return match?.id?.toString()
    }

    private fun resolvePuebloId(display: String?): Int? = resolvePuebloCode(display)?.toIntOrNull()

    private fun resolveSubregionId(display: String?): String? {
        val raw = display?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val candidate = raw.split(" - ").firstOrNull()?.trim().takeUnless { it.isNullOrEmpty() }
            ?: raw
        val matchById = subregionesDisponibles.firstOrNull { it.id.equals(candidate, ignoreCase = true) }
        if (matchById != null) return matchById.id
        val matchByName = subregionesDisponibles.firstOrNull { it.nombre.equals(raw, ignoreCase = true) }
        return matchByName?.id ?: candidate
    }

    private fun formatPueblo(codigo: String?): String {
        if (codigo.isNullOrBlank()) return ""
        val match = pueblosDisponibles.firstOrNull { it.id.toString() == codigo.trim() }
        return match?.let { "${it.id} - ${it.nombre}" } ?: codigo
    }

    private fun formatSubregion(codigo: String?): String {
        if (codigo.isNullOrBlank()) return ""
        val match = subregionesDisponibles.firstOrNull { it.id.equals(codigo, ignoreCase = true) }
        return match?.let { "${it.id} - ${it.nombre}" } ?: codigo
    }

    private fun parseVehiculoId(text: String?): Int? {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val candidate = raw.split(" - ").firstOrNull()?.trim().takeUnless { it.isNullOrEmpty() }
            ?: raw
        return candidate.toIntOrNull()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
