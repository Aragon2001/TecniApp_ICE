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
import com.Arasoftsolutions.tecniapp_ice.Database.entities.MaterialEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.UserEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.R
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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

data class ZonaUI(val id: String, val nombreVisible: String)
data class AveriasUiState(val loading: Boolean = false, val items: List<AveriaUI> = emptyList())

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AveriasViewModel(app: Application) : AndroidViewModel(app) {

    companion object { private const val TAG = "AveriasVM" }

    private val db = AppDatabase.getInstance(app)
    private val repo = AveriasRepository(db)
    private val roomRepo = RoomRepository.getInstance(app)
    private val auth = FirebaseAuth.getInstance()
    private val notificationManager = NotificationManagerCompat.from(app)

    private val q = MutableStateFlow("")
    private val estado = MutableStateFlow("Todos")
    private val zonaIndex = MutableStateFlow(0)
    private val _zonas = MutableStateFlow(
        listOf(
            ZonaUI("Guapiles", "Guápiles"),
            ZonaUI("Guacimo", "Guácimo"),
            ZonaUI("Cariari", "Cariari"),
            ZonaUI("Tortuguero", "Tortuguero")
        )
    )
    val zonas: StateFlow<List<ZonaUI>> = _zonas.asStateFlow()
    private val isLoading = MutableStateFlow(false)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()
    private val _usuario = MutableStateFlow<UserEntity?>(null)
    val usuarioActual: StateFlow<UserEntity?> = _usuario.asStateFlow()
    private val _vehiculos = MutableStateFlow<List<String>>(emptyList())
    val vehiculosDisponibles: StateFlow<List<String>> = _vehiculos.asStateFlow()
    private val _materiales = MutableStateFlow<List<MaterialEntity>>(emptyList())
    val materialesDisponibles: StateFlow<List<MaterialEntity>> = _materiales.asStateFlow()

    private var syncJob: Job? = null

    private fun agenciesSel(): List<String> = when (zonaIndex.value) {
        0 -> listOf("Guapiles", "Guacimo", "Cariari", "Tortuguero")
        else -> listOf(_zonas.value[zonaIndex.value - 1].id)
    }

    val uiState: StateFlow<AveriasUiState> =
        combine(
            q.debounce(250).map { it.trim() }.distinctUntilChanged(),
            estado,
            zonaIndex
        ) { qv, est, _ -> Triple(qv, est, agenciesSel()) }
            .flatMapLatest { (qv, est, ags) ->
                repo.observe(ags, if (est == "Todos") "" else est, qv)
            }
            .map { list ->
                AveriasUiState(
                    loading = isLoading.value,
                    items = list.map { e ->
                        val materialesDetalle = MaterialesSerializer.fromJson(e.materialesDetalleJson)
                        val materialesResumen = e.materialesTexto
                            ?: MaterialesSerializer.toSummary(materialesDetalle)
                        AveriaUI(
                            id = e.caseId,
                            descripcion = "Avería #${e.caseId}",
                            fechaMillis = e.fechaInicioMillis,
                            causa = e.causa ?: "Pendiente de verificar",
                            estado = e.estado,
                            tecnico = e.tecnicoAsignadoNombre ?: "",
                            tecnicoUid = e.tecnicoAsignadoUid,
                            atendidoPor = e.atendidoPorNombre ?: "",
                            atendidoPorUid = e.atendidoPorUid,
                            observaciones = e.observaciones ?: "—",
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
                            horaFinal = e.horaFinalMillis
                        )
                    }
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AveriasUiState())

    init {
        repo.startRealtimeListener()
        createNotificationChannel()
        viewModelScope.launch { loadUsuarioActual() }
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
    }

    fun setQuery(value: String) = viewModelScope.launch { q.emit(value) }
    fun setEstado(value: Estado?) = viewModelScope.launch { estado.emit(value?.name ?: "Todos") }
    fun setZonaIndex(idx: Int) = viewModelScope.launch { zonaIndex.emit(idx) }

    fun nombreTecnicoActual(): String? = _usuario.value?.let { nombreCompleto(it) }
    fun vehiculoPreferido(): String? = _usuario.value?.placaVehiculo

    private suspend fun loadUsuarioActual() {
        val uid = auth.currentUser?.uid ?: return
        val local = roomRepo.obtenerUsuario(uid)
        val user = local ?: runCatching { roomRepo.upsertUserFromFirebase(uid) }.getOrNull()
        _usuario.emit(user)
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
        val nombre = listOfNotNull(user.nombre, user.apellidos).joinToString(" ").trim()
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
            horaInicioMillis = horaInicio,
            horaFinalMillis = horaFinal,
            kilometrajeInicio = kmInicio,
            kilometrajeFinal = kmFinal
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
