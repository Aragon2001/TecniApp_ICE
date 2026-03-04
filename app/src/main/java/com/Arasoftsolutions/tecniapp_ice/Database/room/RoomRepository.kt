package com.Arasoftsolutions.tecniapp_ice.Database.room

import android.content.Context
import android.util.Log
import com.Arasoftsolutions.tecniapp_ice.Database.entities.*
import com.Arasoftsolutions.tecniapp_ice.Database.sync.FirebaseSyncManager
import com.Arasoftsolutions.tecniapp_ice.Database.sync.SubregionNormalizer
import com.Arasoftsolutions.tecniapp_ice.ui.vehiculo.VehiculoPlacaUtils
import com.google.firebase.auth.FirebaseAuth
import com.Arasoftsolutions.tecniapp_ice.ui.vehiculo.RegistroDiarioEntity
import com.Arasoftsolutions.tecniapp_ice.ui.vehiculo.RegistroMantenimientoEntity
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import org.json.JSONObject
// Si usas transacciones, habilita esto y agrega la dependencia de room-ktx:
// import androidx.room.withTransaction

/**
 * Repositorio centralizado para exponer operaciones de lectura sobre Room
 * y para sincronizar datos desde Firebase hacia la base de datos local.
 */
data class UserScope(
    val regionId: String?,
    val subregionId: String?,
    val agenciaTag: String?,
    val vehiculoKey: String?
)

class   RoomRepository(context: Context) {

    private val db = AppDatabase.getInstance(context.applicationContext)
    private val firebase = FirebaseSyncManager(context.applicationContext)
    private val vehiculoDao = db.vehiculoDao()
    private val vehiculoLogDao = db.vehiculoLogDao()
    private val inventarioDao = db.inventarioDao()

    private val realtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var inventarioRealtimeListener: ValueEventListener? = null
    private var luminariasRealtimeListener: ValueEventListener? = null

    companion object {
        private const val TAG = "RoomRepositorySync"
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

    fun observarAgencias(regionId: String): Flow<List<AgenciaEntity>> =
        db.agenciaDao().observarPorRegion(regionId)

    fun observarAgenciasCatalogo(): Flow<List<AgenciaEntity>> =
        db.agenciaDao().observarTodas()

    fun observarVehiculos(agencia: String): Flow<List<VehiculosEntity>> =
        db.vehiculoDao().observarPorAgencia(agencia)

    fun observarVehiculosCatalogo(): Flow<List<VehiculosEntity>> =
        db.vehiculoDao().observarTodos()

    fun observarUltimoKilometraje(placa: String): Flow<Double?> {
        val placaLong = VehiculoPlacaUtils.parsePlacaLong(placa) ?: return flowOf(null)
        return vehiculoDao.observarPorPlaca(placaLong)
            .map { it?.kmActual?.takeIf { km -> km > 0.0 } ?: it?.kmActual }
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

    fun observarVehiculoPorPlaca(placa: Long): Flow<VehiculosEntity?> =
        vehiculoDao.observarPorPlaca(placa)

    fun observeVehiculo(vehiculoId: String): Flow<VehiculosEntity?> =
        vehiculoDao.observarPorVehiculoId(vehiculoId)

    fun observeTimeline(vehiculoId: String): Flow<List<VehiculoLogEntity>> =
        vehiculoLogDao.observeTimeline(vehiculoId)

    fun observarRegistrosDiarios(vehiculoKey: String): Flow<List<RegistroDiarioEntity>> =
        vehiculoLogDao.observeByTipo(vehiculoKey, "DIARIO").map { logs ->
            logs.map { log ->
                RegistroDiarioEntity(
                    vehiculoId = vehiculoKey.toIntOrNull() ?: 0,
                    fecha = log.payloadJson.optStringSafe("fecha"),
                    valor = log.km ?: 0.0,
                    unidad = log.payloadJson.optStringSafe("unidad", "km"),
                    registradoEn = log.timestamp,
                    registradoPor = log.payloadJson.optStringSafe("registradoPor").ifBlank { null },
                    syncStatus = log.syncState
                )
            }
        }

    fun observarMantenimientos(vehiculoKey: String): Flow<List<RegistroMantenimientoEntity>> =
        vehiculoLogDao.observeByTipo(vehiculoKey, "MANTENIMIENTO").map { logs ->
            logs.map { log ->
                RegistroMantenimientoEntity(
                    vehiculoId = vehiculoKey.toIntOrNull() ?: 0,
                    tipoMantenimiento = log.payloadJson.optStringSafe("tipoMantenimiento", "General"),
                    valorActual = log.km ?: 0.0,
                    unidad = log.payloadJson.optStringSafe("unidad", "km"),
                    observaciones = log.payloadJson.optStringSafe("observaciones").ifBlank { null },
                    proximoMantenimiento = log.payloadJson.optDoubleSafe("proximoMantenimiento", log.km ?: 0.0),
                    creadoEn = log.timestamp,
                    syncStatus = log.syncState
                )
            }
        }

    suspend fun upsertVehiculo(entity: VehiculosEntity) = withContext(Dispatchers.IO) {
        vehiculoDao.upsertVehiculo(entity)
    }

    suspend fun actualizarKmActual(placa: String, km: Double) = withContext(Dispatchers.IO) {
        val vehiculo = vehiculoDao.buscarPorVehiculoId(placa) ?: return@withContext
        val updatedAt = System.currentTimeMillis()
        vehiculoDao.actualizarKilometrajeByVehiculoId(placa, km, updatedAt)
        firebase.actualizarVehiculoKm(placa, km)
    }

    suspend fun actualizarSubregion(placa: String, subregion: String?) = withContext(Dispatchers.IO) {
        val vehiculo = vehiculoDao.buscarPorVehiculoId(placa) ?: return@withContext
        val updatedAt = System.currentTimeMillis()
        vehiculoDao.upsertVehiculo(vehiculo.copy(subregion = subregion, updatedAt = updatedAt))
        firebase.actualizarVehiculoCampos(placa, mapOf("subregion" to subregion))
    }

    suspend fun cerrarRegistro(placa: String, cerrado: Boolean) = withContext(Dispatchers.IO) {
        val vehiculo = vehiculoDao.buscarPorVehiculoId(placa) ?: return@withContext
        val updatedAt = System.currentTimeMillis()
        vehiculoDao.upsertVehiculo(vehiculo.copy(registroCerrado = cerrado, updatedAt = updatedAt))
        firebase.actualizarVehiculoCampos(placa, mapOf("registroCerrado" to cerrado))
    }

    suspend fun insertarRegistroDiario(registro: RegistroDiarioEntity) = withContext(Dispatchers.IO) {
        val vehiculoKey = resolveVehiculoKey(registro.vehiculoId)
        val log = VehiculoLogEntity(
            logId = "diario_${vehiculoKey}_${registro.registradoEn}",
            vehiculoId = vehiculoKey,
            tipo = "DIARIO",
            timestamp = registro.registradoEn,
            km = registro.valor,
            payloadJson = JSONObject().put("fecha", registro.fecha).put("unidad", registro.unidad).put("registradoPor", registro.registradoPor).toString(),
            syncState = "PENDING"
        )
        addLogAndUpdateKm(log)
    }

    suspend fun insertarRegistroMantenimiento(registro: RegistroMantenimientoEntity) = withContext(Dispatchers.IO) {
        val vehiculoKey = resolveVehiculoKey(registro.vehiculoId)
        val log = VehiculoLogEntity(
            logId = "mant_${vehiculoKey}_${registro.creadoEn}",
            vehiculoId = vehiculoKey,
            tipo = "MANTENIMIENTO",
            timestamp = registro.creadoEn,
            km = registro.valorActual,
            payloadJson = JSONObject().put("tipoMantenimiento", registro.tipoMantenimiento).put("unidad", registro.unidad).put("observaciones", registro.observaciones).put("proximoMantenimiento", registro.proximoMantenimiento).toString(),
            syncState = "PENDING"
        )
        addLogAndUpdateKm(log)
    }

    suspend fun addLogAndUpdateKm(log: VehiculoLogEntity) = withContext(Dispatchers.IO) {
        db.withTransaction {
            vehiculoLogDao.upsert(log)
            if (log.km != null) {
                val vehiculo = vehiculoDao.buscarPorVehiculoId(log.vehiculoId)
                if (vehiculo != null) {
                    val actual = vehiculo.kmActual.takeIf { it > 0.0 } ?: (vehiculo.kmActual ?: 0.0)
                    val nuevoKm = maxOf(actual, log.km)
                    vehiculoDao.upsertVehiculo(vehiculo.copy(kmActual = nuevoKm, updatedAt = System.currentTimeMillis()))
                }
            }
        }
    }

    suspend fun getPendingLogs(vehiculoId: String, limit: Int = 100): List<VehiculoLogEntity> = withContext(Dispatchers.IO) {
        vehiculoLogDao.getPending(vehiculoId, limit = limit)
    }

    suspend fun markLogsSynced(logIds: List<String>) = withContext(Dispatchers.IO) {
        if (logIds.isNotEmpty()) vehiculoLogDao.markState(logIds, "SYNCED")
    }

    suspend fun markLogError(logId: String, reason: String) = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("error", reason).toString()
        vehiculoLogDao.markSingle(logId, "ERROR", payload)
    }

    suspend fun actualizarMantenimiento(
        vehiculoId: Int,
        mantenimientoUltimo: String?,
        mantenimientoProximo: String?,
        valorActual: Double? = null,
        usaKilometraje: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val vehiculo = vehiculoDao.buscarPorId(vehiculoId) ?: return@withContext
        val kilometrajeBase = vehiculo.kmActual.takeIf { it > 0.0 } ?: (vehiculo.kmActual ?: 0.0)
        val orimetroBase = vehiculo.orimetroActual ?: 0.0
        val nuevoKilometraje = if (usaKilometraje && valorActual != null) {
            maxOf(kilometrajeBase, valorActual)
        } else {
            kilometrajeBase
        }
        val nuevoOrimetro = if (!usaKilometraje && valorActual != null) {
            maxOf(orimetroBase, valorActual)
        } else {
            vehiculo.orimetroActual
        }

        val actualizado = vehiculo.copy(
            mantenimientoUltimo = mantenimientoUltimo,
            mantenimientoProximo = mantenimientoProximo,
            kmActual = nuevoKilometraje,
            orimetroActual = nuevoOrimetro,
            updatedAt = System.currentTimeMillis()
        )
        vehiculoDao.upsertVehiculo(actualizado)
        firebase.actualizarVehiculoCampos(actualizado.vehiculoId, mapOf(
            "kmActual" to actualizado.kmActual,
            "registroCerrado" to actualizado.registroCerrado,
            "mantenimientoUltimo" to actualizado.mantenimientoUltimo,
            "mantenimientoProximo" to actualizado.mantenimientoProximo
        ))
    }

    suspend fun actualizarRegistroDiarioVehiculo(
        vehiculoId: Int,
        fecha: String,
        inicial: Double,
        final: Double?,
        cerrado: Boolean,
        kilometrajeActual: Double?,
        orimetroActual: Double?,
        registrosJson: String?
    ) = withContext(Dispatchers.IO) {
        val vehiculo = vehiculoDao.buscarPorId(vehiculoId) ?: return@withContext
        val actualizado = vehiculo.copy(
            registroFecha = fecha,
            registroInicial = inicial,
            registroFinal = final,
            registroCerrado = cerrado,
            kmActual = kilometrajeActual ?: vehiculo.kmActual,
            orimetroActual = orimetroActual ?: vehiculo.orimetroActual,
            registrosDiariosJson = registrosJson
        )
        firebase.actualizarVehiculoCampos(actualizado.vehiculoId, mapOf(
            "kmActual" to actualizado.kmActual,
            "registroCerrado" to actualizado.registroCerrado
        ))
        vehiculoDao.insertAll(listOf(actualizado))
    }

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

    suspend fun buscarMedidorPorLocalizacion(localizacion: Long): MedidorEntity? =
        db.medidorDao().buscarPorLocalizacion(localizacion)

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

    fun observarUsuario(uid: String): Flow<UserEntity?> =
        db.usuarioDao().observeByUid(uid)

    suspend fun saveUser(user: UserEntity) = withContext(Dispatchers.IO) {
        db.usuarioDao().upsert(user)
    }

    suspend fun buscarUsuarioPorEmail(email: String): UserEntity? = withContext(Dispatchers.IO) {
        firebase.buscarUsuarioPorEmail(email)
    }

    suspend fun buscarUsuarioPorCedula(cedula: String): UserEntity? = withContext(Dispatchers.IO) {
        firebase.buscarUsuarioPorCedula(cedula)
    }

    suspend fun buildUserScope(uid: String): UserScope = withContext(Dispatchers.IO) {
        val user = db.usuarioDao().getByUid(uid) ?: runCatching { upsertUserFromFirebase(uid) }.getOrNull()
        UserScope(
            regionId = user?.region?.trim()?.takeIf { it.isNotEmpty() },
            subregionId = user?.subregion?.trim()?.takeIf { it.isNotEmpty() },
            agenciaTag = user?.agenciaId?.trim()?.takeIf { it.isNotEmpty() }
                ?: user?.agencia?.trim()?.takeIf { it.isNotEmpty() },
            vehiculoKey = user?.placaVehiculo?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    suspend fun actualizarUsuarioAdmin(user: UserEntity) = withContext(Dispatchers.IO) {
        firebase.actualizarUsuarioAdmin(user)
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


    suspend fun obtenerVehiculosCatalogoLocal(): List<VehiculosEntity> = withContext(Dispatchers.IO) {
        db.vehiculoDao().getAll()
    }

    suspend fun obtenerRegistroDiarioPorFecha(vehiculoId: Int, fecha: String): RegistroDiarioEntity? {
        val vehiculoKey = resolveVehiculoKey(vehiculoId)
        return vehiculoLogDao.findLastByTipo(vehiculoKey, "DIARIO")
            ?.toRegistroDiarioEntity(vehiculoId)
            ?.takeIf { it.fecha == fecha }
    }

    suspend fun obtenerUltimoRegistroDiario(vehiculoId: Int): RegistroDiarioEntity? {
        val vehiculoKey = resolveVehiculoKey(vehiculoId)
        return vehiculoLogDao.findLastByTipo(vehiculoKey, "DIARIO")
            ?.toRegistroDiarioEntity(vehiculoId)
    }

    suspend fun obtenerUltimoMantenimiento(vehiculoId: Int): RegistroMantenimientoEntity? {
        val vehiculoKey = resolveVehiculoKey(vehiculoId)
        return vehiculoLogDao.findLastByTipo(vehiculoKey, "MANTENIMIENTO")
            ?.toRegistroMantenimientoEntity(vehiculoId)
    }

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
        firebase.actualizarVehiculoCampos(vehiculo.vehiculoId, mapOf(
            "tipo" to vehiculo.tipo,
            "subregion" to vehiculo.subregion,
            "agencia" to vehiculo.agencia,
            "kmActual" to vehiculo.kmActual,
            "registroCerrado" to vehiculo.registroCerrado
        ))
        db.vehiculoDao().insertAll(listOf(vehiculo))
    }

    suspend fun registrarKilometrajeVehicular(
        placa: String,
        kilometrajeFinal: Double,
        timestamp: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val placaLong = VehiculoPlacaUtils.parsePlacaLong(placa) ?: return@withContext
        val vehiculo = vehiculoDao.buscarPorPlaca(placaLong) ?: return@withContext
        addLogAndUpdateKm(
            VehiculoLogEntity(
                logId = "km_${vehiculo.vehiculoId}_$timestamp",
                vehiculoId = vehiculo.vehiculoId,
                tipo = "KM",
                timestamp = timestamp,
                km = kilometrajeFinal,
                payloadJson = JSONObject().put("placa", placa.trim()).toString(),
                syncState = "PENDING"
            )
        )
    }

    suspend fun registrarMantenimientoVehicular(
        placa: String,
        vehiculoId: Int?,
        tipo: String,
        fecha: Long?,
        valorAlMomento: Double?,
        observaciones: String?,
        proximoKm: Double?,
        proximoHoras: Double?,
        proximoFecha: Long?,
        registradoEn: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val targetVehiculo = when {
            vehiculoId != null -> vehiculoDao.buscarPorId(vehiculoId)
            else -> VehiculoPlacaUtils.parsePlacaLong(placa)?.let { vehiculoDao.buscarPorPlaca(it) }
        } ?: return@withContext
        val payload = JSONObject()
            .put("placa", placa.trim())
            .put("tipo", tipo)
            .put("fecha", fecha)
            .put("observaciones", observaciones)
            .put("proximoKm", proximoKm)
            .put("proximoHoras", proximoHoras)
            .put("proximoFecha", proximoFecha)
            .toString()
        addLogAndUpdateKm(
            VehiculoLogEntity(
                logId = "mant_${targetVehiculo.vehiculoId}_$registradoEn",
                vehiculoId = targetVehiculo.vehiculoId,
                tipo = "MANTENIMIENTO",
                timestamp = registradoEn,
                km = valorAlMomento,
                payloadJson = payload,
                syncState = "PENDING"
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
        val vehiculoKey = resolveVehiculoKey(vehiculoId)
        val existente = inventarioDao.obtenerItem(vehiculoId, codigo)
        val nuevaCantidad = (existente?.cantidadDisponible ?: 0.0) + delta
        if (nuevaCantidad <= 0) {
            existente?.let { inventarioDao.eliminarPorId(it.id) }
            firebase.eliminarInventarioItem(vehiculoKey, codigo)
        } else {
            val item = InventarioItemEntity(
                id = existente?.id ?: 0L,
                vehiculoId = vehiculoId,
                codigoMaterial = codigo.trim(),
                descripcionMaterial = descripcion.ifBlank { existente?.descripcionMaterial ?: codigo },
                cantidadDisponible = nuevaCantidad
            )
            inventarioDao.upsert(item)
            firebase.guardarInventarioItem(vehiculoKey, item)
        }
    }

    suspend fun eliminarInventarioItem(id: Long) = withContext(Dispatchers.IO) {
        val item = inventarioDao.obtenerItemPorId(id)
        inventarioDao.eliminarPorId(id)
        item?.let { firebase.eliminarInventarioItem(resolveVehiculoKey(it.vehiculoId), it.codigoMaterial) }
    }

    suspend fun cargarInventarioDesdeCsv(
        vehiculoId: Int,
        items: List<Pair<String, Double>>
    ) = withContext(Dispatchers.IO) {
        cargarInventarioDesdeLista(vehiculoId, items)
    }

    suspend fun cargarInventarioDesdeLista(
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
        val inventarioActualizado = inventarioDao.obtenerPorVehiculo(vehiculoId)
        firebase.guardarInventarioVehiculo(resolveVehiculoKey(vehiculoId), vehiculoId, inventarioActualizado)
    }

    suspend fun obtenerCodigosMateriales(codigos: Set<String>): Set<String> = withContext(Dispatchers.IO) {
        if (codigos.isEmpty()) return@withContext emptySet()
        db.materialDao().obtenerCodigos(codigos.toList()).toSet()
    }

    suspend fun registrarReparacionLuminaria(
        vehiculoId: Int,
        localizacion: String,
        materiales: List<com.Arasoftsolutions.tecniapp_ice.ui.luminarias.LuminariaMaterialUso>,
        estado: com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado,
        ejecutorNombre: String,
        ejecutorCedula: String?
    ) = withContext(Dispatchers.IO) {
        val ahora = System.currentTimeMillis()
        val fechaReparacion = if (estado == com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado.REPARADA) {
            ahora
        } else {
            null
        }
        val reparacion = LuminariaReparacionEntity(
            vehiculoId = vehiculoId,
            localizacion = localizacion,
            materialesJson = com.Arasoftsolutions.tecniapp_ice.ui.luminarias.LuminariaMaterialSerializer
                .toJson(materiales),
            estado = estado.name,
            ejecutorNombre = ejecutorNombre,
            ejecutorCedula = ejecutorCedula,
            fechaRegistro = ahora,
            fechaCarga = ahora,
            fechaReparacion = fechaReparacion
        )
        val reparacionId = inventarioDao.registrarReparacion(reparacion)
        val agencia = db.vehiculoDao().buscarPorId(vehiculoId)?.agencia
        firebase.guardarReparacionLuminaria(reparacion.copy(id = reparacionId), agencia)
        materiales.forEach { material ->
            ajustarInventario(vehiculoId, material.codigo, material.descripcion, -material.cantidad)
        }
    }

    suspend fun registrarLuminariasPendientes(
        vehiculoId: Int,
        registros: List<com.Arasoftsolutions.tecniapp_ice.ui.luminarias.LuminariaCsvRegistro>,
        ejecutorNombre: String,
        ejecutorCedula: String?
    ) = withContext(Dispatchers.IO) {
        if (registros.isEmpty()) return@withContext
        val agencia = db.vehiculoDao().buscarPorId(vehiculoId)?.agencia
        registros
            .mapNotNull { it.localizacion.trim().takeIf(String::isNotEmpty)?.let { loc -> it.copy(localizacion = loc) } }
            .forEach { registro ->
                val existente = inventarioDao.obtenerReparacionPorLocalizacionYEstado(
                    registro.localizacion,
                    com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado.PENDIENTE.name,
                    vehiculoId
                )
                val cliente = registro.cliente?.trim().takeIf { !it.isNullOrEmpty() }
                val contacto = registro.contacto?.trim().takeIf { !it.isNullOrEmpty() }
                val observaciones = registro.observaciones?.trim().takeIf { !it.isNullOrEmpty() }
                if (existente != null) {
                    val actualizado = existente.copy(
                        cliente = existente.cliente ?: cliente,
                        contacto = existente.contacto ?: contacto,
                        observaciones = existente.observaciones ?: observaciones
                    )
                    if (actualizado != existente) {
                        inventarioDao.actualizarReparacion(actualizado)
                        firebase.guardarReparacionLuminaria(actualizado, agencia)
                    }
                    return@forEach
                }
                val ahora = System.currentTimeMillis()
                val reparacion = LuminariaReparacionEntity(
                    vehiculoId = vehiculoId,
                    localizacion = registro.localizacion,
                    cliente = cliente,
                    contacto = contacto,
                    observaciones = observaciones,
                    materialesJson = com.Arasoftsolutions.tecniapp_ice.ui.luminarias.LuminariaMaterialSerializer
                        .toJson(emptyList()),
                    estado = com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado.PENDIENTE.name,
                    ejecutorNombre = ejecutorNombre,
                    ejecutorCedula = ejecutorCedula,
                    fechaRegistro = ahora,
                    fechaCarga = ahora
                )
                val reparacionId = inventarioDao.registrarReparacion(reparacion)
                firebase.guardarReparacionLuminaria(reparacion.copy(id = reparacionId), agencia)
            }
    }

    suspend fun eliminarReparacionLuminaria(id: Long) = withContext(Dispatchers.IO) {
        val reparacion = inventarioDao.obtenerReparacion(id) ?: return@withContext
        inventarioDao.eliminarReparacion(id)
        val agencia = db.vehiculoDao().buscarPorId(reparacion.vehiculoId)?.agencia
        firebase.eliminarReparacionLuminaria(id, agencia)
        val materiales = com.Arasoftsolutions.tecniapp_ice.ui.luminarias.LuminariaMaterialSerializer
            .fromJson(reparacion.materialesJson)
        materiales.forEach { material ->
            ajustarInventario(
                vehiculoId = reparacion.vehiculoId,
                codigo = material.codigo,
                descripcion = material.descripcion,
                delta = material.cantidad
            )
        }
    }

    suspend fun marcarReparacionLuminariaPendiente(id: Long) = withContext(Dispatchers.IO) {
        val reparacion = inventarioDao.obtenerReparacion(id) ?: return@withContext
        if (com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado.fromRaw(reparacion.estado) !=
            com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado.REPARADA
        ) {
            return@withContext
        }
        val materialesPrevios = com.Arasoftsolutions.tecniapp_ice.ui.luminarias.LuminariaMaterialSerializer
            .fromJson(reparacion.materialesJson)
        val actualizado = reparacion.copy(
            estado = com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado.PENDIENTE.name,
            materialesJson = com.Arasoftsolutions.tecniapp_ice.ui.luminarias.LuminariaMaterialSerializer
                .toJson(emptyList()),
            fechaReparacion = null
        )
        inventarioDao.actualizarReparacion(actualizado)
        val agencia = db.vehiculoDao().buscarPorId(reparacion.vehiculoId)?.agencia
        firebase.guardarReparacionLuminaria(actualizado, agencia)
        materialesPrevios.forEach { material ->
            ajustarInventario(
                vehiculoId = reparacion.vehiculoId,
                codigo = material.codigo,
                descripcion = material.descripcion,
                delta = material.cantidad
            )
        }
    }

    suspend fun obtenerReparacionLuminaria(id: Long): LuminariaReparacionEntity? = withContext(Dispatchers.IO) {
        inventarioDao.obtenerReparacion(id)
    }

    suspend fun actualizarVehiculoLuminaria(id: Long, nuevoVehiculoId: Int) = withContext(Dispatchers.IO) {
        val reparacion = inventarioDao.obtenerReparacion(id) ?: return@withContext
        if (reparacion.vehiculoId == nuevoVehiculoId) return@withContext
        val agenciaAnterior = db.vehiculoDao().buscarPorId(reparacion.vehiculoId)?.agencia
        val agenciaNueva = db.vehiculoDao().buscarPorId(nuevoVehiculoId)?.agencia
        val actualizado = reparacion.copy(vehiculoId = nuevoVehiculoId)
        inventarioDao.actualizarReparacion(actualizado)
        if (!agenciaAnterior.equals(agenciaNueva, ignoreCase = true)) {
            firebase.eliminarReparacionLuminaria(id, agenciaAnterior)
        }
        firebase.guardarReparacionLuminaria(actualizado, agenciaNueva)
    }

    suspend fun actualizarReparacionLuminaria(
        id: Long,
        nuevaLocalizacion: String,
        nuevosMateriales: List<com.Arasoftsolutions.tecniapp_ice.ui.luminarias.LuminariaMaterialUso>,
        nuevoEstado: com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado,
        nuevoEjecutorNombre: String,
        nuevoEjecutorCedula: String?
    ) = withContext(Dispatchers.IO) {
        val reparacion = inventarioDao.obtenerReparacion(id) ?: return@withContext
        val materialesPrevios = com.Arasoftsolutions.tecniapp_ice.ui.luminarias.LuminariaMaterialSerializer
            .fromJson(reparacion.materialesJson)
        val mapPrevio = materialesPrevios.associateBy({ it.codigo }, { it })
        val mapNuevo = nuevosMateriales.associateBy({ it.codigo }, { it })
        val todosCodigos = (mapPrevio.keys + mapNuevo.keys).toSet()
        val fechaReparacion = if (nuevoEstado == com.Arasoftsolutions.tecniapp_ice.Database.entities.LuminariaEstado.REPARADA) {
            reparacion.fechaReparacion ?: System.currentTimeMillis()
        } else {
            reparacion.fechaReparacion
        }
        val agencia = db.vehiculoDao().buscarPorId(reparacion.vehiculoId)?.agencia
        inventarioDao.actualizarReparacion(
            reparacion.copy(
                localizacion = nuevaLocalizacion,
                materialesJson = com.Arasoftsolutions.tecniapp_ice.ui.luminarias.LuminariaMaterialSerializer
                    .toJson(nuevosMateriales),
                estado = nuevoEstado.name,
                ejecutorNombre = nuevoEjecutorNombre,
                ejecutorCedula = nuevoEjecutorCedula,
                fechaReparacion = fechaReparacion
            )
        )
        firebase.guardarReparacionLuminaria(
            reparacion.copy(
                localizacion = nuevaLocalizacion,
                materialesJson = com.Arasoftsolutions.tecniapp_ice.ui.luminarias.LuminariaMaterialSerializer
                    .toJson(nuevosMateriales),
                estado = nuevoEstado.name,
                ejecutorNombre = nuevoEjecutorNombre,
                ejecutorCedula = nuevoEjecutorCedula,
                fechaReparacion = fechaReparacion
            ),
            agencia
        )
        todosCodigos.forEach { codigo ->
            val anterior = mapPrevio[codigo]
            val nuevo = mapNuevo[codigo]
            val deltaCantidad = (nuevo?.cantidad ?: 0.0) - (anterior?.cantidad ?: 0.0)
            if (deltaCantidad != 0.0) {
                ajustarInventario(
                    vehiculoId = reparacion.vehiculoId,
                    codigo = codigo,
                    descripcion = nuevo?.descripcion ?: anterior?.descripcion.orEmpty(),
                    delta = -deltaCantidad
                )
            }
        }
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
    suspend fun syncTecnicos(): Long = withContext(Dispatchers.IO) {
        val tecnicos = firebase.obtenerTecnicos()
        val bytes = estimateBytes(tecnicos)
        if (tecnicos.isNotEmpty()) {
            db.tecnicoDao().eliminarFueraDeCedulas(tecnicos.map { it.cedula })
            db.tecnicoDao().insertAll(tecnicos)
        } else {
            db.tecnicoDao().limpiarTodo()
        }
        bytes
    }

    suspend fun syncMateriales(): Long = withContext(Dispatchers.IO) {
        val materiales = firebase.obtenerMaterialesCatalogo()
        val bytes = estimateBytes(materiales)
        if (materiales.isNotEmpty()) {
            db.materialDao().eliminarFueraDeCodigos(materiales.map { it.codigo })
            db.materialDao().insertAll(materiales)
        } else {
            db.materialDao().limpiarTodo()
        }
        bytes
    }

    suspend fun syncInventario(): Long = withContext(Dispatchers.IO) {
        val inventario = firebase.obtenerInventario()
        val bytes = estimateBytes(inventario)
        inventarioDao.limpiarTodo()
        if (inventario.isNotEmpty()) {
            inventarioDao.insertAll(inventario)
        }
        bytes
    }

    suspend fun syncLuminarias(agencia: String? = null): Long = withContext(Dispatchers.IO) {
        val reparaciones = firebase.obtenerLuminarias(agencia)
        val bytes = estimateBytes(reparaciones)
        inventarioDao.limpiarReparaciones()
        if (reparaciones.isNotEmpty()) {
            inventarioDao.insertarReparaciones(reparaciones)
        }
        bytes
    }

    suspend fun syncInventarioVehiculo(vehiculoId: Int, vehiculoKey: String): Long = withContext(Dispatchers.IO) {
        val key = vehiculoKey.trim()
        if (key.isEmpty()) return@withContext 0L
        val inventarioDirecto = firebase.obtenerInventarioDeVehiculo(key)
        val inventario = if (inventarioDirecto.isNotEmpty()) {
            inventarioDirecto
        } else {
            firebase.obtenerInventario().filter { it.vehiculoId == vehiculoId }
        }
        val bytes = estimateBytes(inventario)
        inventarioDao.eliminarPorVehiculo(vehiculoId)
        if (inventario.isNotEmpty()) {
            inventarioDao.insertAll(inventario)
        }
        bytes
    }

    suspend fun syncLuminariasAgencia(agencia: String): Long = withContext(Dispatchers.IO) {
        val agenciaValue = agencia.trim()
        if (agenciaValue.isEmpty()) return@withContext 0L
        val reparacionesDirectas = firebase.obtenerLuminariasPorAgencia(agenciaValue)
        val reparaciones = if (reparacionesDirectas.isNotEmpty()) {
            reparacionesDirectas
        } else {
            firebase.obtenerLuminarias(null)
        }
        val bytes = estimateBytes(reparaciones)
        inventarioDao.limpiarReparaciones()
        if (reparaciones.isNotEmpty()) {
            inventarioDao.insertarReparaciones(reparaciones)
        }
        bytes
    }

    private suspend fun resolveVehiculoKey(vehiculoId: Int): String {
        val vehiculo = db.vehiculoDao().buscarPorId(vehiculoId)
        return vehiculo?.vehiculoId?.takeIf { it.isNotBlank() }
            ?: vehiculo?.placaRaw?.takeIf { it.isNotBlank() }
            ?: vehiculo?.placa?.takeIf { it != 0L }?.toString()
            ?: vehiculoId.toString()
    }

    suspend fun syncSubregion(
        subregionId: String,
        progress: (done: Int, total: Int, msg: String?, downloadedBytes: Long) -> Unit = { _, _, _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val total = SUBREGION_SYNC_STEPS * 100
        var done = 0
        var downloadedBytes = 0L
        val syncStartedAt = System.currentTimeMillis()
        Log.i(TAG, "[SYNC_SUBREGION] start subregion=$subregionId totalSteps=$total")

        // Si quieres transacción atómica, descomenta y usa withTransaction:
        // db.withTransaction {
        val canonicalSubregion = SubregionNormalizer.canonicalIdOrSelf(subregionId)
            ?: throw IllegalArgumentException("Subregión inválida: $subregionId")
        Log.i(TAG, "[SYNC_SUBREGION] canonicalSubregion=$canonicalSubregion")

        val agenciasPorSubregion = firebase.obtenerAgenciasPorSubregion(canonicalSubregion)
        val agencias = agenciasPorSubregion.ifEmpty { firebase.obtenerAgencias(canonicalSubregion) }
        downloadedBytes += estimateBytes(agencias)
        if (agencias.isNotEmpty()) {
            db.agenciaDao().eliminarPorSubregion(canonicalSubregion)
            db.agenciaDao().insertAll(agencias)
        }
        done += 100
        progress(done, total, "Descargando agencias…", downloadedBytes)
        Log.i(TAG, "[SYNC_SUBREGION] step=$done/$total source=datos_generales/agencias count=${agencias.size} bytes=$downloadedBytes")

        val pueblosRemotos = firebase.obtenerPueblosPorSubregion(canonicalSubregion)
        downloadedBytes += estimateBytes(pueblosRemotos)
        val pueblosASincronizar = if (pueblosRemotos.isNotEmpty()) {
            pueblosRemotos
        } else {
            Log.w(
                "RoomRepository",
                "No se encontraron pueblos para subregión=$canonicalSubregion; se omite el sincronizado de pueblos/localizaciones."
            )
            emptyList()
        }
        if (pueblosASincronizar.isNotEmpty()) {
            db.puebloDao().limpiarSubregion(canonicalSubregion)
            db.puebloDao().insertAll(pueblosASincronizar)
        }
        done += 100
        progress(done, total, "Descargando pueblos…", downloadedBytes)
        Log.i(TAG, "[SYNC_SUBREGION] step=$done/$total source=local/pueblos count=${pueblosASincronizar.size} bytes=$downloadedBytes")

        val idsPueblos = pueblosASincronizar.map { it.id }
        val localizacionesFiltradas = firebase.obtenerLocalizacionesPorPueblos(idsPueblos)
        downloadedBytes += estimateBytes(localizacionesFiltradas)
        if (idsPueblos.isNotEmpty()) {
            db.localizacionDao().eliminarPorPueblos(idsPueblos)
        }
        if (localizacionesFiltradas.isNotEmpty()) {
            db.localizacionDao().insertAll(localizacionesFiltradas)
        }
        done += 100
        progress(done, total, "Descargando localizaciones…", downloadedBytes)
        Log.i(TAG, "[SYNC_SUBREGION] step=$done/$total source=local/localizaciones puebloCount=${idsPueblos.size} localizaciones=${localizacionesFiltradas.size} bytes=$downloadedBytes")

        val vehiculosPorSubregion = firebase.obtenerVehiculosPorSubregion(canonicalSubregion)
        val vehiculosRemotos = vehiculosPorSubregion.ifEmpty { firebase.obtenerVehiculos(canonicalSubregion) }
        val vehiculos = deduplicarVehiculos(vehiculosRemotos)
        downloadedBytes += estimateBytes(vehiculos)
        if (vehiculos.isNotEmpty()) {
            db.vehiculoDao().eliminarPorSubregion(canonicalSubregion)
        }
        val vehiculosCombinados = combinarVehiculosConLocales(vehiculos)
        db.vehiculoDao().insertAll(vehiculosCombinados)
        done += 100
        progress(done, total, "Descargando vehículos…", downloadedBytes)
        Log.i(TAG, "[SYNC_SUBREGION] step=$done/$total source=datos_generales/vehiculos count=${vehiculosCombinados.size} bytes=$downloadedBytes")

        var medidoresTotal = 0
        var medidoresLotes = 0
        var totalMedidoresObjetivo = db.medidorDao().contarPorSubregion(canonicalSubregion)
        val medidoresSyncStartedAt = System.currentTimeMillis()
        db.medidorDao().eliminarPorSubregion(canonicalSubregion)
        firebase.obtenerMedidoresPorLotes(
            subregionId = canonicalSubregion,
            batchSize = 1000
        ) { medidoresBatch ->
            if (medidoresBatch.isEmpty()) return@obtenerMedidoresPorLotes
            db.medidorDao().insertAll(medidoresBatch)
            downloadedBytes += estimateMedidoresBatchBytes(medidoresBatch.size)
            medidoresTotal += medidoresBatch.size
            medidoresLotes += 1
            if (medidoresTotal > totalMedidoresObjetivo) {
                totalMedidoresObjetivo = medidoresTotal
            }
            val elapsed = formatElapsed(System.currentTimeMillis() - medidoresSyncStartedAt)
            Log.i(
                TAG,
                "[SYNC_SUBREGION] medidores_lote=$medidoresLotes loteSize=${medidoresBatch.size} total=$medidoresTotal objetivo=$totalMedidoresObjetivo elapsed=$elapsed bytes=$downloadedBytes"
            )
            progress(
                done + progressUnitsForMedidores(medidoresTotal, totalMedidoresObjetivo),
                total,
                "Descargando medidores… ($medidoresTotal/$totalMedidoresObjetivo) · $elapsed",
                downloadedBytes
            )
        }
        done += 100
        progress(done, total, "Descargando medidores…", downloadedBytes)
        Log.i(TAG, "[SYNC_SUBREGION] step=$done/$total source=medidores/medidores count=$medidoresTotal bytes=$downloadedBytes")
        Log.i(TAG, "[SYNC_SUBREGION] completed subregion=$canonicalSubregion totalBytes=$downloadedBytes tookMs=${System.currentTimeMillis()-syncStartedAt}")
        // }
    }

    suspend fun upsertUserFromFirebase(uid: String): UserEntity = withContext(Dispatchers.IO) {
        val user = firebase.obtenerUsuario(uid)
            ?: throw IllegalStateException("Usuario no encontrado en Firebase")
        db.usuarioDao().upsert(user)
        user
    }

    suspend fun refreshUsuarioActual(): UserEntity? = withContext(Dispatchers.IO) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext null
        val user = firebase.obtenerUsuario(uid) ?: return@withContext null
        db.usuarioDao().upsert(user)
        user
    }

    suspend fun syncCatalogosGenerales(): Long = withContext(Dispatchers.IO) {
        var downloadedBytes = 0L
        val regiones = firebase.obtenerRegiones()
        downloadedBytes += estimateBytes(regiones)
        if (regiones.isNotEmpty()) {
            db.regionDao().eliminarFueraDeIds(regiones.map { it.id })
            db.regionDao().insertAll(regiones)
        } else {
            db.regionDao().limpiarTodo()
        }

        val subregiones = firebase.obtenerSubregiones()
        downloadedBytes += estimateBytes(subregiones)
        if (subregiones.isNotEmpty()) {
            db.subregionDao().eliminarFueraDeIds(subregiones.map { it.id })
            db.subregionDao().insertAll(subregiones)
        } else {
            db.subregionDao().limpiarTodo()
        }

        val agencias = firebase.obtenerAgencias()
        downloadedBytes += estimateBytes(agencias)
        if (agencias.isNotEmpty()) {
            db.agenciaDao().eliminarFueraDeIds(agencias.map { it.id })
            db.agenciaDao().insertAll(agencias)
        } else {
            db.agenciaDao().limpiarTodo()
        }

        val vehiculosRemotos = firebase.obtenerVehiculos()
        val vehiculos = deduplicarVehiculos(vehiculosRemotos)
        downloadedBytes += estimateBytes(vehiculos)
        if (vehiculos.isNotEmpty()) {
            db.vehiculoDao().eliminarFueraDeIds(vehiculos.map { it.id })
            val vehiculosCombinados = combinarVehiculosConLocales(vehiculos)
            db.vehiculoDao().insertAll(vehiculosCombinados)
        } else {
            db.vehiculoDao().limpiarTodo()
        }

        runCatching { syncTecnicos() }
        runCatching { syncMateriales() }
        downloadedBytes
    }

    private fun estimateBytes(value: Any?): Long {
        return when (value) {
            null -> 0L
            is String -> value.toByteArray(Charsets.UTF_8).size.toLong()
            is Number, is Boolean -> 8L
            is MedidorEntity -> estimateMedidorBytes(value)
            is Collection<*> -> value.sumOf { estimateBytes(it) }
            is Map<*, *> -> value.entries.sumOf { estimateBytes(it.key) + estimateBytes(it.value) }
            else -> 64L
        }
    }

    private fun estimateMedidorBytes(medidor: MedidorEntity): Long {
        return estimateBytes(medidor.medidorNumber) +
            estimateBytes(medidor.subregion) +
            estimateBytes(medidor.cliente) +
            estimateBytes(medidor.calle) +
            estimateBytes(medidor.poste) +
            estimateBytes(medidor.metros) +
            estimateBytes(medidor.pueblo) +
            8L
    }

    private fun estimateMedidoresBatchBytes(batchSize: Int): Long {
        return batchSize.toLong() * 180L
    }

    private fun formatElapsed(elapsedMs: Long): String {
        val totalSeconds = elapsedMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun progressUnitsForMedidores(descargados: Int, objetivo: Int): Int {
        if (objetivo <= 0) return 0
        val ratio = descargados.toDouble() / objetivo.toDouble()
        return (ratio * 100.0).toInt().coerceIn(0, 100)
    }

    private suspend fun combinarVehiculosConLocales(remotos: List<VehiculosEntity>): List<VehiculosEntity> {
        val remotosNormalizados = deduplicarVehiculos(remotos)
        if (remotosNormalizados.isEmpty()) return remotosNormalizados
        val locales = db.vehiculoDao().getAll()
        if (locales.isEmpty()) return remotosNormalizados
        val localesPorId = locales.associateBy { it.id }
        val localesPorPlaca = locales.associateBy { it.placa }
        return remotosNormalizados.map { remoto ->
            val local = localesPorId[remoto.id] ?: localesPorPlaca[remoto.placa]
            if (local == null) {
                remoto
            } else {
                remoto.copy(
                    kmActual = remoto.kmActual ?: local.kmActual,
                    orimetroActual = remoto.orimetroActual ?: local.orimetroActual,
                    registroFecha = remoto.registroFecha ?: local.registroFecha,
                    registroInicial = remoto.registroInicial ?: local.registroInicial,
                    registroFinal = remoto.registroFinal ?: local.registroFinal,
                    registroCerrado = remoto.registroCerrado || local.registroCerrado,
                    registrosDiariosJson = remoto.registrosDiariosJson ?: local.registrosDiariosJson,
                    mantenimientoUltimo = remoto.mantenimientoUltimo ?: local.mantenimientoUltimo,
                    mantenimientoProximo = remoto.mantenimientoProximo ?: local.mantenimientoProximo
                )
            }
        }
    }

    private fun deduplicarVehiculos(vehiculos: List<VehiculosEntity>): List<VehiculosEntity> {
        if (vehiculos.isEmpty()) return vehiculos
        val deduplicados = linkedMapOf<String, VehiculosEntity>()
        vehiculos.forEach { vehiculo ->
            val key = if (vehiculo.placa != 0L) {
                "placa:${vehiculo.placa}"
            } else {
                "id:${vehiculo.id}"
            }
            val existente = deduplicados[key]
            deduplicados[key] = if (existente == null) {
                vehiculo
            } else {
                fusionarVehiculos(existente, vehiculo)
            }
        }
        return deduplicados.values.toList()
    }

    private fun fusionarVehiculos(base: VehiculosEntity, otro: VehiculosEntity): VehiculosEntity {
        return base.copy(
            id = if (base.id != 0) base.id else otro.id,
            agencia = base.agencia.ifBlank { otro.agencia },
            placa = if (base.placa != 0L) base.placa else otro.placa,
            tipo = base.tipo.ifBlank { otro.tipo },
            subregion = base.subregion ?: otro.subregion,
            kmActual = base.kmActual ?: otro.kmActual,
            orimetroActual = base.orimetroActual ?: otro.orimetroActual,
            registroFecha = base.registroFecha ?: otro.registroFecha,
            registroInicial = base.registroInicial ?: otro.registroInicial,
            registroFinal = base.registroFinal ?: otro.registroFinal,
            registroCerrado = base.registroCerrado || otro.registroCerrado,
            registrosDiariosJson = base.registrosDiariosJson ?: otro.registrosDiariosJson,
            mantenimientoUltimo = base.mantenimientoUltimo ?: otro.mantenimientoUltimo,
            mantenimientoProximo = base.mantenimientoProximo ?: otro.mantenimientoProximo
        )
    }

    suspend fun limpiarBaseLocal() = withContext(Dispatchers.IO) {
        db.clearAllTables()
    }

    fun startRealtimeSyncForScope(scope: UserScope) {
        stopRealtimeSync()

        val vehiculoKey = scope.vehiculoKey?.trim().orEmpty()
        if (vehiculoKey.isNotBlank()) {
            inventarioRealtimeListener = firebase.startInventarioRealtimeForVehiculo(
                vehiculoKey = vehiculoKey,
                scope = realtimeScope,
                onItemUpsert = { item ->
                    inventarioDao.upsert(item)
                },
                onItemRemove = { vehiculoId, codigo ->
                    inventarioDao.eliminarPorVehiculoYCodigo(vehiculoId, codigo)
                },
                onError = { err ->
                    Log.e("RoomRepository", "Inventario realtime cancelado", err.toException())
                }
            )
        }

        val agenciaTag = scope.agenciaTag?.trim().orEmpty()
        if (agenciaTag.isNotBlank()) {
            luminariasRealtimeListener = firebase.startLuminariasRealtimeForAgencia(
                agenciaKey = agenciaTag,
                scope = realtimeScope,
                onUpsert = { rep ->
                    inventarioDao.upsertReparacion(rep)
                },
                onRemove = { id ->
                    inventarioDao.eliminarReparacionPorId(id)
                },
                onError = { err ->
                    Log.e("RoomRepository", "Luminarias realtime cancelado", err.toException())
                }
            )
        }
    }

    fun stopRealtimeSync() {
        inventarioRealtimeListener?.let { firebase.stopInventarioRealtime(it) }
        luminariasRealtimeListener?.let { firebase.stopLuminariasRealtime(it) }

        inventarioRealtimeListener = null
        luminariasRealtimeListener = null

        realtimeScope.coroutineContext.cancelChildren()
    }

}

private fun VehiculoLogEntity.toRegistroDiarioEntity(vehiculoId: Int): RegistroDiarioEntity {
    return RegistroDiarioEntity(
        vehiculoId = vehiculoId,
        fecha = payloadJson.optStringSafe("fecha"),
        valor = km ?: 0.0,
        unidad = payloadJson.optStringSafe("unidad", "km"),
        registradoEn = timestamp,
        registradoPor = payloadJson.optStringSafe("registradoPor").ifBlank { null },
        syncStatus = syncState
    )
}

private fun VehiculoLogEntity.toRegistroMantenimientoEntity(vehiculoId: Int): RegistroMantenimientoEntity {
    return RegistroMantenimientoEntity(
        vehiculoId = vehiculoId,
        tipoMantenimiento = payloadJson.optStringSafe("tipoMantenimiento", "General"),
        valorActual = km ?: 0.0,
        unidad = payloadJson.optStringSafe("unidad", "km"),
        observaciones = payloadJson.optStringSafe("observaciones").ifBlank { null },
        proximoMantenimiento = payloadJson.optDoubleSafe("proximoMantenimiento", km ?: 0.0),
        creadoEn = timestamp,
        syncStatus = syncState
    )
}

private fun String.optStringSafe(key: String, default: String = ""): String =
    runCatching { JSONObject(this).optString(key, default) }.getOrDefault(default)

private fun String.optDoubleSafe(key: String, default: Double = 0.0): Double =
    runCatching { JSONObject(this).optDouble(key, default) }.getOrDefault(default)
