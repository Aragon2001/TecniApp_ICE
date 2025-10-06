package com.Arasoftsolutions.tecniapp_ice.User

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AgenciaEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.SubregionesEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.UserEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculosEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UserViewModel(private val repository: RoomRepository) : ViewModel() {

    private val _user = MutableStateFlow<UserEntity?>(null)
    val user: StateFlow<UserEntity?> = _user.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val subregions: StateFlow<List<SubregionesEntity>> = repository.observarSubregiones()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val agencies: StateFlow<List<AgenciaEntity>> = repository.observarAgenciasCatalogo()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val vehicles: StateFlow<List<VehiculosEntity>> = repository.observarVehiculosCatalogo()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun loadUser(uid: String) {
        if (uid.isBlank()) {
            _error.value = "UID inválido"
            return
        }
        if (_user.value?.uid == uid) return
        viewModelScope.launch {
            _loading.value = true
            try {
                val local = repository.obtenerUsuario(uid)
                val userEntity = local ?: repository.upsertUserFromFirebase(uid)
                _user.value = userEntity
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "No se pudo cargar el perfil"
            } finally {
                _loading.value = false
            }
        }
    }

    fun updateCachedUser(userEntity: UserEntity, persist: Boolean = false) {
        _user.value = userEntity
        if (persist) {
            viewModelScope.launch { repository.saveUser(userEntity) }
        }
    }

    fun clearError() {
        _error.value = null
    }

    class Factory(private val repository: RoomRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(UserViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return UserViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
