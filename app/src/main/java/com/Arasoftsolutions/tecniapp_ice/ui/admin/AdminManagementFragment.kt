package com.Arasoftsolutions.tecniapp_ice.ui.admin

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AgenciaEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.LocalizacionesEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.MedidorEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.PueblosEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.SubregionesEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculosEntity
import com.Arasoftsolutions.tecniapp_ice.ui.admin.MapCoordinatePickerBottomSheet
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentAdminManagementBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.util.Locale

class AdminManagementFragment : Fragment(R.layout.fragment_admin_management) {

    private var _binding: FragmentAdminManagementBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminManagementViewModel by viewModels()

    private lateinit var medidorAdapter: ArrayAdapter<String>
    private lateinit var medidorPuebloAdapter: ArrayAdapter<String>
    private lateinit var vehiculoAdapter: ArrayAdapter<String>
    private lateinit var vehiculoAgenciaAdapter: ArrayAdapter<String>
    private lateinit var localizacionPuebloAdapter: ArrayAdapter<String>
    private lateinit var localizacionCalleAdapter: ArrayAdapter<String>
    private lateinit var subregionAdapter: ArrayAdapter<String>

    private var pueblosDisponibles: List<PueblosEntity> = emptyList()
    private var subregionesDisponibles: List<SubregionesEntity> = emptyList()
    private var agenciasDisponibles: List<AgenciaEntity> = emptyList()
    private var medidoresDisponibles: List<MedidorEntity> = emptyList()
    private var vehiculosDisponibles: List<VehiculosEntity> = emptyList()
    private var localizacionesDisponibles: List<LocalizacionesEntity> = emptyList()

    private val vehiculoDisplayToPlaca = mutableMapOf<String, Long>()
    private val localizacionDisplayToId = mutableMapOf<String, Int>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAdminManagementBinding.bind(view)

        setupAdapters()
        setupToggle()
        setupListeners()
        observeViewModel()

        parentFragmentManager.setFragmentResultListener(
            MapCoordinatePickerBottomSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val lat = bundle.getDouble(MapCoordinatePickerBottomSheet.RESULT_LAT)
            val lng = bundle.getDouble(MapCoordinatePickerBottomSheet.RESULT_LNG)
            binding.inputAdminLocalizacionLatitud.setText(formatCoordinate(lat))
            binding.inputAdminLocalizacionLongitud.setText(formatCoordinate(lng))
        }
    }

    private fun setupAdapters() {
        val context = requireContext()
        medidorAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, mutableListOf())
        binding.actvAdminMedidorBuscar.setAdapter(medidorAdapter)

        medidorPuebloAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, mutableListOf())
        binding.actvAdminMedidorPueblo.setAdapter(medidorPuebloAdapter)

        vehiculoAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, mutableListOf())
        binding.actvAdminVehiculoBuscar.setAdapter(vehiculoAdapter)

        vehiculoAgenciaAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, mutableListOf())
        binding.actvAdminVehiculoAgencia.setAdapter(vehiculoAgenciaAdapter)

        localizacionPuebloAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, mutableListOf())
        binding.actvAdminLocalizacionPueblo.setAdapter(localizacionPuebloAdapter)

        localizacionCalleAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, mutableListOf())
        binding.actvAdminLocalizacionCalle.setAdapter(localizacionCalleAdapter)

        subregionAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, mutableListOf())
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
        binding.actvAdminMedidorBuscar.setOnEditorActionListener { _, _, _ ->
            viewModel.seleccionarMedidor(binding.actvAdminMedidorBuscar.text?.toString().orEmpty())
            true
        }

        binding.btnAdminMedidorLimpiar.setOnClickListener {
            limpiarFormularioMedidor()
            viewModel.limpiarMedidor()
        }
        binding.btnAdminMedidorGuardar.setOnClickListener { guardarMedidor() }
        binding.btnAdminMedidorEliminar.setOnClickListener { eliminarMedidor() }

        binding.inputAdminMedidorCalle.doAfterTextChanged { actualizarMedidorLocalizacion() }
        binding.inputAdminMedidorPoste.doAfterTextChanged { actualizarMedidorLocalizacion() }
        binding.inputAdminMedidorMetros.doAfterTextChanged { actualizarMedidorLocalizacion() }
        binding.actvAdminMedidorPueblo.setOnItemClickListener { _, _, _, _ -> actualizarMedidorLocalizacion() }
        binding.actvAdminMedidorSubregion.setOnItemClickListener { _, _, _, _ ->
            actualizarPueblosMedidor()
            actualizarMedidorLocalizacion()
        }

        binding.actvAdminVehiculoBuscar.setOnItemClickListener { parent, _, position, _ ->
            val item = parent.getItemAtPosition(position) as? String ?: return@setOnItemClickListener
            viewModel.seleccionarVehiculoPorPlaca(parseVehiculoPlaca(item))
        }
        binding.actvAdminVehiculoBuscar.setOnEditorActionListener { _, _, _ ->
            viewModel.seleccionarVehiculoPorPlaca(parseVehiculoPlaca(binding.actvAdminVehiculoBuscar.text?.toString()))
            true
        }

        binding.btnAdminVehiculoLimpiar.setOnClickListener {
            limpiarFormularioVehiculo()
            viewModel.limpiarVehiculo()
        }
        binding.btnAdminVehiculoGuardar.setOnClickListener { guardarVehiculo() }
        binding.btnAdminVehiculoEliminar.setOnClickListener { eliminarVehiculo() }
        binding.actvAdminVehiculoSubregion.setOnItemClickListener { _, _, _, _ -> actualizarAgenciasFiltradas() }

        binding.actvAdminLocalizacionSubregion.setOnItemClickListener { _, _, _, _ ->
            actualizarPueblosLocalizacion()
        }
        binding.actvAdminLocalizacionPueblo.setOnItemClickListener { _, _, _, _ ->
            actualizarCallesLocalizacion()
        }
        binding.actvAdminLocalizacionCalle.setOnItemClickListener { parent, _, position, _ ->
            val display = parent.getItemAtPosition(position) as? String ?: return@setOnItemClickListener
            val id = localizacionDisplayToId[display]
            if (id != null) {
                viewModel.seleccionarLocalizacion(id)
            }
        }
        binding.actvAdminLocalizacionCalle.setOnEditorActionListener { _, _, _ ->
            val display = binding.actvAdminLocalizacionCalle.text?.toString()
            val id = localizacionDisplayToId[display]
                ?: display?.trim()?.substringBefore(" ")?.toIntOrNull()
            if (id != null) {
                viewModel.seleccionarLocalizacion(id)
            }
            true
        }

        binding.btnAdminLocalizacionLimpiar.setOnClickListener {
            limpiarFormularioLocalizacion()
            viewModel.limpiarLocalizacion()
        }
        binding.btnAdminLocalizacionGuardar.setOnClickListener { guardarLocalizacion() }
        binding.btnAdminLocalizacionEliminar.setOnClickListener { eliminarLocalizacion() }
        binding.btnAdminLocalizacionMapa.setOnClickListener { abrirSelectorMapa() }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.medidores.collect { actualizarMedidores(it) } }
                launch { viewModel.vehiculos.collect { actualizarVehiculos(it) } }
                launch { viewModel.localizaciones.collect { actualizarLocalizaciones(it) } }
                launch { viewModel.pueblos.collect { actualizarPueblos(it) } }
                launch { viewModel.subregiones.collect { actualizarSubregiones(it) } }
                launch { viewModel.agencias.collect { actualizarAgencias(it) } }
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
        vehiculoDisplayToPlaca.clear()
        val datos = lista.sortedBy { it.placa }.map { vehiculo ->
            val display = "${vehiculo.placa} - ${vehiculo.agencia}"
            vehiculoDisplayToPlaca[display] = vehiculo.placa
            display
        }
        vehiculoAdapter.clear()
        vehiculoAdapter.addAll(datos)
        vehiculoAdapter.notifyDataSetChanged()
        actualizarAgenciasFiltradas()
    }

    private fun actualizarLocalizaciones(lista: List<LocalizacionesEntity>) {
        localizacionesDisponibles = lista
        actualizarCallesLocalizacion()
    }

    private fun actualizarPueblos(lista: List<PueblosEntity>) {
        pueblosDisponibles = lista
        actualizarPueblosMedidor()
        actualizarPueblosLocalizacion()
    }

    private fun actualizarSubregiones(lista: List<SubregionesEntity>) {
        subregionesDisponibles = lista
        val datos = lista.sortedBy { it.id }.map { "${it.id} - ${it.nombre}" }
        subregionAdapter.clear()
        subregionAdapter.addAll(datos)
        subregionAdapter.notifyDataSetChanged()
        actualizarPueblosMedidor()
        actualizarPueblosLocalizacion()
        actualizarAgenciasFiltradas()
    }

    private fun actualizarAgencias(lista: List<AgenciaEntity>) {
        agenciasDisponibles = lista
        actualizarAgenciasFiltradas()
    }

    private fun actualizarPueblosMedidor() {
        val subregionSeleccionada = resolveSubregionId(binding.actvAdminMedidorSubregion.text?.toString())
        val datos = pueblosDisponibles
            .filter { subregionSeleccionada == null || it.subregion_id_normalizado.equals(subregionSeleccionada, ignoreCase = true) }
            .sortedBy { it.id }
            .map { "${it.id} - ${it.nombre}" }
        medidorPuebloAdapter.clear()
        medidorPuebloAdapter.addAll(datos)
        medidorPuebloAdapter.notifyDataSetChanged()
        if (!datos.contains(binding.actvAdminMedidorPueblo.text?.toString())) {
            binding.actvAdminMedidorPueblo.setText("", false)
        }
    }

    private fun actualizarPueblosLocalizacion() {
        val subregionSeleccionada = resolveSubregionId(binding.actvAdminLocalizacionSubregion.text?.toString())
        val datos = pueblosDisponibles
            .filter { subregionSeleccionada == null || it.subregion_id_normalizado.equals(subregionSeleccionada, ignoreCase = true) }
            .sortedBy { it.id }
            .map { "${it.id} - ${it.nombre}" }
        localizacionPuebloAdapter.clear()
        localizacionPuebloAdapter.addAll(datos)
        localizacionPuebloAdapter.notifyDataSetChanged()
        if (!datos.contains(binding.actvAdminLocalizacionPueblo.text?.toString())) {
            binding.actvAdminLocalizacionPueblo.setText("", false)
        }
        actualizarCallesLocalizacion()
    }

    private fun actualizarCallesLocalizacion() {
        val puebloId = resolvePuebloId(binding.actvAdminLocalizacionPueblo.text?.toString())
        localizacionCalleAdapter.clear()
        localizacionDisplayToId.clear()
        if (puebloId == null) {
            binding.actvAdminLocalizacionCalle.setText("", false)
            localizacionCalleAdapter.notifyDataSetChanged()
            return
        }
        val datos = localizacionesDisponibles
            .filter { it.pueblo == puebloId }
            .sortedWith(compareBy<LocalizacionesEntity> { it.calle }.thenBy { it.delPoste }.thenBy { it.alPoste })
            .map { entidad ->
                val display = formatLocalizacionDisplay(entidad)
                localizacionDisplayToId[display] = entidad.id
                display
            }
        localizacionCalleAdapter.addAll(datos)
        localizacionCalleAdapter.notifyDataSetChanged()
        val actual = binding.actvAdminLocalizacionCalle.text?.toString()
        if (actual.isNullOrBlank() || !localizacionDisplayToId.containsKey(actual)) {
            binding.actvAdminLocalizacionCalle.setText("", false)
        }
    }

    private fun actualizarAgenciasFiltradas() {
        val subregionSeleccionada = resolveSubregionId(binding.actvAdminVehiculoSubregion.text?.toString())
        val datos = agenciasDisponibles
            .filter { subregionSeleccionada == null || it.subregion?.equals(subregionSeleccionada, ignoreCase = true) == true }
            .sortedBy { it.nombre }
            .map { it.nombre }
        vehiculoAgenciaAdapter.clear()
        vehiculoAgenciaAdapter.addAll(datos)
        vehiculoAgenciaAdapter.notifyDataSetChanged()
        if (!datos.contains(binding.actvAdminVehiculoAgencia.text?.toString())) {
            binding.actvAdminVehiculoAgencia.setText("", false)
        }
    }

    private fun formatLocalizacionDisplay(entidad: LocalizacionesEntity): String {
        val poste = "Poste ${entidad.delPoste}-${entidad.alPoste}"
        return "${entidad.calle} - $poste (ID ${entidad.id})"
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
        binding.inputAdminMedidorCalle.setText(entidad.calle.orEmpty())
        binding.inputAdminMedidorPoste.setText(entidad.poste.orEmpty())
        binding.inputAdminMedidorMetros.setText(entidad.metros.orEmpty())
        binding.actvAdminMedidorPueblo.setText(formatPueblo(entidad.pueblo), false)
        binding.actvAdminMedidorSubregion.setText(formatSubregion(entidad.subregion), false)
        binding.inputAdminMedidorLocalizacion.setText(entidad.localizacion?.toString().orEmpty())
        actualizarMedidorLocalizacion()
    }

    private fun mostrarVehiculo(entidad: VehiculosEntity?) {
        limpiarErroresVehiculo()
        if (entidad == null) {
            limpiarFormularioVehiculo()
            return
        }

        val display = "${entidad.placa} - ${entidad.agencia}"
        binding.actvAdminVehiculoBuscar.setText(display, false)
        binding.inputAdminVehiculoId.setText(entidad.id.toString())
        binding.inputAdminVehiculoPlaca.setText(entidad.placa.toString())
        binding.actvAdminVehiculoAgencia.setText(entidad.agencia, false)
        binding.inputAdminVehiculoTipo.setText(entidad.tipo)
        binding.actvAdminVehiculoSubregion.setText(formatSubregion(entidad.subregion), false)
        actualizarAgenciasFiltradas()
    }

    private fun mostrarLocalizacion(entidad: LocalizacionesEntity?) {
        limpiarErroresLocalizacion()
        if (entidad == null) {
            limpiarFormularioLocalizacion()
            return
        }

        binding.inputAdminLocalizacionId.setText(entidad.id.toString())
        val subregionResuelta = entidad.subregion
            ?: pueblosDisponibles.firstOrNull { it.id == entidad.pueblo }?.subregion_id_normalizado
        binding.actvAdminLocalizacionSubregion.setText(formatSubregion(subregionResuelta), false)
        actualizarPueblosLocalizacion()

        binding.actvAdminLocalizacionPueblo.setText(formatPueblo(entidad.pueblo.toString()), false)
        actualizarCallesLocalizacion()
        binding.actvAdminLocalizacionCalle.setText(formatLocalizacionDisplay(entidad), false)

        binding.inputAdminLocalizacionDireccion.setText(entidad.direccion)
        binding.inputAdminLocalizacionLatitud.setText(entidad.latitud.takeUnless { it == 0.0 }?.let { formatCoordinate(it) }.orEmpty())
        binding.inputAdminLocalizacionLongitud.setText(entidad.longitud.takeUnless { it == 0.0 }?.let { formatCoordinate(it) }.orEmpty())
        binding.inputAdminLocalizacionDelPoste.setText(entidad.delPoste.toString())
        binding.inputAdminLocalizacionAlPoste.setText(entidad.alPoste.toString())
    }

    private fun guardarMedidor() {
        limpiarErroresMedidor()
        val numero = binding.inputAdminMedidorNumero.text?.toString().orEmpty().trim()
        if (numero.isEmpty()) {
            binding.tilAdminMedidorNumero.error = getString(R.string.admin_medidor_error_numero)
            return
        }

        val cliente = binding.inputAdminMedidorCliente.text?.toString()?.trim().orEmpty()
        if (cliente.isEmpty()) {
            binding.tilAdminMedidorCliente.error = getString(R.string.admin_medidor_error_cliente)
            return
        }

        val calle = binding.inputAdminMedidorCalle.text?.toString()?.trim().orEmpty()
        if (calle.isEmpty()) {
            binding.tilAdminMedidorCalle.error = getString(R.string.admin_medidor_error_calle)
            return
        }

        val poste = binding.inputAdminMedidorPoste.text?.toString()?.trim().orEmpty()
        if (poste.isEmpty()) {
            binding.tilAdminMedidorPoste.error = getString(R.string.admin_medidor_error_poste)
            return
        }

        val metros = binding.inputAdminMedidorMetros.text?.toString()?.trim().orEmpty()
        if (metros.isEmpty()) {
            binding.tilAdminMedidorMetros.error = getString(R.string.admin_medidor_error_metros)
            return
        }

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

        val localizacion = generarLocalizacion(puebloCodigo, calle, poste, metros)
        if (localizacion == null) {
            binding.tilAdminMedidorLocalizacion.error = getString(R.string.admin_medidor_error_localizacion)
            return
        }
        binding.inputAdminMedidorLocalizacion.setText(localizacion.toString())

        confirmarAccion(
            getString(R.string.admin_confirm_guardar_medidor_title),
            getString(R.string.admin_confirm_guardar_medidor_message, numero)
        ) {
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

        confirmarAccion(
            getString(R.string.admin_confirm_eliminar_medidor_title),
            getString(R.string.admin_confirm_eliminar_medidor_message, numero)
        ) {
            viewModel.eliminarMedidor(numero, subregionId)
        }
    }

    private fun guardarVehiculo() {
        limpiarErroresVehiculo()
        val id = binding.inputAdminVehiculoId.text?.toString()?.trim()?.toIntOrNull()
        if (id == null) {
            binding.tilAdminVehiculoId.error = getString(R.string.admin_vehiculo_error_id)
            return
        }

        val placaTexto = binding.inputAdminVehiculoPlaca.text?.toString()?.trim().orEmpty()
        val placa = placaTexto.toLongOrNull()
        if (placa == null) {
            binding.tilAdminVehiculoPlaca.error = getString(R.string.admin_vehiculo_error_placa)
            return
        }

        val agencia = resolveAgenciaNombre(binding.actvAdminVehiculoAgencia.text?.toString())
        if (agencia.isNullOrEmpty()) {
            binding.tilAdminVehiculoAgencia.error = getString(R.string.admin_vehiculo_error_agencia)
            return
        }

        val tipo = binding.inputAdminVehiculoTipo.text?.toString()?.trim().orEmpty()
        if (tipo.isEmpty()) {
            binding.tilAdminVehiculoTipo.error = getString(R.string.admin_vehiculo_error_tipo)
            return
        }

        val subregionId = resolveSubregionId(binding.actvAdminVehiculoSubregion.text?.toString())
        if (subregionId.isNullOrEmpty()) {
            binding.tilAdminVehiculoSubregion.error = getString(R.string.admin_vehiculo_error_subregion)
            return
        }

        confirmarAccion(
            getString(R.string.admin_confirm_guardar_vehiculo_title),
            getString(R.string.admin_confirm_guardar_vehiculo_message, placaTexto.ifEmpty { id.toString() })
        ) {
            viewModel.guardarVehiculo(
                id = id,
                placa = placa,
                agencia = agencia,
                tipo = tipo,
                subregionId = subregionId
            )
        }
    }

    private fun eliminarVehiculo() {
        val id = binding.inputAdminVehiculoId.text?.toString()?.trim()?.toIntOrNull()
        if (id == null) {
            binding.tilAdminVehiculoId.error = getString(R.string.admin_vehiculo_error_id)
            return
        }
        val placaTexto = binding.inputAdminVehiculoPlaca.text?.toString()?.trim().takeIf { !it.isNullOrEmpty() }
        confirmarAccion(
            getString(R.string.admin_confirm_eliminar_vehiculo_title),
            getString(R.string.admin_confirm_eliminar_vehiculo_message, placaTexto ?: id.toString())
        ) {
            viewModel.eliminarVehiculo(id, placaTexto)
        }
    }

    private fun guardarLocalizacion() {
        limpiarErroresLocalizacion()
        val id = binding.inputAdminLocalizacionId.text?.toString()?.trim()?.toIntOrNull()
        if (id == null) {
            binding.tilAdminLocalizacionId.error = getString(R.string.admin_localizacion_error_id)
            return
        }

        val subregionId = resolveSubregionId(binding.actvAdminLocalizacionSubregion.text?.toString())
        if (subregionId.isNullOrEmpty()) {
            binding.tilAdminLocalizacionSubregion.error = getString(R.string.admin_localizacion_error_subregion)
            return
        }

        val puebloId = resolvePuebloId(binding.actvAdminLocalizacionPueblo.text?.toString())
        val puebloEntidad = puebloId?.let { idPueblo -> pueblosDisponibles.firstOrNull { it.id == idPueblo } }
        if (puebloId == null || puebloEntidad == null) {
            binding.tilAdminLocalizacionPueblo.error = getString(R.string.admin_localizacion_error_pueblo)
            return
        }
        if (!puebloEntidad.subregion_id_normalizado.equals(subregionId, ignoreCase = true)) {
            binding.tilAdminLocalizacionPueblo.error = getString(R.string.admin_localizacion_error_relacion)
            return
        }

        val calleTexto = binding.actvAdminLocalizacionCalle.text?.toString()?.trim()
        val calle = calleTexto?.takeWhile { it.isDigit() }?.toIntOrNull()
        if (calle == null) {
            binding.tilAdminLocalizacionCalle.error = getString(R.string.admin_localizacion_error_calle)
            return
        }

        val direccion = binding.inputAdminLocalizacionDireccion.text?.toString()?.trim()
        if (direccion.isNullOrEmpty()) {
            binding.tilAdminLocalizacionDireccion.error = getString(R.string.admin_localizacion_error_direccion)
            return
        }

        val latitud = binding.inputAdminLocalizacionLatitud.text?.toString()?.trim()?.toDoubleOrNull()
        if (latitud == null) {
            binding.tilAdminLocalizacionLatitud.error = getString(R.string.admin_localizacion_error_latitud)
            return
        }

        val longitud = binding.inputAdminLocalizacionLongitud.text?.toString()?.trim()?.toDoubleOrNull()
        if (longitud == null) {
            binding.tilAdminLocalizacionLongitud.error = getString(R.string.admin_localizacion_error_longitud)
            return
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

        confirmarAccion(
            getString(R.string.admin_confirm_guardar_localizacion_title),
            getString(R.string.admin_confirm_guardar_localizacion_message, id.toString())
        ) {
            viewModel.guardarLocalizacion(
                id = id,
                puebloId = puebloId,
                calleId = calle,
                direccion = direccion,
                latitud = latitud,
                longitud = longitud,
                delPoste = delPoste,
                alPoste = alPoste,
                subregionId = subregionId
            )
        }
    }

    private fun eliminarLocalizacion() {
        val id = binding.inputAdminLocalizacionId.text?.toString()?.trim()?.toIntOrNull()
        if (id == null) {
            binding.tilAdminLocalizacionId.error = getString(R.string.admin_localizacion_error_id)
            return
        }

        confirmarAccion(
            getString(R.string.admin_confirm_eliminar_localizacion_title),
            getString(R.string.admin_confirm_eliminar_localizacion_message, id.toString())
        ) {
            viewModel.eliminarLocalizacion(id)
        }
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
        binding.actvAdminVehiculoAgencia.setText("", false)
        binding.inputAdminVehiculoTipo.setText("")
        binding.actvAdminVehiculoSubregion.setText("", false)
    }

    private fun limpiarFormularioLocalizacion() {
        limpiarErroresLocalizacion()
        binding.inputAdminLocalizacionId.setText("")
        binding.actvAdminLocalizacionSubregion.setText("", false)
        binding.actvAdminLocalizacionPueblo.setText("", false)
        binding.actvAdminLocalizacionCalle.setText("", false)
        binding.inputAdminLocalizacionDireccion.setText("")
        binding.inputAdminLocalizacionLatitud.setText("")
        binding.inputAdminLocalizacionLongitud.setText("")
        binding.inputAdminLocalizacionDelPoste.setText("")
        binding.inputAdminLocalizacionAlPoste.setText("")
    }

    private fun limpiarErroresMedidor() {
        binding.tilAdminMedidorNumero.error = null
        binding.tilAdminMedidorCliente.error = null
        binding.tilAdminMedidorCalle.error = null
        binding.tilAdminMedidorPoste.error = null
        binding.tilAdminMedidorMetros.error = null
        binding.tilAdminMedidorLocalizacion.error = null
        binding.tilAdminMedidorPueblo.error = null
        binding.tilAdminMedidorSubregion.error = null
    }

    private fun limpiarErroresVehiculo() {
        binding.tilAdminVehiculoId.error = null
        binding.tilAdminVehiculoPlaca.error = null
        binding.tilAdminVehiculoAgencia.error = null
        binding.tilAdminVehiculoTipo.error = null
        binding.tilAdminVehiculoSubregion.error = null
    }

    private fun limpiarErroresLocalizacion() {
        binding.tilAdminLocalizacionId.error = null
        binding.tilAdminLocalizacionSubregion.error = null
        binding.tilAdminLocalizacionPueblo.error = null
        binding.tilAdminLocalizacionCalle.error = null
        binding.tilAdminLocalizacionDireccion.error = null
        binding.tilAdminLocalizacionLatitud.error = null
        binding.tilAdminLocalizacionLongitud.error = null
        binding.tilAdminLocalizacionDelPoste.error = null
        binding.tilAdminLocalizacionAlPoste.error = null
    }

    private fun actualizarMedidorLocalizacion() {
        val puebloCodigo = resolvePuebloCode(binding.actvAdminMedidorPueblo.text?.toString())
        val calle = binding.inputAdminMedidorCalle.text?.toString()?.trim().orEmpty()
        val poste = binding.inputAdminMedidorPoste.text?.toString()?.trim().orEmpty()
        val metros = binding.inputAdminMedidorMetros.text?.toString()?.trim().orEmpty()
        val localizacion = generarLocalizacion(puebloCodigo, calle, poste, metros)
        if (localizacion != null) {
            binding.inputAdminMedidorLocalizacion.setText(localizacion.toString())
        } else {
            binding.inputAdminMedidorLocalizacion.setText("")
        }
    }

    private fun generarLocalizacion(puebloCodigo: String?, calle: String, poste: String, metros: String): Long? {
        val codigo = puebloCodigo?.takeIf { it.isNotBlank() } ?: return null
        if (calle.isBlank() || poste.isBlank() || metros.isBlank()) return null
        val puebloSegment = codigo.filter(Char::isDigit)
        if (puebloSegment.isEmpty()) return null
        val calleSegment = calle.filter(Char::isDigit).padStart(3, '0')
        val posteSegment = poste.filter(Char::isDigit).padStart(3, '0')
        val metrosSegment = metros.filter(Char::isDigit).padStart(2, '0')
        val localizacionString = puebloSegment + calleSegment + posteSegment + metrosSegment
        return localizacionString.toLongOrNull()
    }

    private fun formatCoordinate(value: Double): String =
        String.format(Locale.US, "%.6f", value)

    private fun confirmarAccion(titulo: String, mensaje: String, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(titulo)
            .setMessage(mensaje)
            .setPositiveButton(R.string.admin_confirm_accept) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.admin_confirm_cancel, null)
            .show()
    }

    private fun abrirSelectorMapa() {
        val latitud = binding.inputAdminLocalizacionLatitud.text?.toString()?.trim()?.toDoubleOrNull()
        val longitud = binding.inputAdminLocalizacionLongitud.text?.toString()?.trim()?.toDoubleOrNull()
        MapCoordinatePickerBottomSheet.newInstance(latitud, longitud)
            .show(parentFragmentManager, MapCoordinatePickerBottomSheet.TAG)
    }

    private fun parseVehiculoPlaca(text: String?): Long? {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty()) return null
        vehiculoDisplayToPlaca[raw]?.let { return it }
        val candidate = raw.split(" - ").firstOrNull()?.filter(Char::isDigit)?.takeIf { it.isNotEmpty() }
            ?: raw.filter(Char::isDigit)
        return candidate.toLongOrNull()
    }

    private fun resolveAgenciaNombre(display: String?): String? {
        val raw = display?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val match = agenciasDisponibles.firstOrNull { it.nombre.equals(raw, ignoreCase = true) }
        return match?.nombre ?: raw
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
