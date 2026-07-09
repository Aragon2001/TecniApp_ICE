package com.Arasoftsolutions.tecniapp_ice.Database.room

import android.content.Context
import com.Arasoftsolutions.tecniapp_ice.Database.entities.*
import com.Arasoftsolutions.tecniapp_ice.Database.sync.FirebaseSyncManager
import com.Arasoftsolutions.tecniapp_ice.Database.sync.SyncLog
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
import java.util.UUID
import com.Arasoftsolutions.tecniapp_ice.ui.vehiculo.FirebaseVehicleDataSource
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
    private val firebaseVehicleDs = FirebaseVehicleDataSource()
    private val vehiculoLogDao = db.vehiculoLogDao()
    private val inventarioDao = db.inventarioDao()
    private val luminariaReparacionDao = db.luminariaReparacionDao()

    private val realtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var inventarioRealtimeListener: ValueEventListener? = null
    private var luminariasRealtimeListener: ValueEventListener? = null

    companion object {
        private const val TAG = "RoomRepositorySync"

        /**
         * Número de pasos con progreso (`done += 100`) dentro de [syncSubregion]:
         * agencias, pueblos, localizaciones, vehículos y medidores = 5.
         * IMPORTANTE (AUDITORIA.md §C5): si se agrega/quita un paso en `syncSubregion`,
         * actualizar esta constante o la barra de progreso quedará descalibrada.
         */
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
        val uniqueId = UUID.randomUUID().toString().replace("-", "").take(12)
        val log = VehiculoLogEntity(
            logId = "diario_${vehiculoKey}_${registro.registradoEn}_$uniqueId",
            vehiculoId = vehiculoKey,
            tipo = "DIARIO",
            timestamp = registro.registradoEn,
            km = registro.valor,
            payloadJson = JSONObject()
                .put("fecha",          registro.fecha)
                .put("unidad",         registro.unidad)
                .put("registradoPor",  registro.registradoPor)
                .put("cerrado",        registro.cerrado)
                .put("kmFinal",        registro.kmFinal)
                .put("actividad",      registro.actividad)
                .put("cuenta",         registro.cuenta)
                .put("numeroCaso",     registro.numeroCaso)
                .put("lugar",          registro.lugar)
                .put("horasLaboradas", registro.horasLaboradas)
                .put("combustible",    registro.combustible)
                .put("observaciones",  registro.observaciones)
                .toString(),
            syncState = "PENDING"
        )
        addLogAndUpdateKm(log)
    }

    suspend fun insertarRegistroMantenimiento(registro: RegistroMantenimientoEntity) = withContext(Dispatchers.IO) {
        val vehiculoKey = resolveVehiculoKey(registro.vehiculoId)
        val uniqueId = UUID.randomUUID().toString().replace("-", "").take(12)
        val log = VehiculoLogEntity(
            logId = "mant_${vehiculoKey}_${registro.creadoEn}_$uniqueId",
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
        mantenimientoUltimo: String?,   // ignorado — no se persiste, solo km
        mantenimientoProximo: String?,  // ignorado — se calcula desde vehiculo_log
        valorActual: Double? = null,
        usaKilometraje: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val vehiculo = vehiculoDao.buscarPorId(vehiculoId) ?: return@withContext

        val nuevoKm = if (usaKilometraje && valorActual != null)
            maxOf(vehiculo.kmActual, valorActual)
        else
            vehiculo.kmActual

        val nuevoOrimetro = if (!usaKilometraje && valorActual != null)
            maxOf(vehiculo.orimetroActual ?: 0.0, valorActual)
        else
            vehiculo.orimetroActual

        val actualizado = vehiculo.copy(
            kmActual       = nuevoKm,
            orimetroActual = nuevoOrimetro,
            updatedAt      = System.currentTimeMillis()
            // mantenimientoUltimo y mantenimientoProximo deliberadamente no se tocan
        )
        vehiculoDao.upsertVehiculo(actualizado)

        // Solo sube km y registroCerrado — sin resumen de mantenimiento
        firebase.actualizarVehiculoCampos(
            actualizado.vehiculoId,
            mapOf(
                "kmActual"        to actualizado.kmActual,
                "registroCerrado" to actualizado.registroCerrado
            )
        )
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
            kmActual = maxOf(vehiculo.kmActual, kilometrajeActual ?: 0.0)
                .takeIf { it > 0.0 } ?: vehiculo.kmActual,
            orimetroActual = maxOf(vehiculo.orimetroActual ?: 0.0, orimetroActual ?: 0.0)
                .takeIf { it > 0.0 } ?: vehiculo.orimetroActual,
            updatedAt = System.currentTimeMillis(),
            registrosDiariosJson = registrosJson
        )
        firebase.actualizarVehiculoCampos(actualizado.vehiculoId, mapOf(
            "kmActual" to actualizado.kmActual,
            "registroCerrado" to actualizado.registroCerrado
        ))
        vehiculoDao.insertAll(listOf(actualizado))
    }

    fun observarReparaciones(): Flow<List<LuminariaReparacionEntity>> =
        luminariaReparacionDao.observarTodas()

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
        SyncLog.i(
            TAG,
            "[INV_DIAG][USER_SCOPE] uid=$uid placaVehiculo=${user?.placaVehiculo ?: "null"} agenciaId=${user?.agenciaId ?: "null"} agencia=${user?.agencia ?: "null"} subregion=${user?.subregion ?: "null"}"
        )
        UserScope(
            regionId = user?.region?.trim()?.takeIf { it.isNotEmpty() },
            subregionId = user?.subregion?.trim()?.takeIf { it.isNotEmpty() },
            agenciaTag = user?.agencia?.trim()?.takeIf { it.isNotEmpty() }
                ?: user?.agenciaId?.trim()?.takeIf { it.isNotEmpty() },
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

    suspend fun obtenerTodosRegistrosDiarios(vehiculoId: String): List<RegistroDiarioEntity> =
        withContext(Dispatchers.IO) {
            val numericId = vehiculoId.toIntOrNull()
            val vehiculoKey = if (numericId != null) resolveVehiculoKey(numericId) else vehiculoId
            vehiculoLogDao.getAll(vehiculoKey, "DIARIO").map { log ->
                log.toRegistroDiarioEntity(vehiculoKey.toIntOrNull() ?: 0)
            }
        }

    suspend fun syncVehiculoDesdeFirebase(vehiculoId: String) = withContext(Dispatchers.IO) {
        val remoto = firebaseVehicleDs.pullVehiculoBase(vehiculoId) ?: return@withContext
        val local  = vehiculoDao.buscarPorVehiculoId(vehiculoId)

        val merged = if (local == null) {
            remoto
        } else {
            local.copy(
                // km siempre el mayor entre local y Firebase
                kmActual       = maxOf(local.kmActual, remoto.kmActual),
                orimetroActual = maxOf(local.orimetroActual ?: 0.0, remoto.orimetroActual ?: 0.0)
                    .takeIf { it > 0.0 } ?: local.orimetroActual,
                registroCerrado = remoto.registroCerrado || local.registroCerrado,
                // registrosDiariosJson: preferir local si ya tiene datos (fue quien los registró)
                // usar remoto solo si local está vacío (dispositivo nuevo)
                registrosDiariosJson = if (local.registrosDiariosJson.isNullOrBlank())
                    remoto.registrosDiariosJson
                else
                    local.registrosDiariosJson,
                // mantenimientoUltimo y mantenimientoProximo no se copian:
                // se reconstruyen automáticamente desde vehiculo_log
                updatedAt = System.currentTimeMillis()
            )
        }
        vehiculoDao.upsertVehiculo(merged)

        // Pull de logs de mantenimiento desde Firebase → pobla vehiculo_log
        // Esencial para dispositivos nuevos que no tienen historial en Room.
        // upsert con OnConflictStrategy.REPLACE: logIds "_pull" no colisionan
        // con los locales "_UUID", así que no hay riesgo de sobreescribir
        // registros propios del dispositivo.
        val mantLogs = firebaseVehicleDs.pullMantenimientosLogs(vehiculoId)
        mantLogs.forEach { log -> vehiculoLogDao.upsert(log) }

        SyncLog.i(
            "RoomRepositorySync",
            "[SYNC_VEHICULO_FIREBASE] vehiculoId=$vehiculoId " +
            "kmMerged=${merged.kmActual} mantLogsPulled=${mantLogs.size}"
        )
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
        ejecutorCedula: String?,
        cliente: String? = null,
        contacto: String? = null,
        observaciones: String? = null
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
            cliente = cliente?.trim()?.ifBlank { null },
            contacto = contacto?.trim()?.ifBlank { null },
            observaciones = observaciones?.trim()?.ifBlank { null },
            materialesJson = com.Arasoftsolutions.tecniapp_ice.ui.luminarias.LuminariaMaterialSerializer
                .toJson(materiales),
            estado = estado.name,
            ejecutorNombre = ejecutorNombre,
            ejecutorCedula = ejecutorCedula,
            fechaRegistro = ahora,
            fechaCarga = ahora,
            fechaReparacion = fechaReparacion
        )
        val reparacionId = luminariaReparacionDao.insertar(reparacion)
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
                val existente = luminariaReparacionDao.obtenerPorLocalizacionYEstado(
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
                        luminariaReparacionDao.actualizar(actualizado)
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
                val reparacionId = luminariaReparacionDao.insertar(reparacion)
                firebase.guardarReparacionLuminaria(reparacion.copy(id = reparacionId), agencia)
            }
    }

    suspend fun eliminarReparacionLuminaria(id: Long) = withContext(Dispatchers.IO) {
        val reparacion = luminariaReparacionDao.obtenerPorId(id) ?: return@withContext
        luminariaReparacionDao.eliminar(id)
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
        val reparacion = luminariaReparacionDao.obtenerPorId(id) ?: return@withContext
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
        luminariaReparacionDao.actualizar(actualizado)
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
        luminariaReparacionDao.obtenerPorId(id)
    }

    suspend fun actualizarVehiculoLuminaria(id: Long, nuevoVehiculoId: Int) = withContext(Dispatchers.IO) {
        val reparacion = luminariaReparacionDao.obtenerPorId(id) ?: return@withContext
        if (reparacion.vehiculoId == nuevoVehiculoId) return@withContext
        val agenciaAnterior = db.vehiculoDao().buscarPorId(reparacion.vehiculoId)?.agencia
        val agenciaNueva = db.vehiculoDao().buscarPorId(nuevoVehiculoId)?.agencia
        val actualizado = reparacion.copy(vehiculoId = nuevoVehiculoId)
        luminariaReparacionDao.actualizar(actualizado)
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
        nuevoEjecutorCedula: String?,
        nuevoCliente: String? = null,
        nuevoContacto: String? = null,
        nuevasObservaciones: String? = null
    ) = withContext(Dispatchers.IO) {
        val reparacion = luminariaReparacionDao.obtenerPorId(id) ?: return@withContext
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
        val entidadActualizada = reparacion.copy(
            localizacion = nuevaLocalizacion,
            cliente = nuevoCliente?.trim()?.ifBlank { null } ?: reparacion.cliente,
            contacto = nuevoContacto?.trim()?.ifBlank { null } ?: reparacion.contacto,
            observaciones = nuevasObservaciones?.trim()?.ifBlank { null } ?: reparacion.observaciones,
            materialesJson = com.Arasoftsolutions.tecniapp_ice.ui.luminarias.LuminariaMaterialSerializer
                .toJson(nuevosMateriales),
            estado = nuevoEstado.name,
            ejecutorNombre = nuevoEjecutorNombre,
            ejecutorCedula = nuevoEjecutorCedula,
            fechaReparacion = fechaReparacion
        )
        luminariaReparacionDao.actualizar(entidadActualizada)
        firebase.guardarReparacionLuminaria(entidadActualizada, agencia)
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
        if (tecnicos.isEmpty()) return@withContext bytes

        val locales = db.tecnicoDao().observarTecnicosSnapshot().associateBy { it.cedula }
        val cambios = tecnicos.filter { remoto ->
            val local = locales[remoto.cedula]
            local == null || local != remoto
        }
        if (cambios.isNotEmpty()) {
            db.tecnicoDao().insertAll(cambios)
        }
        bytes
    }


    suspend fun syncMateriales(): Long = withContext(Dispatchers.IO) {
        val materiales = firebase.obtenerMaterialesCatalogo()
        val bytes = estimateBytes(materiales)
        if (materiales.isEmpty()) return@withContext bytes

        val locales = db.materialDao().observarMaterialesSnapshot().associateBy { it.codigo }
        val cambios = materiales.filter { remoto ->
            val local = locales[remoto.codigo]
            local == null || local != remoto
        }
        if (cambios.isNotEmpty()) {
            db.materialDao().insertAll(cambios)
        }
        bytes
    }


    suspend fun syncInventario(): Long = withContext(Dispatchers.IO) {
        val inventario = firebase.obtenerInventario()
        val bytes = estimateBytes(inventario)
        if (inventario.isEmpty()) return@withContext bytes

        val locales = inventario.groupBy { it.vehiculoId }
            .flatMap { (vehiculoId, _) -> inventarioDao.obtenerPorVehiculo(vehiculoId) }
            .associateBy { "${it.vehiculoId}:${it.codigoMaterial}" }
        val cambios = inventario.filter { remoto ->
            val key = "${remoto.vehiculoId}:${remoto.codigoMaterial}"
            val local = locales[key]
            local == null ||
                    local.descripcionMaterial != remoto.descripcionMaterial ||
                    local.cantidadDisponible != remoto.cantidadDisponible
        }
        if (cambios.isNotEmpty()) {
            inventarioDao.insertAll(cambios)
        }
        bytes
    }


    suspend fun syncLuminarias(agencia: String? = null): Long = withContext(Dispatchers.IO) {
        val reparaciones = firebase.obtenerLuminarias(agencia)
        val bytes = estimateBytes(reparaciones)
        if (reparaciones.isNotEmpty()) {
            luminariaReparacionDao.insertarTodas(reparaciones)
        }
        bytes
    }


    suspend fun syncInventarioVehiculo(vehiculoId: Int, vehiculoKey: String): Long = withContext(Dispatchers.IO) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val scope = uid?.let { buildUserScope(it) } ?: UserScope(
            regionId = null,
            subregionId = null,
            agenciaTag = null,
            vehiculoKey = vehiculoKey.trim().takeIf { it.isNotEmpty() }
        )
        SyncLog.i(TAG, "[INV_DIAG][SYNC_INVENTARIO] start uid=${uid ?: "null"} vehiculoIdInput=$vehiculoId")

        val vehiculoLocal = resolveVehiculoLocal(scope)
        if (vehiculoLocal == null) {
            SyncLog.w(TAG, "[INV_DIAG][SYNC_INVENTARIO_SKIP] reason=vehiculo_local_not_resolved uid=${uid ?: "null"}")
            return@withContext 0L
        }
        val vehiculoIdLocal = vehiculoLocal.id
        SyncLog.i(
            TAG,
            "[INV_DIAG][SYNC_INVENTARIO] vehiculo_local_resolved vehiculoIdLocal=$vehiculoIdLocal vehiculoKeyLength=${vehiculoLocal.vehiculoId.length}"
        )

        val candidates = buildInventarioKeyCandidates(scope, vehiculoLocal)
        val (selectedKey, inventario) = fetchInventarioForCandidates(vehiculoIdLocal, candidates)
        if (inventario.isEmpty()) {
            SyncLog.i(
                TAG,
                "[INV_DIAG][SYNC_INVENTARIO_SKIP] reason=no_remote_items vehiculoIdLocal=$vehiculoIdLocal selectedKey=${selectedKey ?: "none"} items=0"
            )
            SyncLog.i(
                TAG,
                "[SYNC_INVENTARIO] Sin datos remotos para vehiculoIdLocal=$vehiculoIdLocal; se omite fallback global para evitar descargas masivas"
            )
            return@withContext 0L
        }
        SyncLog.i(TAG, "[INV_DIAG][SYNC_INVENTARIO] fetch_success vehiculoIdLocal=$vehiculoIdLocal selectedKey=${selectedKey ?: "none"} remoteItems=${inventario.size}")
        val bytes = estimateBytes(inventario)

        val inventarioMapped = inventario.map { it.copy(vehiculoId = vehiculoIdLocal) }
        val locales = inventarioDao.obtenerPorVehiculo(vehiculoIdLocal).associateBy { it.codigoMaterial }
        val cambios = inventarioMapped.filter { remoto ->
            val local = locales[remoto.codigoMaterial]
            local == null ||
                    local.descripcionMaterial != remoto.descripcionMaterial ||
                    local.cantidadDisponible != remoto.cantidadDisponible
        }
        if (cambios.isNotEmpty()) {
            inventarioDao.insertAll(cambios)
        }
        bytes
    }

    private suspend fun fetchInventarioForCandidates(
        vehiculoIdLocal: Int,
        candidates: List<String>
    ): Pair<String?, List<InventarioItemEntity>> {
        if (candidates.isEmpty()) {
            SyncLog.w(TAG, "[INV_DIAG][FETCH_CANDIDATES] vehiculoIdLocal=$vehiculoIdLocal reason=no_candidates")
            return null to emptyList()
        }
        candidates.forEachIndexed { index, candidate ->
            SyncLog.i(
                TAG,
                "[INV_DIAG][FETCH_CANDIDATES] vehiculoIdLocal=$vehiculoIdLocal attempt=${index + 1}/${candidates.size} keyLength=${candidate.length}"
            )
            val items = firebase.obtenerInventarioDeVehiculo(candidate)
            if (items.isNotEmpty()) {
                SyncLog.i(
                    TAG,
                    "[INV_DIAG][FETCH_CANDIDATES] vehiculoIdLocal=$vehiculoIdLocal selectedAttempt=${index + 1} keyLength=${candidate.length} items=${items.size}"
                )
                return candidate to items
            }
            SyncLog.i(
                TAG,
                "[INV_DIAG][FETCH_CANDIDATES] vehiculoIdLocal=$vehiculoIdLocal emptyAttempt=${index + 1} keyLength=${candidate.length}"
            )
        }
        SyncLog.w(TAG, "[INV_DIAG][FETCH_CANDIDATES] vehiculoIdLocal=$vehiculoIdLocal result=no_items")
        return null to emptyList()
    }


    suspend fun syncLuminariasAgencia(agencia: String): Long = withContext(Dispatchers.IO) {
        val agenciaValue = agencia.trim()
        if (agenciaValue.isEmpty()) {
            SyncLog.w(TAG, "[LUM_ROOM][SKIP_REASON] reason=agencia_blank")
            return@withContext 0L
        }
        SyncLog.i(TAG, "[LUM_ROOM][FETCH_START] agencia=$agenciaValue")
        val reparaciones = firebase.obtenerLuminariasPorAgencia(agenciaValue)
        SyncLog.i(TAG, "[LUM_ROOM][FETCH_RESULT] agencia=$agenciaValue reparaciones=${reparaciones.size}")
        val duplicateCount = reparaciones
            .groupBy { it.id }
            .values
            .sumOf { bucket -> (bucket.size - 1).coerceAtLeast(0) }
        SyncLog.i(TAG, "[LUM_ROOM][DUPLICATE_COUNT] agencia=$agenciaValue duplicates=$duplicateCount")
        if (reparaciones.isEmpty()) {
            SyncLog.i(TAG, "[LUM_ROOM][SKIP_REASON] reason=no_remote_data agencia=$agenciaValue")
            return@withContext 0L
        }
        val bytes = estimateBytes(reparaciones)
        // Swap atómico: eliminar registros locales de la agencia e insertar los remotos en una sola transacción.
        db.withTransaction {
            luminariaReparacionDao.eliminarPorAgencia(agenciaValue)
            luminariaReparacionDao.insertarTodas(reparaciones)
        }
        SyncLog.i(TAG, "[LUM_ROOM][SYNC_ATOMIC] agencia=$agenciaValue synced=${reparaciones.size}")
        val totalLocal = luminariaReparacionDao.contar()
        SyncLog.i(TAG, "[LUM_ROOM][POST_SYNC_TOTAL] agencia=$agenciaValue totalLocal=$totalLocal")
        bytes
    }

    private suspend fun resolveVehiculoLocal(scope: UserScope): VehiculoEntity? {
        val rawKey = scope.vehiculoKey?.trim().orEmpty()
        if (rawKey.isBlank()) {
            SyncLog.w(TAG, "[INV_DIAG][RESOLVE_VEHICULO_LOCAL] fail method=none reason=vehiculoKey_blank")
            return null
        }

        rawKey.toIntOrNull()?.let { numericId ->
            db.vehiculoDao().buscarPorId(numericId)?.let { vehiculo ->
                SyncLog.i(TAG, "[INV_DIAG][RESOLVE_VEHICULO_LOCAL] success method=vehiculoId_int vehiculoId=${vehiculo.id}")
                return vehiculo
            }
            SyncLog.i(TAG, "[INV_DIAG][RESOLVE_VEHICULO_LOCAL] miss method=vehiculoId_int value=$numericId")
        }

        db.vehiculoDao().buscarPorVehiculoId(rawKey)?.let { vehiculo ->
            SyncLog.i(TAG, "[INV_DIAG][RESOLVE_VEHICULO_LOCAL] success method=placaRaw vehiculoId=${vehiculo.id}")
            return vehiculo
        }
        SyncLog.i(TAG, "[INV_DIAG][RESOLVE_VEHICULO_LOCAL] miss method=placaRaw")

        rawKey.toLongOrNull()?.let { placaLong ->
            db.vehiculoDao().buscarPorPlaca(placaLong)?.let { vehiculo ->
                SyncLog.i(TAG, "[INV_DIAG][RESOLVE_VEHICULO_LOCAL] success method=placa_long vehiculoId=${vehiculo.id}")
                return vehiculo
            }
            SyncLog.i(TAG, "[INV_DIAG][RESOLVE_VEHICULO_LOCAL] miss method=placa_long value=$placaLong")
        }

        db.vehiculoDao().buscarPorVehiculoId(rawKey)?.let { vehiculo ->
            SyncLog.i(TAG, "[INV_DIAG][RESOLVE_VEHICULO_LOCAL] success method=placaVehiculo_usuario vehiculoId=${vehiculo.id}")
            return vehiculo
        }

        SyncLog.w(TAG, "[INV_DIAG][RESOLVE_VEHICULO_LOCAL] fail method=all keyLength=${rawKey.length}")
        return null
    }

    private fun buildInventarioKeyCandidates(
        scope: UserScope,
        vehiculo: VehiculoEntity?
    ): List<String> {
        val ordered = listOf(
            vehiculo?.vehiculoId,
            vehiculo?.placaRaw,
            vehiculo?.placa?.takeIf { it > 0L }?.toString(),
            scope.vehiculoKey
        )
        val deduped = linkedSetOf<String>()
        ordered.forEach { raw ->
            val candidate = raw?.trim().orEmpty()
            if (candidate.isNotEmpty()) {
                deduped.add(candidate)
            }
        }
        val candidates = deduped.toList()
        SyncLog.i(
            TAG,
            "[INV_DIAG][KEY_CANDIDATES] count=${candidates.size} order=vehiculoId>placaRaw>placaLong>scopeKey lengths=${candidates.map { it.length }}"
        )
        return candidates
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
        SyncLog.i(TAG, "[SYNC_SUBREGION] start subregion=$subregionId totalSteps=$total")

        // Si quieres transacción atómica, descomenta y usa withTransaction:
        // db.withTransaction {
        val canonicalSubregion = SubregionNormalizer.canonicalIdOrSelf(subregionId)
            ?: throw IllegalArgumentException("Subregión inválida: $subregionId")
        SyncLog.i(TAG, "[SYNC_SUBREGION] canonicalSubregion=$canonicalSubregion")

        val agencias = firebase.obtenerAgenciasPorSubregion(canonicalSubregion)
        downloadedBytes += estimateBytes(agencias)
        if (agencias.isNotEmpty()) {
            val localesAgencias = db.agenciaDao().getAll()
                .filter { it.subregion.equals(canonicalSubregion, ignoreCase = true) }
                .associateBy { it.id }
            val agenciasNuevasOCambiadas = agencias.filter { remoto ->
                val local = localesAgencias[remoto.id]
                local == null || local != remoto
            }
            if (agenciasNuevasOCambiadas.isNotEmpty()) {
                db.agenciaDao().insertAll(agenciasNuevasOCambiadas)
            }
        }
        done += 100
        progress(done, total, "Descargando agencias…", downloadedBytes)
        SyncLog.i(TAG, "[SYNC_SUBREGION] step=$done/$total source=datos_generales/agencias count=${agencias.size} bytes=$downloadedBytes")

        val pueblosRemotos = firebase.obtenerPueblosPorSubregion(canonicalSubregion)
        downloadedBytes += estimateBytes(pueblosRemotos)
        val pueblosASincronizar = if (pueblosRemotos.isNotEmpty()) {
            pueblosRemotos
        } else {
            SyncLog.w(
                "RoomRepository",
                "No se encontraron pueblos para subregión=$canonicalSubregion; se omite el sincronizado de pueblos/localizaciones."
            )
            emptyList()
        }
        if (pueblosASincronizar.isNotEmpty()) {
            val pueblosLocales = db.puebloDao().getAll()
                .filter { it.subregion_id_normalizado == canonicalSubregion }
                .associateBy { it.id }
            val pueblosNuevosOCambiados = pueblosASincronizar.filter { remoto ->
                val local = pueblosLocales[remoto.id]
                local == null || local != remoto
            }
            if (pueblosNuevosOCambiados.isNotEmpty()) {
                db.puebloDao().insertAll(pueblosNuevosOCambiados)
            }
        }
        done += 100
        progress(done, total, "Descargando pueblos…", downloadedBytes)
        SyncLog.i(TAG, "[SYNC_SUBREGION] step=$done/$total source=local/pueblos count=${pueblosASincronizar.size} bytes=$downloadedBytes")

        // Combina IDs descargados ahora + IDs ya guardados en Room para esta subregión.
        // Esto evita que un fallo puntual en la descarga de pueblos deje sin localizaciones.
        val idsPueblosDescargados = pueblosASincronizar.map { it.id }
        val idsPueblosLocales = db.puebloDao().obtenerIdsPorSubregion(canonicalSubregion)
        val idsPueblos = (idsPueblosDescargados + idsPueblosLocales).distinct()
        SyncLog.i(TAG, "[SYNC_SUBREGION] idsPueblos total=${idsPueblos.size} (descargados=${idsPueblosDescargados.size} locales=${idsPueblosLocales.size})")

        val localizacionesFiltradas = if (idsPueblos.isNotEmpty()) {
            firebase.obtenerLocalizacionesPorPueblos(idsPueblos, canonicalSubregion)
        } else {
            SyncLog.w(TAG, "[SYNC_SUBREGION] sin idsPueblos — se omite descarga de localizaciones")
            emptyList()
        }
        downloadedBytes += estimateBytes(localizacionesFiltradas)
        if (localizacionesFiltradas.isNotEmpty()) {
            val locales = idsPueblos.flatMap { db.localizacionDao().obtenerPorPueblo(it) }.associateBy { it.id }
            val nuevasOCambiadas = localizacionesFiltradas.filter { remoto ->
                val local = locales[remoto.id]
                local == null || local != remoto
            }
            if (nuevasOCambiadas.isNotEmpty()) {
                db.localizacionDao().insertAll(nuevasOCambiadas)
            }
        }
        done += 100
        progress(done, total, "Descargando localizaciones…", downloadedBytes)
        SyncLog.i(TAG, "[SYNC_SUBREGION] step=$done/$total source=local/localizaciones puebloCount=${idsPueblos.size} localizaciones=${localizacionesFiltradas.size} bytes=$downloadedBytes")

        val vehiculosRemotos = firebase.obtenerVehiculosPorSubregion(canonicalSubregion)
        val vehiculos = deduplicarVehiculos(vehiculosRemotos)
        downloadedBytes += estimateBytes(vehiculos)
        val vehiculosCombinados = combinarVehiculosConLocales(vehiculos)
        db.vehiculoDao().insertAll(vehiculosCombinados)
        done += 100
        progress(done, total, "Descargando vehículos…", downloadedBytes)
        SyncLog.i(TAG, "[SYNC_SUBREGION] step=$done/$total source=datos_generales/vehiculos count=${vehiculosCombinados.size} bytes=$downloadedBytes")

        var medidoresTotal = 0
        var medidoresLotes = 0
        var totalMedidoresObjetivo = firebase.contarMedidoresSubregion(canonicalSubregion)
        val medidoresSyncStartedAt = System.currentTimeMillis()
        if (totalMedidoresObjetivo <= 0) {
            totalMedidoresObjetivo = db.medidorDao().contarPorSubregion(canonicalSubregion)
        }
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
            val elapsed = formatElapsed(medidoresSyncStartedAt)
            val medidoresProgress = progressUnitsForMedidores(medidoresTotal, totalMedidoresObjetivo)
            SyncLog.i(
                TAG,
                "[SYNC_SUBREGION] medidores_lote=$medidoresLotes loteSize=${medidoresBatch.size} total=$medidoresTotal objetivo=$totalMedidoresObjetivo medidoresProgress=$medidoresProgress elapsed=$elapsed bytes=$downloadedBytes"
            )
            progress(
                done + medidoresProgress,
                total,
                "Descargando medidores… ($medidoresTotal/$totalMedidoresObjetivo) · $elapsed",
                downloadedBytes
            )
        }
        done += 100
        progress(done, total, "Descargando medidores…", downloadedBytes)
        SyncLog.i(TAG, "[SYNC_SUBREGION] step=$done/$total source=medidores/medidores count=$medidoresTotal bytes=$downloadedBytes")
        SyncLog.i(TAG, "[SYNC_SUBREGION] completed subregion=$canonicalSubregion totalBytes=$downloadedBytes tookMs=${System.currentTimeMillis()-syncStartedAt}")
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
            val locales = db.regionDao().getAll().associateBy { it.id }
            val nuevasOCambiadas = regiones.filter { remoto ->
                val local = locales[remoto.id]
                local == null || local != remoto
            }
            if (nuevasOCambiadas.isNotEmpty()) db.regionDao().insertAll(nuevasOCambiadas)
        }

        val subregiones = firebase.obtenerSubregiones()
        downloadedBytes += estimateBytes(subregiones)
        if (subregiones.isNotEmpty()) {
            val locales = db.subregionDao().getAll().associateBy { it.id }
            val nuevasOCambiadas = subregiones.filter { remoto ->
                val local = locales[remoto.id]
                local == null || local != remoto
            }
            if (nuevasOCambiadas.isNotEmpty()) db.subregionDao().insertAll(nuevasOCambiadas)
        }

        val agencias = firebase.obtenerAgencias()
        downloadedBytes += estimateBytes(agencias)
        if (agencias.isNotEmpty()) {
            val locales = db.agenciaDao().getAll().associateBy { it.id }
            val nuevasOCambiadas = agencias.filter { remoto ->
                val local = locales[remoto.id]
                local == null || local != remoto
            }
            if (nuevasOCambiadas.isNotEmpty()) db.agenciaDao().insertAll(nuevasOCambiadas)
        }

        val vehiculosRemotos = firebase.obtenerVehiculos()
        val vehiculos = deduplicarVehiculos(vehiculosRemotos)
        downloadedBytes += estimateBytes(vehiculos)
        if (vehiculos.isNotEmpty()) {
            val vehiculosCombinados = combinarVehiculosConLocales(vehiculos)
            val locales = db.vehiculoDao().getAll().associateBy { it.vehiculoId }
            val nuevosOCambiados = vehiculosCombinados.filter { remoto ->
                val local = locales[remoto.vehiculoId]
                local == null || local != remoto
            }
            if (nuevosOCambiados.isNotEmpty()) db.vehiculoDao().insertAll(nuevosOCambiados)
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

    private fun formatElapsed(startedAtMs: Long): String {
        val elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L)
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
                    kmActual = maxOf(remoto.kmActual, local.kmActual),
                    orimetroActual = maxOf(remoto.orimetroActual ?: 0.0, local.orimetroActual ?: 0.0)
                        .takeIf { it > 0.0 } ?: local.orimetroActual,
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
            kmActual = maxOf(base.kmActual, otro.kmActual),
            orimetroActual = maxOf(base.orimetroActual ?: 0.0, otro.orimetroActual ?: 0.0)
                .takeIf { it > 0.0 } ?: base.orimetroActual,
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

    suspend fun startRealtimeSyncForScope(scope: UserScope) {
        stopRealtimeSync()

        val vehiculoLocal = resolveVehiculoLocal(scope)
        val vehiculoKey = scope.vehiculoKey?.trim().orEmpty()
        if (vehiculoKey.isNotBlank() && vehiculoLocal != null) {
            SyncLog.i(TAG, "[INV_DIAG][REALTIME_SETUP] vehiculoKey=$vehiculoKey vehiculoIdLocal=${vehiculoLocal.id}")
            inventarioRealtimeListener = firebase.startInventarioRealtimeForVehiculo(
                vehiculoKey = vehiculoKey,
                vehiculoIdLocal = vehiculoLocal.id,
                scope = realtimeScope,
                onItemUpsert = { item ->
                    inventarioDao.upsert(item)
                },
                onItemRemove = { vehiculoId, codigo ->
                    inventarioDao.eliminarPorVehiculoYCodigo(vehiculoId, codigo)
                },
                onError = { err ->
                    SyncLog.e("RoomRepository", "Inventario realtime cancelado", err.toException())
                }
            )
        } else {
            val reason = when {
                vehiculoKey.isBlank() -> "vehiculoKey_blank"
                else -> "vehiculo_local_not_resolved"
            }
            SyncLog.w(TAG, "[INV_DIAG][REALTIME_SKIP] reason=$reason")
        }

        val agenciaTag = scope.agenciaTag?.trim().orEmpty()
        if (agenciaTag.isNotBlank()) {
            luminariasRealtimeListener = firebase.startLuminariasRealtimeForAgencia(
                agenciaKey = agenciaTag,
                scope = realtimeScope,
                onUpsert = { rep ->
                    luminariaReparacionDao.upsert(rep)
                },
                onRemove = { id ->
                    luminariaReparacionDao.eliminar(id)
                },
                onError = { err ->
                    SyncLog.e("RoomRepository", "Luminarias realtime cancelado", err.toException())
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
        vehiculoId    = vehiculoId,
        fecha         = payloadJson.optStringSafe("fecha"),
        valor         = km ?: 0.0,
        unidad        = payloadJson.optStringSafe("unidad", "km"),
        registradoEn  = timestamp,
        registradoPor = payloadJson.optStringSafe("registradoPor").ifBlank { null },
        syncStatus    = syncState,
        cerrado       = payloadJson.optBoolSafe("cerrado"),
        kmFinal       = payloadJson.optDoubleSafe("kmFinal").takeIf { it > 0.0 },
        actividad     = payloadJson.optStringSafe("actividad").ifBlank { null },
        cuenta        = payloadJson.optStringSafe("cuenta").ifBlank { null },
        numeroCaso    = payloadJson.optStringSafe("numeroCaso").ifBlank { null },
        lugar         = payloadJson.optStringSafe("lugar").ifBlank { null },
        horasLaboradas = payloadJson.optIntSafe("horasLaboradas").takeIf { it > 0 },
        combustible   = payloadJson.optStringSafe("combustible").ifBlank { null },
        observaciones = payloadJson.optStringSafe("observaciones").ifBlank { null }
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

private fun String.optBoolSafe(key: String, default: Boolean = false): Boolean =
    runCatching { JSONObject(this).optBoolean(key, default) }.getOrDefault(default)

private fun String.optIntSafe(key: String, default: Int = 0): Int =
    runCatching { JSONObject(this).optInt(key, default) }.getOrDefault(default)