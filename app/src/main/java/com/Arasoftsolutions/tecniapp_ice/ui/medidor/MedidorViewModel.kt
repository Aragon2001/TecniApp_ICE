package com.Arasoftsolutions.tecniapp_ice.ui.medidor

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Arasoftsolutions.tecniapp_ice.Database.entities.MedidorEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.Database.sync.FirebaseSyncManager
import com.Arasoftsolutions.tecniapp_ice.R
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gestiona la consulta de medidores priorizando la base de datos local
 * y manteniendo un estado de UI observable.
 */
class MedidorViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = RoomRepository.getInstance(app)
    private val firebase = FirebaseSyncManager(app)
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(
        MedidorUiState(
            message = app.getString(R.string.medidor_estado_instruccion)
        )
    )
    val uiState: StateFlow<MedidorUiState> = _uiState.asStateFlow()

    private var subregionActual: String? = null
    private var initialized = false

    fun initialize() {
        if (initialized) return
        initialized = true
        viewModelScope.launch {
            val context = getApplication<Application>()
            _uiState.update {
                it.copy(
                    isLoading = true,
                    message = context.getString(R.string.medidor_estado_preparando)
                )
            }
            prepararCacheLocal()
        }
    }

    private suspend fun prepararCacheLocal() {
        val context = getApplication<Application>()
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    message = context.getString(R.string.medidor_estado_requiere_sesion)
                )
            }
            return
        }

        try {
            val subregion = withContext(Dispatchers.IO) {
                val local = repository.obtenerUsuario(uid)
                val ensured = local ?: runCatching { repository.upsertUserFromFirebase(uid) }
                    .onFailure { throwable ->
                        Log.e("MedidorViewModel", "Error obteniendo usuario desde Firebase", throwable)
                    }
                    .getOrNull()
                ensured?.subregion?.trim()?.takeIf { it.isNotEmpty() }
            }

            if (subregion.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = context.getString(R.string.medidor_estado_sin_subregion)
                    )
                }
                return
            }

            subregionActual = subregion

            val medidoresLocales = withContext(Dispatchers.IO) {
                repository.contarMedidores(subregion)
            }

            if (medidoresLocales == 0) {
                _uiState.update {
                    it.copy(message = context.getString(R.string.medidor_estado_cargando_cache))
                }

                val syncResult = withContext(Dispatchers.IO) {
                    runCatching { repository.syncSubregion(subregion) }
                }

                if (syncResult.isFailure) {
                    Log.e("MedidorViewModel", "Error sincronizando subregión", syncResult.exceptionOrNull())
                }

                val medidoresPostSync = withContext(Dispatchers.IO) {
                    repository.contarMedidores(subregion)
                }

                if (medidoresPostSync == 0) {
                    val medidoresDescargados = withContext(Dispatchers.IO) {
                        runCatching { firebase.obtenerMedidores(subregion) }
                            .getOrElse { throwable ->
                                Log.e("MedidorViewModel", "Error descargando medidores", throwable)
                                emptyList()
                            }
                    }
                    if (medidoresDescargados.isNotEmpty()) {
                        withContext(Dispatchers.IO) {
                            repository.insertarMedidores(medidoresDescargados)
                        }
                    }
                }
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isReady = true,
                    message = context.getString(R.string.medidor_estado_listo)
                )
            }
        } catch (t: Throwable) {
            Log.e("MedidorViewModel", "Error preparando cache local", t)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    message = context.getString(R.string.medidor_estado_error_generico)
                )
            }
        }
    }

    fun buscar(numero: String) {
        val trimmed = numero.trim()
        if (trimmed.isEmpty()) return

        val subregion = subregionActual
        if (subregion.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    medidor = null,
                    message = getApplication<Application>().getString(R.string.medidor_estado_sin_subregion)
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }

            try {
                val local = withContext(Dispatchers.IO) {
                    repository.buscarMedidorPorNumero(trimmed)
                }

                if (local != null) {
                    _uiState.update { it.copy(isLoading = false, medidor = local, message = null) }
                    return@launch
                }

                val remoto = withContext(Dispatchers.IO) {
                    runCatching { firebase.buscarMedidorEnFirebase(subregion, trimmed) }
                        .getOrNull()
                }

                if (remoto != null) {
                    withContext(Dispatchers.IO) { repository.insertarMedidor(remoto) }
                    _uiState.update { it.copy(isLoading = false, medidor = remoto, message = null) }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            medidor = null,
                            message = getApplication<Application>().getString(
                                R.string.medidor_estado_no_result,
                                trimmed
                            )
                        )
                    }
                }
            } catch (t: Throwable) {
                Log.e("MedidorViewModel", "Error consultando medidor", t)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        medidor = null,
                        message = getApplication<Application>().getString(R.string.medidor_estado_error_generico)
                    )
                }
            }
        }
    }

    fun limpiarResultado() {
        _uiState.update { it.copy(medidor = null) }
    }

    fun obtenerMedidorActual(): MedidorEntity? = _uiState.value.medidor

    suspend fun obtenerDescripcionPueblo(codigo: String?): String? {
        val subregion = subregionActual ?: return codigo?.takeIf { it.isNotBlank() }
        val id = codigo?.trim()?.toIntOrNull() ?: return codigo?.takeIf { it.isNotBlank() }
        return withContext(Dispatchers.IO) {
            repository.obtenerPuebloPorId(subregion, id)?.let { pueblo ->
                "${pueblo.id} - ${pueblo.nombre}"
            }
        }
    }
}

data class MedidorUiState(
    val isLoading: Boolean = false,
    val medidor: MedidorEntity? = null,
    val message: String? = null,
    val isReady: Boolean = false
)
