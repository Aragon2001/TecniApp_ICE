package com.Arasoftsolutions.tecniapp_ice.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AveriaEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.MedidorEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.UserEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.preferences.DataStoreManager
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriasRepository
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriasSyncWorker
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.max

/**
 * Opción A: AndroidViewModel con constructor (Application).
 * El factory por defecto puede instanciarlo sin errores.
 *
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = RoomRepository(app)
    private val database = AppDatabase.getInstance(app)
    private val averiasRepository = AveriasRepository(database)
    private val dataStore = DataStoreManager.getInstance(app)
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val sharing = SharingStarted.WhileSubscribed(5_000)

    // Subregión activa que define las consultas a Room
    private val _subregion = MutableStateFlow<String?>(null)
    fun setSubregion(id: String) { _subregion.value = id }

    private val _usuario = MutableStateFlow<UserEntity?>(null)
    val usuario: StateFlow<UserEntity?> = _usuario.asStateFlow()

    private val _agenciasFiltro = MutableStateFlow<List<String>>(emptyList())

    // Lista observable de medidores para la subregión (lectura 100% Room)
    val medidores: StateFlow<List<MedidorEntity>> =
        _subregion
            .flatMapLatest { id ->
                if (id.isNullOrEmpty()) flowOf(emptyList())
                else repo.observarMedidores(id)
            }
            .stateIn(viewModelScope, sharing, emptyList())

    private val averias: StateFlow<List<AveriaEntity>> =
        _agenciasFiltro
            .flatMapLatest { agencias ->
                averiasRepository.observe(agencias, agencias.size, "", "")
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

    val kilometrosInicialesHoy: StateFlow<Double> =
        combine(averias, usuarioUid) { lista, uid ->
            if (uid.isNullOrBlank()) {
                0.0
            } else {
                val hoy = LocalDate.now()
                lista.sumOf { averia ->
                    val kilometrajeInicio = averia.kilometrajeInicio
                    val fechaInicio = averia.atencionHoraInicioMillis
                        ?: averia.horaInicioMillis
                    if (kilometrajeInicio != null && fechaInicio != null) {
                        val fecha = Instant.ofEpochMilli(fechaInicio)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        if (
                            fecha == hoy &&
                            (averia.tecnicoAsignadoUid.equals(uid, ignoreCase = true) ||
                                averia.atendidoPorUid.equals(uid, ignoreCase = true))
                        ) {
                            max(kilometrajeInicio, 0.0)
                        } else {
                            0.0
                        }
                    } else {
                        0.0
                    }
                }
            }
        }.stateIn(viewModelScope, sharing, 0.0)

    val lastManualSync: StateFlow<Long?> =
        dataStore.lastManualSyncMillis
            .stateIn(viewModelScope, sharing, null)

    fun loadUsuarioActual() {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val user = repo.obtenerUsuario(uid)
            _usuario.value = user
            user?.subregion?.takeIf { it.isNotBlank() }?.let { setSubregion(it) }
            _agenciasFiltro.value = user?.let { usuario ->
                listOfNotNull(canonicalAgencyTag(usuario.agencia ?: usuario.subregion))
            } ?: emptyList()
        }
    }

    fun triggerManualSync() {
        AveriasSyncWorker.triggerNow(getApplication())
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
