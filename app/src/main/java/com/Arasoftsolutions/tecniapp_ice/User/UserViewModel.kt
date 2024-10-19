package com.Arasoftsolutions.tecniapp_ice.User

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

data class User(
    val cedula: String = "",
    val nombre: String = "",
    val apellidos: String = "",
    val email: String = "",
    val telefono: String = "",
    val agencia: String = "",
    val subregion: String = "",
    val placaVehiculo: String = "",
    val password: String = "" // Campo para la contraseña
)

class UserViewModel : ViewModel() {

    // LiveData para las listas de datos
    private val _subregions = MutableLiveData<List<String>>()
    val subregions: LiveData<List<String>> get() = _subregions

    private val _agencies = MutableLiveData<List<String>>()
    val agencies: LiveData<List<String>> get() = _agencies

    private val _vehicles = MutableLiveData<List<String>>()
    val vehicles: LiveData<List<String>> get() = _vehicles

    private val _userData = MutableLiveData<User>()
    val userData: LiveData<User> get() = _userData

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    // Referencias a la base de datos
    private val subregionsDatabase: DatabaseReference = FirebaseDatabase.getInstance("https://tecniapp-ice-datosgenerales.firebaseio.com").getReference("subregiones")
    private val agenciesDatabase: DatabaseReference = FirebaseDatabase.getInstance("https://tecniapp-ice-datosgenerales.firebaseio.com").getReference("agencias")
    private val vehiclesDatabase: DatabaseReference = FirebaseDatabase.getInstance("https://tecniapp-ice-datosgenerales.firebaseio.com").getReference("vehiculos")
    private val usersDatabase: DatabaseReference = FirebaseDatabase.getInstance("https://tecniapp-ice-user.firebaseio.com").getReference("usuarios")

    // Método para cargar subregiones
    fun loadSubregions() {
        subregionsDatabase.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val subregionList = mutableListOf("Seleccione una Subregion")
                for (subregionSnapshot in dataSnapshot.children) {
                    val subregionName = subregionSnapshot.child("nombre").getValue(String::class.java)
                    if (subregionName != null) {
                        subregionList.add(subregionName)
                    }
                }
                _subregions.value = subregionList
                Log.d("UserViewModel", "Subregiones cargadas: $subregionList")
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("UserViewModel", "Error al cargar subregiones: ${databaseError.message}")
            }
        })
    }

    // Método para cargar agencias
    fun loadAgencies(selectedSubregion: String) {
        agenciesDatabase.orderByChild("subregion").equalTo(selectedSubregion).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val agencyList = mutableListOf("Seleccione una Agencia")
                for (agencySnapshot in dataSnapshot.children) {
                    val agencyName = agencySnapshot.child("nombre").getValue(String::class.java)
                    if (agencyName != null) {
                        agencyList.add(agencyName)
                    }
                }
                _agencies.value = agencyList
                Log.d("UserViewModel", "Agencias cargadas para $selectedSubregion: $agencyList")
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("UserViewModel", "Error al cargar agencias: ${databaseError.message}")
            }
        })
    }

    // Método para cargar vehículos
    fun loadVehicles(selectedAgency: String) {
        vehiclesDatabase.orderByChild("agencia").equalTo(selectedAgency).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val vehicleList = mutableListOf("Seleccione un Vehículo")
                for (vehicleSnapshot in dataSnapshot.children) {
                    val vehiclePlate = vehicleSnapshot.child("placa").getValue(String::class.java)
                    if (vehiclePlate != null) {
                        vehicleList.add(vehiclePlate)
                    }
                }
                _vehicles.value = vehicleList
                Log.d("UserViewModel", "Vehículos cargados para $selectedAgency: $vehicleList")
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("UserViewModel", "Error al cargar vehículos: ${databaseError.message}")
            }
        })
    }



    // Método para actualizar datos del usuario
    fun updateUserData(user: User) {
        usersDatabase.child(user.cedula).setValue(user)
            .addOnSuccessListener {
                _userData.value = user // Actualiza los datos del usuario en el LiveData
                Log.d("UserViewModel", "Datos del usuario actualizados: $user")
            }
            .addOnFailureListener {
                Log.e("UserViewModel", "Error al actualizar datos del usuario: ${it.message}")
            }
    }

    // Método para actualizar contraseña
    fun updatePassword(newPassword: String) {
        val user = auth.currentUser
        user?.updatePassword(newPassword)?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("UserViewModel", "Contraseña actualizada correctamente")
            } else {
                Log.e("UserViewModel", "Error al actualizar contraseña: ${task.exception?.message}")
            }
        }
    }
}
