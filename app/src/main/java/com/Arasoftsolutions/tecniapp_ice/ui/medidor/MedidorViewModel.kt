// MedidorViewModel.kt - actualizado
package com.Arasoftsolutions.tecniapp_ice.ui.medidor

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.Arasoftsolutions.tecniapp_ice.Database.entities.MedidorEntity

class MedidorViewModel : ViewModel() {
    private val _medidor = MutableLiveData<MedidorEntity?>()
    val medidor: LiveData<MedidorEntity?> = _medidor

    fun setMedidor(medidorEntity: MedidorEntity) {
        _medidor.value = medidorEntity
    }

    fun clear() {
        _medidor.value = null
    }
}
