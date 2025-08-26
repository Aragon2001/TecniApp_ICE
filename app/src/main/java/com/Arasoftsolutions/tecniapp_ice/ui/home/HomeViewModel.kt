package com.Arasoftsolutions.tecniapp_ice.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Arasoftsolutions.tecniapp_ice.Database.entities.MedidorEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.Database.sync.Synchronizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: RoomRepository,
    private val synchronizer: Synchronizer,
    private val subregionId: String
) : ViewModel() {

    val medidores: Flow<List<MedidorEntity>> = repository.observarMedidores(subregionId)

    fun refresh(
        onStart: (String) -> Unit = {},
        onProgress: (Int, Int, String?) -> Unit = { _, _, _ -> },
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        viewModelScope.launch {
            synchronizer.syncSubregion(subregionId, onStart, onProgress, onSuccess, onError)
        }
    }
}
