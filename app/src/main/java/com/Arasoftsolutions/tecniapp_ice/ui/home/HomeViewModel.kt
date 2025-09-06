package com.Arasoftsolutions.tecniapp_ice.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Arasoftsolutions.tecniapp_ice.Database.entities.MedidorEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Opción A: AndroidViewModel con constructor (Application).
 * El factory por defecto puede instanciarlo sin errores.
 *
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = RoomRepository(app)

    // Subregión activa que define las consultas a Room
    private val _subregion = MutableStateFlow<String?>(null)
    fun setSubregion(id: String) { _subregion.value = id }

    // Lista observable de medidores para la subregión (lectura 100% Room)
    val medidores: StateFlow<List<MedidorEntity>> =
        _subregion
            .flatMapLatest { id ->
                if (id.isNullOrEmpty()) flowOf(emptyList())
                else repo.observarMedidores(id)
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /**
     * Dispara la sincronización de la subregión y reporta progreso al caller.
     * Mantiene Room-first: descarga de Firebase → guarda en Room → UI observa Room.
     */
  fun refresh(
    onStart: (String) -> Unit = {},
    onProgress: (Int, Int, String?) -> Unit = { _, _, _ -> },
    onSuccess: () -> Unit = {},
    onError: (Throwable) -> Unit = {}
)
{
        viewModelScope.launch {
            try {
                val id = _subregion.value ?: return@launch
                onStart("Sincronizando…")
                repo.syncSubregion(id) { done, total, msg -> onProgress(done, total, msg) }
                onSuccess()
            } catch (t: Throwable) {
                onError(t)
            }
        }
    }
}
