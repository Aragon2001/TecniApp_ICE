package com.Arasoftsolutions.tecniapp_ice.Database.room

import android.content.Context
import com.Arasoftsolutions.tecniapp_ice.Database.entities.*
import com.Arasoftsolutions.tecniapp_ice.Database.sync.FirebaseSyncManager
import com.Arasoftsolutions.tecniapp_ice.Database.sync.SubregionNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
// Si usas transacciones, habilita esto y agrega la dependencia de room-ktx:
// import androidx.room.withTransaction

/**
 * Repositorio centralizado para exponer operaciones de lectura sobre Room
 * y para sincronizar datos desde Firebase hacia la base de datos local.
 */
class   RoomRepository(context: Context) {

    private val db = AppDatabase.getInstance(context.applicationContext)
    private val firebase = FirebaseSyncManager(context.applicationContext)
    private val kilometrajeDao = db.vehiculoKilometrajeDao()
    private val inventarioDao = db.inventarioDao()

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

    fun observarTodosLosMedidores(): Flow<List<MedidorEntity>> = db.medidorDao().observarTodos()

    fun observarPueblos(subregionId: String): Flow<List<PueblosEntity>> =
        db.puebloDao().observarPorSubregion(subregionId)

    fun observarLocalizacionesPorPueblo(puebloId: Int): Flow<List<LocalizacionesEntity>> =
        db.localizacionDao().observarPorPueblo(puebloId)

    fun observarTodasLasLocalizaciones(): Flow<List<LocalizacionesEntity>> =
        db.localizacionDao().observarTodas()

    fun observarLocalizacionesDePueblos(puebloIds: List<Int>): Flow<List<LocalizacionesEntity>> =
        if (puebloIds.isEmpty()) flowOf(emptyList()) else db.localizacionDao().observarPorPueblos(puebloIds)

    fun observarAgencias(subregionId: String): Flow<List<AgenciaEntity>> =
        db.agenciaDao().observarPorSubregion(subregionId)

    fun observarAgenciasCatalogo(): Flow<List<AgenciaEntity>> =
        db.agenciaDao().observarTodas()

    fun observarVehiculos(subregionId: String): Flow<List<VehiculosEntity>> =
        db.vehiculoDao().observarPorSubregion(subregionId)

    fun observarVehiculosCatalogo(): Flow<List<VehiculosEntity>> =
        db.vehiculoDao().observarTodos()

    fun observarUltimoKilometraje(placa: String): Flow<VehiculoKilometrajeEntity?> {
        val normalizada = VehiculoKilometrajeEntity.normalizarPlaca(placa)
            ?: return flowOf(null)
        return kilometrajeDao.observarUltimo(normalizada)
    }

    fun observarTodosLosPueblos(): Flow<List<PueblosEntity>> = db.puebloDao().observarTodos()

    fun observarMateriales(): Flow<List<MaterialEntity>> =
        db.materialDao().observarMateriales()

    fun observarTecnicos(): Flow<List<TecnicoEntity>> =
        db.tecnicoDao().observarTecnicos()

    fun observarInventarioPorVehiculo(vehiculoId: Int): Flow<List<InventarioConVehiculo>> =
        inventarioDao.observarInventarioPorVehiculo(vehiculoId)

    fun observarInventarioGeneral(): Flow<List<InventarioConVehiculo>> =
        inventarioDao.observarInventarioGeneral()

    fun observarReparaciones(): Flow<List<LuminariaReparacionEntity>> =
        inventarioDao.observarReparaciones()

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

    suspend fun guardarMedidor(
        subregionId: String,
        subregionNombre: String?,
        medidor: MedidorEntity,
    ) = withContext(Dispatchers.IO) {
        firebase.registrarMedidorManual(subregionId, subregionNombre, medidor)
        db.medidorDao().insertAll(listOf(medidor))
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

    suspend fun obtenerPuebloPorId(puebloId: Int): PueblosEntity? =
        db.puebloDao().buscarPorId(puebloId)

    suspend fun obtenerCallesPorPueblo(puebloId: Int): List<LocalizacionesEntity> =
        db.localizacionDao().obtenerPorPueblo(puebloId)

    suspend fun obtenerLocalizacionPorId(id: Long): LocalizacionesEntity? =
        db.localizacionDao().buscarPorId(id)

    suspend fun obtenerVehiculoPorId(id: Int): VehiculosEntity? = db.vehiculoDao().buscarPorId(id)

    suspend fun obtenerVehiculoPorPlaca(placa: Long): VehiculosEntity? =
        db.vehiculoDao().buscarPorPlaca(placa)

    suspend fun obtenerMaterialPorCodigo(codigo: String): MaterialEntity? =
        db.materialDao().obtenerPorCodigo(codigo)

    suspend fun buscarLocalizacion(
        puebloId: Int,
        calleId: Int,
        direccion: String?
    ): LocalizacionesEntity? {
        val coincidencias = db.localizacionDao().buscarPorCalle(puebloId, calleId)
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

    suspend fun guardarVehiculo(vehiculo: VehiculosEntity) = withContext(Dispatchers.IO) {
        firebase.guardarVehiculo(vehiculo)
        db.vehiculoDao().insertAll(listOf(vehiculo))
    }

    suspend fun registrarKilometrajeVehicular(
        placa: String,
        kilometrajeFinal: Double,
        timestamp: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val normalizada = VehiculoKilometrajeEntity.normalizarPlaca(placa)
            ?: return@withContext
        kilometrajeDao.insertar(
            VehiculoKilometrajeEntity(
                placa = placa.trim(),
                placaNormalizada = normalizada,
                kilometrajeFinal = kilometrajeFinal,
                registradoEn = timestamp
            )
        )
    }

    suspend fun eliminarVehiculo(id: Int) = withContext(Dispatchers.IO) {
        firebase.eliminarVehiculo(id)
        db.vehiculoDao().eliminarPorId(id)
    }

    suspend fun guardarLocalizacion(localizacion: LocalizacionesEntity) = withContext(Dispatchers.IO) {
        firebase.guardarLocalizacion(localizacion)
        db.localizacionDao().insertAll(listOf(localizacion))
    }

    suspend fun eliminarLocalizacion(id: Int) = withContext(Dispatchers.IO) {
        firebase.eliminarLocalizacion(id)
        db.localizacionDao().eliminarPorId(id)
    }

    suspend fun ajustarInventario(
        vehiculoId: Int,
        codigo: String,
        descripcion: String,
        delta: Double
    ) = withContext(Dispatchers.IO) {
        if (codigo.isBlank() || delta == 0.0) return@withContext
        val existente = inventarioDao.obtenerItem(vehiculoId, codigo)
        val nuevaCantidad = (existente?.cantidadDisponible ?: 0.0) + delta
        if (nuevaCantidad <= 0) {
            existente?.let { inventarioDao.eliminarPorId(it.id) }
        } else {
            val item = InventarioItemEntity(
                id = existente?.id ?: 0L,
                vehiculoId = vehiculoId,
                codigoMaterial = codigo.trim(),
                descripcionMaterial = descripcion.ifBlank { existente?.descripcionMaterial ?: codigo },
                cantidadDisponible = nuevaCantidad
            )
            inventarioDao.upsert(item)
        }
    }

    suspend fun eliminarInventarioItem(id: Long) = withContext(Dispatchers.IO) {
        inventarioDao.eliminarPorId(id)
    }

    suspend fun cargarInventarioDesdeCsv(
        vehiculoId: Int,
        items: List<Pair<String, Double>>
    ) = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext
        inventarioDao.eliminarPorVehiculo(vehiculoId)
        items
            .filter { it.second > 0 }
            .forEach { (codigo, cantidad) ->
                val descripcion = db.materialDao().obtenerPorCodigo(codigo)?.descripcion ?: codigo
                val item = InventarioItemEntity(
                    vehiculoId = vehiculoId,
                    codigoMaterial = codigo.trim(),
                    descripcionMaterial = descripcion,
                    cantidadDisponible = cantidad
                )
                inventarioDao.upsert(item)
            }
    }

    suspend fun registrarReparacionLuminaria(
        vehiculoId: Int,
        localizacion: String,
        codigoMaterial: String,
        descripcionMaterial: String,
        cantidad: Double
    ) = withContext(Dispatchers.IO) {
        val reparacion = LuminariaReparacionEntity(
            vehiculoId = vehiculoId,
            localizacion = localizacion,
            codigoMaterial = codigoMaterial,
            descripcionMaterial = descripcionMaterial,
            cantidadUtilizada = cantidad,
            fechaRegistro = System.currentTimeMillis()
        )
        inventarioDao.registrarReparacion(reparacion)
        ajustarInventario(vehiculoId, codigoMaterial, descripcionMaterial, -cantidad)
    }

    suspend fun eliminarReparacionLuminaria(id: Long) = withContext(Dispatchers.IO) {
        val reparacion = inventarioDao.obtenerReparacion(id) ?: return@withContext
        inventarioDao.eliminarReparacion(id)
        ajustarInventario(
            vehiculoId = reparacion.vehiculoId,
            codigo = reparacion.codigoMaterial,
            descripcion = reparacion.descripcionMaterial,
            delta = reparacion.cantidadUtilizada
        )
    }

    suspend fun actualizarReparacionLuminaria(
        id: Long,
        nuevaLocalizacion: String,
        nuevaCantidad: Double
    ) = withContext(Dispatchers.IO) {
        val reparacion = inventarioDao.obtenerReparacion(id) ?: return@withContext
        val delta = nuevaCantidad - reparacion.cantidadUtilizada
        inventarioDao.actualizarReparacion(
            reparacion.copy(localizacion = nuevaLocalizacion, cantidadUtilizada = nuevaCantidad)
        )
        ajustarInventario(
            vehiculoId = reparacion.vehiculoId,
            codigo = reparacion.codigoMaterial,
            descripcion = reparacion.descripcionMaterial,
            delta = -delta
        )
    }

    suspend fun eliminarMedidor(
        subregionId: String,
        subregionNombre: String?,
        numero: String,
    ) = withContext(Dispatchers.IO) {
        firebase.eliminarMedidor(subregionId, subregionNombre, numero)
        db.medidorDao().eliminarPorNumero(numero)
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
        val canonicalSubregion = SubregionNormalizer.canonicalIdOrSelf(subregionId)
            ?: throw IllegalArgumentException("Subregión inválida: $subregionId")

        val agencias = firebase.obtenerAgencias(canonicalSubregion)
        db.agenciaDao().insertAll(agencias)
        progress(++done, total, "Agencias")

        val pueblosRemotos = firebase.obtenerPueblos()
        val pueblosNormalizados = pueblosRemotos.map { remoto ->
            val base = remoto.subregion_id_normalizado.takeIf { it.isNotBlank() } ?: remoto.subregion
            val canonico = SubregionNormalizer.canonicalIdOrSelf(base) ?: ""
            remoto.copy(subregion_id_normalizado = canonico)
        }
        val pueblosFiltrados = pueblosNormalizados.filter { it.subregion_id_normalizado == canonicalSubregion }
        db.puebloDao().eliminarFueraDeSubregion(canonicalSubregion)
        if (pueblosFiltrados.isNotEmpty()) {
            db.puebloDao().limpiarSubregion(canonicalSubregion)
            db.puebloDao().insertAll(pueblosFiltrados)
        }
        progress(++done, total, "Pueblos")

        val idsPueblos = if (pueblosFiltrados.isNotEmpty()) {
            pueblosFiltrados.map { it.id }
        } else {
            db.puebloDao().obtenerIdsPorSubregion(canonicalSubregion)
        }
        val idsSet = idsPueblos.toSet()
        val localizacionesRemotas = firebase.obtenerLocalizaciones()
        val localizacionesFiltradas = localizacionesRemotas.filter { it.pueblo in idsSet }
        db.localizacionDao().limpiarTodo()
        if (localizacionesFiltradas.isNotEmpty()) {
            db.localizacionDao().insertAll(localizacionesFiltradas)
        }
        progress(++done, total, "Localizaciones")

        val vehiculos = firebase.obtenerVehiculos(canonicalSubregion)
        db.vehiculoDao().insertAll(vehiculos)
        progress(++done, total, "Vehículos")

        val medidores = firebase.obtenerMedidores(canonicalSubregion)
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
