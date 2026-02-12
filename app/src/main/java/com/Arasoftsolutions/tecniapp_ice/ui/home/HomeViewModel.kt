package com.Arasoftsolutions.tecniapp_ice.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AveriaEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado
import com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaReparacionEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.MedidorEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.UserEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.preferences.DataStoreManager
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriaNotificationPreferences
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriasRepository
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriasSyncWorker
import com.Arasoftsolutions.tecniapp_ice.ui.averias.normalizeAveriaText
import com.Arasoftsolutions.tecniapp_ice.ui.averias.shouldNotifyForAgency
import com.Arasoftsolutions.tecniapp_ice.ui.vehiculo.TipoVehiculo
import com.Arasoftsolutions.tecniapp_ice.ui.vehiculo.inferirTipoVehiculo
import com.Arasoftsolutions.tecniapp_ice.ui.vehiculo.VehiculoPlacaUtils
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Opción A: AndroidViewModel con constructor (Application).
 * El factory por defecto puede instanciarlo sin errores.
 *
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = RoomRepository.getInstance(app)
    private val database = AppDatabase.getInstance(app)
    private val averiasRepository = AveriasRepository(database)
    private val dataStore = DataStoreManager.getInstance(app)
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val sharing = SharingStarted.WhileSubscribed(5_000)
    private val formatoFechaEtm = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)

    // Subregión activa que define las consultas a Room
    private val _subregion = MutableStateFlow<String?>(null)
    fun setSubregion(id: String) { _subregion.value = id }

    private val _usuario = MutableStateFlow<UserEntity?>(null)
    val usuario: StateFlow<UserEntity?> = _usuario.asStateFlow()
    private var usuarioObserverStarted = false

    data class AveriasPendientesPorAgencia(val agencia: String, val pendientes: Int)

    // Lista observable de medidores para la subregión (lectura 100% Room)
    val medidores: StateFlow<List<MedidorEntity>> =
        _subregion
            .flatMapLatest { id ->
                if (id.isNullOrEmpty()) flowOf(emptyList())
                else repo.observarMedidores(id)
            }
            .stateIn(viewModelScope, sharing, emptyList())

    private val agenciasSeleccionadas: StateFlow<List<String>> =
        AveriaNotificationPreferences.selectedAgenciesFlow(app)
            .stateIn(viewModelScope, sharing, emptyList())

    private val agenciasPreferidas: StateFlow<List<String>> =
        combine(agenciasSeleccionadas, usuario) { agencias, user ->
            if (agencias.isNotEmpty()) {
                agencias
            } else {
                listOfNotNull(user?.agencia?.takeIf { it.isNotBlank() }
                    ?: user?.subregion?.takeIf { it.isNotBlank() })
            }
        }.stateIn(viewModelScope, sharing, emptyList())

    private val agenciasFiltroTags: StateFlow<List<String>> =
        agenciasPreferidas
            .map { agencias ->
                agencias.mapNotNull { canonicalAgencyTag(it) }
                    .distinctBy { it.lowercase(Locale.getDefault()) }
            }
            .stateIn(viewModelScope, sharing, emptyList())

    private val averias: StateFlow<List<AveriaEntity>> =
        agenciasFiltroTags
            .flatMapLatest { agencias ->
                averiasRepository.observe(agencias, "", "", "")
            }
            .stateIn(viewModelScope, sharing, emptyList())

    private val usuarioUid: StateFlow<String?> =
        usuario
            .map { it?.uid }
            .stateIn(viewModelScope, sharing, null)

    val averiasAsignadasCount: StateFlow<Int> =
        combine(averias, usuarioUid) { lista, uid ->
            if (uid.isNullOrBlank()) {
                0
            } else {
                lista.count { averia ->
                    averia.tecnicoAsignadoUid.equals(uid, ignoreCase = true) &&
                        !averia.estado.equals("Resuelta", ignoreCase = true)
                }
            }
        }.stateIn(viewModelScope, sharing, 0)

    val averiasResueltasHoyCount: StateFlow<Int> =
        combine(averias, usuarioUid) { lista, uid ->
            if (uid.isNullOrBlank()) {
                0
            } else {
                val hoy = LocalDate.now()
                lista.count { averia ->
                    averia.atendidoPorUid.equals(uid, ignoreCase = true) &&
                        averia.atencionHoraFinalMillis?.let { millis ->
                            Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate() == hoy
                        } == true
                }
            }
        }.stateIn(viewModelScope, sharing, 0)

    val averiasPendientesPorAgencia: StateFlow<List<AveriasPendientesPorAgencia>> =
        combine(averias, agenciasPreferidas) { lista, agencias ->
            if (agencias.isEmpty()) {
                emptyList()
            } else {
                val pendientes = lista.filter { averia ->
                    !averia.estado.equals("Resuelta", ignoreCase = true)
                }
                agencias.map { agencia ->
                    val normalized = normalizeAveriaText(agencia)
                    val count = if (normalized.isBlank()) {
                        0
                    } else {
                        pendientes.count { averia ->
                            shouldNotifyForAgency(averia, setOf(normalized))
                        }
                    }
                    AveriasPendientesPorAgencia(agencia, count)
                }
            }
        }.stateIn(viewModelScope, sharing, emptyList())

    private val placaVehiculo: StateFlow<String?> =
        usuario
            .map { it?.placaVehiculo?.takeIf { placa -> placa.isNotBlank() } }
            .stateIn(viewModelScope, sharing, null)

    private val vehiculoAsignado: StateFlow<com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculosEntity?> =
        placaVehiculo
            .flatMapLatest { placa ->
                val placaLong = VehiculoPlacaUtils.parsePlacaLong(placa)
                if (placaLong == null) {
                    flowOf(null)
                } else {
                    repo.observarVehiculoPorPlaca(placaLong)
                }
            }
            .stateIn(viewModelScope, sharing, null)

    private val registroHoy: StateFlow<Boolean> =
        vehiculoAsignado
            .map { vehiculo ->
                val hoy = LocalDate.now().format(formatoFechaEtm)
                vehiculo?.registroFecha == hoy && vehiculo.registroInicial != null
            }
            .stateIn(viewModelScope, sharing, false)

    val registroEtmPendiente: StateFlow<Boolean> =
        combine(placaVehiculo, registroHoy) { placa, registroOk ->
            placa.isNullOrBlank() || !registroOk
        }.stateIn(viewModelScope, sharing, false)

    val valorEtmActual: StateFlow<Double?> =
        vehiculoAsignado
            .map { vehiculo ->
                vehiculo?.registroFinal
                    ?: vehiculo?.registroInicial
                    ?: vehiculo?.kilometrajeActual
                    ?: vehiculo?.orimetroActual
            }
            .stateIn(viewModelScope, sharing, null)

    val tipoVehiculo: StateFlow<TipoVehiculo> =
        placaVehiculo
            .flatMapLatest { placa ->
                flow {
                    val placaLong = VehiculoPlacaUtils.parsePlacaLong(placa)
                    val tipo = placaLong?.let { repo.obtenerVehiculoPorPlaca(it)?.tipo }
                    emit(inferirTipoVehiculo(tipo))
                }
            }
            .stateIn(viewModelScope, sharing, TipoVehiculo.LIVIANO)

    val lastManualSync: StateFlow<Long?> =
        dataStore.lastManualSyncMillis
            .stateIn(viewModelScope, sharing, null)

    private val reparacionesLuminarias: StateFlow<List<LuminariaReparacionEntity>> =
        repo.observarReparaciones()
            .stateIn(viewModelScope, sharing, emptyList())

    private val vehiculoUsuarioId: StateFlow<Int?> =
        usuario
            .flatMapLatest { user ->
                flow {
                    val placa = user?.placaVehiculo?.trim().orEmpty()
                    val placaLong = VehiculoPlacaUtils.parsePlacaLong(placa)
                    val vehiculoId = placaLong?.let { repo.obtenerVehiculoPorPlaca(it)?.id }
                    emit(vehiculoId)
                }
            }
            .stateIn(viewModelScope, sharing, null)

    val luminariasPendientesCount: StateFlow<Int> =
        combine(reparacionesLuminarias, usuario, vehiculoUsuarioId) { reparaciones, user, vehiculoId ->
            val rol = user?.rol?.trim()?.lowercase(Locale.getDefault())
            val esSupervisor = rol == "supervisor"
            val esAdministrador = rol == "administrador"
            val visibles = if (esSupervisor || esAdministrador) {
                reparaciones
            } else {
                reparaciones.filter { reparacion -> reparacion.vehiculoId == vehiculoId }
            }
            visibles.count { reparacion ->
                    LuminariaEstado.fromRaw(reparacion.estado) == LuminariaEstado.PENDIENTE
            }
        }.stateIn(viewModelScope, sharing, 0)

    fun loadUsuarioActual() {
        if (usuarioObserverStarted) return
        val uid = auth.currentUser?.uid ?: return
        usuarioObserverStarted = true
        viewModelScope.launch {
            launch {
                repo.observarUsuario(uid).collect { user ->
                    _usuario.value = user
                    user?.subregion?.takeIf { it.isNotBlank() }?.let { setSubregion(it) }
                }
            }
            repo.refreshUsuarioActual()
        }
    }

    fun triggerManualSync() {
        AveriasSyncWorker.triggerNow(getApplication(), showSyncNotification = true)
        viewModelScope.launch {
            dataStore.markManualSyncNow()
        }
    }

    private fun canonicalAgencyTag(nombre: String?): String? {
        val raw = nombre?.trim()
        if (raw.isNullOrEmpty()) return null
        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase(Locale.getDefault())
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
        if (normalized.isEmpty()) return null
        val cleaned = normalized
            .removePrefix("s ")
            .removePrefix("sub ")
            .removePrefix("agencia ")
            .trim()
        if (cleaned.isEmpty()) return null
        val parts = cleaned.split(" ")
        val canonical = parts.joinToString("") { part ->
            if (part.length == 1) part.uppercase(Locale.getDefault())
            else part.substring(0, 1).uppercase(Locale.getDefault()) + part.substring(1)
        }
        return canonical.ifBlank { null }
    }
}
