package com.Arasoftsolutions.tecniapp_ice.Database.room

import android.content.Context
import com.Arasoftsolutions.tecniapp_ice.Database.entities.*
import com.Arasoftsolutions.tecniapp_ice.Database.sync.FirebaseSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
// Si usas transacciones, habilita esto y agrega la dependencia de room-ktx:
// import androidx.room.withTransaction

/**
 * Repositorio centralizado para exponer operaciones de lectura sobre Room
 * y para sincronizar datos desde Firebase hacia la base de datos local.
 */
class RoomRepository(context: Context) {

    private val db = AppDatabase.getInstance(context.applicationContext)
    private val firebase = FirebaseSyncManager(context.applicationContext)

    // ----- Lecturas observables -----
    fun observarMedidores(subregionId: String): Flow<List<MedidorEntity>> =
        db.medidorDao().observarPorSubregion(subregionId)

    fun observarLocalizaciones(subregionId: String): Flow<List<LocalizacionesEntity>> =
        db.localizacionDao().observarPorSubregion(subregionId)

    fun observarPueblos(subregionId: String): Flow<List<PueblosEntity>> =
        db.puebloDao().observarPorSubregion(subregionId)

    fun observarAgencias(subregionId: String): Flow<List<AgenciaEntity>> =
        db.agenciaDao().observarPorSubregion(subregionId)

    fun observarVehiculos(subregionId: String): Flow<List<VehiculosEntity>> =
        db.vehiculoDao().observarPorSubregion(subregionId)
    /** busca un medidor por numero */

suspend fun buscarMedidorPorNumero(numero: String): MedidorEntity? {
    return db.medidorDao().buscarPorNumero(numero)
}
/** inserta un medidor */
suspend fun insertarMedidor(entity: MedidorEntity) {
    db.medidorDao().insertAll(listOf(entity))
}

    /** Obtiene un usuario almacenado localmente por su UID. */
  suspend fun obtenerUsuario(uid: String): UserEntity? =
    db.usuarioDao().getByUid(uid)

    // ----- Sincronización -----
    /**
     * Descarga los datos de la subregión indicada desde Firebase y los
     * guarda en Room reemplazando los existentes. El callback de progreso es
     * opcional y se utiliza para informar al usuario.
     */
    suspend fun syncSubregion(
        subregionId: String,
        progress: (done: Int, total: Int, msg: String?) -> Unit = { _, _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val total = 5
        var done = 0

        // Si quieres transacción atómica, descomenta y usa withTransaction:
        // db.withTransaction {
            val agencias = firebase.obtenerAgencias(subregionId)
            db.agenciaDao().insertAll(agencias)
            progress(++done, total, "Agencias")

            val pueblos = firebase.obtenerPueblos(subregionId)
            db.puebloDao().insertAll(pueblos)
            progress(++done, total, "Pueblos")

            val localizaciones = firebase.obtenerLocalizaciones(subregionId)
            db.localizacionDao().insertAll(localizaciones)
            progress(++done, total, "Localizaciones")

            val vehiculos = firebase.obtenerVehiculos(subregionId)
            db.vehiculoDao().insertAll(vehiculos)
            progress(++done, total, "Vehículos")

            val medidores = firebase.obtenerMedidores(subregionId)
            db.medidorDao().insertAll(medidores)
            progress(++done, total, "Medidores")
        // }
    }

    /**
     * Obtiene un usuario desde Firebase por su UID y lo almacena en la base
     * de datos local. Devuelve la entidad almacenada.
     */
    suspend fun upsertUserFromFirebase(uid: String): UserEntity = withContext(Dispatchers.IO) {
        val user = firebase.obtenerUsuario(uid)
            ?: throw IllegalStateException("Usuario no encontrado en Firebase")
        db.usuarioDao().upsert(user)
        user
    }
    companion object {
    @Volatile
    private var INSTANCE: RoomRepository? = null

    fun getInstance(context: Context): RoomRepository {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: RoomRepository(context.applicationContext).also {
                INSTANCE = it
            }
        }
    }
}

}
