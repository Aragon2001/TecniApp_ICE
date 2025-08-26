// ==============================
// RoomRepository.kt - Repositorio centralizado para acceso a Room
// ==============================
package com.Arasoftsolutions.tecniapp_ice.Database.room

import android.content.Context
import com.Arasoftsolutions.tecniapp_ice.Database.entities.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repositorio encargado de encapsular el acceso a Room
 * y exponer funciones suspend para operaciones con DAOs.
 */
class RoomRepository(context: Context) {

    // Instancia de la base de datos
    private val db = AppDatabase.getInstance(context.applicationContext)

    // =======================
    // Operaciones con USUARIOS
    // =======================

    suspend fun insertarUsuario(usuario: UserEntity) = withContext(Dispatchers.IO) {
        db.usuarioDao().insert(usuario)
    }

    suspend fun obtenerUsuarioPorEmail(email: String): UserEntity? = withContext(Dispatchers.IO) {
        db.usuarioDao().getByEmail(email)
    }

    suspend fun obtenerTodosLosUsuarios(): List<UserEntity> = withContext(Dispatchers.IO) {
        db.usuarioDao().getAll()
    }

    // =======================
    // Operaciones con AGENCIAS
    // =======================

    suspend fun insertarAgencias(lista: List<AgenciaEntity>) = withContext(Dispatchers.IO) {
        db.agenciaDao().insertAll(lista)
    }

    suspend fun obtenerAgencias(): List<AgenciaEntity> = withContext(Dispatchers.IO) {
        db.agenciaDao().getAll()
    }

    // =======================
    // Operaciones con LOCALIZACIONES
    // =======================

    suspend fun insertarLocalizaciones(lista: List<LocalizacionesEntity>) = withContext(Dispatchers.IO) {
        db.localizacionDao().insertAll(lista)
    }

    suspend fun obtenerLocalizaciones(): List<LocalizacionesEntity> = withContext(Dispatchers.IO) {
        db.localizacionDao().getAll()
    }

    // =======================
    // Operaciones con MEDIDORES
    // =======================

    suspend fun insertarMedidores(lista: List<MedidorEntity>) = withContext(Dispatchers.IO) {
        db.medidorDao().insertAll(lista)
    }

    suspend fun obtenerMedidores(): List<MedidorEntity> = withContext(Dispatchers.IO) {
        db.medidorDao().getAll()
    }

    // =======================
    // Operaciones con PUEBLOS
    // =======================

    suspend fun insertarPueblos(lista: List<PueblosEntity>) = withContext(Dispatchers.IO) {
        db.puebloDao().insertAll(lista)
    }

    suspend fun obtenerPueblos(): List<PueblosEntity> = withContext(Dispatchers.IO) {
        db.puebloDao().getAll()
    }

    // =======================
    // Operaciones con SUBREGIONES
    // =======================

    suspend fun insertarSubregiones(lista: List<SubregionesEntity>) = withContext(Dispatchers.IO) {
        db.subregionDao().insertAll(lista)
    }

    suspend fun obtenerSubregiones(): List<SubregionesEntity> = withContext(Dispatchers.IO) {
        db.subregionDao().getAll()
    }

    // =======================
    // Operaciones con VEHÍCULOS
    // =======================

    suspend fun insertarVehiculos(lista: List<VehiculosEntity>) = withContext(Dispatchers.IO) {
        db.vehiculoDao().insertAll(lista)
    }

    suspend fun obtenerVehiculos(): List<VehiculosEntity> = withContext(Dispatchers.IO) {
        db.vehiculoDao().getAll()
    }
}
