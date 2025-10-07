package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Arasoftsolutions.tecniapp_ice.BuildConfig
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AgenciaEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AveriaEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.MaterialEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.MedidorEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.RegionEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.SubregionesEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.TecnicoEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.UserEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.apellidosCompletos
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.Database.sync.FirebaseSyncManager
import com.Arasoftsolutions.tecniapp_ice.R
import com.google.firebase.auth.FirebaseAuth
import java.text.Normalizer
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

enum class Estado {
    PENDIENTE, ASIGNADA, EN_ATENCION, RESUELTA;

    companion object {
        fun fromLabel(value: String): Estado {
            val normalized = value.lowercase(Locale.getDefault())
            return when {
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
    private val auth = FirebaseAuth.getInstance()
    private val notificationManager = NotificationManagerCompat.from(app)
    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val q = MutableStateFlow("")
    private val estado = MutableStateFlow<Estado?>(null)

    private val allRegionsLabel = app.getString(R.string.averias_filtro_region_todas)
    private val allAgenciesLabel = app.getString(R.string.averias_filtro_agencia_todas)

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
    private val _usuario = MutableStateFlow<UserEntity?>(null)
    val usuarioActual: StateFlow<UserEntity?> = _usuario.asStateFlow()
    private val _vehiculos = MutableStateFlow<List<String>>(emptyList())
    val vehiculosDisponibles: StateFlow<List<String>> = _vehiculos.asStateFlow()
    private val _materiales = MutableStateFlow<List<MaterialEntity>>(emptyList())
    val materialesDisponibles: StateFlow<List<MaterialEntity>> = _materiales.asStateFlow()
    private val _tecnicos = MutableStateFlow<List<TecnicoEntity>>(emptyList())
    val tecnicosDisponibles: StateFlow<List<TecnicoEntity>> = _tecnicos.asStateFlow()
    private val _medidorEstado = MutableStateFlow<MedidorLookupState>(MedidorLookupState.Idle)
    val medidorEstado: StateFlow<MedidorLookupState> = _medidorEstado.asStateFlow()

    private var syncJob: Job? = null
    private var cachedRegiones: List<RegionEntity> = emptyList()
    private var cachedSubregiones: List<SubregionesEntity> = emptyList()
    private var cachedAgencias: List<AgenciaEntity> = emptyList()
    private val regionKeywords = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    private var pendingUser: UserEntity? = null
    private var pendingRegionId: String? = prefs.getString(PREF_REGION_ID, null)
    private var pendingRegionName: String? = prefs.getString(PREF_REGION_NAME, null)
    private var pendingAgencyId: String? = prefs.getString(PREF_AGENCIA_ID, null)
    private var pendingAgencyName: String? = prefs.getString(PREF_AGENCIA_NAME, null)

    private data class FilterConfig(
        val query: String,
        val estado: Estado?,
        val region: RegionUI,
        val agencia: AgenciaUI
    )

    val uiState: StateFlow<AveriasUiState> =
        combine(
            q.debounce(250).map { it.trim() }.distinctUntilChanged(),
            estado,
            _regionSeleccionada,
            _agenciaSeleccionada
        ) { qv, est, regionSel, agenciaSel ->
            FilterConfig(qv, est, regionSel, agenciaSel)
        }
            .flatMapLatest { config ->
                repo.observe(emptyList(), "", config.query)
                    .map { list ->
                        list.filter { entity ->
                            matchesEstado(entity, config.estado) &&
                                    matchesRegion(entity, config.region) &&
                                    matchesAgencia(entity, config.agencia)
                        }
                    }
            }
            .map { list ->
                AveriasUiState(
                    loading = isLoading.value,
                    items = list.map { e ->
                        val materialesDetalle = MaterialesSerializer.fromJson(e.materialesDetalleJson)
                        val materialesResumen = e.materialesTexto
                            ?: MaterialesSerializer.toSummary(materialesDetalle)
                        val tecnicosAtendieron = TecnicosSerializer.fromJson(e.tecnicosAtendieronJson)
                        AveriaUI(
                            id = e.caseId,
                            descripcion = "Avería #${e.caseId}",
                            fechaMillis = e.fechaInicioMillis,
                            causa = e.causa?.trim().orEmpty(),
                            estado = e.estado,
                            tecnico = e.tecnicoAsignadoNombre ?: "",
                            tecnicoUid = e.tecnicoAsignadoUid,
                            atendidoPor = e.atendidoPorNombre ?: "",
                            atendidoPorUid = e.atendidoPorUid,
                            observaciones = e.observaciones?.trim().orEmpty(),
                            nise = e.nise ?: "",
                            agencia = e.nombreAgencia ?: (e.agencia ?: ""),
                            region = e.region ?: "",
                            zonaTag = e.agenciaTag,
                            lat = e.lat ?: 0.0,
                            lng = e.lng ?: 0.0,
                            vehiculo = e.vehiculoAsignado,
                            materialesResumen = materialesResumen,
                            materialesDetalle = materialesDetalle,
                            horaAtencionInicio = e.atencionHoraInicioMillis,
                            horaAtencionFinal = e.atencionHoraFinalMillis,
                            kilometrajeInicio = e.kilometrajeInicio,
                            kilometrajeFinal = e.kilometrajeFinal,
                            horaInicio = e.horaInicioMillis,
                            horaFinal = e.horaFinalMillis,
                            cliente = e.cliente?.trim(),
                            localizacion = e.localizacion?.trim(),
                            tecnicosAtendieron = tecnicosAtendieron,
                            tipoAfectacion = TipoAfectacion.fromRaw(e.tipoAfectacion),
                            numeroMedidor = e.numeroMedidor?.trim(),
                            medidorCalle = e.medidorCalle?.trim(),
                            medidorPueblo = e.medidorPueblo?.trim(),
                            medidorMetros = e.medidorMetros?.trim(),
                            medidorPoste = e.medidorPoste?.trim()
                        )
                    }
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AveriasUiState())

    init {
        repo.startRealtimeListener()
        createNotificationChannel()
        observeCatalogos()
        viewModelScope.launch { loadUsuarioActual() }
        viewModelScope.launch { syncCatalogosGenerales() }
        viewModelScope.launch {
            usuarioActual
                .filterNotNull()
                .flatMapLatest { user ->
                    val subregion = user.subregion
                    if (subregion.isNullOrBlank()) flowOf(emptyList())
                    else roomRepo.observarVehiculos(subregion)
                }
                .collectLatest { vehiculos ->
                    val preferido = _usuario.value?.placaVehiculo?.takeIf { !it.isNullOrBlank() }?.trim()
                    val lista = buildList {
                        if (!preferido.isNullOrBlank()) add(preferido)
                        vehiculos.forEach { add(it.placa.toString()) }
                    }.distinct()
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
                _tecnicos.value = lista
            }
        }
    }

    fun setQuery(value: String) = viewModelScope.launch { q.emit(value) }
    fun setEstado(value: Estado?) = viewModelScope.launch { estado.emit(value) }


    fun setRegionIndex(idx: Int) = viewModelScope.launch {
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
                val subregion = user.subregion?.takeIf { it.isNotBlank() }
                if (subregion.isNullOrBlank()) {
                    _medidorEstado.value = MedidorLookupState.Error(
                        getApplication<Application>().getString(R.string.medidor_estado_sin_subregion)
                    )
                    return@launch
                }
                val medidor = withContext(Dispatchers.IO) {
                    roomRepo.buscarMedidorPorNumero(trimmed)
                        ?: firebaseSync.buscarMedidorEnFirebase(subregion, trimmed)?.also {
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

    private fun matchesEstado(entity: AveriaEntity, estadoSeleccionado: Estado?): Boolean {
        if (estadoSeleccionado == null) return true
        val estadoEntity = Estado.fromLabel(entity.estado)
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
        val numeroMedidor = data.numeroMedidor?.takeIf { it.isNotBlank() } ?: ui.numeroMedidor
        val medidorCalle = data.medidorCalle?.takeIf { it.isNotBlank() } ?: ui.medidorCalle
        val medidorPueblo = data.medidorPueblo?.takeIf { it.isNotBlank() } ?: ui.medidorPueblo
        val medidorMetros = data.medidorMetros?.takeIf { it.isNotBlank() } ?: ui.medidorMetros
        val medidorPoste = data.medidorPoste?.takeIf { it.isNotBlank() } ?: ui.medidorPoste
        val horaInicio = data.horaInicioMillis
            ?: ui.horaAtencionInicio
            ?: ui.horaInicio
            ?: System.currentTimeMillis()
        val horaFinal = data.horaFinalMillis ?: ui.horaAtencionFinal ?: ui.horaFinal
        val kmInicio = data.kilometrajeInicio ?: ui.kilometrajeInicio
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
            kilometrajeInicio = kmInicio,
            kilometrajeFinal = kmFinal,
            tipoAfectacion = tipo,
            numeroMedidor = numeroMedidor,
            medidorCalle = medidorCalle,
            medidorPueblo = medidorPueblo,
            medidorMetros = medidorMetros,
            medidorPoste = medidorPoste
        )
    }

    private suspend fun ensurePropietario(ui: AveriaUI): UserEntity? {
        val user = requireUsuario() ?: return null
        val asignadoA = ui.tecnicoUid
        if (!asignadoA.isNullOrBlank() && asignadoA != user.uid) {
            _messages.emit(getApplication<Application>().getString(R.string.averia_error_no_autorizado))
            return null
        }
        return user
    }

    private fun canSendNotifications(): Boolean {
        val context = getApplication<Application>()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            notificationManager.areNotificationsEnabled()
        }
    }

    private fun notifyAsignada(caseId: String, tecnico: String?) {
        if (!canSendNotifications()) return
        val context = getApplication<Application>()
        val title = context.getString(R.string.averia_notificacion_asignada_title)
        val body = context.getString(
            R.string.averia_notificacion_asignada_body,
            caseId,
            tecnico ?: context.getString(R.string.averia_sin_asignar)
        )
        val notification = NotificationCompat.Builder(context, "averias_channel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        if (ActivityCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) != PackageManager.PERMISSION_GRANTED
) {
    return
}

        notificationManager.notify(caseId.hashCode(), notification)
    }

    private fun notifyEnAtencion(caseId: String, tecnico: String?) {
        if (!canSendNotifications()) return
        val context = getApplication<Application>()
        val title = context.getString(R.string.averia_notificacion_atencion_title)
        val body = context.getString(
            R.string.averia_notificacion_atencion_body,
            caseId,
            tecnico ?: context.getString(R.string.averia_sin_asignar)
        )
        val notification = NotificationCompat.Builder(context, "averias_channel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        if (ActivityCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) != PackageManager.PERMISSION_GRANTED
) {
    return
}

        notificationManager.notify((caseId.hashCode() shl 1), notification)
    }

    private fun notifyResuelta(caseId: String) {
        if (!canSendNotifications()) return
        val context = getApplication<Application>()
        val title = context.getString(R.string.averia_notificacion_resuelta_title)
        val body = context.getString(R.string.averia_notificacion_resuelta_body, caseId)
        val notification = NotificationCompat.Builder(context, "averias_channel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        if (ActivityCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) != PackageManager.PERMISSION_GRANTED
) {
    return
}

        notificationManager.notify((caseId.hashCode() shl 2), notification)
    }

    fun syncNow() {
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            isLoading.emit(true)
            try {
                repo.pullFromFirebaseOnce()
                repo.syncPendientesConFirebase()
                repo.syncFromIce(BuildConfig.ICE_BEARER)
            } catch (t: Throwable) {
                Log.e(TAG, "Error al sincronizar averías", t)
            } finally {
                isLoading.emit(false)
            }
        }
    }

    fun onToggleAsignacion(ui: AveriaUI) {
        viewModelScope.launch {
            when (Estado.fromLabel(ui.estado)) {
                Estado.PENDIENTE -> {
                    val user = requireUsuario() ?: return@launch
                    val nombreTecnico = nombreCompleto(user)
                    val vehiculo = user.placaVehiculo?.takeIf { !it.isNullOrBlank() }
                    repo.asignar(ui.id, user.uid, nombreTecnico, vehiculo)
                    notifyAsignada(ui.id, nombreTecnico)
                    _messages.tryEmit(getApplication<Application>().getString(R.string.averia_exito_asignada))
                }
                Estado.ASIGNADA -> {
                    ensurePropietario(ui) ?: return@launch
                    repo.revertirAPendiente(ui.id)
                    _messages.tryEmit(getApplication<Application>().getString(R.string.averia_exito_desasignada))
                }
                else -> return@launch
            }
            syncNow()
        }
    }

    fun onAutoAsignarPendiente(ui: AveriaUI) {
        viewModelScope.launch {
            if (Estado.fromLabel(ui.estado) != Estado.PENDIENTE) return@launch
            if (!ui.tecnicoUid.isNullOrBlank()) return@launch
            val user = requireUsuario() ?: return@launch
            val nombreTecnico = nombreCompleto(user)
            val vehiculo = user.placaVehiculo?.takeIf { !it.isNullOrBlank() }
            repo.asignar(ui.id, user.uid, nombreTecnico, vehiculo)
            notifyAsignada(ui.id, nombreTecnico)
            _messages.tryEmit(getApplication<Application>().getString(R.string.averia_exito_asignada))
            syncNow()
        }
    }

    fun onAtender(ui: AveriaUI, data: AveriaActionData) {
        if (data.causa.isBlank()) {
            _messages.tryEmit(getApplication<Application>().getString(R.string.averia_error_causa_requerida))
            return
        }
        viewModelScope.launch {
            if (Estado.fromLabel(ui.estado) != Estado.ASIGNADA) return@launch
            val user = ensurePropietario(ui) ?: return@launch
            val resolved = resolveData(ui, data, user)
            repo.enAtencion(ui.id, resolved)
            notifyEnAtencion(ui.id, resolved.atendidoPorNombre ?: user?.let { nombreCompleto(it) })
            _messages.tryEmit(getApplication<Application>().getString(R.string.averia_exito_en_atencion))
            syncNow()
        }
    }

    fun onCancelarAtencion(ui: AveriaUI) {
        viewModelScope.launch {
            if (Estado.fromLabel(ui.estado) != Estado.EN_ATENCION) return@launch
            ensurePropietario(ui) ?: return@launch
            repo.revertirAPendiente(ui.id)
            _messages.tryEmit(getApplication<Application>().getString(R.string.averia_exito_cancelada))
            syncNow()
        }
    }

    fun onResolver(ui: AveriaUI, data: AveriaActionData) {
        if (data.causa.isBlank()) {
            _messages.tryEmit(getApplication<Application>().getString(R.string.averia_error_causa_requerida))
            return
        }
        viewModelScope.launch {
            if (Estado.fromLabel(ui.estado) != Estado.EN_ATENCION) return@launch
            val user = ensurePropietario(ui) ?: return@launch
            val resolved = resolveData(ui, data, user)
            repo.cerrar(ui.id, resolved)
            notifyResuelta(ui.id)
            _messages.tryEmit(getApplication<Application>().getString(R.string.averia_exito_resuelta))
            syncNow()
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

                val regionItems = listOf(RegionUI(null, allRegionsLabel)) +
                    regiones.sortedBy { it.nombre }
                        .distinctBy { it.id }
                        .map { RegionUI(it.id, it.nombre) }
                _regiones.emit(regionItems)

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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(
                NotificationChannel("averias_channel", "Averías", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        repo.stopRealtimeListener()
    }
}

private fun String.normalize(): String {
    val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
    return normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        .lowercase(Locale.getDefault())
}
