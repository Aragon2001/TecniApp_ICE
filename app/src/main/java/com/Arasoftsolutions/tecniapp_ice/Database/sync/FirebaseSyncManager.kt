package com.Arasoftsolutions.tecniapp_ice.Database.sync

import android.content.Context
import com.Arasoftsolutions.tecniapp_ice.Database.entities.*
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

/**
 * Acceso centralizado a Realtime Database en *varios* proyectos.
 * Cada método es `suspend` y filtra por subregión cuando aplica.
 *
 * Endpoints:
 *  - Usuarios / verificationCodes: https://tecniapp-ice-user.firebaseio.com/
 *  - Agencias / Subregiones / Vehículos: https://tecniapp-ice-datosgenerales.firebaseio.com/
 *  - Localizaciones / pueblos: https://tecniapp-ice.firebaseio.com/
 *  - Medidores: https://tecniapp-ice-default-rtdb.firebaseio.com/
 */
class FirebaseSyncManager(context: Context) {

    // ---- Referencias por base ----
    private val dbUsers: DatabaseReference =
        FirebaseDatabase.getInstance("https://tecniapp-ice-user.firebaseio.com").reference

    private val dbDatosGenerales: DatabaseReference =
        FirebaseDatabase.getInstance("https://tecniapp-ice-datosgenerales.firebaseio.com").reference

    private val dbLocal: DatabaseReference =
        FirebaseDatabase.getInstance("https://tecniapp-ice.firebaseio.com").reference

    private val dbMedidores: DatabaseReference =
        FirebaseDatabase.getInstance("https://tecniapp-ice-default-rtdb.firebaseio.com").reference

    // ---------- Usuarios (por UID) ----------
    suspend fun obtenerUsuario(uid: String): UserEntity? {
        // /usuarios/{uid}
        val snap = dbUsers.child("usuarios").child(uid).get().await()
        return snap.getValue(UserEntity::class.java)
    }

    // ---------- Catálogos en "datosgenerales" ----------
    suspend fun obtenerAgencias(subregionId: String): List<AgenciaEntity> {
        // /agencias
        val snap = dbDatosGenerales.child("agencias").get().await()
        return snap.children.mapNotNull { it.getValue(AgenciaEntity::class.java) }
            .filter { it.subregion == subregionId }
    }

    suspend fun obtenerVehiculos(subregionId: String): List<VehiculosEntity> {
        // /vehiculos
        val snap = dbDatosGenerales.child("vehiculos").get().await()
        return snap.children.mapNotNull { it.getValue(VehiculosEntity::class.java) }
            .filter { it.subregion == subregionId }
    }

    suspend fun obtenerSubregiones(): List<SubregionesEntity> {
        // útil si luego quieres precargar subregiones
        val snap = dbDatosGenerales.child("subregiones").get().await()
        return snap.children.mapNotNull { it.getValue(SubregionesEntity::class.java) }
    }

    // ---------- Localizaciones / pueblos en "tecniapp-ice" ----------
    suspend fun obtenerLocalizaciones(subregionId: String): List<LocalizacionesEntity> {
        // /Localizaciones  (ojo a mayúscula de la colección original)
        val node = if (dbLocal.child("Localizaciones").get().await().exists()) {
            "Localizaciones"
        } else {
            // fallback por si existiera en minúsculas
            "localizaciones"
        }
        val snap = dbLocal.child(node).get().await()
        return snap.children.mapNotNull { it.getValue(LocalizacionesEntity::class.java) }
            .filter { it.subregion == subregionId }
    }

    suspend fun obtenerPueblos(subregionId: String): List<PueblosEntity> {
        // /pueblos  (según tu estructura está en minúsculas)
        val node = if (dbLocal.child("pueblos").get().await().exists()) "pueblos" else "Pueblos"
        val snap = dbLocal.child(node).get().await()
        return snap.children.mapNotNull { it.getValue(PueblosEntity::class.java) }
            .filter { it.subregion == subregionId }
    }

    // ---------- Medidores en "default-rtdb" ----------
    suspend fun obtenerMedidores(subregionId: String): List<MedidorEntity> {
        // Ruta: /Medidores/Medidores/SubRegion {Subregion}/...
        val ruta = "Medidores/Medidores/SubRegion $subregionId"
        val snap = dbMedidores.child(ruta).get().await()

        // Cada hijo puede ser un número (ej. "714") que contiene los campos del medidor
        return snap.children.flatMap { grupo ->
            grupo.children.mapNotNull { it.getValue(MedidorEntity::class.java) }
        }.map { it.copy(subregion = subregionId) }
    }
}
