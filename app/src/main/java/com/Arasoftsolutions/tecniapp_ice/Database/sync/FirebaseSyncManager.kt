package com.Arasoftsolutions.tecniapp_ice.Database.sync

import android.content.Context
import android.util.Log
import com.Arasoftsolutions.tecniapp_ice.Database.entities.*
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

/**
 * Sincroniza datos desde Firebase Realtime Database hacia Room.
 * Usa RoomRepository(context) – NO TecniAppDatabaseHelper.
 */
class FirebaseSyncManager(private val context: Context) {

    // Realtime Database (usa la instancia por defecto; si tienes URL custom, usa getInstance(url))
    private val database = FirebaseDatabase.getInstance().reference

    // Repositorio central de Room (tu clase existente)
    private val repository = RoomRepository(context)

    /**
     * Punto de entrada de la sincronización – orquesta todas las descargas.
     */
    suspend fun sincronizarFirebaseConLocal() {
        Log.d("FirebaseSync", "🔄 Iniciando sincronización...")

        sincronizarUsuarioAutenticado()  // Primero el usuario (necesitamos su subregión/agencia para filtrar)
        sincronizarAgencias()
        sincronizarPueblos()
        sincronizarLocalizaciones()
        sincronizarVehiculos()
        sincronizarMedidores()

        Log.d("FirebaseSync", "✅ Sincronización completa")
    }

    /**
     * 1) Usuario autenticado (por email de FirebaseAuth)
     * Colección: "usuarios"  (minúsculas, alineado con LoginActivity)
     */
    private suspend fun sincronizarUsuarioAutenticado() {
        val email = FirebaseAuth.getInstance().currentUser?.email?.trim() ?: return
        val snapshot = database.child("usuarios")
            .orderByChild("email")
            .equalTo(email)
            .get()
            .await()

        if (snapshot.exists()) {
            val user = snapshot.children.firstOrNull()?.getValue(UserEntity::class.java)
            user?.let {
                // Inserta/actualiza (tu DAO debería tener OnConflictStrategy.REPLACE)
                repository.insertarUsuario(it)
                Log.d("FirebaseSync", "👤 Usuario sincronizado: ${it.email}")
            } ?: Log.w("FirebaseSync", "⚠ Usuario nulo al parsear snapshot para $email")
        } else {
            Log.w("FirebaseSync", "⚠ No se encontró usuario con email $email en Firebase.")
        }
    }

    /**
     * 2) Agencias – filtradas por subregión del usuario
     */
    private suspend fun sincronizarAgencias() {
        val usuario = usuarioActual() ?: return
        val snapshot = database.child("Agencias").get().await()

        val agencias = snapshot.children
            .mapNotNull { it.getValue(AgenciaEntity::class.java) }
            .filter { it.subregion == usuario.subregion }

        if (agencias.isNotEmpty()) repository.insertarAgencias(agencias)
        Log.d("FirebaseSync", "🏢 Agencias sincronizadas: ${agencias.size}")
    }

    /**
     * 3) Pueblos – filtrados por subregión
     */
    private suspend fun sincronizarPueblos() {
        val usuario = usuarioActual() ?: return
        val snapshot = database.child("Pueblos").get().await()

        val pueblos = snapshot.children
            .mapNotNull { it.getValue(PueblosEntity::class.java) }
            .filter { it.subregion == usuario.subregion }

        if (pueblos.isNotEmpty()) repository.insertarPueblos(pueblos)
        Log.d("FirebaseSync", "🏘 Pueblos sincronizados: ${pueblos.size}")
    }

    /**
     * 4) Localizaciones – por ahora todas (ajusta si quieres filtrar por subregión)
     */
    private suspend fun sincronizarLocalizaciones() {
        val snapshot = database.child("Localizaciones").get().await()
        val localizaciones = snapshot.children.mapNotNull { it.getValue(LocalizacionesEntity::class.java) }

        if (localizaciones.isNotEmpty()) repository.insertarLocalizaciones(localizaciones)
        Log.d("FirebaseSync", "📍 Localizaciones sincronizadas: ${localizaciones.size}")
    }

    /**
     * 5) Vehículos – filtrados por agencia del usuario
     */
    private suspend fun sincronizarVehiculos() {
        val usuario = usuarioActual() ?: return
        val snapshot = database.child("Vehiculos").get().await()

        val vehiculos = snapshot.children
            .mapNotNull { it.getValue(VehiculosEntity::class.java) }
            .filter { it.agencia == usuario.agencia }

        if (vehiculos.isNotEmpty()) repository.insertarVehiculos(vehiculos)
        Log.d("FirebaseSync", "🚗 Vehículos sincronizados: ${vehiculos.size}")
    }

    /**
     * 6) Medidores – según tu estructura:
     * "Medidores/Medidores/SubRegion X" (ajusta si cambia)
     */
    private suspend fun sincronizarMedidores() {
        val usuario = usuarioActual() ?: return
        val ruta = "Medidores/Medidores/SubRegion ${usuario.subregion}"

        val snapshot = database.child(ruta).get().await()
        val medidores = snapshot.children.mapNotNull { it.getValue(MedidorEntity::class.java) }

        if (medidores.isNotEmpty()) repository.insertarMedidores(medidores)
        Log.d("FirebaseSync", "🔌 Medidores sincronizados: ${medidores.size}")
    }

    /**
     * Utilidad: obtener usuario actual desde Room por email de Firebase.
     */
    private suspend fun usuarioActual(): UserEntity? {
        val email = FirebaseAuth.getInstance().currentUser?.email?.trim().orEmpty()
        if (email.isEmpty()) return null
        return repository.obtenerUsuarioPorEmail(email) ?: repository.obtenerTodosLosUsuarios().firstOrNull()
    }
}
