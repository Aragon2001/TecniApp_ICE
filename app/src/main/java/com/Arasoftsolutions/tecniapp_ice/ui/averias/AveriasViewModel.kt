package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.app.Application
import android.content.Context
import android.location.Geocoder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AgenciaEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AveriaEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.InventarioConVehiculo
import com.Arasoftsolutions.tecniapp_ice.Database.entities.MaterialEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.MedidorEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.RegionEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.SubregionesEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.TecnicoEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.UserEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.apellidosCompletos
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.ui.vehiculo.VehiculoPlacaUtils
import com.Arasoftsolutions.tecniapp_ice.Database.sync.FirebaseSyncManager
import com.Arasoftsolutions.tecniapp_ice.R
import com.google.firebase.auth.FirebaseAuth
import com.Arasoftsolutions.tecniapp_ice.preferences.DataStoreManager
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

enum class Estado {
    PENDIENTE, ASIGNADA, EN_ATENCION, RESUELTA, ANULADA;

    companion object {
        fun fromLabel(value: String): Estado {
            val normalized = value.lowercase(Locale.getDefault())
            return when {
                normalized.contains("anul") -> ANULADA
                normalized.contains("resuel") -> RESUELTA
                normalized.contains("en at") -> EN_ATENCION
                normalized.contains("asign") -> ASIGNADA
                else -> PENDIENTE
            }
        }
    }
}

data class RegionUI(val id: String?, val nombreVisible: String)
data class AgenciaUI(val id: String?, val nombreVisible: String)
data class AveriasUiState(val loading: Boolean = false, val items: List<AveriaUI> = emptyList())
data class VehiculoUI(val placa: String, val agencia: String)

sealed class MedidorLookupState {
    object Idle : MedidorLookupState()
    object Loading : MedidorLookupState()
    data class Success(val medidor: MedidorEntity) : MedidorLookupState()
    data class NotFound(val numero: String) : MedidorLookupState()
    data class Error(val message: String) : MedidorLookupState()
}

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AveriasViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "AveriasVM"
        private const val PREFS_NAME = "app_preferences"
        private const val PREF_REGION_ID = "averias_pref_region_id"
        private const val PREF_REGION_NAME = "averias_pref_region_name"
        private const val PREF_AGENCIA_ID = "averias_pref_agencia_id"
        private const val PREF_AGENCIA_NAME = "averias_pref_agencia_name"
    }

    private val db = AppDatabase.getInstance(app)
    private val repo = AveriasRepository(db)
    private val roomRepo = RoomRepository.getInstance(app)
    private val firebaseSync = FirebaseSyncManager(app)
    private val dataStore = DataStoreManager.getInstance(app)
    private val auth = FirebaseAuth.getInstance()
    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val q = MutableStateFlow("")
    private val estado = MutableStateFlow<Estado?>(null)

    private val allRegionsLabel = app.getString(R.string.averias_filtro_region_todas)
    private val allAgenciesLabel = app.getString(R.string.averias_filtro_agencia_todas)

    private val _notificationsEnabled = MutableStateFlow(AveriaNotificationPreferences.areNotificationsEnabled(app))
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()
    private val _notificationAgencies = MutableStateFlow(AveriaNotificationPreferences.getSelectedAgencies(app))
    val notificationAgencies: StateFlow<List<String>> = _notificationAgencies.asStateFlow()
    private val _notificationSuggestions = MutableStateFlow<List<String>>(emptyList())
    val notificationSuggestions: StateFlow<List<String>> = _notificationSuggestions.asStateFlow()

    private val _regiones = MutableStateFlow(listOf(RegionUI(null, allRegionsLabel)))
    val regiones: StateFlow<List<RegionUI>> = _regiones.asStateFlow()
    private val _regionSeleccionada = MutableStateFlow(RegionUI(null, allRegionsLabel))
    val regionSeleccionada: StateFlow<RegionUI> = _regionSeleccionada.asStateFlow()
    private val _agencias = MutableStateFlow(listOf(AgenciaUI(null, allAgenciesLabel)))
    val agencias: StateFlow<List<AgenciaUI>> = _agencias.asStateFlow()
    private val _agenciaSeleccionada = MutableStateFlow(AgenciaUI(null, allAgenciesLabel))
    val agenciaSeleccionada: StateFlow<AgenciaUI> = _agenciaSeleccionada.asStateFlow()

    private val isLoading = MutableStateFlow(false)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()
    private val _shareRequests = MutableSharedFlow<AveriaUI>(extraBufferCapacity = 1)
    val shareRequests = _shareRequests.asSharedFlow()
    private val _usuario = MutableStateFlow<UserEntity?>(null)
    val usuarioActual: StateFlow<UserEntity?> = _usuario.asStateFlow()
    private val _vehiculos = MutableStateFlow<List<VehiculoUI>>(emptyList())
    val vehiculosDisponibles: StateFlow<List<VehiculoUI>> = _vehiculos.asStateFlow()
    private val _materiales = MutableStateFlow<List<MaterialEntity>>(emptyList())
    val materialesDisponibles: StateFlow<List<MaterialEntity>> = _materiales.asStateFlow()
    private val _tecnicos = MutableStateFlow<List<TecnicoEntity>>(emptyList())
    val tecnicosDisponibles: StateFlow<List<TecnicoEntity>> = _tecnicos.asStateFlow()
    private val _medidorEstado = MutableStateFlow<MedidorLookupState>(MedidorLookupState.Idle)
    val medidorEstado: StateFlow<MedidorLookupState> = _medidorEstado.asStateFlow()
    private val _addresses = MutableStateFlow<Map<String, String>>(emptyMap())
    private val pendingAddressLookups = mutableSetOf<String>()
    private var cachedRegiones: List<RegionEntity> = emptyList()
    private var cachedSubregiones: List<SubregionesEntity> = emptyList()
    private var cachedAgencias: List<AgenciaEntity> = emptyList()
    private val regionKeywords = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    private var pendingUser: UserEntity? = null
    private var usuarioObserverStarted = false
    private var pendingRegionId: String? = prefs.getString(PREF_REGION_ID, null)
    private var pendingRegionName: String? = prefs.getString(PREF_REGION_NAME, null)
    private var pendingAgencyId: String? = prefs.getString(PREF_AGENCIA_ID, null)
    private var pendingAgencyName: String? = prefs.getString(PREF_AGENCIA_NAME, null)
    private var catalogosSyncAttempted = false
    private var tecnicosSyncAttempted = false
    private var filtersTouchedByUser = false
    private val drafts = mutableMapOf<String, AveriaDraft>()

    data class FechaFiltro(val inicioMillis: Long, val finExclusiveMillis: Long)

    private data class FilterConfig(
        val query: String,
        val estado: Estado?,
        val region: RegionUI,
        val agencia: AgenciaUI,
        val fecha: FechaFiltro?
    )

    private val fechaFiltro = MutableStateFlow<FechaFiltro?>(null)
    val fechaFiltroState: StateFlow<FechaFiltro?> = fechaFiltro.asStateFlow()
    private val zoneId: ZoneId = ZoneId.systemDefault()

    private val filteredAverias =
        combine(
            q.debounce(250).map { it.trim() }.distinctUntilChanged(),
            estado,
            _regionSeleccionada,
            _agenciaSeleccionada,
            fechaFiltro
        ) { qv, est, regionSel, agenciaSel, fechaSel ->
            FilterConfig(qv, est, regionSel, agenciaSel, fechaSel)
        }
            .flatMapLatest { config ->
                repo.observe(emptyList(), "", config.query, "")
                    .map { list ->
                        list.filter { entity ->
                            matchesEstado(entity, config.estado) &&
                                    matchesRegion(entity, config.region) &&
                                    matchesAgencia(entity, config.agencia) &&
                                    matchesFecha(entity, config.fecha)
                        }
                    }
            }

    val uiState: StateFlow<AveriasUiState> =
        combine(filteredAverias, _addresses, isLoading) { list, addresses, loading ->
            val orderedByDate = list.sortedWith(
                compareByDescending<AveriaEntity> { it.fechaInicioMillis }
                    .thenByDescending { it.lastUpdated }
            )
            val items = orderedByDate.map { entity ->
                val savedAddress = entity.direccion?.takeIf { it.isNotBlank() }
                val address = addresses[entity.caseId] ?: savedAddress
                if (address == null) {
                    queueAddressLookup(entity)
                }
                buildAveriaUi(entity, address)
            }
            AveriasUiState(loading = loading, items = items)
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AveriasUiState())

    init {
        AveriaNotifications.ensureChannel(app)
        observeCatalogos()
        observeUsuarioActual()
        viewModelScope.launch { loadUsuarioActual() }
        viewModelScope.launch { syncCatalogosGenerales() }
        viewModelScope.launch {
            dataStore.notificationsEnabled.collectLatest { enabled ->
                if (_notificationsEnabled.value != enabled) {
                    _notificationsEnabled.value = enabled
                }
                if (AveriaNotificationPreferences.areNotificationsEnabled(getApplication()) != enabled) {
                    AveriaNotificationPreferences.setNotificationsEnabled(getApplication(), enabled)
                }
            }
        }
        viewModelScope.launch {
            usuarioActual
                .filterNotNull()
                .flatMapLatest { user ->
                    val agencia = user.agencia?.takeIf { it.isNotBlank() }
                        ?: user.agenciaId?.takeIf { it.isNotBlank() }
                    if (agencia.isNullOrBlank()) flowOf(emptyList())
                    else roomRepo.observarVehiculos(agencia)
                }
                .collectLatest { vehiculos ->
                    val preferidoPlaca = _usuario.value?.placaVehiculo?.takeIf { !it.isNullOrBlank() }?.trim()
                    val preferidoAgencia = _usuario.value?.agencia?.trim().orEmpty()
                    val lista = buildList {
                        if (!preferidoPlaca.isNullOrBlank()) {
                            add(VehiculoUI(preferidoPlaca, preferidoAgencia))
                        }
                        vehiculos.forEach { entity ->
                            val placaTexto = entity.placa.toString()
                            add(VehiculoUI(placaTexto, entity.agencia))
                        }
                    }
                        .distinctBy { it.placa.uppercase(Locale.getDefault()) }
                    _vehiculos.value = lista
                }
        }
        viewModelScope.launch {
            roomRepo.observarMateriales().collectLatest { catalogo ->
                _materiales.value = catalogo
            }
        }
        viewModelScope.launch {
            roomRepo.observarTecnicos().collectLatest { lista ->
                if (lista.isEmpty()) {
                    if (!tecnicosSyncAttempted) {
                        tecnicosSyncAttempted = true
                        runCatching { roomRepo.syncTecnicos() }
                            .onFailure { Log.w(TAG, "No se pudieron sincronizar técnicos", it) }
                    }
                } else {
                    tecnicosSyncAttempted = false
                }
                _tecnicos.value = lista
            }
        }
    }

    private fun observeUsuarioActual() {
        if (usuarioObserverStarted) return
        val uid = auth.currentUser?.uid ?: return
        usuarioObserverStarted = true
        viewModelScope.launch {
            roomRepo.observarUsuario(uid).collectLatest { user ->
                _usuario.value = user
                if (user != null) {
                    pendingUser = user
                    refreshPendingSelection()
                    val regionItems = _regiones.value
                    if (regionItems.isNotEmpty()) {
                        applyPendingSelectionsIfPossible(regionItems)
                    }
                }
            }
        }
    }

    fun setQuery(value: String) = viewModelScope.launch { q.emit(value) }
    fun setEstado(value: Estado?) = viewModelScope.launch { estado.emit(value) }

    fun setFechaFiltro(inicioMillis: Long, finMillis: Long) {
        val startDate = pickerUtcToLocalDate(inicioMillis)
        val endDate = pickerUtcToLocalDate(finMillis)
        val (first, last) = if (startDate <= endDate) startDate to endDate else endDate to startDate
        val startOfDay = first.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endExclusive = last.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        fechaFiltro.value = FechaFiltro(startOfDay, endExclusive)
        // TODO(Codex): Recalcular rango respetando límites locales sin desfase de zona
    }

    private fun pickerUtcToLocalDate(millis: Long): LocalDate {
        return Instant.ofEpochMilli(millis)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
        // TODO(Codex): Interpretar selección del DatePicker en UTC puro para evitar restar un día
    }

    fun clearFechaFiltro() {
        fechaFiltro.value = null
    }

    fun observarInventarioPorPlaca(placa: String?): Flow<List<InventarioConVehiculo>> {
        val texto = placa?.trim().orEmpty()
        if (texto.isBlank()) return flowOf(emptyList<InventarioConVehiculo>())
        val placaLong = VehiculoPlacaUtils.parsePlacaLong(texto)
            ?: VehiculoPlacaUtils.parsePlacaLong(texto.replace("ICE", "", ignoreCase = true))
            ?: return flowOf(emptyList<InventarioConVehiculo>())
        return roomRepo.observarVehiculoPorPlaca(placaLong)
            .flatMapLatest { vehiculo ->
                if (vehiculo == null) flowOf(emptyList<InventarioConVehiculo>())
                else roomRepo.observarInventarioPorVehiculo(vehiculo.id)
            }
    }

    fun observarUltimoKilometrajePorPlaca(placa: String?): Flow<Double?> {
        val texto = placa?.trim().orEmpty()
        if (texto.isBlank()) return flowOf<Double?>(null)
        val normalizado = if (VehiculoPlacaUtils.parsePlacaLong(texto) == null) {
            texto.replace("ICE", "", ignoreCase = true)
        } else {
            texto
        }
        return roomRepo.observarUltimoKilometraje(normalizado)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        if (_notificationsEnabled.value == enabled) return
        _notificationsEnabled.value = enabled
        AveriaNotificationPreferences.setNotificationsEnabled(getApplication(), enabled)
        AveriaNotifications.notifyPreferenceToggle(getApplication(), enabled)
        viewModelScope.launch { dataStore.setNotificationsEnabled(enabled) }
        // TODO(Codex): Emitir notificación local al activar/desactivar alertas
    }

    fun addNotificationAgency(nombre: String) {
        val trimmed = nombre.trim()
        if (trimmed.isEmpty()) return
        AveriaNotificationPreferences.addAgency(getApplication(), trimmed)
        _notificationAgencies.value = AveriaNotificationPreferences.getSelectedAgencies(getApplication())
    }

    fun removeNotificationAgency(nombre: String) {
        AveriaNotificationPreferences.removeAgency(getApplication(), nombre)
        _notificationAgencies.value = AveriaNotificationPreferences.getSelectedAgencies(getApplication())
    }


    fun setRegionIndex(idx: Int) = viewModelScope.launch {
        filtersTouchedByUser = true
        val regiones = _regiones.value
        val selected = regiones.getOrNull(idx) ?: regiones.first()
        _regionSeleccionada.emit(selected)
        savePreferredRegion(selected)

        val nuevasAgencias = buildAgencias(selected.id)
        _agencias.emit(nuevasAgencias)

        val agenciaActual = _agenciaSeleccionada.value
        val agenciaPorDefecto = nuevasAgencias.firstOrNull { opcion ->
            val actualId = agenciaActual.id?.takeIf { it.isNotBlank() }
            val actualNombre = agenciaActual.nombreVisible
            (actualId != null && opcion.id?.equals(actualId, ignoreCase = true) == true) ||
                    opcion.nombreVisible.equals(actualNombre, ignoreCase = true)
        } ?: selectPendingAgency(nuevasAgencias)
        _agenciaSeleccionada.emit(agenciaPorDefecto)
        savePreferredAgency(agenciaPorDefecto)
        clearPendingAgencySelection()
    }

    fun setAgenciaIndex(idx: Int) = viewModelScope.launch {
        filtersTouchedByUser = true
        val agencias = _agencias.value
        val selected = agencias.getOrNull(idx) ?: agencias.first()
        _agenciaSeleccionada.emit(selected)
        savePreferredAgency(selected)
        clearPendingAgencySelection()
    }

    fun resetMedidorEstado() {
        _medidorEstado.value = MedidorLookupState.Idle
    }

    fun buscarMedidor(numero: String) {
        val trimmed = numero.trim()
        if (trimmed.isEmpty()) {
            _medidorEstado.value = MedidorLookupState.Idle
            return
        }
        viewModelScope.launch {
            _medidorEstado.value = MedidorLookupState.Loading
            try {
                val user = requireUsuario() ?: run {
                    _medidorEstado.value = MedidorLookupState.Error(
                        getApplication<Application>().getString(R.string.averia_error_usuario_no_autenticado)
                    )
                    return@launch
                }
                val subregionId = user.subregion?.takeIf { it.isNotBlank() }
                val subregionNombre = user.subregionNombre?.takeIf { it.isNotBlank() }
                val storageKey = subregionId ?: subregionNombre
                if (storageKey.isNullOrBlank()) {
                    _medidorEstado.value = MedidorLookupState.Error(
                        getApplication<Application>().getString(R.string.medidor_estado_sin_subregion)
                    )
                    return@launch
                }
                val medidor = withContext(Dispatchers.IO) {
                    roomRepo.buscarMedidorPorNumero(trimmed)
                        ?: firebaseSync.buscarMedidorEnFirebase(storageKey, subregionNombre, trimmed)?.also {
                            roomRepo.insertarMedidor(it)
                        }
                }
                if (medidor != null) {
                    _medidorEstado.value = MedidorLookupState.Success(medidor)
                } else {
                    _medidorEstado.value = MedidorLookupState.NotFound(trimmed)
                }
            } catch (t: Throwable) {
                _medidorEstado.value = MedidorLookupState.Error(
                    t.localizedMessage
                        ?: getApplication<Application>().getString(R.string.medidor_estado_error_generico)
                )
            }
        }
    }

    fun nombreTecnicoActual(): String? = _usuario.value?.let { nombreCompleto(it) }
    fun vehiculoPreferido(): String? = _usuario.value?.placaVehiculo

    suspend fun medidorExisteEnRoom(numero: String): Boolean =
        withContext(Dispatchers.IO) { repo.medidorExisteEnRoom(numero) }

    private fun matchesEstado(entity: AveriaEntity, estadoSeleccionado: Estado?): Boolean {
        if (estadoSeleccionado == null) return true
        val estadoEntity = if (entity.estadoClor.equals("RESUELTA", ignoreCase = true)) {
            Estado.RESUELTA
        } else {
            Estado.fromLabel(entity.estado)
        }
        return estadoEntity == estadoSeleccionado
    }

    private fun matchesRegion(entity: AveriaEntity, region: RegionUI): Boolean {
        val regionId = region.id ?: return true
        val haystack = listOfNotNull(entity.region, entity.nombreAgencia, entity.agencia)
            .joinToString(" ")
            .normalize()
        if (haystack.isBlank()) return false
        val keywords = regionKeywords.value[regionId].orEmpty()
        if (keywords.isEmpty()) return false
        return keywords.any { haystack.contains(it) }
    }

    private fun matchesAgencia(entity: AveriaEntity, agencia: AgenciaUI): Boolean {
        if (agencia.id == null) return true
        val haystack = listOfNotNull(entity.nombreAgencia, entity.agencia)
            .joinToString(" ")
            .normalize()
        if (haystack.isBlank()) return false

        val normalizedName = agencia.nombreVisible.normalize()
        if (normalizedName.isNotBlank() && haystack.contains(normalizedName)) return true

        val normalizedId = agencia.id.normalize()
        return normalizedId.isNotBlank() && haystack.contains(normalizedId)
    }

    private fun matchesFecha(entity: AveriaEntity, filtro: FechaFiltro?): Boolean {
        filtro ?: return true
        val fecha = entity.fechaInicioMillis
        if (fecha <= 0L) return false
        if (fecha < filtro.inicioMillis) return false
        if (fecha >= filtro.finExclusiveMillis) return false
        return true
    }

    private fun matchesQuery(entity: AveriaEntity, query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return true
        val objetivo = trimmed.lowercase(Locale.getDefault())
        return listOfNotNull(
            entity.caseId,
            entity.nombreAgencia,
            entity.causa,
            entity.observaciones,
            entity.clientesAfectados
        ).any { campo ->
            campo.lowercase(Locale.getDefault()).contains(objetivo)
        }
    }

    private fun toStartOfDay(millis: Long): Long {
        return Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
            .atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    private fun toEndExclusive(millis: Long): Long {
        val date: LocalDate = Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
        return date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    private suspend fun loadUsuarioActual() {
        val uid = auth.currentUser?.uid ?: return
        val local = roomRepo.obtenerUsuario(uid)
        val user = local ?: runCatching { roomRepo.upsertUserFromFirebase(uid) }.getOrNull()
        _usuario.emit(user)
        if (user != null) {
            pendingUser = user
            refreshPendingSelection()
            val regionItems = _regiones.value
            if (regionItems.isNotEmpty()) {
                applyPendingSelectionsIfPossible(regionItems)
            }
        }
    }

    private suspend fun requireUsuario(): UserEntity? {
        val current = _usuario.value
        if (current != null) return current
        loadUsuarioActual()
        val refreshed = _usuario.value
        if (refreshed == null) {
            _messages.emit(getApplication<Application>().getString(R.string.averia_error_usuario_no_autenticado))
        }
        return refreshed
    }

    fun getDraft(caseId: String): AveriaDraft? = drafts[caseId]

    fun saveDraft(caseId: String, draft: AveriaDraft) {
        if (draft.isEmpty()) {
            drafts.remove(caseId)
        } else {
            drafts[caseId] = draft
        }
    }

    fun clearDraft(caseId: String) {
        drafts.remove(caseId)
    }

    private fun nombreCompleto(user: UserEntity): String {
        val nombre = listOfNotNull(user.nombre, user.apellidosCompletos)
            .joinToString(" ")
            .trim()
        return nombre.ifBlank { user.email ?: user.uid }
    }

    private fun resolveData(ui: AveriaUI, data: AveriaActionData, user: UserEntity?): AveriaActionData {
        val nombre = data.atendidoPorNombre?.takeIf { it.isNotBlank() }
            ?: user?.let { nombreCompleto(it) }
        val uid = data.atendidoPorUid ?: user?.uid
        val vehiculo = data.vehiculo?.takeIf { it.isNotBlank() }
            ?: user?.placaVehiculo?.takeIf { !it.isNullOrBlank() }
            ?: ui.vehiculo
        val observaciones = data.observaciones?.takeIf { it.isNotBlank() }
        val materiales = if (data.materiales.isNotEmpty()) data.materiales else ui.materialesDetalle
        val tecnicos = if (data.tecnicos.isNotEmpty()) data.tecnicos else ui.tecnicosAtendieron
        val localizacion = data.localizacion ?: ui.localizacion
        val cliente = data.cliente ?: ui.cliente
        val tipo = data.tipoAfectacion
        val evidencias = if (data.evidencias.isNotEmpty()) data.evidencias else ui.evidencias
        val numeroMedidor = data.numeroMedidor?.takeIf { it.isNotBlank() } ?: ui.numeroMedidor
        val medidorCalle = data.medidorCalle?.takeIf { it.isNotBlank() } ?: ui.medidorCalle
        val medidorPueblo = data.medidorPueblo?.takeIf { it.isNotBlank() } ?: ui.medidorPueblo
        val medidorMetros = data.medidorMetros?.takeIf { it.isNotBlank() } ?: ui.medidorMetros
        val medidorPoste = data.medidorPoste?.takeIf { it.isNotBlank() } ?: ui.medidorPoste
        val horaInicio = data.horaInicioMillis
            ?: ui.horaAtencionInicio
            ?: ui.horaInicio
        val horaFinal = data.horaFinalMillis ?: ui.horaAtencionFinal ?: ui.horaFinal
        val horaLlegada = data.horaLlegadaMillis ?: ui.horaLlegada
        val kmInicio = data.kilometrajeInicio ?: ui.kilometrajeInicio
        val kmLlegada = data.kilometrajeLlegada ?: ui.kilometrajeLlegada
        val kmFinal = data.kilometrajeFinal ?: ui.kilometrajeFinal
        return data.copy(
            atendidoPorNombre = nombre,
            atendidoPorUid = uid,
            vehiculo = vehiculo,
            observaciones = observaciones,
            materiales = materiales,
            tecnicos = tecnicos,
            localizacion = localizacion,
            cliente = cliente,
            horaInicioMillis = horaInicio,
            horaFinalMillis = horaFinal,
            horaLlegadaMillis = horaLlegada,
            kilometrajeInicio = kmInicio,
            kilometrajeLlegada = kmLlegada,
            kilometrajeFinal = kmFinal,
            tipoAfectacion = tipo,
            numeroMedidor = numeroMedidor,
            medidorCalle = medidorCalle,
            medidorPueblo = medidorPueblo,
            medidorMetros = medidorMetros,
            medidorPoste = medidorPoste,
            evidencias = evidencias
        )
    }

    private fun buildAveriaUi(entity: AveriaEntity, direccion: String?): AveriaUI {
        val materialesDetalle = MaterialesSerializer.fromJson(entity.materialesDetalleJson)
        val materialesResumen = entity.materialesTexto
            ?: MaterialesSerializer.toSummary(materialesDetalle)
        val tecnicosAtendieron = TecnicosSerializer.fromJson(entity.tecnicosAtendieronJson)
        val evidencias = EvidenciasSerializer.fromJson(entity.evidenciasJson)
        return AveriaUI(
            id = entity.caseId,
            descripcion = "Avería #${entity.caseId}",
            fechaMillis = entity.fechaInicioMillis,
            causa = entity.causa?.trim().orEmpty(),
            estado = entity.estado,
            tecnico = entity.tecnicoAsignadoNombre ?: "",
            tecnicoUid = entity.tecnicoAsignadoUid,
            atendidoPor = entity.atendidoPorNombre ?: "",
            atendidoPorUid = entity.atendidoPorUid,
            observaciones = entity.observaciones?.trim().orEmpty(),
            nise = entity.nise ?: "",
            agencia = entity.nombreAgencia ?: (entity.agencia ?: ""),
            region = entity.region ?: "",
            zonaTag = entity.agenciaTag,
            lat = entity.lat ?: 0.0,
            lng = entity.lng ?: 0.0,
            vehiculo = entity.vehiculoAsignado,
            materialesResumen = materialesResumen,
            materialesDetalle = materialesDetalle,
            horaAtencionInicio = entity.atencionHoraInicioMillis,
            horaAtencionFinal = entity.atencionHoraFinalMillis,
            horaLlegada = entity.horaLlegadaMillis,
            kilometrajeInicio = entity.kilometrajeInicio,
            kilometrajeLlegada = entity.kilometrajeLlegada,
            kilometrajeFinal = entity.kilometrajeFinal,
            horaInicio = entity.horaInicioMillis,
            horaFinal = entity.horaFinalMillis,
            cliente = entity.cliente?.trim(),
            localizacion = entity.localizacion?.trim(),
            direccion = direccion ?: entity.direccion?.trim(),
            tecnicosAtendieron = tecnicosAtendieron,
            tipoAfectacion = TipoAfectacion.fromRaw(entity.tipoAfectacion),
            numeroMedidor = entity.numeroMedidor?.trim(),
            medidorCalle = entity.medidorCalle?.trim(),
            medidorPueblo = entity.medidorPueblo?.trim(),
            medidorMetros = entity.medidorMetros?.trim(),
            medidorPoste = entity.medidorPoste?.trim(),
            causaClor= entity.causaClor?.trim(),
            estadoClor = entity.estadoClor?.trim(),
            observacionesClor = entity.observacionesClor?.trim(),
            evidencias = evidencias
        )
    }

    private fun buildResolvedUi(base: AveriaUI, data: AveriaActionData): AveriaUI {
        val materialesResumen = MaterialesSerializer.toSummary(data.materiales)
            .ifBlank { base.materialesResumen }
        val observaciones = data.observaciones?.trim().orEmpty()
        val atendido = data.atendidoPorNombre?.trim().orEmpty()
        val horaInicio = data.horaInicioMillis ?: base.horaInicio
        val horaFinal = data.horaFinalMillis ?: base.horaFinal
        return base.copy(
            causa = data.causa,
            estado = "Resuelta",
            atendidoPor = atendido,
            atendidoPorUid = data.atendidoPorUid,
            observaciones = observaciones,
            vehiculo = data.vehiculo ?: base.vehiculo,
            materialesResumen = materialesResumen,
            materialesDetalle = data.materiales,
            horaAtencionInicio = data.horaInicioMillis ?: base.horaAtencionInicio,
            horaAtencionFinal = data.horaFinalMillis ?: base.horaAtencionFinal,
            horaLlegada = data.horaLlegadaMillis ?: base.horaLlegada,
            kilometrajeInicio = data.kilometrajeInicio ?: base.kilometrajeInicio,
            kilometrajeLlegada = data.kilometrajeLlegada ?: base.kilometrajeLlegada,
            kilometrajeFinal = data.kilometrajeFinal ?: base.kilometrajeFinal,
            horaInicio = horaInicio,
            horaFinal = horaFinal,
            cliente = data.cliente ?: base.cliente,
            localizacion = data.localizacion ?: base.localizacion,
            tecnicosAtendieron = data.tecnicos,
            tipoAfectacion = data.tipoAfectacion,
            numeroMedidor = data.numeroMedidor,
            medidorCalle = data.medidorCalle,
            medidorPueblo = data.medidorPueblo,
            medidorMetros = data.medidorMetros,
            medidorPoste = data.medidorPoste,
            evidencias = data.evidencias
        )
    }

    private fun queueAddressLookup(entity: AveriaEntity) {
        val lat = entity.lat ?: return
        val lng = entity.lng ?: return
        if (lat == 0.0 && lng == 0.0) return
        val caseId = entity.caseId
        synchronized(pendingAddressLookups) {
            if (_addresses.value.containsKey(caseId) || pendingAddressLookups.contains(caseId)) {
                return
            }
            pendingAddressLookups.add(caseId)
        }
        viewModelScope.launch(Dispatchers.IO) {
            val address = resolveAddress(lat, lng)
            synchronized(pendingAddressLookups) {
                pendingAddressLookups.remove(caseId)
            }
            if (!address.isNullOrBlank()) {
                val cleaned = sanitizeAddress(address) ?: address
                _addresses.update { it + (caseId to cleaned) }
                runCatching { repo.persistDireccion(caseId, cleaned) }
            }
        }
    }

    private fun resolveAddress(lat: Double, lng: Double): String? {
        if (!Geocoder.isPresent()) return null
        return try {
            val geocoder = Geocoder(getApplication(), Locale.getDefault())
            @Suppress("DEPRECATION")
            val results = geocoder.getFromLocation(lat, lng, 1)
            val address = results?.firstOrNull() ?: return null

            val localityParts = buildList {
                address.subLocality?.takeIf { it.isNotBlank() }?.let { add(it) }
                address.locality?.takeIf { it.isNotBlank() }?.let { add(it) }
                address.subAdminArea?.takeIf { it.isNotBlank() && !contains(it) }?.let { add(it) }
                address.adminArea?.takeIf { it.isNotBlank() && !contains(it) }?.let { add(it) }
            }

            val locality = localityParts.joinToString(", ")

            val street = buildList {
                address.thoroughfare?.takeIf { it.isNotBlank() && !isPlusCode(it) }?.let { add(it) }
                address.subThoroughfare?.takeIf { it.isNotBlank() && !isPlusCode(it) }?.let { add(it) }
            }.joinToString(" ").trim()

            val feature = address.featureName?.takeIf { it.isNotBlank() && !isPlusCode(it) }
            val cleanedFeature = sanitizeAddress(feature)

            val composed = buildList {
                sanitizeAddress(street)?.takeIf { it.isNotBlank() }?.let { add(it) }
                cleanedFeature?.takeIf { it.isNotBlank() && !contains(it) }?.let { add(it) }
                sanitizeAddress(locality)?.takeIf { it.isNotBlank() }?.let { add(it) }
            }.joinToString(", ").ifBlank {
                locality.ifBlank { cleanedFeature }
            }

            composed?.takeIf { it.isNotBlank() }
        } catch (t: Throwable) {
            Log.w(TAG, "No se pudo obtener la dirección para ($lat,$lng)", t)
            null
        }
    }

    private fun sanitizeAddress(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val normalized = raw.trim().replace("  ", " ")
        val parts = normalized.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val plusCode = parts.firstOrNull { isPlusCode(it) }
        val nonPlus = parts.filterNot { isPlusCode(it) }
        val base = nonPlus.joinToString(", ").trim().ifBlank { null }

        return when {
            base != null && plusCode != null -> "$base cerca de $plusCode"
            base != null -> base
            plusCode != null -> "Cerca de $plusCode"
            else -> null
        }
    }

    private fun isPlusCode(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val trimmed = value.trim()
        if (!trimmed.contains('+')) return false
        return trimmed.any { it.isLetterOrDigit() }
    }

    private suspend fun ensurePropietario(ui: AveriaUI): UserEntity? {
        val user = requireUsuario() ?: return null
        val placaUsuario = user.placaVehiculo?.trim()
        val placaAveria = ui.vehiculo?.trim()
        val pertenecePorVehiculo = !placaUsuario.isNullOrBlank() && !placaAveria.isNullOrBlank() &&
            placaUsuario.equals(placaAveria, ignoreCase = true)
        val estado = Estado.fromLabel(ui.estado)
        val asignadoA = ui.ownerUidFor(estado)
        if (!asignadoA.isNullOrBlank() && asignadoA != user.uid && !pertenecePorVehiculo) {
            _messages.emit(getApplication<Application>().getString(R.string.averia_error_no_autorizado))
            return null
        }
        if (asignadoA.isNullOrBlank() && !placaAveria.isNullOrBlank() && !pertenecePorVehiculo) {
            _messages.emit(getApplication<Application>().getString(R.string.averia_error_no_autorizado))
            return null
        }
        return user
    }

    fun resolveUserRegionLabel(user: UserEntity?): String? {
        if (user == null) return null
        val regionNombre = user.regionNombre?.trim().takeIf { !it.isNullOrBlank() }
        val regionId = user.region?.trim().takeIf { !it.isNullOrBlank() }
        val subregionId = user.subregion?.trim().takeIf { !it.isNullOrBlank() }
        val regionFromId = regionId?.let { id ->
            cachedRegiones.firstOrNull { it.id.equals(id, ignoreCase = true) }?.nombre
        }
        val regionFromSubregion = subregionId
            ?.let { subId ->
                cachedSubregiones.firstOrNull { it.id.equals(subId, ignoreCase = true) }?.regionId
            }
            ?.let { regionFromSubId ->
                cachedRegiones.firstOrNull { it.id.equals(regionFromSubId, ignoreCase = true) }?.nombre
            }
        return regionNombre ?: regionFromId ?: regionFromSubregion ?: regionId
    }

    private fun normalizeSubregionKey(value: String?): String? {
        val normalized = normalizeAveriaText(value)
        val compact = normalized.replace("[^a-z0-9]".toRegex(), "")
        return compact.takeIf { it.isNotBlank() }
    }

    private fun normalizeRegionKey(value: String?): String? {
        val normalized = normalizeAveriaText(value)
        val compact = normalized.replace("[^a-z0-9]".toRegex(), "")
        return compact.takeIf { it.isNotBlank() }
    }

    private fun isSubregionMismatch(ui: AveriaUI): Boolean {
        val user = _usuario.value ?: return false
        val userRegionKeys = listOfNotNull(
            normalizeRegionKey(user.regionNombre),
            normalizeRegionKey(user.region),
            user.subregion
                ?.let { subId -> cachedSubregiones.firstOrNull { it.id.equals(subId, ignoreCase = true) }?.regionId }
                ?.let { regionId -> cachedRegiones.firstOrNull { it.id.equals(regionId, ignoreCase = true) }?.nombre }
                ?.let { normalizeRegionKey(it) }
        ).distinct()
        val averiaRegionKeys = listOfNotNull(
            normalizeRegionKey(ui.region),
            ui.zonaTag
                ?.let { zona ->
                    cachedSubregiones.firstOrNull {
                        it.id.equals(zona, ignoreCase = true) || it.nombre.equals(zona, ignoreCase = true)
                    }?.regionId
                }
                ?.let { regionId -> cachedRegiones.firstOrNull { it.id.equals(regionId, ignoreCase = true) }?.nombre }
                ?.let { normalizeRegionKey(it) }
        ).distinct()
        if (userRegionKeys.isNotEmpty() && averiaRegionKeys.isNotEmpty()) {
            val sameRegion = userRegionKeys.any { userKey ->
                averiaRegionKeys.any { averiaKey ->
                    averiaKey.contains(userKey) || userKey.contains(averiaKey)
                }
            }
            if (!sameRegion) return true
        }

        return false
    }

    fun isSubregionAllowed(ui: AveriaUI): Boolean = !isSubregionMismatch(ui)

    private suspend fun ensureSubregionAllowed(ui: AveriaUI): Boolean {
        if (isSubregionMismatch(ui)) {
            _messages.emit(getApplication<Application>().getString(R.string.averia_error_region_diferente))
            return false
        }
        return true
    }

    private fun isSupervisorRole(user: UserEntity): Boolean {
        val role = user.rol?.trim()?.lowercase(Locale.getDefault()).orEmpty()
        return role == "supervisor" || role == "administrador"
    }

    fun canAssignToCrew(): Boolean = _usuario.value?.let(::isSupervisorRole) == true

    fun assignToSelf(ui: AveriaUI) {
        onToggleAsignacion(ui)
    }

    fun assignToVehiculo(ui: AveriaUI, vehiculo: String) {
        viewModelScope.launch {
            if (!ensureSubregionAllowed(ui)) return@launch
            if (Estado.fromLabel(ui.estado) != Estado.PENDIENTE) return@launch
            val user = requireUsuario() ?: return@launch
            if (!isSupervisorRole(user)) {
                _messages.tryEmit(getApplication<Application>().getString(R.string.averia_error_no_autorizado))
                return@launch
            }
            val vehiculoLimpio = vehiculo.trim()
            val nombreCuadrilla = "Cuadrilla $vehiculoLimpio"
            repo.asignar(ui.id, "", nombreCuadrilla, vehiculoLimpio)
            _messages.tryEmit(getApplication<Application>().getString(R.string.averia_exito_asignada_cuadrilla, vehiculo.trim()))
            AveriasSyncWorker.triggerNow(getApplication(), showSyncNotification = false)
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            isLoading.emit(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    repo.syncPendientesConFirebase()
                    repo.pullFromFirebaseOnce()
                }
                roomRepo.refreshUsuarioActual()
            }.onFailure { error ->
                Log.e(TAG, "Error al refrescar averías manualmente", error)
                _messages.tryEmit(
                    error.localizedMessage
                        ?: getApplication<Application>().getString(R.string.averia_error_sync)
                )
            }
            isLoading.emit(false)
        }
    }

    fun onToggleAsignacion(ui: AveriaUI) {
        viewModelScope.launch {
            if (!ensureSubregionAllowed(ui)) return@launch
            when (Estado.fromLabel(ui.estado)) {
                Estado.PENDIENTE -> {
                    val user = requireUsuario() ?: return@launch
                    val nombreTecnico = nombreCompleto(user)
                    val vehiculo = user.placaVehiculo?.takeIf { !it.isNullOrBlank() }
                    repo.asignar(ui.id, user.uid, nombreTecnico, vehiculo)
                    _messages.tryEmit(getApplication<Application>().getString(R.string.averia_exito_asignada))
                }
                Estado.ASIGNADA -> {
                    ensurePropietario(ui) ?: return@launch
                    repo.revertirAPendiente(ui.id)
                    _messages.tryEmit(getApplication<Application>().getString(R.string.averia_exito_desasignada))
                }
                else -> return@launch
            }
        }
    }

    fun onAutoAsignarPendiente(ui: AveriaUI) {
        viewModelScope.launch {
            if (!ensureSubregionAllowed(ui)) return@launch
            if (Estado.fromLabel(ui.estado) != Estado.PENDIENTE) return@launch
            if (!ui.tecnicoUid.isNullOrBlank()) return@launch
            val user = requireUsuario() ?: return@launch
            val nombreTecnico = nombreCompleto(user)
            val vehiculo = user.placaVehiculo?.takeIf { !it.isNullOrBlank() }
            repo.asignar(ui.id, user.uid, nombreTecnico, vehiculo)
            _messages.tryEmit(getApplication<Application>().getString(R.string.averia_exito_asignada))
        }
    }

    fun onAtender(ui: AveriaUI, data: AveriaActionData) {
        viewModelScope.launch {
            if (!ensureSubregionAllowed(ui)) return@launch
            val estado = Estado.fromLabel(ui.estado)
            if (estado != Estado.ASIGNADA && estado != Estado.PENDIENTE) return@launch
            val user = when (estado) {
                Estado.ASIGNADA -> ensurePropietario(ui)
                Estado.PENDIENTE -> requireUsuario()
                else -> null
            } ?: return@launch
            if (estado == Estado.PENDIENTE && ui.tecnicoUid.isNullOrBlank()) {
                val nombreTecnico = nombreCompleto(user)
                val vehiculo = user.placaVehiculo?.takeIf { !it.isNullOrBlank() }
                repo.asignar(ui.id, user.uid, nombreTecnico, vehiculo)
            }
            val resolved = resolveData(ui, data, user)
            repo.enAtencion(ui.id, resolved)
            _messages.tryEmit(getApplication<Application>().getString(R.string.averia_exito_en_atencion))
            clearDraft(ui.id)
            AveriasSyncWorker.triggerNow(getApplication(), showSyncNotification = false)
        }
    }

    fun onCancelarAtencion(ui: AveriaUI) {
        viewModelScope.launch {
            if (!ensureSubregionAllowed(ui)) return@launch
            if (Estado.fromLabel(ui.estado) != Estado.EN_ATENCION) return@launch
            ensurePropietario(ui) ?: return@launch
            repo.revertirAPendiente(ui.id)
            clearDraft(ui.id)
            _messages.tryEmit(getApplication<Application>().getString(R.string.averia_exito_cancelada))
        }
    }

    fun onRevertirAnulada(ui: AveriaUI) {
        viewModelScope.launch {
            if (!ensureSubregionAllowed(ui)) return@launch
            if (Estado.fromLabel(ui.estado) != Estado.ANULADA) return@launch
            val ownerUid = ui.ownerUidFor(Estado.ANULADA)
            if (!ownerUid.isNullOrBlank()) {
                ensurePropietario(ui) ?: return@launch
            }
            repo.revertirAPendiente(ui.id)
            _messages.tryEmit(getApplication<Application>().getString(R.string.averia_exito_revertida))
        }
    }

    fun onRevertirResuelta(ui: AveriaUI) {
        viewModelScope.launch {
            if (!ensureSubregionAllowed(ui)) return@launch
            if (Estado.fromLabel(ui.estado) != Estado.RESUELTA) return@launch
            val user = requireUsuario() ?: return@launch
            if (!isSupervisorRole(user)) {
                _messages.tryEmit(getApplication<Application>().getString(R.string.averia_error_revertir_no_autorizado))
                return@launch
            }
            repo.revertirAPendiente(ui.id)
            clearDraft(ui.id)
            _messages.tryEmit(getApplication<Application>().getString(R.string.averia_exito_revertida_resuelta))
            AveriasSyncWorker.triggerNow(getApplication(), showSyncNotification = false)
        }
    }

    fun onResolver(ui: AveriaUI, data: AveriaActionData, esActualizacion: Boolean = false) {
        if (data.causa.isBlank()) {
            _messages.tryEmit(getApplication<Application>().getString(R.string.averia_error_causa_requerida))
            return
        }
        viewModelScope.launch {
            if (!ensureSubregionAllowed(ui)) return@launch
            val estadoActual = Estado.fromLabel(ui.estado)
            if (estadoActual != Estado.EN_ATENCION && estadoActual != Estado.RESUELTA) return@launch
            val user = ensurePropietario(ui) ?: return@launch
            val resolved = resolveData(ui, data, user)
            repo.cerrar(ui.id, resolved)
            val address = _addresses.value[ui.id] ?: ui.direccion
            val baseUi = if (!address.isNullOrBlank()) ui.copy(direccion = address) else ui
            val shareItem = buildResolvedUi(baseUi, resolved)
            _shareRequests.tryEmit(shareItem)
            val mensaje = if (esActualizacion) {
                getApplication<Application>().getString(R.string.averia_exito_cambios_actualizados)
            } else {
                getApplication<Application>().getString(R.string.averia_exito_resuelta)
            }
            _messages.tryEmit(mensaje)
            clearDraft(ui.id)
            AveriasSyncWorker.triggerNow(getApplication(), showSyncNotification = false)
        }
    }

    fun onEliminarResuelta(ui: AveriaUI) {
        viewModelScope.launch {
            if (!ensureSubregionAllowed(ui)) return@launch
            if (Estado.fromLabel(ui.estado) != Estado.RESUELTA) return@launch
            ensurePropietario(ui) ?: return@launch
            runCatching { repo.eliminarAveria(ui.id) }
                .onSuccess {
                    _messages.tryEmit(getApplication<Application>().getString(R.string.averia_exito_eliminada))
                    AveriasSyncWorker.triggerNow(getApplication(), showSyncNotification = false)
                }
                .onFailure {
                    Log.e(TAG, "No se pudo eliminar la avería ${ui.id}", it)
                    _messages.tryEmit(getApplication<Application>().getString(R.string.averia_error_eliminar))
                }
        }
    }

    fun onAnularPendiente(ui: AveriaUI) {
        viewModelScope.launch {
            if (!ensureSubregionAllowed(ui)) return@launch
            if (Estado.fromLabel(ui.estado) != Estado.PENDIENTE) return@launch
            ensurePropietario(ui) ?: return@launch
            runCatching { repo.anular(ui.id) }
                .onSuccess {
                    _messages.tryEmit(getApplication<Application>().getString(R.string.averia_exito_anulada))
                    AveriasSyncWorker.triggerNow(getApplication(), showSyncNotification = false)
                }
                .onFailure {
                    Log.e(TAG, "No se pudo anular la avería ${ui.id}", it)
                    _messages.tryEmit(getApplication<Application>().getString(R.string.averia_error_eliminar))
                }
        }
    }

    private fun buildAgencias(regionId: String?): List<AgenciaUI> {
        val filtered = cachedAgencias
            .filter { agency ->
                if (regionId.isNullOrBlank()) return@filter true
                val agencyRegion = agency.regionId
                    ?: cachedSubregiones.firstOrNull { sub ->
                        sub.id.equals(agency.subregion, ignoreCase = true)
                    }?.regionId
                agencyRegion.equals(regionId, ignoreCase = true)
            }
            .sortedBy { it.nombre }
            .distinctBy { agency ->
                agency.id.takeIf { !it.isNullOrBlank() }?.lowercase(Locale.getDefault())
                    ?: agency.nombre.lowercase(Locale.getDefault())
            }
            .map { agency ->
                val id = agency.id.takeIf { !it.isNullOrBlank() } ?: agency.nombre
                AgenciaUI(id, agency.nombre)
            }

        return listOf(AgenciaUI(null, allAgenciesLabel)) + filtered
    }

    private fun observeCatalogos() {
        viewModelScope.launch {
            roomRepo.observarCatalogosGenerales().collectLatest { (regiones, subregiones, agencias) ->
                cachedRegiones = regiones
                cachedSubregiones = subregiones
                cachedAgencias = agencias
                refreshPendingSelection()

                if (regiones.isEmpty() && subregiones.isEmpty() && agencias.isEmpty()) {
                    if (!catalogosSyncAttempted) {
                        catalogosSyncAttempted = true
                        syncCatalogosGenerales()
                    }
                    _regiones.emit(listOf(RegionUI(null, allRegionsLabel)))
                    _agencias.emit(listOf(AgenciaUI(null, allAgenciesLabel)))
                    return@collectLatest
                } else {
                    catalogosSyncAttempted = false
                }

                val regionItems = listOf(RegionUI(null, allRegionsLabel)) +
                    regiones.sortedBy { it.nombre }
                        .distinctBy { it.id }
                        .map { RegionUI(it.id, it.nombre) }
                _regiones.emit(regionItems)

                val sugerencias = buildAgencias(null)
                    .drop(1)
                    .map { it.nombreVisible }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
                _notificationSuggestions.value = sugerencias

                val applied = applyPendingSelectionsIfPossible(regionItems)
                if (!applied) {
                    val selectedRegion = regionItems.firstOrNull { it.id == _regionSeleccionada.value.id }
                        ?: regionItems.first()
                    _regionSeleccionada.emit(selectedRegion)

                    val agenciasItems = buildAgencias(selectedRegion.id)
                    _agencias.emit(agenciasItems)

                    val selectedAgency = agenciasItems.firstOrNull { it.id == _agenciaSeleccionada.value.id }
                        ?: agenciasItems.first()
                    _agenciaSeleccionada.emit(selectedAgency)
                }

                regionKeywords.value = computeRegionKeywords(regiones, subregiones, agencias)
            }
        }
    }

    private fun selectPendingAgency(options: List<AgenciaUI>): AgenciaUI {
        if (options.isEmpty()) return AgenciaUI(null, allAgenciesLabel)
        val pendingId = pendingAgencyId?.takeIf { it.isNotBlank() }
            ?: prefs.getString(PREF_AGENCIA_ID, null)?.takeIf { it.isNotBlank() }
        val pendingName = pendingAgencyName?.takeIf { it.isNotBlank() }
            ?: prefs.getString(PREF_AGENCIA_NAME, null)?.takeIf { it.isNotBlank() }
        return options.firstOrNull { agency ->
            (pendingId != null && agency.id?.equals(pendingId, ignoreCase = true) == true) ||
                    (pendingName != null && agency.nombreVisible.equals(pendingName, ignoreCase = true))
        } ?: options.first()
    }

    private fun savePreferredRegion(region: RegionUI) {
        prefs.edit().apply {
            if (region.id.isNullOrBlank()) {
                remove(PREF_REGION_ID)
                remove(PREF_REGION_NAME)
            } else {
                putString(PREF_REGION_ID, region.id)
                putString(PREF_REGION_NAME, region.nombreVisible)
            }
        }.apply()
    }

    private fun savePreferredAgency(agency: AgenciaUI) {
        prefs.edit().apply {
            if (agency.id.isNullOrBlank()) {
                remove(PREF_AGENCIA_ID)
                remove(PREF_AGENCIA_NAME)
            } else {
                putString(PREF_AGENCIA_ID, agency.id)
                putString(PREF_AGENCIA_NAME, agency.nombreVisible)
            }
        }.apply()
    }

    private fun clearPendingAgencySelection() {
        pendingAgencyId = null
        pendingAgencyName = null
    }

    private fun refreshPendingSelection() {
        if (filtersTouchedByUser) return

        if (pendingRegionId.isNullOrBlank()) {
            pendingRegionId = prefs.getString(PREF_REGION_ID, null)?.takeIf { it.isNotBlank() }
        }
        if (pendingRegionName.isNullOrBlank()) {
            pendingRegionName = prefs.getString(PREF_REGION_NAME, null)?.takeIf { it.isNotBlank() }
        }
        if (pendingAgencyId.isNullOrBlank()) {
            pendingAgencyId = prefs.getString(PREF_AGENCIA_ID, null)?.takeIf { it.isNotBlank() }
        }
        if (pendingAgencyName.isNullOrBlank()) {
            pendingAgencyName = prefs.getString(PREF_AGENCIA_NAME, null)?.takeIf { it.isNotBlank() }
        }

        val hasPendingPreference = !pendingRegionId.isNullOrBlank() ||
            !pendingRegionName.isNullOrBlank() ||
            !pendingAgencyId.isNullOrBlank() ||
            !pendingAgencyName.isNullOrBlank()
        if (hasPendingPreference) return

        pendingUser?.let { user ->
            val regionIdFromUser = user.region?.takeIf { it.isNotBlank() }
                ?: user.subregion?.let { subId ->
                    cachedSubregiones.firstOrNull { it.id.equals(subId, ignoreCase = true) }?.regionId
                }
            if (!regionIdFromUser.isNullOrBlank()) {
                pendingRegionId = regionIdFromUser
                pendingRegionName = user.regionNombre?.takeIf { it.isNotBlank() }
                    ?: cachedRegiones.firstOrNull { it.id.equals(regionIdFromUser, ignoreCase = true) }?.nombre
            } else {
                val regionNameFromUser = user.regionNombre?.takeIf { it.isNotBlank() }
                if (!regionNameFromUser.isNullOrBlank()) {
                    pendingRegionName = regionNameFromUser
                }
            }

            val agencyIdFromUser = user.agenciaId?.takeIf { it.isNotBlank() }
            val agencyNameFromUser = user.agencia?.takeIf { it.isNotBlank() }
            if (!agencyIdFromUser.isNullOrBlank()) pendingAgencyId = agencyIdFromUser
            if (!agencyNameFromUser.isNullOrBlank()) pendingAgencyName = agencyNameFromUser
        }
    }

    private suspend fun applyPendingSelectionsIfPossible(regionItems: List<RegionUI>): Boolean {
        val regionId = pendingRegionId?.takeIf { it.isNotBlank() }
        val regionName = pendingRegionName?.takeIf { it.isNotBlank() }
        val targetRegion = when {
            regionId != null -> regionItems.firstOrNull { it.id?.equals(regionId, ignoreCase = true) == true }
            regionName != null -> regionItems.firstOrNull { it.nombreVisible.equals(regionName, ignoreCase = true) }
            else -> null
        } ?: return false

        _regionSeleccionada.emit(targetRegion)

        val agencias = buildAgencias(targetRegion.id)
        _agencias.emit(agencias)

        val agency = selectPendingAgency(agencias)
        _agenciaSeleccionada.emit(agency)

        savePreferredRegion(targetRegion)
        savePreferredAgency(agency)
        clearPendingSelections()
        return true
    }

    private fun clearPendingSelections() {
        pendingRegionId = null
        pendingRegionName = null
        pendingAgencyId = null
        pendingAgencyName = null
    }

    private suspend fun syncCatalogosGenerales() {
        runCatching { roomRepo.syncCatalogosGenerales() }
            .onFailure { Log.w(TAG, "No se pudieron sincronizar catálogos generales", it) }
    }

    private fun computeRegionKeywords(
        regiones: List<RegionEntity>,
        subregiones: List<SubregionesEntity>,
        agencias: List<AgenciaEntity>
    ): Map<String, List<String>> {
        if (regiones.isEmpty()) return emptyMap()
        val subregionesPorRegion = subregiones.groupBy { it.regionId }
        val agenciasPorRegion = agencias.groupBy { agency ->
            agency.regionId
                ?: subregiones.firstOrNull { it.id.equals(agency.subregion, ignoreCase = true) }
                    ?.regionId
        }
        return regiones.associate { region ->
            val keywords = mutableSetOf(region.nombre.normalize())
            subregionesPorRegion[region.id]?.forEach { keywords += it.nombre.normalize() }
            agenciasPorRegion[region.id]?.forEach { keywords += it.nombre.normalize() }
            region.id to keywords.toList()
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}

private fun String.normalize(): String {
    val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
    return normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        .lowercase(Locale.getDefault())
}
