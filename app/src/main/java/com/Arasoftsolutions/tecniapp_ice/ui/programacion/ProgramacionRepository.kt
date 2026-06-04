package com.Arasoftsolutions.tecniapp_ice.ui.programacion

import com.Arasoftsolutions.tecniapp_ice.Database.entities.ProgramacionEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.ProgramacionFotoEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ProgramacionRepository(private val db: AppDatabase) {

    private val dao get() = db.programacionDao()
    private val firebaseRef = FirebaseDatabase
        .getInstance("https://tecniapp-ice-programacion.firebaseio.com/")
        .reference
        .child("programaciones")

    // ─── Consultas ────────────────────────────────────────────────────────────

    fun observeProgramaciones(subregion: String, vehiculoId: String?): Flow<List<ProgramacionEntity>> =
        if (vehiculoId.isNullOrBlank()) dao.observeBySubregion(subregion)
        else dao.observeBySubregionVehiculo(subregion, vehiculoId)

    fun observeOrdenesSupervisor(agenciaTag: String): Flow<List<ProgramacionEntity>> =
        dao.observeOrdenesSupervisor(agenciaTag)

    fun observeOrdenesPendientesTecnico(agenciaTag: String, vehiculo: String): Flow<List<ProgramacionEntity>> =
        dao.observeOrdenesPendientesTecnico(agenciaTag, vehiculo)

    fun observeOrdenesAtendidasPorTecnico(agenciaTag: String, uid: String): Flow<List<ProgramacionEntity>> =
        dao.observeOrdenesAtendidasPorTecnico(agenciaTag, uid)

    fun observeTodasOrdenesTecnico(agenciaTag: String, vehiculo: String, uid: String): Flow<List<ProgramacionEntity>> =
        dao.observeTodasOrdenesTecnico(agenciaTag, vehiculo, uid)

    suspend fun observeTieneFotos(programacionId: String): Boolean =
        dao.observeTieneFotos(programacionId).firstOrNull() ?: false

    suspend fun getById(id: String): ProgramacionEntity? = dao.getById(id)

    // ─── Crear orden (supervisor) ─────────────────────────────────────────────

    suspend fun crearOrden(input: ProgramacionEntity, fotosAsignacion: List<String>): Result<Unit> =
        runCatching {
            dao.upsert(input)
            if (fotosAsignacion.isNotEmpty()) {
                dao.upsertFotos(fotosAsignacion.map {
                    ProgramacionFotoEntity(UUID.randomUUID().toString(), input.programacionId, it, FOTO_TIPO_ASIGNACION)
                })
            }
            val payload = input.toFirebaseMap() + mapOf(
                "fotosAsignacion" to fotosAsignacion,
                "fotosCierre" to emptyList<String>()
            )
            firebaseRef.child(input.subregion).child(input.vehiculoId).child(input.programacionId)
                .setValue(payload).await()
        }

    // Método heredado — compatibilidad
    suspend fun crearProgramacion(input: ProgramacionEntity, fotosAsignacion: List<String>) {
        crearOrden(input, fotosAsignacion).getOrThrow()
    }

    // ─── Acciones técnico ─────────────────────────────────────────────────────

    suspend fun iniciarAtencion(id: String, uid: String, nombre: String): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        dao.iniciarAtencion(id, uid, nombre, now)
        firebaseRef.child(id).updateChildren(
            mapOf("estado" to ESTADO_EN_ATENCION, "tecnicoPrincipalUid" to uid,
                "tecnicoPrincipalNombre" to nombre, "fechaInicioAtencion" to now, "updatedAt" to now)
        ).await()
    }

    suspend fun finalizarAtencion(
        id: String,
        uid: String,
        descripcion: String,
        fotosAtencion: List<String>,
        tecnicosParticipantes: List<TecnicoParticipante>,
        gastoMateriales: Boolean,
        materiales: List<MaterialGastado>
    ): Result<Unit> = runCatching {
        if (descripcion.isBlank()) error("La descripción de atención es obligatoria.")
        if (gastoMateriales && materiales.isEmpty()) error("Debe agregar al menos un material.")

        val now = System.currentTimeMillis()
        val fotosJson = if (fotosAtencion.isNotEmpty()) fotosAtencion.joinToString(",", "[", "]") { "\"$it\"" } else null
        val tecnicosJson = if (tecnicosParticipantes.isNotEmpty())
            tecnicosParticipantes.joinToString(",", "[", "]") { "{\"uid\":\"${it.uid}\",\"nombre\":\"${it.nombre}\"}" }
        else null
        val materialesJson = if (gastoMateriales && materiales.isNotEmpty())
            materiales.joinToString(",", "[", "]") {
                "{\"codigo\":\"${it.codigo}\",\"nombre\":\"${it.nombre}\",\"cantidad\":${it.cantidad},\"unidad\":\"${it.unidad}\"}"
            }
        else null

        dao.finalizarAtencion(id, uid, descripcion, fotosJson, tecnicosJson, gastoMateriales, materialesJson, now)

        val remoteUpdates = mutableMapOf<String, Any>(
            "estado" to ESTADO_ATENDIDA,
            "descripcionAtencion" to descripcion,
            "gastoMateriales" to gastoMateriales,
            "fechaFinalizacion" to now,
            "updatedAt" to now
        )
        fotosJson?.let { remoteUpdates["fotosAtencionJson"] = it }
        tecnicosJson?.let { remoteUpdates["tecnicosAtendieronJson"] = it }
        materialesJson?.let { remoteUpdates["materialesJson"] = it }
        firebaseRef.child(id).updateChildren(remoteUpdates).await()
    }

    suspend fun reabrirOrden(id: String, uid: String, motivo: String?): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        dao.reabrirOrden(id, uid, motivo, now)
        firebaseRef.child(id).updateChildren(
            mapOf("estado" to ESTADO_REABIERTA, "reabierta" to true, "reabiertaPorUid" to uid,
                "motivoReapertura" to (motivo ?: ""), "fechaReapertura" to now, "updatedAt" to now)
        ).await()
    }

    // ─── Acciones supervisor ──────────────────────────────────────────────────

    suspend fun cancelarOrden(id: String, uid: String, motivo: String?): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        dao.cancelarOrden(id, uid, motivo, now)
        firebaseRef.child(id).updateChildren(
            mapOf("estado" to ESTADO_CANCELADA, "canceladaPorUid" to uid,
                "motivoCancelacion" to (motivo ?: ""), "fechaCancelacion" to now, "updatedAt" to now)
        ).await()
    }

    suspend fun eliminarOrden(id: String, uid: String, motivo: String?): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        dao.eliminarLogico(id, uid, motivo, now)
        firebaseRef.child(id).updateChildren(
            mapOf("estado" to ESTADO_ELIMINADA, "eliminada" to true, "eliminadaPorUid" to uid,
                "motivoEliminacion" to (motivo ?: ""), "fechaEliminacion" to now, "updatedAt" to now)
        ).await()
    }

    suspend fun restaurarOrden(id: String): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        dao.restaurarOrden(id, now)
        firebaseRef.child(id).updateChildren(
            mapOf("estado" to ESTADO_PENDIENTE, "eliminada" to false, "updatedAt" to now)
        ).await()
    }

    // ─── Sync ─────────────────────────────────────────────────────────────────

    suspend fun syncScoped(subregion: String, vehiculoId: String?) {
        val root = if (vehiculoId.isNullOrBlank()) firebaseRef.child(subregion)
        else firebaseRef.child(subregion).child(vehiculoId)

        val snap = root.get().await()
        val items = mutableListOf<ProgramacionEntity>()
        val fotos = mutableListOf<ProgramacionFotoEntity>()

        val vehiculoNodes = if (vehiculoId.isNullOrBlank()) snap.children else listOf(snap)
        vehiculoNodes.forEach { vehiculoNode ->
            vehiculoNode.children.forEach { node ->
                val parsed = node.toProgramacionEntity(subregion)
                if (parsed != null) {
                    items += parsed
                    val fotosAsignacion = node.child("fotosAsignacion").children.mapNotNull { it.getValue(String::class.java) }
                    val fotosCierre = node.child("fotosCierre").children.mapNotNull { it.getValue(String::class.java) }
                    fotos += fotosAsignacion.map { url ->
                        ProgramacionFotoEntity(UUID.randomUUID().toString(), parsed.programacionId, url, FOTO_TIPO_ASIGNACION)
                    }
                    fotos += fotosCierre.map { url ->
                        ProgramacionFotoEntity(UUID.randomUUID().toString(), parsed.programacionId, url, FOTO_TIPO_CIERRE)
                    }
                }
            }
        }

        if (items.isNotEmpty()) dao.upsertProgramaciones(items)
        if (fotos.isNotEmpty()) {
            items.forEach { dao.deleteFotosByProgramacion(it.programacionId) }
            dao.upsertFotos(fotos)
        }
    }

    // Método heredado — compatibilidad
    suspend fun actualizarEstado(
        programacionId: String,
        subregion: String,
        vehiculoId: String,
        nuevoEstado: String,
        observaciones: String?,
        fotosCierre: List<String>
    ): Result<Unit> {
        val current = dao.getById(programacionId)
            ?: return Result.failure(IllegalStateException("No encontrada"))
        if (!transicionValidaLegacy(current.estado, nuevoEstado)) {
            return Result.failure(IllegalArgumentException("Transición inválida"))
        }
        val now = System.currentTimeMillis()
        val updated = current.copy(
            estado = nuevoEstado,
            observaciones = observaciones?.trim().takeIf { !it.isNullOrEmpty() } ?: current.observaciones,
            fechaEjecucion = if (nuevoEstado == ESTADO_ATENDIDA) now else current.fechaEjecucion,
            updatedAt = now
        )
        dao.upsertProgramaciones(listOf(updated))
        if (fotosCierre.isNotEmpty()) {
            dao.upsertFotos(fotosCierre.map {
                ProgramacionFotoEntity(UUID.randomUUID().toString(), programacionId, it, FOTO_TIPO_CIERRE)
            })
        }
        val remote = firebaseRef.child(subregion).child(vehiculoId).child(programacionId)
        val fbUpdates = mutableMapOf<String, Any>("estado" to nuevoEstado, "updatedAt" to now)
        updated.observaciones?.let { fbUpdates["observaciones"] = it }
        updated.fechaEjecucion?.let { fbUpdates["fechaEjecucion"] = it }
        remote.updateChildren(fbUpdates).await()
        if (fotosCierre.isNotEmpty()) remote.child("fotosCierre").setValue(fotosCierre).await()
        return Result.success(Unit)
    }

    private fun transicionValidaLegacy(actual: String, nuevo: String): Boolean = when (actual) {
        ESTADO_PENDIENTE -> nuevo == ESTADO_EN_ATENCION
        ESTADO_EN_ATENCION -> nuevo == ESTADO_ATENDIDA
        ESTADO_ATENDIDA -> nuevo == ESTADO_REABIERTA
        ESTADO_REABIERTA -> nuevo in listOf(ESTADO_EN_ATENCION, ESTADO_ATENDIDA)
        else -> false
    }

    companion object {
        const val ESTADO_PENDIENTE = "PENDIENTE"
        const val ESTADO_EN_ATENCION = "EN_ATENCION"
        const val ESTADO_ATENDIDA = "ATENDIDA"
        const val ESTADO_REABIERTA = "REABIERTA"
        const val ESTADO_CANCELADA = "CANCELADA"
        const val ESTADO_ELIMINADA = "ELIMINADA"

        // Alias de compatibilidad
        @Deprecated("Use ESTADO_EN_ATENCION", ReplaceWith("ESTADO_EN_ATENCION"))
        const val ESTADO_EN_PROCESO = "EN_ATENCION"
        @Deprecated("Use ESTADO_ATENDIDA", ReplaceWith("ESTADO_ATENDIDA"))
        const val ESTADO_EJECUTADA = "ATENDIDA"

        const val FOTO_TIPO_ASIGNACION = "ASIGNACION"
        const val FOTO_TIPO_CIERRE = "CIERRE"
        const val FOTO_TIPO_ATENCION = "ATENCION"
    }
}

data class TecnicoParticipante(val uid: String, val nombre: String)
data class MaterialGastado(val codigo: String, val nombre: String, val cantidad: Int, val unidad: String)

private fun com.google.firebase.database.DataSnapshot.toProgramacionEntity(subregion: String): ProgramacionEntity? {
    val programacionId = child("programacionId").getValue(String::class.java) ?: key ?: return null
    val vehiculoId = child("vehiculoId").getValue(String::class.java) ?: return null
    val rawEstado = child("estado").getValue(String::class.java) ?: ProgramacionRepository.ESTADO_PENDIENTE
    val estado = when (rawEstado) {
        "EN_PROCESO" -> ProgramacionRepository.ESTADO_EN_ATENCION
        "EJECUTADA" -> ProgramacionRepository.ESTADO_ATENDIDA
        else -> rawEstado
    }

    return ProgramacionEntity(
        programacionId = programacionId,
        vehiculoId = vehiculoId,
        placa = child("placa").getValue(String::class.java) ?: "",
        localizacion = child("localizacion").getValue(String::class.java) ?: "",
        circuito = child("circuito").getValue(String::class.java) ?: "",
        cuenta = child("cuenta").getValue(String::class.java) ?: "",
        actividad = child("actividad").getValue(String::class.java) ?: "",
        descripcion = child("descripcion").getValue(String::class.java),
        lat = child("lat").getValue(Double::class.java),
        lng = child("lng").getValue(Double::class.java),
        estado = estado,
        observaciones = child("observaciones").getValue(String::class.java),
        fechaAsignacion = child("fechaAsignacion").getValue(Long::class.java) ?: System.currentTimeMillis(),
        fechaEjecucion = child("fechaEjecucion").getValue(Long::class.java),
        supervisorId = child("supervisorId").getValue(String::class.java) ?: "",
        tecnicoId = child("tecnicoId").getValue(String::class.java) ?: "",
        subregion = child("subregion").getValue(String::class.java) ?: subregion,
        updatedAt = child("updatedAt").getValue(Long::class.java) ?: System.currentTimeMillis(),
        // Nuevos campos
        localizacionOriginal = child("localizacionOriginal").getValue(String::class.java),
        localizacionNormalizada = child("localizacionNormalizada").getValue(String::class.java) ?: "",
        agenciaTag = child("agenciaTag").getValue(String::class.java) ?: subregion,
        cuadrillaAsignada = child("cuadrillaAsignada").getValue(String::class.java),
        supervisorNombre = child("supervisorNombre").getValue(String::class.java),
        fotosSupervisorJson = child("fotosSupervisorJson").getValue(String::class.java),
        tecnicoPrincipalUid = child("tecnicoPrincipalUid").getValue(String::class.java),
        tecnicoPrincipalNombre = child("tecnicoPrincipalNombre").getValue(String::class.java),
        tecnicosAtendieronJson = child("tecnicosAtendieronJson").getValue(String::class.java),
        descripcionAtencion = child("descripcionAtencion").getValue(String::class.java),
        fotosAtencionJson = child("fotosAtencionJson").getValue(String::class.java),
        gastoMateriales = child("gastoMateriales").getValue(Boolean::class.java) ?: false,
        materialesJson = child("materialesJson").getValue(String::class.java),
        fechaInicioAtencion = child("fechaInicioAtencion").getValue(Long::class.java),
        fechaFinalizacion = child("fechaFinalizacion").getValue(Long::class.java),
        reabierta = child("reabierta").getValue(Boolean::class.java) ?: false,
        fechaReapertura = child("fechaReapertura").getValue(Long::class.java),
        reabiertaPorUid = child("reabiertaPorUid").getValue(String::class.java),
        motivoReapertura = child("motivoReapertura").getValue(String::class.java),
        canceladaPorUid = child("canceladaPorUid").getValue(String::class.java),
        fechaCancelacion = child("fechaCancelacion").getValue(Long::class.java),
        motivoCancelacion = child("motivoCancelacion").getValue(String::class.java),
        eliminada = child("eliminada").getValue(Boolean::class.java) ?: false,
        eliminadaPorUid = child("eliminadaPorUid").getValue(String::class.java),
        fechaEliminacion = child("fechaEliminacion").getValue(Long::class.java),
        motivoEliminacion = child("motivoEliminacion").getValue(String::class.java),
        syncState = "SYNCED"
    )
}

private fun ProgramacionEntity.toFirebaseMap(): Map<String, Any?> = mapOf(
    "programacionId" to programacionId,
    "vehiculoId" to vehiculoId,
    "placa" to placa,
    "localizacion" to localizacion,
    "localizacionOriginal" to localizacionOriginal,
    "localizacionNormalizada" to localizacionNormalizada,
    "circuito" to circuito,
    "cuenta" to cuenta,
    "actividad" to actividad,
    "descripcion" to descripcion,
    "lat" to lat,
    "lng" to lng,
    "estado" to estado,
    "observaciones" to observaciones,
    "fechaAsignacion" to fechaAsignacion,
    "fechaEjecucion" to fechaEjecucion,
    "supervisorId" to supervisorId,
    "supervisorNombre" to supervisorNombre,
    "tecnicoId" to tecnicoId,
    "subregion" to subregion,
    "agenciaTag" to agenciaTag,
    "cuadrillaAsignada" to cuadrillaAsignada,
    "fotosSupervisorJson" to fotosSupervisorJson,
    "gastoMateriales" to gastoMateriales,
    "eliminada" to eliminada,
    "updatedAt" to updatedAt
)
