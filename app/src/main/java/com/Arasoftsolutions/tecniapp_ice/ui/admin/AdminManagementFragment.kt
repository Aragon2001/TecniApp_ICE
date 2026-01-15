package com.Arasoftsolutions.tecniapp_ice.ui.admin

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.annotation.IdRes
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

    private var subregionUsuario: AdminManagementViewModel.SubregionUsuario? = null

    private var pueblosCatalogo: List<PueblosEntity> = emptyList()
    private var subregionesCatalogo: List<SubregionesEntity> = emptyList()
    private var agenciasCatalogo: List<AgenciaEntity> = emptyList()
    private var medidoresCatalogo: List<MedidorEntity> = emptyList()
    private var vehiculosCatalogo: List<VehiculosEntity> = emptyList()
    private var localizacionesCatalogo: List<LocalizacionesEntity> = emptyList()

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
            actualizarIconoSeccion(checkedId)
        }
        binding.toggleAdminSections.check(R.id.btnAdminMedidores)
    }

    private fun actualizarIconoSeccion(@IdRes checkedId: Int) {
        val (iconRes, contentDescriptionRes) = when (checkedId) {
            R.id.btnAdminMedidores -> R.drawable.ic_menu_medidor to R.string.admin_section_icon_medidores
            R.id.btnAdminVehiculos -> R.drawable.ic_menu_vehiculo to R.string.admin_section_icon_vehiculos
            R.id.btnAdminLocalizaciones -> R.drawable.ic_menu_localizacion to R.string.admin_section_icon_localizaciones
            else -> R.drawable.ic_menu_medidor to R.string.admin_section_icon_medidores
        }
        binding.imageAdminSectionIcon.setImageResource(iconRes)
        binding.imageAdminSectionIcon.contentDescription = getString(contentDescriptionRes)
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

        binding.inputAdminMedidorNumero.doAfterTextChanged { actualizarEstadoBotonesMedidor() }

        binding.btnAdminMedidorLimpiar.setOnClickListener {
            limpiarFormularioMedidor()
            viewModel.limpiarMedidor()
            actualizarEstadoBotonesMedidor()
        }
        binding.btnAdminMedidorAgregar.setOnClickListener { agregarMedidor() }
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

        binding.inputAdminVehiculoPlaca.doAfterTextChanged { actualizarEstadoBotonesVehiculo() }

        binding.btnAdminVehiculoLimpiar.setOnClickListener {
            limpiarFormularioVehiculo()
            viewModel.limpiarVehiculo()
            actualizarEstadoBotonesVehiculo()
        }
        binding.btnAdminVehiculoAgregar.setOnClickListener { agregarVehiculo() }
        binding.btnAdminVehiculoGuardar.setOnClickListener { guardarVehiculo() }
        binding.btnAdminVehiculoEliminar.setOnClickListener { eliminarVehiculo() }
        binding.actvAdminVehiculoSubregion.setOnItemClickListener { _, _, _, _ ->
            actualizarAgenciasFiltradas()
            actualizarEstadoBotonesVehiculo()
        }

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
            actualizarEstadoBotonesLocalizacion()
        }
        binding.btnAdminLocalizacionAgregar.setOnClickListener { agregarLocalizacion() }
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
                launch { viewModel.subregionUsuario.collect { actualizarSubregionUsuario(it) } }
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
        medidoresCatalogo = lista
        medidoresDisponibles = filtrarPorSubregionUsuario(medidoresCatalogo) { it.subregion }
        val datos = medidoresDisponibles.map { it.medidorNumber }.sorted()
        medidorAdapter.clear()
        medidorAdapter.addAll(datos)
        medidorAdapter.notifyDataSetChanged()
        actualizarEstadoBotonesMedidor()
    }

    private fun actualizarVehiculos(lista: List<VehiculosEntity>) {
        vehiculosCatalogo = lista
        vehiculosDisponibles = filtrarPorSubregionUsuario(vehiculosCatalogo) { it.subregion }
        vehiculoDisplayToPlaca.clear()
        val datos = vehiculosDisponibles.sortedBy { it.placa }.map { vehiculo ->
            val display = "${vehiculo.placa} - ${vehiculo.agencia}"
            vehiculoDisplayToPlaca[display] = vehiculo.placa
            display
        }
        vehiculoAdapter.clear()
        vehiculoAdapter.addAll(datos)
        vehiculoAdapter.notifyDataSetChanged()
        actualizarAgenciasFiltradas()
        actualizarEstadoBotonesVehiculo()
    }

    private fun actualizarLocalizaciones(lista: List<LocalizacionesEntity>) {
        localizacionesCatalogo = lista
        localizacionesDisponibles = localizacionesCatalogo
        actualizarCallesLocalizacion()
        actualizarEstadoBotonesLocalizacion()
    }

    private fun actualizarPueblos(lista: List<PueblosEntity>) {
        pueblosCatalogo = lista
        pueblosDisponibles = filtrarPorSubregionUsuario(pueblosCatalogo) { it.subregion_id_normalizado }
        actualizarPueblosMedidor()
        actualizarPueblosLocalizacion()
    }

    private fun actualizarSubregiones(lista: List<SubregionesEntity>) {
        subregionesCatalogo = lista
        subregionesDisponibles = filtrarSubregionesUsuario(subregionesCatalogo)
        val datos = subregionesDisponibles
            .sortedBy { it.nombre.lowercase(Locale.getDefault()) }
            .map { it.nombre }
        subregionAdapter.clear()
        subregionAdapter.addAll(datos)
        subregionAdapter.notifyDataSetChanged()
        aplicarSubregionPorDefecto()
        actualizarPueblosMedidor()
        actualizarPueblosLocalizacion()
        actualizarAgenciasFiltradas()
    }

    private fun actualizarAgencias(lista: List<AgenciaEntity>) {
        agenciasCatalogo = lista
        agenciasDisponibles = filtrarPorSubregionUsuario(agenciasCatalogo) { it.subregion }
        actualizarAgenciasFiltradas()
    }

    private fun actualizarPueblosMedidor() {
        val subregionSeleccionada = resolveSubregionId(binding.actvAdminMedidorSubregion.text?.toString())
            ?: subregionUsuario?.id
        val subregionNombre = resolveSubregionNombre(binding.actvAdminMedidorSubregion.text?.toString())
            ?: subregionUsuario?.nombre
        val datos = pueblosDisponibles
            .filter {
                subregionSeleccionada == null && subregionNombre == null ||
                    matchesSubregionFiltro(it.subregion_id_normalizado, subregionSeleccionada, subregionNombre)
            }
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
            ?: subregionUsuario?.id
        val subregionNombre = resolveSubregionNombre(binding.actvAdminLocalizacionSubregion.text?.toString())
            ?: subregionUsuario?.nombre
        val datos = pueblosDisponibles
            .filter {
                subregionSeleccionada == null && subregionNombre == null ||
                    matchesSubregionFiltro(it.subregion_id_normalizado, subregionSeleccionada, subregionNombre)
            }
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
            ?: subregionUsuario?.id
        val subregionNombre = resolveSubregionNombre(binding.actvAdminVehiculoSubregion.text?.toString())
            ?: subregionUsuario?.nombre
        val datos = agenciasDisponibles
            .filter {
                subregionSeleccionada == null && subregionNombre == null ||
                    matchesSubregionFiltro(it.subregion, subregionSeleccionada, subregionNombre)
            }
            .sortedBy { it.nombre }
            .map { it.nombre }
        vehiculoAgenciaAdapter.clear()
        vehiculoAgenciaAdapter.addAll(datos)
        vehiculoAgenciaAdapter.notifyDataSetChanged()
        if (!datos.contains(binding.actvAdminVehiculoAgencia.text?.toString())) {
            binding.actvAdminVehiculoAgencia.setText("", false)
        }
        actualizarEstadoBotonesVehiculo()
    }

    private fun actualizarSubregionUsuario(subregion: AdminManagementViewModel.SubregionUsuario?) {
        subregionUsuario = subregion
        actualizarSubregiones(subregionesCatalogo)
        actualizarAgencias(agenciasCatalogo)
        actualizarPueblos(pueblosCatalogo)
        actualizarMedidores(medidoresCatalogo)
        actualizarVehiculos(vehiculosCatalogo)
        actualizarLocalizaciones(localizacionesCatalogo)
    }

    private fun aplicarSubregionPorDefecto() {
        val unica = subregionesDisponibles.singleOrNull()?.nombre ?: return
        var cambio = false
        if (binding.actvAdminMedidorSubregion.text.isNullOrBlank()) {
            binding.actvAdminMedidorSubregion.setText(unica, false)
            cambio = true
        }
        if (binding.actvAdminVehiculoSubregion.text.isNullOrBlank()) {
            binding.actvAdminVehiculoSubregion.setText(unica, false)
            cambio = true
        }
        if (binding.actvAdminLocalizacionSubregion.text.isNullOrBlank()) {
            binding.actvAdminLocalizacionSubregion.setText(unica, false)
            cambio = true
        }
        if (cambio) {
            actualizarPueblosMedidor()
            actualizarPueblosLocalizacion()
            actualizarAgenciasFiltradas()
        }
    }

    private fun filtrarSubregionesUsuario(lista: List<SubregionesEntity>): List<SubregionesEntity> {
        val subregion = subregionUsuario ?: return lista
        return lista.filter { entidad ->
            matchesSubregion(entidad.id, subregion) || matchesSubregion(entidad.nombre, subregion)
        }
    }

    private fun <T> filtrarPorSubregionUsuario(
        lista: List<T>,
        selector: (T) -> String?
    ): List<T> {
        val subregion = subregionUsuario
        val subregionId = subregion?.id?.trim().orEmpty()
        val subregionNombre = subregion?.nombre?.trim().orEmpty()
        if (subregionId.isEmpty() && subregionNombre.isEmpty()) return lista
        return lista.filter { item -> matchesSubregion(selector(item), subregion) }
    }

    private fun matchesSubregion(
        value: String?,
        subregion: AdminManagementViewModel.SubregionUsuario?
    ): Boolean {
        val candidate = value?.trim().orEmpty()
        if (candidate.isEmpty()) return false
        val subregionId = subregion?.id?.trim().orEmpty()
        val subregionNombre = subregion?.nombre?.trim().orEmpty()
        if (subregionId.isEmpty() && subregionNombre.isEmpty()) return false
        return candidate.equals(subregionId, ignoreCase = true) ||
            candidate.equals(subregionNombre, ignoreCase = true)
    }

    private fun matchesSubregionFiltro(value: String?, subregionId: String?, subregionNombre: String?): Boolean {
        val candidate = value?.trim().takeIf { it.isNotEmpty() } ?: return false
        val matchesId = subregionId?.equals(candidate, ignoreCase = true) == true
        val matchesNombre = subregionNombre?.equals(candidate, ignoreCase = true) == true
        return matchesId || matchesNombre
    }

    private fun formatLocalizacionDisplay(entidad: LocalizacionesEntity): String = entidad.calle.toString()

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
        actualizarEstadoBotonesMedidor()
    }

    private fun mostrarVehiculo(entidad: VehiculosEntity?) {
        limpiarErroresVehiculo()
        if (entidad == null) {
            limpiarFormularioVehiculo()
            return
        }

        val display = "${entidad.placa} - ${entidad.agencia}"
        binding.actvAdminVehiculoBuscar.setText(display, false)
        binding.inputAdminVehiculoPlaca.setText(entidad.placa.toString())
        binding.actvAdminVehiculoAgencia.setText(entidad.agencia, false)
        binding.inputAdminVehiculoTipo.setText(entidad.tipo)
        binding.actvAdminVehiculoSubregion.setText(formatSubregion(entidad.subregion), false)
        actualizarAgenciasFiltradas()
        actualizarEstadoBotonesVehiculo()
    }

    private fun mostrarLocalizacion(entidad: LocalizacionesEntity?) {
        limpiarErroresLocalizacion()
        if (entidad == null) {
            limpiarFormularioLocalizacion()
            return
        }

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
        actualizarEstadoBotonesLocalizacion()
    }

    private fun guardarMedidor() {
        limpiarErroresMedidor()
        val form = obtenerMedidorFormulario() ?: return

        val existente = medidoresDisponibles.firstOrNull { it.medidorNumber.equals(form.numero, ignoreCase = true) }
        if (existente == null) {
            binding.tilAdminMedidorNumero.error = getString(R.string.admin_medidor_error_no_existe)
            return
        }

        val localizacionDuplicada = medidoresDisponibles.any {
            it.medidorNumber != existente.medidorNumber && it.localizacion == form.localizacion
        }
        if (localizacionDuplicada) {
            binding.tilAdminMedidorLocalizacion.error = getString(R.string.admin_medidor_error_localizacion_existente)
            return
        }

        confirmarAccion(
            getString(R.string.admin_confirm_guardar_medidor_title),
            getString(R.string.admin_confirm_guardar_medidor_message, form.numero)
        ) {
            viewModel.actualizarMedidor(
                numero = form.numero,
                cliente = form.cliente,
                localizacion = form.localizacion,
                calle = form.calle,
                poste = form.poste,
                metros = form.metros,
                puebloCodigo = form.puebloCodigo,
                subregionId = form.subregionId
            )
        }
    }

    private fun agregarMedidor() {
        limpiarErroresMedidor()
        val form = obtenerMedidorFormulario() ?: return

        if (medidoresDisponibles.any { it.medidorNumber.equals(form.numero, ignoreCase = true) }) {
            binding.tilAdminMedidorNumero.error = getString(R.string.admin_medidor_error_existente)
            return
        }

        val localizacionDuplicada = medidoresDisponibles.any { it.localizacion == form.localizacion }
        if (localizacionDuplicada) {
            binding.tilAdminMedidorLocalizacion.error = getString(R.string.admin_medidor_error_localizacion_existente)
            return
        }

        confirmarAccion(
            getString(R.string.admin_confirm_agregar_medidor_title),
            getString(R.string.admin_confirm_agregar_medidor_message, form.numero)
        ) {
            viewModel.crearMedidor(
                numero = form.numero,
                cliente = form.cliente,
                localizacion = form.localizacion,
                calle = form.calle,
                poste = form.poste,
                metros = form.metros,
                puebloCodigo = form.puebloCodigo,
                subregionId = form.subregionId
            )
        }
    }

    private fun eliminarMedidor() {
        val numero = binding.inputAdminMedidorNumero.text?.toString()?.trim().orEmpty()
        if (numero.isEmpty()) {
            binding.tilAdminMedidorNumero.error = getString(R.string.admin_medidor_error_numero)
            return
        }
        val existente = medidoresDisponibles.firstOrNull { it.medidorNumber.equals(numero, ignoreCase = true) }
        val subregionId = existente?.subregion
            ?: resolveSubregionId(binding.actvAdminMedidorSubregion.text?.toString())
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
        val form = obtenerVehiculoFormulario() ?: return

        val seleccionado = viewModel.vehiculoSeleccionado.value
        val existente = seleccionado ?: vehiculosDisponibles.firstOrNull { it.placa == form.placa }
        val id = existente?.id ?: run {
            binding.tilAdminVehiculoPlaca.error = getString(R.string.admin_vehiculo_error_no_existe)
            return
        }

        val placaEnUso = vehiculosDisponibles.any { it.placa == form.placa && it.id != id }
        if (placaEnUso) {
            binding.tilAdminVehiculoPlaca.error = getString(R.string.admin_vehiculo_error_placa_existente)
            return
        }

        confirmarAccion(
            getString(R.string.admin_confirm_guardar_vehiculo_title),
            getString(R.string.admin_confirm_guardar_vehiculo_message, form.placa.toString())
        ) {
            viewModel.actualizarVehiculo(
                id = id,
                placa = form.placa,
                agencia = form.agencia,
                tipo = form.tipo,
                subregionId = form.subregionId
            )
        }
    }

    private fun agregarVehiculo() {
        limpiarErroresVehiculo()
        val form = obtenerVehiculoFormulario() ?: return

        if (vehiculosDisponibles.any { it.placa == form.placa }) {
            binding.tilAdminVehiculoPlaca.error = getString(R.string.admin_vehiculo_error_placa_existente)
            return
        }

        confirmarAccion(
            getString(R.string.admin_confirm_agregar_vehiculo_title),
            getString(R.string.admin_confirm_agregar_vehiculo_message, form.placa.toString())
        ) {
            viewModel.crearVehiculo(
                placa = form.placa,
                agencia = form.agencia,
                tipo = form.tipo,
                subregionId = form.subregionId
            )
        }
    }

    private fun eliminarVehiculo() {
        val placaTexto = binding.inputAdminVehiculoPlaca.text?.toString()?.trim().orEmpty()
        val placa = placaTexto.toLongOrNull()
        val seleccionado = viewModel.vehiculoSeleccionado.value
        val existente = seleccionado ?: vehiculosDisponibles.firstOrNull { it.placa == placa }
        if (existente == null) {
            binding.tilAdminVehiculoPlaca.error = getString(R.string.admin_vehiculo_error_no_existe)
            return
        }

        confirmarAccion(
            getString(R.string.admin_confirm_eliminar_vehiculo_title),
            getString(
                R.string.admin_confirm_eliminar_vehiculo_message,
                placaTexto.ifEmpty { existente.id.toString() }
            )
        ) {
            viewModel.eliminarVehiculo(existente.id, placaTexto.ifEmpty { existente.placa.toString() })
        }
    }

    private fun guardarLocalizacion() {
        limpiarErroresLocalizacion()
        val seleccionada = viewModel.localizacionSeleccionada.value
        if (seleccionada == null) {
            binding.tilAdminLocalizacionCalle.error = getString(R.string.admin_localizacion_error_calle)
            return
        }

        val form = obtenerLocalizacionFormulario() ?: return

        val duplicada = localizacionesDisponibles.any {
            it.id != seleccionada.id &&
                it.pueblo == form.puebloId &&
                it.calle == form.calleId &&
                it.delPoste == form.delPoste &&
                it.alPoste == form.alPoste
        }
        if (duplicada) {
            binding.tilAdminLocalizacionCalle.error = getString(R.string.admin_localizacion_error_existente)
            return
        }

        confirmarAccion(
            getString(R.string.admin_confirm_guardar_localizacion_title),
            getString(
                R.string.admin_confirm_guardar_localizacion_message,
                seleccionada.id.toString()
            )
        ) {
            viewModel.actualizarLocalizacion(
                id = seleccionada.id,
                puebloId = form.puebloId,
                calleId = form.calleId,
                direccion = form.direccion,
                latitud = form.latitud,
                longitud = form.longitud,
                delPoste = form.delPoste,
                alPoste = form.alPoste,
                subregionId = form.subregionId
            )
        }
    }

    private fun agregarLocalizacion() {
        limpiarErroresLocalizacion()
        val form = obtenerLocalizacionFormulario() ?: return

        val duplicada = localizacionesDisponibles.any {
            it.pueblo == form.puebloId &&
                it.calle == form.calleId &&
                it.delPoste == form.delPoste &&
                it.alPoste == form.alPoste
        }
        if (duplicada) {
            binding.tilAdminLocalizacionCalle.error = getString(R.string.admin_localizacion_error_existente)
            return
        }

        confirmarAccion(
            getString(R.string.admin_confirm_agregar_localizacion_title),
            getString(
                R.string.admin_confirm_agregar_localizacion_message,
                formatPueblo(form.puebloId.toString())
            )
        ) {
            viewModel.crearLocalizacion(
                puebloId = form.puebloId,
                calleId = form.calleId,
                direccion = form.direccion,
                latitud = form.latitud,
                longitud = form.longitud,
                delPoste = form.delPoste,
                alPoste = form.alPoste,
                subregionId = form.subregionId
            )
        }
    }

    private fun eliminarLocalizacion() {
        val seleccionada = viewModel.localizacionSeleccionada.value
            ?: run {
                val display = binding.actvAdminLocalizacionCalle.text?.toString()?.trim()
                val id = localizacionDisplayToId[display]
                id?.let { localizacionesDisponibles.firstOrNull { loc -> loc.id == it } }
            }
        if (seleccionada == null) {
            binding.tilAdminLocalizacionCalle.error = getString(R.string.admin_localizacion_error_calle)
            return
        }

        confirmarAccion(
            getString(R.string.admin_confirm_eliminar_localizacion_title),
            getString(R.string.admin_confirm_eliminar_localizacion_message, seleccionada.id.toString())
        ) {
            viewModel.eliminarLocalizacion(seleccionada.id)
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
        actualizarEstadoBotonesMedidor()
    }

    private fun limpiarFormularioVehiculo() {
        limpiarErroresVehiculo()
        binding.actvAdminVehiculoBuscar.setText("", false)
        binding.inputAdminVehiculoPlaca.setText("")
        binding.actvAdminVehiculoAgencia.setText("", false)
        binding.inputAdminVehiculoTipo.setText("")
        binding.actvAdminVehiculoSubregion.setText("", false)
        actualizarEstadoBotonesVehiculo()
    }

    private fun limpiarFormularioLocalizacion() {
        limpiarErroresLocalizacion()
        binding.actvAdminLocalizacionSubregion.setText("", false)
        binding.actvAdminLocalizacionPueblo.setText("", false)
        binding.actvAdminLocalizacionCalle.setText("", false)
        binding.inputAdminLocalizacionDireccion.setText("")
        binding.inputAdminLocalizacionLatitud.setText("")
        binding.inputAdminLocalizacionLongitud.setText("")
        binding.inputAdminLocalizacionDelPoste.setText("")
        binding.inputAdminLocalizacionAlPoste.setText("")
        actualizarEstadoBotonesLocalizacion()
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
        binding.tilAdminVehiculoPlaca.error = null
        binding.tilAdminVehiculoAgencia.error = null
        binding.tilAdminVehiculoTipo.error = null
        binding.tilAdminVehiculoSubregion.error = null
    }

    private fun limpiarErroresLocalizacion() {
        binding.tilAdminLocalizacionSubregion.error = null
        binding.tilAdminLocalizacionPueblo.error = null
        binding.tilAdminLocalizacionCalle.error = null
        binding.tilAdminLocalizacionDireccion.error = null
        binding.tilAdminLocalizacionLatitud.error = null
        binding.tilAdminLocalizacionLongitud.error = null
        binding.tilAdminLocalizacionDelPoste.error = null
        binding.tilAdminLocalizacionAlPoste.error = null
    }

    private fun actualizarEstadoBotonesMedidor() {
        val numero = binding.inputAdminMedidorNumero.text?.toString()?.trim().orEmpty()
        val existe = numero.isNotEmpty() && medidoresDisponibles.any { it.medidorNumber.equals(numero, ignoreCase = true) }
        binding.btnAdminMedidorAgregar.isVisible = numero.isNotEmpty() && !existe
        binding.btnAdminMedidorGuardar.isVisible = existe
        binding.btnAdminMedidorEliminar.isVisible = existe
    }

    private fun actualizarEstadoBotonesVehiculo() {
        val placaTexto = binding.inputAdminVehiculoPlaca.text?.toString()?.trim().orEmpty()
        val placa = placaTexto.toLongOrNull()
        val existe = placa != null && vehiculosDisponibles.any { it.placa == placa }
        binding.btnAdminVehiculoAgregar.isVisible = placa != null && !existe
        binding.btnAdminVehiculoGuardar.isVisible = existe
        binding.btnAdminVehiculoEliminar.isVisible = existe
    }

    private fun actualizarEstadoBotonesLocalizacion() {
        val seleccion = viewModel.localizacionSeleccionada.value
        val haySeleccion = seleccion != null
        binding.btnAdminLocalizacionAgregar.isVisible = !haySeleccion
        binding.btnAdminLocalizacionGuardar.isVisible = haySeleccion
        binding.btnAdminLocalizacionEliminar.isVisible = haySeleccion
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

    private fun obtenerMedidorFormulario(): MedidorForm? {
        val numero = binding.inputAdminMedidorNumero.text?.toString().orEmpty().trim()
        if (numero.isEmpty()) {
            binding.tilAdminMedidorNumero.error = getString(R.string.admin_medidor_error_numero)
            return null
        }

        val cliente = binding.inputAdminMedidorCliente.text?.toString()?.trim().orEmpty()
        if (cliente.isEmpty()) {
            binding.tilAdminMedidorCliente.error = getString(R.string.admin_medidor_error_cliente)
            return null
        }

        val subregionId = resolveSubregionId(binding.actvAdminMedidorSubregion.text?.toString())
            ?.takeIf { it.isNotEmpty() }
            ?: run {
                binding.tilAdminMedidorSubregion.error = getString(R.string.admin_medidor_error_subregion)
                return null
            }

        val puebloCodigo = resolvePuebloCode(binding.actvAdminMedidorPueblo.text?.toString())
            ?: run {
                binding.tilAdminMedidorPueblo.error = getString(R.string.admin_medidor_error_pueblo)
                return null
            }

        val calle = binding.inputAdminMedidorCalle.text?.toString()?.trim().orEmpty()
        if (calle.isEmpty()) {
            binding.tilAdminMedidorCalle.error = getString(R.string.admin_medidor_error_calle)
            return null
        }

        val poste = binding.inputAdminMedidorPoste.text?.toString()?.trim().orEmpty()
        if (poste.isEmpty()) {
            binding.tilAdminMedidorPoste.error = getString(R.string.admin_medidor_error_poste)
            return null
        }

        val metros = binding.inputAdminMedidorMetros.text?.toString()?.trim().orEmpty()
        if (metros.isEmpty()) {
            binding.tilAdminMedidorMetros.error = getString(R.string.admin_medidor_error_metros)
            return null
        }

        val localizacion = generarLocalizacion(puebloCodigo, calle, poste, metros)
        if (localizacion == null) {
            binding.tilAdminMedidorLocalizacion.error = getString(R.string.admin_medidor_error_localizacion)
            return null
        }
        binding.inputAdminMedidorLocalizacion.setText(localizacion.toString())

        return MedidorForm(
            numero = numero,
            cliente = cliente,
            calle = calle,
            poste = poste,
            metros = metros,
            puebloCodigo = puebloCodigo,
            subregionId = subregionId,
            localizacion = localizacion
        )
    }

    private fun obtenerVehiculoFormulario(): VehiculoForm? {
        val subregionId = resolveSubregionId(binding.actvAdminVehiculoSubregion.text?.toString())
            ?.takeIf { it.isNotEmpty() }
            ?: run {
                binding.tilAdminVehiculoSubregion.error = getString(R.string.admin_vehiculo_error_subregion)
                return null
            }

        val agencia = resolveAgenciaNombre(binding.actvAdminVehiculoAgencia.text?.toString())?.trim().orEmpty()
        if (agencia.isEmpty()) {
            binding.tilAdminVehiculoAgencia.error = getString(R.string.admin_vehiculo_error_agencia)
            return null
        }

        val placaTexto = binding.inputAdminVehiculoPlaca.text?.toString()?.trim().orEmpty()
        val placa = placaTexto.toLongOrNull()
        if (placa == null) {
            binding.tilAdminVehiculoPlaca.error = getString(R.string.admin_vehiculo_error_placa)
            return null
        }

        val tipo = binding.inputAdminVehiculoTipo.text?.toString()?.trim().orEmpty()
        if (tipo.isEmpty()) {
            binding.tilAdminVehiculoTipo.error = getString(R.string.admin_vehiculo_error_tipo)
            return null
        }

        return VehiculoForm(
            placa = placa,
            agencia = agencia,
            tipo = tipo,
            subregionId = subregionId
        )
    }

    private fun obtenerLocalizacionFormulario(): LocalizacionForm? {
        val subregionId = resolveSubregionId(binding.actvAdminLocalizacionSubregion.text?.toString())
            ?.takeIf { it.isNotEmpty() }
            ?: run {
                binding.tilAdminLocalizacionSubregion.error = getString(R.string.admin_localizacion_error_subregion)
                return null
            }

        val puebloId = resolvePuebloId(binding.actvAdminLocalizacionPueblo.text?.toString())
        val puebloEntidad = puebloId?.let { idPueblo -> pueblosDisponibles.firstOrNull { it.id == idPueblo } }
        if (puebloId == null || puebloEntidad == null) {
            binding.tilAdminLocalizacionPueblo.error = getString(R.string.admin_localizacion_error_pueblo)
            return null
        }
        if (!puebloEntidad.subregion_id_normalizado.equals(subregionId, ignoreCase = true)) {
            binding.tilAdminLocalizacionPueblo.error = getString(R.string.admin_localizacion_error_relacion)
            return null
        }

        val calleTexto = binding.actvAdminLocalizacionCalle.text?.toString()?.trim().orEmpty()
        val calle = calleTexto.filter(Char::isDigit).toIntOrNull()
        if (calle == null) {
            binding.tilAdminLocalizacionCalle.error = getString(R.string.admin_localizacion_error_calle)
            return null
        }

        val direccion = binding.inputAdminLocalizacionDireccion.text?.toString()?.trim()
        if (direccion.isNullOrEmpty()) {
            binding.tilAdminLocalizacionDireccion.error = getString(R.string.admin_localizacion_error_direccion)
            return null
        }

        val latitud = binding.inputAdminLocalizacionLatitud.text?.toString()?.trim()?.toDoubleOrNull()
        if (latitud == null) {
            binding.tilAdminLocalizacionLatitud.error = getString(R.string.admin_localizacion_error_latitud)
            return null
        }

        val longitud = binding.inputAdminLocalizacionLongitud.text?.toString()?.trim()?.toDoubleOrNull()
        if (longitud == null) {
            binding.tilAdminLocalizacionLongitud.error = getString(R.string.admin_localizacion_error_longitud)
            return null
        }

        val delPoste = binding.inputAdminLocalizacionDelPoste.text?.toString()?.trim()?.toIntOrNull()
        if (delPoste == null) {
            binding.tilAdminLocalizacionDelPoste.error = getString(R.string.admin_localizacion_error_del_poste)
            return null
        }

        val alPoste = binding.inputAdminLocalizacionAlPoste.text?.toString()?.trim()?.toIntOrNull()
        if (alPoste == null) {
            binding.tilAdminLocalizacionAlPoste.error = getString(R.string.admin_localizacion_error_al_poste)
            return null
        }

        return LocalizacionForm(
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

    private fun resolveSubregionNombre(display: String?): String? {
        val raw = display?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val candidate = raw.split(" - ").getOrNull(1)?.trim().takeUnless { it.isNullOrEmpty() }
            ?: raw
        val matchByName = subregionesDisponibles.firstOrNull { it.nombre.equals(candidate, ignoreCase = true) }
        if (matchByName != null) return matchByName.nombre
        val matchById = subregionesDisponibles.firstOrNull { it.id.equals(candidate, ignoreCase = true) }
        return matchById?.nombre ?: candidate
    }

    private fun formatPueblo(codigo: String?): String {
        if (codigo.isNullOrBlank()) return ""
        val match = pueblosDisponibles.firstOrNull { it.id.toString() == codigo.trim() }
        return match?.let { "${it.id} - ${it.nombre}" } ?: codigo
    }

    private fun formatSubregion(codigo: String?): String {
        if (codigo.isNullOrBlank()) return ""
        val matchById = subregionesDisponibles.firstOrNull { it.id.equals(codigo, ignoreCase = true) }
        val matchByName = subregionesDisponibles.firstOrNull { it.nombre.equals(codigo, ignoreCase = true) }
        return matchById?.nombre ?: matchByName?.nombre ?: codigo
    }

    private fun parseVehiculoId(text: String?): Int? {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val candidate = raw.split(" - ").firstOrNull()?.trim().takeUnless { it.isNullOrEmpty() }
            ?: raw
        return candidate.toIntOrNull()
    }

    private data class MedidorForm(
        val numero: String,
        val cliente: String,
        val calle: String,
        val poste: String,
        val metros: String,
        val puebloCodigo: String,
        val subregionId: String,
        val localizacion: Long,
    )

    private data class VehiculoForm(
        val placa: Long,
        val agencia: String,
        val tipo: String,
        val subregionId: String,
    )

    private data class LocalizacionForm(
        val puebloId: Int,
        val calleId: Int,
        val direccion: String,
        val latitud: Double,
        val longitud: Double,
        val delPoste: Int,
        val alPoste: Int,
        val subregionId: String,
    )

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
