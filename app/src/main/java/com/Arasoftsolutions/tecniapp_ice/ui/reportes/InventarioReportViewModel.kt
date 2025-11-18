package com.Arasoftsolutions.tecniapp_ice.ui.reportes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Arasoftsolutions.tecniapp_ice.Database.entities.InventarioConVehiculo
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InventarioReportViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = RoomRepository.getInstance(app)

    private val _inventario = MutableStateFlow<List<InventarioConVehiculo>>(emptyList())
    val inventario: StateFlow<List<InventarioConVehiculo>> = _inventario.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observarInventarioGeneral().collect { items ->
                _inventario.value = items
            }
        }
    }
}
