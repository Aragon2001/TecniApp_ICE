package com.Arasoftsolutions.tecniapp_ice.Database.sync

import android.content.Context
import com.Arasoftsolutions.tecniapp_ice.Database.entities.*
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

/**
 * Encapsula el acceso a Firebase Realtime Database. Todos los métodos son
 * suspend y devuelven listas de entidades filtradas por subregión.
 */
class FirebaseSyncManager(context: Context) {

    private val db = FirebaseDatabase.getInstance().reference

    suspend fun obtenerUsuario(uid: String): UserEntity? {
        val snapshot = db.child("usuarios").child(uid).get().await()
        return snapshot.getValue(UserEntity::class.java)
    }

    suspend fun obtenerAgencias(subregionId: String): List<AgenciaEntity> {
        val snap = db.child("Agencias").get().await()
        return snap.children.mapNotNull { it.getValue(AgenciaEntity::class.java) }
            .filter { it.subregion == subregionId }
    }

    suspend fun obtenerPueblos(subregionId: String): List<PueblosEntity> {
        val snap = db.child("Pueblos").get().await()
        return snap.children.mapNotNull { it.getValue(PueblosEntity::class.java) }
            .filter { it.subregion == subregionId }
    }

    suspend fun obtenerLocalizaciones(subregionId: String): List<LocalizacionesEntity> {
        val snap = db.child("Localizaciones").get().await()
        return snap.children.mapNotNull { it.getValue(LocalizacionesEntity::class.java) }
            .filter { it.subregion == subregionId }
    }

    suspend fun obtenerVehiculos(subregionId: String): List<VehiculosEntity> {
        val snap = db.child("Vehiculos").get().await()
        return snap.children.mapNotNull { it.getValue(VehiculosEntity::class.java) }
            .filter { it.subregion == subregionId }
    }

    suspend fun obtenerMedidores(subregionId: String): List<MedidorEntity> {
        val ruta = "Medidores/Medidores/SubRegion $subregionId"
        val snap = db.child(ruta).get().await()
        return snap.children.mapNotNull { it.getValue(MedidorEntity::class.java) }
            .map { it.copy(subregion = subregionId) }
    }
}
