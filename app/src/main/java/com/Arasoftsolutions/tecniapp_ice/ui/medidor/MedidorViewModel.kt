package com.Arasoftsolutions.tecniapp_ice.ui.medidor

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Arasoftsolutions.tecniapp_ice.Database.entities.MedidorEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.Database.sync.FirebaseSyncManager
import com.Arasoftsolutions.tecniapp_ice.Database.sync.SubregionNormalizer
import com.Arasoftsolutions.tecniapp_ice.R
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private var pueblosRegistroJob: Job? = null
    private var pendingRegistroSubregionSelection: String? = null

    init {
        viewModelScope.launch {
            repository.observarSubregiones().collect { subregiones ->
                val opciones = subregiones
                    .map { SubregionOption(it.id, it.nombre) }
                    .sortedBy { it.displayName.lowercase() }
                _uiState.update { estado ->
                    estado.copy(subregionOptions = opciones)
                }
                ensureRegistroSubregionSeleccionada()
            }
        }
    }

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
            pendingRegistroSubregionSelection = storageKey

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
                    Log.w(
                        "MedidorViewModel",
                        "No se encontraron medidores luego de syncSubregion para subregion=$storageKey"
                    )
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
            ensureRegistroSubregionSeleccionada()
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
                    showManualForm = false,
                    showNotFoundDialog = false,
                    showCloudLookupDialog = false,
                    cloudLookupNumero = null
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
                            showManualForm = false,
                            showNotFoundDialog = false,
                            notFoundOffline = false,
                            showCloudLookupDialog = false,
                            cloudLookupNumero = null
                        )
                    }
                    return@launch
                }

                if (!hasInternetConnection()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            medidor = null,
                            message = context.getString(R.string.medidor_estado_sin_red, trimmed),
                            notFoundNumero = trimmed,
                            showManualForm = false,
                            showNotFoundDialog = true,
                            notFoundOffline = true,
                            showCloudLookupDialog = false,
                            cloudLookupNumero = null
                        )
                    }
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        showCloudLookupDialog = true,
                        cloudLookupNumero = trimmed
                    )
                }

                val remoto = withContext(Dispatchers.IO) {
                    runCatching { firebase.buscarMedidorEnFirebaseLigero(storageKey, displayName, trimmed) }
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
                            showManualForm = false,
                            showNotFoundDialog = false,
                            notFoundOffline = false,
                            showCloudLookupDialog = false,
                            cloudLookupNumero = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            medidor = null,
                            message = context.getString(R.string.medidor_estado_no_result_registro, trimmed),
                            notFoundNumero = trimmed,
                            showManualForm = false,
                            showNotFoundDialog = true,
                            notFoundOffline = false,
                            showCloudLookupDialog = false,
                            cloudLookupNumero = null
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
                        showManualForm = false,
                        showNotFoundDialog = false,
                        notFoundOffline = false,
                        showCloudLookupDialog = false,
                        cloudLookupNumero = null
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
                showManualForm = false,
                showNotFoundDialog = false,
                showCloudLookupDialog = false,
                cloudLookupNumero = null
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
        ensureRegistroSubregionSeleccionada()
        _uiState.update { it.copy(showManualForm = true, showNotFoundDialog = false, notFoundOffline = false) }
    }

    fun cancelarRegistroManual() {
        _uiState.update {
            it.copy(showManualForm = false, isRegistering = false, showNotFoundDialog = false, notFoundOffline = false)
        }
    }

    private fun ensureRegistroSubregionSeleccionada() {
        val opciones = _uiState.value.subregionOptions
        if (opciones.isEmpty()) return
        val estado = _uiState.value
        if (!estado.selectedSubregionId.isNullOrBlank()) return

        val candidatoRaw = pendingRegistroSubregionSelection
            ?: subregionId
            ?: subregionNombre

        val seleccion = opciones.firstOrNull { option ->
            option.matches(candidatoRaw) || option.matches(SubregionNormalizer.canonicalIdOrSelf(candidatoRaw))
        } ?: opciones.firstOrNull()

        seleccion?.let { seleccionarSubregionParaRegistro(it) }
    }

    fun seleccionarSubregionParaRegistro(option: SubregionOption) {
        val canonical = option.canonicalId
        if (canonical.isBlank()) return
        pendingRegistroSubregionSelection = canonical
        pueblosRegistroJob?.cancel()
        _uiState.update {
            it.copy(
                selectedSubregionId = canonical,
                selectedSubregionNombre = option.nombre.takeIf { nombre -> nombre.isNotBlank() },
                selectedSubregionDisplay = option.displayName,
                puebloOptions = emptyList(),
                selectedPuebloId = null,
                selectedPuebloNombre = null,
                selectedPuebloDisplay = null,
                isPueblosLoading = true
            )
        }
        pueblosRegistroJob = viewModelScope.launch {
            repository.observarPueblos(canonical).collect { pueblos ->
                val opcionesPueblo = pueblos
                    .map { PuebloOption(it.id, it.nombre) }
                    .sortedBy { it.displayName.lowercase() }
                _uiState.update { estado ->
                    val seleccionadoActual = estado.selectedPuebloId
                    val matching = opcionesPueblo.firstOrNull { it.id == seleccionadoActual }
                    estado.copy(
                        puebloOptions = opcionesPueblo,
                        isPueblosLoading = false,
                        selectedPuebloId = matching?.id,
                        selectedPuebloNombre = matching?.nombre,
                        selectedPuebloDisplay = matching?.displayName
                    )
                }
            }
        }
    }

    fun seleccionarPuebloParaRegistro(option: PuebloOption) {
        _uiState.update {
            it.copy(
                selectedPuebloId = option.id,
                selectedPuebloNombre = option.nombre.takeIf { nombre -> nombre.isNotBlank() },
                selectedPuebloDisplay = option.displayName
            )
        }
    }

    fun limpiarSeleccionPueblo() {
        _uiState.update {
            it.copy(selectedPuebloId = null, selectedPuebloNombre = null, selectedPuebloDisplay = null)
        }
    }

    fun onNotFoundDialogMostrado() {
        _uiState.update { it.copy(showNotFoundDialog = false, notFoundOffline = false) }
    }

    fun registrarMedidorManual(
        numero: String,
        cliente: String,
        localizacion: Long,
        calle: String,
        poste: String,
        metros: String
    ) {
        val numeroLimpio = numero.trim()
        if (numeroLimpio.isEmpty()) {
            _uiState.update {
                it.copy(message = getApplication<Application>().getString(R.string.medidor_registro_requerido_numero))
            }
            return
        }

        val context = getApplication<Application>()
        val estadoActual = _uiState.value
        val subregionSeleccionada = estadoActual.selectedSubregionId
            ?: pendingRegistroSubregionSelection?.let { SubregionNormalizer.canonicalIdOrSelf(it) }
        if (subregionSeleccionada.isNullOrBlank()) {
            _uiState.update {
                it.copy(message = context.getString(R.string.medidor_registro_subregion_requerida))
            }
            return
        }
        val subregionNombreSeleccionada = estadoActual.selectedSubregionNombre
            ?: estadoActual.selectedSubregionDisplay
            ?: subregionNombre
            ?: subregionSeleccionada

        val puebloId = estadoActual.selectedPuebloId
        if (puebloId == null) {
            _uiState.update {
                it.copy(message = context.getString(R.string.medidor_registro_pueblo_requerido))
            }
            return
        }
        val clienteLimpio = cliente.trim()
        if (clienteLimpio.isEmpty()) {
            _uiState.update {
                it.copy(message = context.getString(R.string.medidor_registro_cliente_requerido))
            }
            return
        }
        val calleLimpia = calle.trim()
        if (calleLimpia.isEmpty()) {
            _uiState.update {
                it.copy(message = context.getString(R.string.medidor_registro_calle_requerida))
            }
            return
        }
        val posteLimpio = poste.trim()
        if (posteLimpio.isEmpty()) {
            _uiState.update {
                it.copy(message = context.getString(R.string.medidor_registro_poste_requerido))
            }
            return
        }
        val metrosLimpios = metros.trim()
        if (metrosLimpios.isEmpty()) {
            _uiState.update {
                it.copy(message = context.getString(R.string.medidor_registro_metros_requeridos))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isRegistering = true, showNotFoundDialog = false) }
            try {
                val existenteLocal = withContext(Dispatchers.IO) {
                    repository.buscarMedidorPorNumero(numeroLimpio)
                }
                if (existenteLocal != null) {
                    _uiState.update {
                    it.copy(
                        isRegistering = false,
                        medidor = existenteLocal,
                        message = context.getString(R.string.medidor_estado_encontrado, existenteLocal.medidorNumber),
                        notFoundNumero = null,
                        showManualForm = false,
                        showNotFoundDialog = false,
                        notFoundOffline = false
                    )
                }
                return@launch
            }

                val existenteRemoto = withContext(Dispatchers.IO) {
                    runCatching {
                        firebase.buscarMedidorEnFirebase(
                            subregionSeleccionada,
                            subregionNombreSeleccionada,
                            numeroLimpio
                        )
                    }.getOrNull()
                }
                if (existenteRemoto != null) {
                    withContext(Dispatchers.IO) { repository.insertarMedidor(existenteRemoto) }
                    _uiState.update {
                    it.copy(
                        isRegistering = false,
                        medidor = existenteRemoto,
                        message = context.getString(R.string.medidor_estado_encontrado, existenteRemoto.medidorNumber),
                        notFoundNumero = null,
                        showManualForm = false,
                        showNotFoundDialog = false,
                        notFoundOffline = false
                    )
                }
                return@launch
            }

                val entity = MedidorEntity(
                    medidorNumber = numeroLimpio,
                    cliente = clienteLimpio,
                    localizacion = localizacion,
                    metros = metrosLimpios,
                    poste = posteLimpio,
                    calle = calleLimpia,
                    pueblo = puebloId.toString(),
                    subregion = subregionSeleccionada
                )

                withContext(Dispatchers.IO) {
                    firebase.registrarMedidorManual(subregionSeleccionada, subregionNombreSeleccionada, entity)
                    repository.insertarMedidor(entity)
                }

                _uiState.update {
                    it.copy(
                        isRegistering = false,
                        medidor = entity,
                        message = context.getString(R.string.medidor_estado_registro_exito, numeroLimpio),
                        notFoundNumero = null,
                        showManualForm = false,
                        showNotFoundDialog = false,
                        notFoundOffline = false
                    )
                }
            } catch (t: Throwable) {
                Log.e("MedidorViewModel", "Error registrando medidor", t)
                _uiState.update {
                    it.copy(
                        isRegistering = false,
                        message = context.getString(R.string.medidor_estado_registro_error, numeroLimpio),
                        showManualForm = true,
                        notFoundNumero = numeroLimpio,
                        notFoundOffline = false
                    )
                }
            }
        }
    }

    private fun hasInternetConnection(): Boolean {
        val connectivityManager = getApplication<Application>()
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

data class MedidorUiState(
    val isLoading: Boolean = false,
    val medidor: MedidorEntity? = null,
    val message: String? = null,
    val isReady: Boolean = false,
    val notFoundNumero: String? = null,
    val cloudLookupNumero: String? = null,
    val showManualForm: Boolean = false,
    val isRegistering: Boolean = false,
    val subregionNombre: String? = null,
    val showNotFoundDialog: Boolean = false,
    val notFoundOffline: Boolean = false,
    val showCloudLookupDialog: Boolean = false,
    val subregionOptions: List<SubregionOption> = emptyList(),
    val selectedSubregionId: String? = null,
    val selectedSubregionNombre: String? = null,
    val selectedSubregionDisplay: String? = null,
    val puebloOptions: List<PuebloOption> = emptyList(),
    val selectedPuebloId: Int? = null,
    val selectedPuebloNombre: String? = null,
    val selectedPuebloDisplay: String? = null,
    val isPueblosLoading: Boolean = false
)

data class SubregionOption(
    val id: String,
    val nombre: String
) {
    private fun resolveCanonical(): String {
        val idCandidate = SubregionNormalizer.canonicalIdOrSelf(id)
            ?: id.trim().takeIf { it.isNotEmpty() }
        val nombreCandidate = SubregionNormalizer.canonicalIdOrSelf(nombre)
            ?: nombre.trim().takeIf { it.isNotEmpty() }
        return idCandidate ?: nombreCandidate ?: ""
    }

    val canonicalId: String = resolveCanonical()

    val displayName: String = nombre.takeIf { it.isNotBlank() } ?: canonicalId

    fun matches(valor: String?): Boolean {
        val trimmed = valor?.trim().orEmpty()
        if (trimmed.isEmpty()) return false
        val canonicalValor = SubregionNormalizer.canonicalIdOrSelf(trimmed) ?: trimmed
        return trimmed.equals(id, true) ||
            trimmed.equals(nombre, true) ||
            canonicalValor.equals(canonicalId, true)
    }
}

data class PuebloOption(
    val id: Int,
    val nombre: String
) {
    val displayName: String = if (nombre.isBlank()) {
        id.toString()
    } else {
        "$id - $nombre"
    }
}
