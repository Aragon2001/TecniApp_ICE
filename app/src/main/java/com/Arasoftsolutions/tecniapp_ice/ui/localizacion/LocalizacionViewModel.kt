package com.Arasoftsolutions.tecniapp_ice.ui.localizacion

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.Arasoftsolutions.tecniapp_ice.Database.entities.LocalizacionesEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.Database.sync.SubregionNormalizer
import com.Arasoftsolutions.tecniapp_ice.R
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocalizacionViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = RoomRepository.getInstance(app)
    private val auth = FirebaseAuth.getInstance()

    private val puebloPlaceholder = app.getString(R.string.localizacion_select_pueblo)
    private val callePlaceholder = app.getString(R.string.localizacion_select_calle)

    private val _pueblos = MutableLiveData<List<String>>()
    val pueblos: LiveData<List<String>> = _pueblos

    private val _calles = MutableLiveData<List<String>>()
    val calles: LiveData<List<String>> = _calles

    private val _estado = MutableLiveData<Estado>()
    val estado: LiveData<Estado> = _estado

    private val _localizacion = MutableLiveData<Localizacion?>()
    val localizacion: LiveData<Localizacion?> = _localizacion

    private val _marcadoresCalles = MutableLiveData<List<MarcadorCalle>>(emptyList())
    val marcadoresCalles: LiveData<List<MarcadorCalle>> = _marcadoresCalles

    private val _mostrarTodasCalles = MutableLiveData(false)
    val mostrarTodasCalles: LiveData<Boolean> = _mostrarTodasCalles

    private var subregionActual: String? = null
    private var initialized = false
    private var pueblosJob: Job? = null
    private var sincronizando = false
    private var intentoSyncRealizado = false
    private var cacheCallesActuales: List<LocalizacionesEntity> = emptyList()
    private var puebloSeleccionadoActual: Int? = null
    private val preferencias = app.getSharedPreferences("localizacion_prefs", Context.MODE_PRIVATE)
    private var preferenciaMostrarCallesClave: String? = null

    fun prepararDatos() {
        if (initialized) return
        initialized = true
        _estado.value = Estado.Cargando
        viewModelScope.launch { cargarContexto() }
    }

    private suspend fun cargarContexto() {
        val context = getApplication<Application>()
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            _estado.postValue(Estado.Error(context.getString(R.string.medidor_estado_requiere_sesion)))
            return
        }

        try {
            val subregionCanonica = withContext(Dispatchers.IO) {
                val local = repository.obtenerUsuario(uid)
                val ensured = local ?: runCatching { repository.upsertUserFromFirebase(uid) }
                    .onFailure { throwable ->
                        Log.e("LocalizacionViewModel", "Error obteniendo usuario desde Firebase", throwable)
                    }
                    .getOrNull()
                ensured?.subregion?.trim()?.takeIf { it.isNotEmpty() }
            }?.let { SubregionNormalizer.canonicalIdOrSelf(it) }

            if (subregionCanonica.isNullOrBlank()) {
                subregionActual = null
                _pueblos.postValue(listOf(puebloPlaceholder))
                _estado.postValue(
                    Estado.Error(
                        context.getString(R.string.localizacion_estado_sin_subregion)
                    )
                )
                return
            }

            subregionActual = subregionCanonica
            preferenciaMostrarCallesClave = "mostrar_todas_calles_" + uid
            val preferencia = preferencias.getBoolean(preferenciaMostrarCallesClave, false)
            _mostrarTodasCalles.postValue(preferencia)

            _estado.postValue(Estado.Cargando)
            observarPueblos(subregionCanonica)
        } catch (t: Throwable) {
            _estado.postValue(Estado.Error(context.getString(R.string.localizacion_estado_error_generico)))
        }
    }

    private fun observarPueblos(subregion: String) {
        pueblosJob?.cancel()
        intentoSyncRealizado = false
        pueblosJob = viewModelScope.launch {
            repository.observarPueblos(subregion).collectLatest { lista ->
                if (lista.isEmpty()) {
                    if (!intentoSyncRealizado) {
                        intentoSyncRealizado = true
                        sincronizarSubregion(subregion)
                        return@collectLatest
                    }
                    _pueblos.postValue(listOf(puebloPlaceholder))
                    _estado.postValue(
                        Estado.Error(
                            getApplication<Application>().getString(R.string.localizacion_estado_sin_pueblos)
                        )
                    )
                } else {
                    intentoSyncRealizado = false
                    val pueblosOrdenados = lista
                        .sortedBy { it.id }
                        .map { entidad -> "${entidad.id} - ${entidad.nombre}" }
                    _pueblos.postValue(listOf(puebloPlaceholder) + pueblosOrdenados)
                    _estado.postValue(Estado.Exito)
                }
            }
        }
    }

    fun cargarCallesParaPueblo(pueblo: Int) {
        val subregion = subregionActual
        if (subregion.isNullOrBlank()) {
            _estado.value = Estado.Error(
                getApplication<Application>().getString(R.string.localizacion_estado_error_generico)
            )
            return
        }

        viewModelScope.launch {
            _estado.value = Estado.Cargando
            puebloSeleccionadoActual = pueblo
            var calles = withContext(Dispatchers.IO) {
                repository.obtenerCallesPorPueblo(pueblo)
            }

            if (calles.isEmpty()) {
                sincronizarSubregion(subregion)
                calles = withContext(Dispatchers.IO) {
                    repository.obtenerCallesPorPueblo(pueblo)
                }
            }

            cacheCallesActuales = calles
            actualizarMarcadoresParaSeleccion()

            if (calles.isEmpty()) {
                _calles.value = listOf(callePlaceholder)
                _estado.value = Estado.Error(
                    getApplication<Application>().getString(R.string.localizacion_estado_sin_calles)
                )
            } else {
                val opciones = mutableListOf(callePlaceholder)
                opciones += calles
                    .sortedBy { it.calle }
                    .distinctBy { it.calle to it.direccion }
                    .map { "${it.calle} - ${it.direccion}" }
                _calles.value = opciones
                _estado.value = Estado.Exito
            }
        }
    }

    fun cargarLocalizacionParaCalle(calle: Int, codigoPueblo: Int, direccion: String?) {
        viewModelScope.launch {
            _estado.value = Estado.Cargando
            var entidad = withContext(Dispatchers.IO) {
                repository.buscarLocalizacion(codigoPueblo, calle, direccion)
            }

            if (entidad == null) {
                val subregion = subregionActual
                if (!subregion.isNullOrBlank()) {
                    sincronizarSubregion(subregion)
                    entidad = withContext(Dispatchers.IO) {
                        repository.buscarLocalizacion(codigoPueblo, calle, direccion)
                    }
                }
            }

            if (entidad == null) {
                _localizacion.value = null
                _estado.value = Estado.Error(
                    getApplication<Application>().getString(R.string.localizacion_estado_sin_localizacion)
                )
            } else {
                _localizacion.value = Localizacion(
                    direccion = entidad.direccion.ifBlank {
                        getApplication<Application>().getString(R.string.profile_summary_placeholder)
                    },
                    latitud = entidad.latitud,
                    longitud = entidad.longitud,
                    delPoste = entidad.delPoste,
                    alPoste = entidad.alPoste,
                    calleValor = entidad.calle
                )
                _estado.value = Estado.Exito
            }
        }
    }

    fun actualizarPreferenciaMostrarCalles(mostrar: Boolean) {
        _mostrarTodasCalles.value = mostrar
        preferenciaMostrarCallesClave?.let { clave ->
            preferencias.edit().putBoolean(clave, mostrar).apply()
        }

        if (mostrar) {
            actualizarMarcadoresParaSeleccion()
        } else {
            _marcadoresCalles.value = emptyList()
        }
    }

    private fun actualizarMarcadoresParaSeleccion() {
        if (_mostrarTodasCalles.value != true) {
            _marcadoresCalles.value = emptyList()
            return
        }

        val seleccion = puebloSeleccionadoActual ?: run {
            _marcadoresCalles.value = emptyList()
            _estado.value = Estado.Error(
                getApplication<Application>().getString(R.string.localizacion_calles_toast_sin_pueblo)
            )
            return
        }

        val marcadores = cacheCallesActuales
            .filter { it.latitud != 0.0 || it.longitud != 0.0 }
            .filter { it.pueblo == seleccion }
            .distinctBy { it.calle to it.direccion }
            .map { entidad ->
                MarcadorCalle(
                    latitud = entidad.latitud,
                    longitud = entidad.longitud,
                    titulo = "${entidad.calle} - ${entidad.direccion}",
                    snippet = buildString {
                        val codigo = buildString {
                            append(entidad.pueblo.toString().padStart(4, '0'))
                            append("-")
                            append(entidad.calle.toString().padStart(3, '0'))
                            append("-")
                            append(entidad.delPoste.toString().padStart(3, '0'))
                            append("-00")
                        }
                        append(codigo)
                        append("")
                        val postes = if (entidad.alPoste != 0 && entidad.alPoste != entidad.delPoste) {
                            getApplication<Application>().getString(
                                R.string.localizacion_marker_rango_postes,
                                entidad.delPoste,
                                entidad.alPoste
                            )
                        } else {
                            getApplication<Application>().getString(
                                R.string.localizacion_marker_poste_unico,
                                entidad.delPoste
                            )
                        }
                        append(postes)
                    }
                )
            }

        if (marcadores.isEmpty()) {
            _estado.value = Estado.Error(
                getApplication<Application>().getString(R.string.localizacion_estado_calles_sin_coordenadas)
            )
        }

        _marcadoresCalles.value = marcadores
    }

    private suspend fun sincronizarSubregion(subregion: String) {
        if (sincronizando) return
        sincronizando = true
        try {
            _estado.postValue(Estado.Cargando)
            withContext(Dispatchers.IO) {
                repository.syncSubregion(subregion)
            }
        } catch (t: Throwable) {
            _estado.postValue(
                Estado.Error(
                    getApplication<Application>().getString(R.string.localizacion_estado_sync_error)
                )
            )
            intentoSyncRealizado = false
        } finally {
            sincronizando = false
        }
    }

    data class Localizacion(
        val direccion: String,
        val latitud: Double,
        val longitud: Double,
        val delPoste: Int,
        val alPoste: Int,
        val calleValor: Int,
    )

    data class MarcadorCalle(
        val latitud: Double,
        val longitud: Double,
        val titulo: String,
        val snippet: String,
    )

    sealed class Estado {
        object Cargando : Estado()
        object Exito : Estado()
        data class Error(val mensaje: String) : Estado()
    }
}
