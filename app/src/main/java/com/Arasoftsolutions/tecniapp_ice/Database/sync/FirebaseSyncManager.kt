package com.Arasoftsolutions.tecniapp_ice.Database.sync

import android.content.Context
import android.util.Log
import com.Arasoftsolutions.tecniapp_ice.Database.entities.*
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

/**
 * Acceso centralizado a Realtime Database en *varios* proyectos.
 *
 * Endpoints:
 * - Usuarios / verificationCodes: https://tecniapp-ice-user.firebaseio.com/
 * - Agencias / Subregiones / Vehículos: https://tecniapp-ice-datosgenerales.firebaseio.com/
 * - Localizaciones / pueblos: https://tecniapp-ice.firebaseio.com/
 * - Medidores: https://tecniapp-ice-default-rtdb.firebaseio.com/
 */
class FirebaseSyncManager(context: Context) {

    private val dbUsers: DatabaseReference =
        FirebaseDatabase.getInstance("https://tecniapp-ice-user.firebaseio.com").reference

    private val dbDatosGenerales: DatabaseReference =
        FirebaseDatabase.getInstance("https://tecniapp-ice-datosgenerales.firebaseio.com").reference

    private val dbLocal: DatabaseReference =
        FirebaseDatabase.getInstance("https://tecniapp-ice.firebaseio.com").reference

    private val dbMedidores: DatabaseReference =
        FirebaseDatabase.getInstance("https://tecniapp-ice-default-rtdb.firebaseio.com").reference

    // --- USUARIOS ---
    suspend fun obtenerUsuario(uid: String): UserEntity? {
        val snap = dbUsers.child("usuarios").child(uid).get().await()
        return snap.getValue(UserEntity::class.java)
    }

    suspend fun upsertUsuarioConEmail(uid: String, email: String, otrosCampos: Map<String, Any?> = emptyMap()) {
        val emailLower = email.trim().lowercase()
        val base = mapOf(
            "uid" to uid,
            "email" to email,
            "email_lower" to emailLower
        )
        val payload = base + otrosCampos
        dbUsers.child("usuarios").child(uid).updateChildren(payload).await()
    }

    suspend fun buscarUsuarioPorEmail(email: String): UserEntity? {
        val emailLower = email.trim().lowercase()
        val snap = dbUsers.child("usuarios")
            .orderByChild("email_lower")
            .equalTo(emailLower)
            .limitToFirst(1)
            .get()
            .await()

        val first = snap.children.firstOrNull() ?: return null
        return first.getValue(UserEntity::class.java)
    }

    // --- DATOS GENERALES (Agencias / Subregiones / Vehículos) ---
    suspend fun obtenerAgencias(subregionId: String): List<AgenciaEntity> {
        val snap = dbDatosGenerales.child("agencias").get().await()
        return snap.children.mapNotNull { it.getValue(AgenciaEntity::class.java) }
            .filter { it.subregion == subregionId }
    }

    suspend fun obtenerVehiculos(subregionId: String): List<VehiculosEntity> {
        val snap = dbDatosGenerales.child("vehiculos").get().await()
        return snap.children.mapNotNull { it.getValue(VehiculosEntity::class.java) }
            .filter { it.subregion == subregionId }
    }

    suspend fun obtenerSubregiones(): List<SubregionesEntity> {
        val snap = dbDatosGenerales.child("subregiones").get().await()
        return snap.children.mapNotNull { it.getValue(SubregionesEntity::class.java) }
    }

    // --- LOCALIZACIONES / PUEBLOS ---
    suspend fun obtenerLocalizaciones(subregionId: String): List<LocalizacionesEntity> {
        val node = if (dbLocal.child("Localizaciones").get().await().exists()) {
            "Localizaciones"
        } else {
            "localizaciones"
        }

        val snap = dbLocal.child(node).get().await()
        return snap.children.mapNotNull { it.getValue(LocalizacionesEntity::class.java) }
            .filter { it.subregion == subregionId }
    }

    suspend fun obtenerPueblos(subregionId: String): List<PueblosEntity> {
        val node = if (dbLocal.child("pueblos").get().await().exists()) "pueblos" else "Pueblos"
        val snap = dbLocal.child(node).get().await()
        return snap.children.mapNotNull { it.getValue(PueblosEntity::class.java) }
            .filter { it.subregion == subregionId }
    }

    // --- MEDIDORES (Sync completa) ---
    suspend fun obtenerMedidores(subregion: String): List<MedidorEntity> {
        val ruta = "Medidores/Medidores/SubRegion $subregion"
        val snap = dbMedidores.child(ruta).get().await()

        val list = mutableListOf<MedidorEntity>()

        for (grupo in snap.children) {
            for (medidor in grupo.children) {
                try {
                    val entity = medidor.getValue(MedidorEntity::class.java)
                    if (entity != null) {
                        list.add(entity.copy(subregion = subregion))
                    }
                } catch (e: Exception) {
                    Log.e("SYNC", "🛑 Error MedidorEntity: ${e.message}")
                    Log.e("SYNC", "⛔ Data: ${medidor.value}")
                }
            }
        }

        return list
    }

    // --- MEDIDOR (Búsqueda puntual de uno solo) ---
    suspend fun buscarMedidorEnFirebase(subregion: String, medidorNumber: String): MedidorEntity? {
        val ruta = "Medidores/Medidores/SubRegion $subregion"
        val snap = dbMedidores.child(ruta).child(medidorNumber).get().await()

        return try {
            val entity = snap.getValue(MedidorEntity::class.java)
            entity?.copy(subregion = subregion)
        } catch (e: Exception) {
            Log.e("SYNC", "🛑 Error MedidorEntity único: ${e.message}")
            null
        }
    }
}
