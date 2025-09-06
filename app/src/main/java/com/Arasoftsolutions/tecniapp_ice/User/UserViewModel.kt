package com.Arasoftsolutions.tecniapp_ice.User

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.Arasoftsolutions.tecniapp_ice.Database.entities.UserEntity




class UserViewModel : ViewModel() {

    private val _subregions = MutableLiveData<List<String>>()
    val subregions: LiveData<List<String>> get() = _subregions

    private val _agencies = MutableLiveData<List<String>>()
    val agencies: LiveData<List<String>> get() = _agencies

    private val _vehicles = MutableLiveData<List<String>>()
    val vehicles: LiveData<List<String>> get() = _vehicles

    private val _userEntityData = MutableLiveData<UserEntity>()
    val userEntityData: LiveData<UserEntity> get() = _userEntityData

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> get() = _error

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val subregionsDatabase: DatabaseReference = FirebaseDatabase.getInstance("https://tecniapp-ice-datosgenerales.firebaseio.com").getReference("subregiones")
    private val agenciesDatabase: DatabaseReference = FirebaseDatabase.getInstance("https://tecniapp-ice-datosgenerales.firebaseio.com").getReference("agencias")
    private val vehiclesDatabase: DatabaseReference = FirebaseDatabase.getInstance("https://tecniapp-ice-datosgenerales.firebaseio.com").getReference("vehiculos")
    private val usersDatabase: DatabaseReference = FirebaseDatabase.getInstance("https://tecniapp-ice-user.firebaseio.com").getReference("usuarios")

    // Cargar subregiones
    fun loadSubregions() {
        subregionsDatabase.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val subregionList = mutableListOf("Seleccione una Subregion")
                for (subregionSnapshot in dataSnapshot.children) {
                    val subregionName = subregionSnapshot.child("nombre").getValue(String::class.java)
                    subregionName?.let {
                        subregionList.add(it)
                    }
                }
                _subregions.value = subregionList
                Log.d("UserViewModel", "SubregionesEntity cargadas: $subregionList")
            }

            override fun onCancelled(databaseError: DatabaseError) {
                _error.postValue("Error al cargar subregiones: ${databaseError.message}")
                Log.e("UserViewModel", "Error al cargar subregiones: ${databaseError.message}")
            }
        })
    }

    // Cargar agencias
    fun loadAgencies(selectedSubregion: String) {
        agenciesDatabase.orderByChild("subregion").equalTo(selectedSubregion).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val agencyList = mutableListOf("Seleccione una Agencia")
                for (agencySnapshot in dataSnapshot.children) {
                    val agencyName = agencySnapshot.child("nombre").getValue(String::class.java)
                    agencyName?.let {
                        agencyList.add(it)
                    }
                }
                _agencies.value = agencyList
                Log.d("UserViewModel", "Agencias cargadas para $selectedSubregion: $agencyList")
            }

            override fun onCancelled(databaseError: DatabaseError) {
                _error.postValue("Error al cargar agencias: ${databaseError.message}")
                Log.e("UserViewModel", "Error al cargar agencias: ${databaseError.message}")
            }
        })
    }

    // Cargar vehículos
    fun loadVehicles(selectedAgency: String) {
        vehiclesDatabase.orderByChild("agencia").equalTo(selectedAgency).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val vehicleList = mutableListOf("Seleccione un Vehículo")
                for (vehicleSnapshot in dataSnapshot.children) {
                    val vehiclePlate = vehicleSnapshot.child("placa").getValue(String::class.java)
                    vehiclePlate?.let {
                        vehicleList.add(it)
                    }
                }
                _vehicles.value = vehicleList
                Log.d("UserViewModel", "Vehículos cargados para $selectedAgency: $vehicleList")
            }

            override fun onCancelled(databaseError: DatabaseError) {
                _error.postValue("Error al cargar vehículos: ${databaseError.message}")
                Log.e("UserViewModel", "Error al cargar vehículos: ${databaseError.message}")
            }
        })
    }

    // Actualizar datos del usuario
    fun updateUserData(userEntity: UserEntity) {
        usersDatabase.child(userEntity.uid).setValue(userEntity)
            .addOnSuccessListener {
                _userEntityData.value = userEntity
                Log.d("UserViewModel", "Datos del usuario actualizados: $userEntity")
            }
            .addOnFailureListener {
                _error.postValue("Error al actualizar datos del usuario: ${it.message}")
                Log.e("UserViewModel", "Error al actualizar datos del usuario: ${it.message}")
            }
    }

    fun loadCurrentUser(email: String) {
        usersDatabase.orderByChild("email").equalTo(email).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val userEntity = snapshot.children.firstOrNull()?.getValue(UserEntity::class.java)
                    userEntity?.let {
                        _userEntityData.postValue(it)
                        Log.d("UserViewModel", "Datos del usuario cargados: $it")
                    }
                } else {
                    _error.postValue("Usuario no encontrado con email: $email")
                    Log.w("UserViewModel", "Usuario no encontrado con email: $email")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                _error.postValue("Error al cargar datos del usuario: ${error.message}")
                Log.e("UserViewModel", "Error al cargar datos del usuario: ${error.message}")
            }
        })
    }

    // Actualizar contraseña
    fun updatePassword(newPassword: String) {
        val user = auth.currentUser
        user?.updatePassword(newPassword)?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("UserViewModel", "Contraseña actualizada correctamente")
            } else {
                _error.postValue("Error al actualizar contraseña: ${task.exception?.message}")
                Log.e("UserViewModel", "Error al actualizar contraseña: ${task.exception?.message}")
            }
        }
    }
}
