package com.Arasoftsolutions.tecniapp_ice.Database.room

import android.content.Context
import com.Arasoftsolutions.tecniapp_ice.Database.entities.*
import com.Arasoftsolutions.tecniapp_ice.Database.sync.FirebaseSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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

    companion object {
        const val SUBREGION_SYNC_STEPS = 5

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

    // ----- Lecturas observables -----
    fun observarMedidores(subregionId: String): Flow<List<MedidorEntity>> =
        db.medidorDao().observarPorSubregion(subregionId)

    fun observarLocalizaciones(subregionId: String): Flow<List<LocalizacionesEntity>> =
        db.localizacionDao().observarPorSubregion(subregionId)

    fun observarPueblos(subregionId: String): Flow<List<PueblosEntity>> =
        db.puebloDao().observarPorSubregion(subregionId)

    fun observarTodosLosPueblos(): Flow<List<PueblosEntity>> =
        db.puebloDao().observarTodos()

    fun observarAgencias(subregionId: String): Flow<List<AgenciaEntity>> =
        db.agenciaDao().observarPorSubregion(subregionId)

    fun observarAgenciasCatalogo(): Flow<List<AgenciaEntity>> =
        db.agenciaDao().observarTodas()

    fun observarVehiculos(subregionId: String): Flow<List<VehiculosEntity>> =
        db.vehiculoDao().observarPorSubregion(subregionId)

    fun observarVehiculosCatalogo(): Flow<List<VehiculosEntity>> =
        db.vehiculoDao().observarTodos()

    fun observarMateriales(): Flow<List<MaterialEntity>> =
        db.materialDao().observarMateriales()

    fun observarTecnicos(): Flow<List<TecnicoEntity>> =
        db.tecnicoDao().observarTecnicos()

    fun observarRegiones(): Flow<List<RegionEntity>> = db.regionDao().observarTodas()

    fun observarSubregiones(): Flow<List<SubregionesEntity>> =
        db.subregionDao().observarTodas()

    fun observarCatalogosGenerales(): Flow<Triple<List<RegionEntity>, List<SubregionesEntity>, List<AgenciaEntity>>> =
        combine(
            observarRegiones(),
            observarSubregiones(),
            observarAgenciasCatalogo()
        ) { regiones, subregiones, agencias ->
            Triple(regiones, subregiones, agencias)
        }

    suspend fun buscarMedidorPorNumero(numero: String): MedidorEntity? =
        db.medidorDao().buscarPorNumero(numero)

    suspend fun insertarMedidor(entity: MedidorEntity) {
        db.medidorDao().insertAll(listOf(entity))
    }

    suspend fun insertarMedidores(medidores: List<MedidorEntity>) {
        if (medidores.isNotEmpty()) {
            db.medidorDao().insertAll(medidores)
        }
    }

    suspend fun contarMedidores(subregionId: String): Int =
        db.medidorDao().contarPorSubregion(subregionId)

    suspend fun obtenerUsuario(uid: String): UserEntity? =
        db.usuarioDao().getByUid(uid)

    suspend fun saveUser(user: UserEntity) = withContext(Dispatchers.IO) {
        db.usuarioDao().upsert(user)
    }

    suspend fun obtenerPuebloPorId(subregionId: String, puebloId: Int): PueblosEntity? =
        db.puebloDao().buscarPorId(subregionId, puebloId)

    suspend fun obtenerCallesPorPueblo(
        subregionId: String,
        puebloId: Int
    ): List<LocalizacionesEntity> =
        db.localizacionDao().obtenerPorPueblo(subregionId, puebloId)

    suspend fun obtenerCallesPorPuebloGlobal(puebloId: Int): List<LocalizacionesEntity> =
        db.localizacionDao().obtenerPorPuebloGlobal(puebloId)

    suspend fun buscarLocalizacion(
        subregionId: String,
        puebloId: Int,
        calleId: Int,
        direccion: String?
    ): LocalizacionesEntity? {
        val coincidencias = db.localizacionDao().buscarPorCalle(subregionId, puebloId, calleId)
        return seleccionarLocalizacion(coincidencias, direccion)
    }

    suspend fun buscarLocalizacionGlobal(
        puebloId: Int,
        calleId: Int,
        direccion: String?
    ): LocalizacionesEntity? {
        val coincidencias = db.localizacionDao().buscarPorCalleGlobal(puebloId, calleId)
        return seleccionarLocalizacion(coincidencias, direccion)
    }

    private fun seleccionarLocalizacion(
        coincidencias: List<LocalizacionesEntity>,
        direccion: String?
    ): LocalizacionesEntity? {
        if (coincidencias.isEmpty()) return null

        val direccionNormalizada = direccion?.trim()?.lowercase()
        return coincidencias.firstOrNull { loc ->
            val dir = loc.direccion.trim().lowercase()
            direccionNormalizada?.let { dir == it } ?: true
        } ?: coincidencias.first()
    }

    // ----- Sincronización -----
    suspend fun syncTecnicos() = withContext(Dispatchers.IO) {
        val tecnicos = firebase.obtenerTecnicos()
        if (tecnicos.isNotEmpty()) {
            db.tecnicoDao().insertAll(tecnicos)
        }
    }

    suspend fun syncMateriales() = withContext(Dispatchers.IO) {
        val materiales = firebase.obtenerMaterialesCatalogo()
        if (materiales.isNotEmpty()) {
            db.materialDao().insertAll(materiales)
        }
    }

    suspend fun syncSubregion(
        subregionId: String,
        progress: (done: Int, total: Int, msg: String?) -> Unit = { _, _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val total = SUBREGION_SYNC_STEPS
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

    suspend fun upsertUserFromFirebase(uid: String): UserEntity = withContext(Dispatchers.IO) {
        val user = firebase.obtenerUsuario(uid)
            ?: throw IllegalStateException("Usuario no encontrado en Firebase")
        db.usuarioDao().upsert(user)
        user
    }

    suspend fun syncCatalogosGenerales() = withContext(Dispatchers.IO) {
        val regiones = firebase.obtenerRegiones()
        if (regiones.isNotEmpty()) {
            db.regionDao().insertAll(regiones)
        }

        val subregiones = firebase.obtenerSubregiones()
        if (subregiones.isNotEmpty()) {
            db.subregionDao().insertAll(subregiones)
        }

        val agencias = firebase.obtenerAgencias()
        if (agencias.isNotEmpty()) {
            db.agenciaDao().insertAll(agencias)
        }

        val vehiculos = firebase.obtenerVehiculos()
        if (vehiculos.isNotEmpty()) {
            db.vehiculoDao().insertAll(vehiculos)
        }

        runCatching { syncTecnicos() }
        runCatching { syncMateriales() }
    }

}
