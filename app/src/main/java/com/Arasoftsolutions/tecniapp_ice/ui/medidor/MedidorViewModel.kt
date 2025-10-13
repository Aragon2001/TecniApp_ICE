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

    private var subregionId: String? = null
    private var subregionNombre: String? = null
    private var subregionStorageKey: String? = null
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
            val subregionData = withContext(Dispatchers.IO) {
                val local = repository.obtenerUsuario(uid)
                val ensured = local ?: runCatching { repository.upsertUserFromFirebase(uid) }
                    .onFailure { throwable ->
                        Log.e("MedidorViewModel", "Error obteniendo usuario desde Firebase", throwable)
                    }
                    .getOrNull()
                ensured?.let { user ->
                    val id = user.subregion?.trim()?.takeIf { it.isNotEmpty() }
                    val nombre = user.subregionNombre?.trim()?.takeIf { it.isNotEmpty() }
                    id to nombre
                }
            }

            val (id, nombre) = subregionData ?: (null to null)
            val storageKey = id ?: nombre

            if (storageKey.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = context.getString(R.string.medidor_estado_sin_subregion)
                    )
                }
                return
            }

            subregionId = id
            subregionNombre = nombre
            subregionStorageKey = storageKey

            val medidoresLocales = withContext(Dispatchers.IO) {
                repository.contarMedidores(storageKey)
            }

            if (medidoresLocales == 0) {
                _uiState.update {
                    it.copy(message = context.getString(R.string.medidor_estado_cargando_cache))
                }

                val syncResult = id?.let {
                    withContext(Dispatchers.IO) {
                        runCatching { repository.syncSubregion(it) }
                    }
                }

                if (syncResult != null && syncResult.isFailure) {
                    Log.e("MedidorViewModel", "Error sincronizando subregión", syncResult.exceptionOrNull())
                }

                val medidoresPostSync = withContext(Dispatchers.IO) {
                    repository.contarMedidores(storageKey)
                }

                if (medidoresPostSync == 0) {
                    val medidoresDescargados = withContext(Dispatchers.IO) {
                        runCatching { firebase.obtenerMedidores(storageKey, nombre ?: id) }
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
                    message = context.getString(R.string.medidor_estado_listo),
                    subregionNombre = nombre ?: id
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

        val storageKey = subregionStorageKey
        if (storageKey.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    medidor = null,
                    message = getApplication<Application>().getString(R.string.medidor_estado_sin_subregion)
                )
            }
            return
        }

        val displayName = subregionNombre ?: subregionId

        viewModelScope.launch {
            val context = getApplication<Application>()
            _uiState.update {
                it.copy(
                    isLoading = true,
                    message = null,
                    isRegistering = false,
                    notFoundNumero = null,
                    showManualForm = false
                )
            }

            try {
                val local = withContext(Dispatchers.IO) {
                    repository.buscarMedidorPorNumero(trimmed)
                }

                if (local != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            medidor = local,
                            message = context.getString(R.string.medidor_estado_encontrado, local.medidorNumber),
                            notFoundNumero = null,
                            showManualForm = false
                        )
                    }
                    return@launch
                }

                val remoto = withContext(Dispatchers.IO) {
                    runCatching { firebase.buscarMedidorEnFirebase(storageKey, displayName, trimmed) }
                        .getOrNull()
                }

                if (remoto != null) {
                    withContext(Dispatchers.IO) { repository.insertarMedidor(remoto) }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            medidor = remoto,
                            message = context.getString(R.string.medidor_estado_encontrado, remoto.medidorNumber),
                            notFoundNumero = null,
                            showManualForm = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            medidor = null,
                            message = context.getString(R.string.medidor_estado_no_result_registro, trimmed),
                            notFoundNumero = trimmed,
                            showManualForm = false
                        )
                    }
                }
            } catch (t: Throwable) {
                Log.e("MedidorViewModel", "Error consultando medidor", t)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        medidor = null,
                        message = getApplication<Application>().getString(R.string.medidor_estado_error_generico),
                        notFoundNumero = null,
                        showManualForm = false
                    )
                }
            }
        }
    }

    fun limpiarResultado() {
        _uiState.update {
            it.copy(
                medidor = null,
                notFoundNumero = null,
                showManualForm = false
            )
        }
    }

    fun obtenerMedidorActual(): MedidorEntity? = _uiState.value.medidor

    suspend fun obtenerDescripcionPueblo(codigo: String?): String? {
        val id = codigo?.trim()?.toIntOrNull() ?: return codigo?.takeIf { it.isNotBlank() }
        return withContext(Dispatchers.IO) {
            repository.obtenerPuebloPorId(id)?.let { pueblo ->
                "${pueblo.id} - ${pueblo.nombre}"
            }
        }
    }

    fun habilitarRegistroManual() {
        _uiState.update { it.copy(showManualForm = true) }
    }

    fun cancelarRegistroManual() {
        _uiState.update { it.copy(showManualForm = false, isRegistering = false) }
    }

    fun registrarMedidorManual(
        numero: String,
        cliente: String?,
        localizacion: Long?,
        calle: String?,
        poste: String?,
        metros: String?,
        pueblo: String?
    ) {
        val storageKey = subregionStorageKey
        if (storageKey.isNullOrBlank()) {
            cancelarRegistroManual()
            return
        }

        val numeroLimpio = numero.trim()
        if (numeroLimpio.isEmpty()) {
            _uiState.update {
                it.copy(message = getApplication<Application>().getString(R.string.medidor_registro_requerido_numero))
            }
            return
        }

        val context = getApplication<Application>()
        val displayName = subregionNombre ?: subregionId

        viewModelScope.launch {
            _uiState.update { it.copy(isRegistering = true) }
            try {
                val entity = MedidorEntity(
                    medidorNumber = numeroLimpio,
                    cliente = cliente?.trim()?.takeIf { it.isNotEmpty() },
                    localizacion = localizacion,
                    metros = metros?.trim()?.takeIf { it.isNotEmpty() },
                    poste = poste?.trim()?.takeIf { it.isNotEmpty() },
                    calle = calle?.trim()?.takeIf { it.isNotEmpty() },
                    pueblo = pueblo?.trim()?.takeIf { it.isNotEmpty() },
                    subregion = storageKey
                )

                withContext(Dispatchers.IO) {
                    firebase.registrarMedidorManual(storageKey, displayName, entity)
                    repository.insertarMedidor(entity)
                }

                _uiState.update {
                    it.copy(
                        isRegistering = false,
                        medidor = entity,
                        message = context.getString(R.string.medidor_estado_registro_exito, numeroLimpio),
                        notFoundNumero = null,
                        showManualForm = false
                    )
                }
            } catch (t: Throwable) {
                Log.e("MedidorViewModel", "Error registrando medidor", t)
                _uiState.update {
                    it.copy(
                        isRegistering = false,
                        message = context.getString(R.string.medidor_estado_registro_error, numeroLimpio),
                        showManualForm = true,
                        notFoundNumero = numeroLimpio
                    )
                }
            }
        }
    }
}

data class MedidorUiState(
    val isLoading: Boolean = false,
    val medidor: MedidorEntity? = null,
    val message: String? = null,
    val isReady: Boolean = false,
    val notFoundNumero: String? = null,
    val showManualForm: Boolean = false,
    val isRegistering: Boolean = false,
    val subregionNombre: String? = null
)
