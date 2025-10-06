package com.Arasoftsolutions.tecniapp_ice.ui.localizacion

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
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

    private val _pueblos = MutableLiveData<List<String>>()
    val pueblos: LiveData<List<String>> = _pueblos

    private val _calles = MutableLiveData<List<String>>()
    val calles: LiveData<List<String>> = _calles

    private val _estado = MutableLiveData<Estado>()
    val estado: LiveData<Estado> = _estado

    private val _localizacion = MutableLiveData<Localizacion?>()
    val localizacion: LiveData<Localizacion?> = _localizacion

    private var subregionActual: String? = null
    private var initialized = false
    private var pueblosJob: Job? = null

    fun prepararDatos() {
        if (initialized) return
        initialized = true
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
            val subregion = withContext(Dispatchers.IO) {
                repository.obtenerUsuario(uid)?.subregion?.trim()?.takeIf { it.isNotEmpty() }
            }

            if (subregion.isNullOrBlank()) {
                _estado.postValue(Estado.Error(context.getString(R.string.localizacion_estado_sin_subregion)))
                return
            }

            subregionActual = subregion
            observarPueblos(subregion)
        } catch (t: Throwable) {
            _estado.postValue(Estado.Error(context.getString(R.string.localizacion_estado_error_generico)))
        }
    }

    private fun observarPueblos(subregion: String) {
        pueblosJob?.cancel()
        pueblosJob = viewModelScope.launch {
            repository.observarPueblos(subregion).collectLatest { lista ->
                if (lista.isEmpty()) {
                    _pueblos.postValue(listOf("Seleccione un pueblo"))
                    _estado.postValue(Estado.Error(getApplication<Application>().getString(R.string.localizacion_estado_sin_pueblos)))
                } else {
                    val pueblosOrdenados = lista.sortedBy { it.nombre }
                        .map { "${it.id} - ${it.nombre}" }
                    _pueblos.postValue(listOf("Seleccione un pueblo") + pueblosOrdenados)
                    _estado.postValue(Estado.Exito)
                }
            }
        }
    }

    fun cargarCallesParaPueblo(pueblo: Int) {
        val subregion = subregionActual ?: run {
            _estado.value = Estado.Error(getApplication<Application>().getString(R.string.localizacion_estado_sin_subregion))
            return
        }

        viewModelScope.launch {
            _estado.value = Estado.Cargando
            val calles = withContext(Dispatchers.IO) {
                repository.obtenerCallesPorPueblo(subregion, pueblo)
            }

            if (calles.isEmpty()) {
                _calles.value = listOf("Seleccione una calle")
                _estado.value = Estado.Error(getApplication<Application>().getString(R.string.localizacion_estado_sin_calles))
            } else {
                val opciones = mutableListOf("Seleccione una calle")
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
        val subregion = subregionActual ?: run {
            _estado.value = Estado.Error(getApplication<Application>().getString(R.string.localizacion_estado_sin_subregion))
            return
        }

        viewModelScope.launch {
            _estado.value = Estado.Cargando
            val entidad = withContext(Dispatchers.IO) {
                repository.buscarLocalizacion(subregion, codigoPueblo, calle, direccion)
            }

            if (entidad == null) {
                _localizacion.value = null
                _estado.value = Estado.Error(getApplication<Application>().getString(R.string.localizacion_estado_sin_localizacion))
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

    data class Localizacion(
        val direccion: String,
        val latitud: Double,
        val longitud: Double,
        val delPoste: Int,
        val alPoste: Int,
        val calleValor: Int,
    )

    sealed class Estado {
        object Cargando : Estado()
        object Exito : Estado()
        data class Error(val mensaje: String) : Estado()
    }
}
