package com.Arasoftsolutions.tecniapp_ice.Database.room

import android.content.Context
import com.Arasoftsolutions.tecniapp_ice.Database.entities.*
import com.Arasoftsolutions.tecniapp_ice.Database.sync.FirebaseSyncManager
import com.Arasoftsolutions.tecniapp_ice.Database.sync.SubregionNormalizer
import com.google.firebase.auth.FirebaseAuth
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
    private val etmRegistroDao = db.etmRegistroDao()

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

    fun observarAgencias(regionId: String): Flow<List<AgenciaEntity>> =
        db.agenciaDao().observarPorRegion(regionId)

    fun observarAgenciasCatalogo(): Flow<List<AgenciaEntity>> =
        db.agenciaDao().observarTodas()

    fun observarVehiculos(agencia: String): Flow<List<VehiculosEntity>> =
        db.vehiculoDao().observarPorAgencia(agencia)

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

    fun observarRegistrosEtm(placa: String, limite: Int = 30): Flow<List<EtmRegistroEntity>> =
        etmRegistroDao.observarUltimos(placa, limite)

    suspend fun obtenerRegistroEtmHoy(placa: String, fecha: String): EtmRegistroEntity? =
        etmRegistroDao.obtenerPorPlacaYFecha(placa, fecha)

    suspend fun guardarRegistroEtm(registro: EtmRegistroEntity) =
        etmRegistroDao.insertar(registro)

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

    private suspend fun resolveVehiculoKey(vehiculoId: Int): String {
        val vehiculo = db.vehiculoDao().buscarPorId(vehiculoId)
        return vehiculo?.placa?.toString() ?: vehiculoId.toString()
    }

    suspend fun syncSubregion(
        subregionId: String,
        progress: (done: Int, total: Int, msg: String?, downloadedBytes: Long) -> Unit = { _, _, _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val total = SUBREGION_SYNC_STEPS
        var done = 0
        var downloadedBytes = 0L

        // Si quieres transacción atómica, descomenta y usa withTransaction:
        // db.withTransaction {
        val canonicalSubregion = SubregionNormalizer.canonicalIdOrSelf(subregionId)
            ?: throw IllegalArgumentException("Subregión inválida: $subregionId")

        val agenciasPorSubregion = firebase.obtenerAgencias(canonicalSubregion)
        val agencias = agenciasPorSubregion.ifEmpty { firebase.obtenerAgencias() }
        downloadedBytes += estimateBytes(agencias)
        if (agenciasPorSubregion.isNotEmpty()) {
            db.agenciaDao().eliminarPorSubregion(canonicalSubregion)
        } else if (agencias.isNotEmpty()) {
            db.agenciaDao().eliminarFueraDeIds(agencias.map { it.id })
        } else {
            db.agenciaDao().limpiarTodo()
        }
        db.agenciaDao().insertAll(agencias)
        progress(++done, total, "Descargando agencias…", downloadedBytes)

        val pueblosRemotos = firebase.obtenerPueblos()
        downloadedBytes += estimateBytes(pueblosRemotos)
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
        progress(++done, total, "Descargando pueblos…", downloadedBytes)

        val idsPueblos = if (pueblosFiltrados.isNotEmpty()) {
            pueblosFiltrados.map { it.id }
        } else {
            db.puebloDao().obtenerIdsPorSubregion(canonicalSubregion)
        }
        val idsSet = idsPueblos.toSet()
        val localizacionesRemotas = firebase.obtenerLocalizaciones()
        downloadedBytes += estimateBytes(localizacionesRemotas)
        val localizacionesFiltradas = localizacionesRemotas.filter { it.pueblo in idsSet }
        db.localizacionDao().limpiarTodo()
        if (localizacionesFiltradas.isNotEmpty()) {
            db.localizacionDao().insertAll(localizacionesFiltradas)
        }
        progress(++done, total, "Descargando localizaciones…", downloadedBytes)

        val vehiculosPorSubregion = firebase.obtenerVehiculos(canonicalSubregion)
        val vehiculos = vehiculosPorSubregion.ifEmpty { firebase.obtenerVehiculos() }
        downloadedBytes += estimateBytes(vehiculos)
        if (vehiculosPorSubregion.isNotEmpty()) {
            db.vehiculoDao().eliminarPorSubregion(canonicalSubregion)
        } else if (vehiculos.isNotEmpty()) {
            db.vehiculoDao().eliminarFueraDeIds(vehiculos.map { it.id })
        } else {
            db.vehiculoDao().limpiarTodo()
        }
        db.vehiculoDao().insertAll(vehiculos)
        progress(++done, total, "Descargando vehículos…", downloadedBytes)

        val medidores = firebase.obtenerMedidores(canonicalSubregion)
        downloadedBytes += estimateBytes(medidores)
        db.medidorDao().eliminarPorSubregion(canonicalSubregion)
        db.medidorDao().insertAll(medidores)
        progress(++done, total, "Descargando medidores…", downloadedBytes)
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

        val vehiculos = firebase.obtenerVehiculos()
        downloadedBytes += estimateBytes(vehiculos)
        if (vehiculos.isNotEmpty()) {
            db.vehiculoDao().eliminarFueraDeIds(vehiculos.map { it.id })
            db.vehiculoDao().insertAll(vehiculos)
        } else {
            db.vehiculoDao().limpiarTodo()
        }

        runCatching { syncTecnicos() }
        runCatching { syncMateriales() }
        downloadedBytes
    }

    private fun estimateBytes(value: Any?): Long {
        return value?.toString()?.toByteArray(Charsets.UTF_8)?.size?.toLong() ?: 0L
    }

    suspend fun limpiarBaseLocal() = withContext(Dispatchers.IO) {
        db.clearAllTables()
    }

}
