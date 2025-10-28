package com.Arasoftsolutions.tecniapp_ice.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AgenciaEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.LocalizacionesEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.MedidorEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.SubregionesEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculosEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdminManagementViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RoomRepository.getInstance(application)

    val medidores: StateFlow<List<MedidorEntity>> = repository.observarTodosLosMedidores()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val vehiculos: StateFlow<List<VehiculosEntity>> = repository.observarVehiculosCatalogo()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val localizaciones: StateFlow<List<LocalizacionesEntity>> = repository.observarTodasLasLocalizaciones()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pueblos = repository.observarTodosLosPueblos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val subregiones: StateFlow<List<SubregionesEntity>> = repository.observarSubregiones()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val agencias: StateFlow<List<AgenciaEntity>> = repository.observarAgenciasCatalogo()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _medidorSeleccionado = MutableStateFlow<MedidorEntity?>(null)
    val medidorSeleccionado: StateFlow<MedidorEntity?> = _medidorSeleccionado.asStateFlow()

    private val _vehiculoSeleccionado = MutableStateFlow<VehiculosEntity?>(null)
    val vehiculoSeleccionado: StateFlow<VehiculosEntity?> = _vehiculoSeleccionado.asStateFlow()

    private val _localizacionSeleccionada = MutableStateFlow<LocalizacionesEntity?>(null)
    val localizacionSeleccionada: StateFlow<LocalizacionesEntity?> = _localizacionSeleccionada.asStateFlow()

    private val _eventos = MutableSharedFlow<AdminEvent>()
    val eventos = _eventos.asSharedFlow()

    fun seleccionarMedidor(numero: String) {
        viewModelScope.launch {
            val trimmed = numero.trim()
            if (trimmed.isEmpty()) {
                _medidorSeleccionado.value = null
                return@launch
            }
            val medidor = repository.buscarMedidorPorNumero(trimmed)
            _medidorSeleccionado.value = medidor
            if (medidor == null) {
                _eventos.emit(AdminEvent.Error(texto(R.string.admin_medidor_no_encontrado)))
            }
        }
    }

    fun limpiarMedidor() {
        _medidorSeleccionado.value = null
    }

    fun crearMedidor(
        numero: String,
        cliente: String,
        localizacion: Long,
        calle: String,
        poste: String,
        metros: String,
        puebloCodigo: String,
        subregionId: String,
    ) = procesarMedidor(numero, cliente, localizacion, calle, poste, metros, puebloCodigo, subregionId, esNuevo = true)

    fun actualizarMedidor(
        numero: String,
        cliente: String,
        localizacion: Long,
        calle: String,
        poste: String,
        metros: String,
        puebloCodigo: String,
        subregionId: String,
    ) = procesarMedidor(numero, cliente, localizacion, calle, poste, metros, puebloCodigo, subregionId, esNuevo = false)

    private fun procesarMedidor(
        numero: String,
        cliente: String,
        localizacion: Long,
        calle: String,
        poste: String,
        metros: String,
        puebloCodigo: String,
        subregionId: String,
        esNuevo: Boolean,
    ) {
        viewModelScope.launch {
            val numeroLimpio = numero.trim()
            if (numeroLimpio.isEmpty()) {
                _eventos.emit(AdminEvent.Error(texto(R.string.admin_medidor_error_numero)))
                return@launch
            }

            val subregionLimpia = subregionId.trim().takeIf { it.isNotEmpty() }
                ?: run {
                    _eventos.emit(AdminEvent.Error(texto(R.string.admin_medidor_error_subregion)))
                    return@launch
                }

            val subregionCatalogo = subregiones.value.firstOrNull {
                it.id.equals(subregionLimpia, ignoreCase = true)
            } ?: run {
                _eventos.emit(AdminEvent.Error(texto(R.string.admin_validacion_subregion_inexistente)))
                return@launch
            }

            val puebloLimpio = puebloCodigo.trim().takeIf { it.isNotEmpty() }
                ?: run {
                    _eventos.emit(AdminEvent.Error(texto(R.string.admin_medidor_error_pueblo)))
                    return@launch
                }

            if (pueblos.value.none { it.id.toString() == puebloLimpio }) {
                _eventos.emit(AdminEvent.Error(texto(R.string.admin_medidor_error_pueblo)))
                return@launch
            }

            val existente = repository.buscarMedidorPorNumero(numeroLimpio)
            val medidoresActuales = medidores.value

            if (esNuevo && existente != null) {
                _eventos.emit(AdminEvent.Error(texto(R.string.admin_medidor_error_existente)))
                return@launch
            }

            if (!esNuevo && existente == null) {
                _eventos.emit(AdminEvent.Error(texto(R.string.admin_medidor_error_no_existe)))
                return@launch
            }

            val localizacionDuplicada = medidoresActuales.any {
                it.medidorNumber != numeroLimpio && it.localizacion == localizacion
            }
            if (localizacionDuplicada) {
                _eventos.emit(AdminEvent.Error(texto(R.string.admin_medidor_error_localizacion_existente)))
                return@launch
            }

            val medidor = MedidorEntity(
                medidorNumber = numeroLimpio,
                cliente = cliente.trim(),
                localizacion = localizacion,
                calle = calle.trim(),
                poste = poste.trim(),
                metros = metros.trim(),
                pueblo = puebloLimpio,
                subregion = subregionLimpia
            )

            try {
                repository.guardarMedidor(subregionLimpia, subregionCatalogo.nombre, medidor)
                _medidorSeleccionado.value = medidor
                _eventos.emit(AdminEvent.Success(texto(R.string.admin_medidor_guardar_exito, numeroLimpio)))
            } catch (t: Throwable) {
                _eventos.emit(AdminEvent.Error(errorMensaje(t)))
            }
        }
    }

    fun eliminarMedidor(numero: String, subregionId: String) {
        viewModelScope.launch {
            val numeroLimpio = numero.trim()
            if (numeroLimpio.isEmpty()) {
                _eventos.emit(AdminEvent.Error(texto(R.string.admin_medidor_error_numero)))
                return@launch
            }
            val subregionLimpia = subregionId.trim()
            if (subregionLimpia.isEmpty()) {
                _eventos.emit(AdminEvent.Error(texto(R.string.admin_medidor_error_subregion)))
                return@launch
            }

            val subregionCatalogo = subregiones.value.firstOrNull {
                it.id.equals(subregionLimpia, ignoreCase = true)
            } ?: run {
                _eventos.emit(AdminEvent.Error(texto(R.string.admin_validacion_subregion_inexistente)))
                return@launch
            }

            try {
                repository.eliminarMedidor(subregionLimpia, subregionCatalogo.nombre, numeroLimpio)
                _medidorSeleccionado.value = null
                _eventos.emit(AdminEvent.Success(texto(R.string.admin_medidor_eliminar_exito, numeroLimpio)))
            } catch (t: Throwable) {
                _eventos.emit(AdminEvent.Error(errorMensaje(t)))
            }
        }
    }

    fun seleccionarVehiculoPorId(id: Int?) {
        viewModelScope.launch {
            if (id == null) {
                _vehiculoSeleccionado.value = null
                return@launch
            }
            val vehiculo = repository.obtenerVehiculoPorId(id)
            if (vehiculo == null) {
                _vehiculoSeleccionado.value = null
                _eventos.emit(AdminEvent.Error(texto(R.string.admin_vehiculo_no_encontrado)))
            } else {
                _vehiculoSeleccionado.value = vehiculo
            }
        }
    }

    fun seleccionarVehiculoPorPlaca(placa: Long?) {
        viewModelScope.launch {
            if (placa == null) {
                _vehiculoSeleccionado.value = null
                return@launch
            }
            val vehiculo = repository.obtenerVehiculoPorPlaca(placa)
            if (vehiculo == null) {
                _vehiculoSeleccionado.value = null
                _eventos.emit(AdminEvent.Error(texto(R.string.admin_vehiculo_no_encontrado)))
            } else {
                _vehiculoSeleccionado.value = vehiculo
            }
        }
    }

    fun limpiarVehiculo() {
        _vehiculoSeleccionado.value = null
    }

    fun crearVehiculo(
        placa: Long,
        agencia: String,
        tipo: String,
        subregionId: String,
    ) {
        viewModelScope.launch {
            val subregionLimpia = subregionId.trim().takeIf { it.isNotEmpty() }
                ?: run {
                    _eventos.emit(AdminEvent.Error(texto(R.string.admin_vehiculo_error_subregion)))
                    return@launch
                }
            if (subregiones.value.none { it.id.equals(subregionLimpia, ignoreCase = true) }) {
                _eventos.emit(AdminEvent.Error(texto(R.string.admin_validacion_subregion_inexistente)))
                return@launch
            }

            val agenciaLimpia = agencia.trim()
            if (agenciaLimpia.isEmpty()) {
                _eventos.emit(AdminEvent.Error(texto(R.string.admin_vehiculo_error_agencia)))
                return@launch
            }

            val existente = repository.obtenerVehiculoPorPlaca(placa)
            if (existente != null) {
                _eventos.emit(AdminEvent.Error(texto(R.string.admin_vehiculo_error_placa_existente)))
                return@launch
            }

            val nuevoId = (vehiculos.value.maxOfOrNull { it.id } ?: 0) + 1
            val vehiculo = VehiculosEntity(
                id = nuevoId,
                agencia = agenciaLimpia,
                placa = placa,
                tipo = tipo.trim(),
                subregion = subregionLimpia
            )

            try {
                repository.guardarVehiculo(vehiculo)
                _vehiculoSeleccionado.value = vehiculo
                _eventos.emit(AdminEvent.Success(texto(R.string.admin_vehiculo_guardar_exito, placa.toString())))
            } catch (t: Throwable) {
                _eventos.emit(AdminEvent.Error(errorMensaje(t)))
            }
        }
    }

    fun actualizarVehiculo(
        id: Int,
        placa: Long,
        agencia: String,
        tipo: String,
        subregionId: String,
    ) {
        viewModelScope.launch {
            val subregionLimpia = subregionId.trim().takeIf { it.isNotEmpty() }
                ?: run {
                    _eventos.emit(AdminEvent.Error(texto(R.string.admin_vehiculo_error_subregion)))
                    return@launch
                }
            if (subregiones.value.none { it.id.equals(subregionLimpia, ignoreCase = true) }) {
                _eventos.emit(AdminEvent.Error(texto(R.string.admin_validacion_subregion_inexistente)))
                return@launch
            }

            val agenciaLimpia = agencia.trim()
            if (agenciaLimpia.isEmpty()) {
                _eventos.emit(AdminEvent.Error(texto(R.string.admin_vehiculo_error_agencia)))
                return@launch
            }

            val existente = repository.obtenerVehiculoPorId(id)
                ?: run {
                    _eventos.emit(AdminEvent.Error(texto(R.string.admin_vehiculo_error_no_existe)))
                    return@launch
                }

            val otroConPlaca = repository.obtenerVehiculoPorPlaca(placa)
            if (otroConPlaca != null && otroConPlaca.id != id) {
                _eventos.emit(AdminEvent.Error(texto(R.string.admin_vehiculo_error_placa_existente)))
                return@launch
            }

            val vehiculo = existente.copy(
                agencia = agenciaLimpia,
                placa = placa,
                tipo = tipo.trim(),
                subregion = subregionLimpia
            )

            try {
                repository.guardarVehiculo(vehiculo)
                _vehiculoSeleccionado.value = vehiculo
                _eventos.emit(AdminEvent.Success(texto(R.string.admin_vehiculo_guardar_exito, placa.toString())))
            } catch (t: Throwable) {
                _eventos.emit(AdminEvent.Error(errorMensaje(t)))
            }
        }
    }

    fun eliminarVehiculo(id: Int, etiqueta: String?) {
        viewModelScope.launch {
            try {
                repository.eliminarVehiculo(id)
                _vehiculoSeleccionado.value = null
                val label = etiqueta?.takeIf { it.isNotBlank() } ?: id.toString()
                _eventos.emit(AdminEvent.Success(texto(R.string.admin_vehiculo_eliminar_exito, label)))
            } catch (t: Throwable) {
                _eventos.emit(AdminEvent.Error(errorMensaje(t)))
            }
        }
    }

    fun seleccionarLocalizacion(id: Int?) {
        viewModelScope.launch {
            if (id == null) {
                _localizacionSeleccionada.value = null
                return@launch
            }
            val entidad = repository.obtenerLocalizacionPorId(id.toLong())
            if (entidad == null) {
                _localizacionSeleccionada.value = null
                _eventos.emit(AdminEvent.Error(texto(R.string.admin_localizacion_no_encontrada)))
            } else {
                _localizacionSeleccionada.value = entidad
            }
        }
    }

    fun limpiarLocalizacion() {
        _localizacionSeleccionada.value = null
    }

    fun crearLocalizacion(
        puebloId: Int,
        calleId: Int,
        direccion: String,
        latitud: Double,
        longitud: Double,
        delPoste: Int,
        alPoste: Int,
        subregionId: String,
    ) {
        procesarLocalizacion(null, puebloId, calleId, direccion, latitud, longitud, delPoste, alPoste, subregionId)
    }

    fun actualizarLocalizacion(
        id: Int,
        puebloId: Int,
        calleId: Int,
        direccion: String,
        latitud: Double,
        longitud: Double,
        delPoste: Int,
        alPoste: Int,
        subregionId: String,
    ) {
        procesarLocalizacion(id, puebloId, calleId, direccion, latitud, longitud, delPoste, alPoste, subregionId)
    }

    private fun procesarLocalizacion(
        id: Int?,
        puebloId: Int,
        calleId: Int,
        direccion: String,
        latitud: Double,
        longitud: Double,
        delPoste: Int,
        alPoste: Int,
        subregionId: String,
    ) {
        viewModelScope.launch {
            val subregionLimpia = subregionId.trim().takeIf { it.isNotEmpty() }
                ?: run {
                    _eventos.emit(AdminEvent.Error(texto(R.string.admin_localizacion_error_subregion)))
                    return@launch
                }
            if (subregiones.value.none { it.id.equals(subregionLimpia, ignoreCase = true) }) {
                _eventos.emit(AdminEvent.Error(texto(R.string.admin_validacion_subregion_inexistente)))
                return@launch
            }

            val puebloEntidad = pueblos.value.firstOrNull { it.id == puebloId }
                ?: run {
                    _eventos.emit(AdminEvent.Error(texto(R.string.admin_localizacion_error_pueblo)))
                    return@launch
                }
            if (!puebloEntidad.subregion_id_normalizado.equals(subregionLimpia, ignoreCase = true)) {
                _eventos.emit(AdminEvent.Error(texto(R.string.admin_localizacion_error_relacion)))
                return@launch
            }

            val direccionVal = direccion.trim()
            if (direccionVal.isEmpty()) {
                _eventos.emit(AdminEvent.Error(texto(R.string.admin_localizacion_error_direccion)))
                return@launch
            }

            val existeDuplicado = localizaciones.value.any {
                (id == null || it.id != id) &&
                    it.pueblo == puebloId &&
                    it.calle == calleId &&
                    it.delPoste == delPoste &&
                    it.alPoste == alPoste
            }
            if (existeDuplicado) {
                _eventos.emit(AdminEvent.Error(texto(R.string.admin_localizacion_error_existente)))
                return@launch
            }

            val identificador = id ?: (localizaciones.value.maxOfOrNull { it.id } ?: 0) + 1

            val entity = LocalizacionesEntity(
                id = identificador,
                pueblo = puebloId,
                calle = calleId,
                direccion = direccionVal,
                latitud = latitud,
                longitud = longitud,
                delPoste = delPoste,
                alPoste = alPoste,
                subregion = subregionLimpia
            )

            try {
                repository.guardarLocalizacion(entity)
                _localizacionSeleccionada.value = entity
                _eventos.emit(AdminEvent.Success(texto(R.string.admin_localizacion_guardar_exito, identificador.toString())))
            } catch (t: Throwable) {
                _eventos.emit(AdminEvent.Error(errorMensaje(t)))
            }
        }
    }

    fun eliminarLocalizacion(id: Int) {
        viewModelScope.launch {
            try {
                repository.eliminarLocalizacion(id)
                _localizacionSeleccionada.value = null
                _eventos.emit(AdminEvent.Success(texto(R.string.admin_localizacion_eliminar_exito, id.toString())))
            } catch (t: Throwable) {
                _eventos.emit(AdminEvent.Error(errorMensaje(t)))
            }
        }
    }

    private fun texto(resId: Int, vararg args: Any?): String =
        getApplication<Application>().getString(resId, *args)

    private fun errorMensaje(t: Throwable): String {
        val detalle = t.localizedMessage?.takeIf { it.isNotBlank() } ?: texto(R.string.admin_error_desconocido)
        return texto(R.string.admin_accion_error_general, detalle)
    }
}

sealed class AdminEvent {
    data class Success(val message: String) : AdminEvent()
    data class Error(val message: String) : AdminEvent()
}
