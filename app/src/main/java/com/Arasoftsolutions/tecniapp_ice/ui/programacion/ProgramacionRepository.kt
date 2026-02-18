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

    fun observeProgramaciones(subregion: String, vehiculoId: String?): Flow<List<ProgramacionEntity>> =
        if (vehiculoId.isNullOrBlank()) dao.observeBySubregion(subregion)
        else dao.observeBySubregionVehiculo(subregion, vehiculoId)

    suspend fun observeTieneFotos(programacionId: String): Boolean =
        dao.observeTieneFotos(programacionId).firstOrNull() ?: false

    suspend fun crearProgramacion(input: ProgramacionEntity, fotosAsignacion: List<String>) {
        dao.upsertProgramaciones(listOf(input))
        if (fotosAsignacion.isNotEmpty()) {
            dao.upsertFotos(
                fotosAsignacion.map {
                    ProgramacionFotoEntity(
                        fotoId = UUID.randomUUID().toString(),
                        programacionId = input.programacionId,
                        url = it,
                        tipo = FOTO_TIPO_ASIGNACION
                    )
                }
            )
        }

        val payload = input.toFirebaseMap() + mapOf(
            "fotosAsignacion" to fotosAsignacion,
            "fotosCierre" to emptyList<String>()
        )
        firebaseRef.child(input.subregion).child(input.vehiculoId).child(input.programacionId).setValue(payload).await()
    }

    suspend fun syncScoped(subregion: String, vehiculoId: String?) {
        val root = if (vehiculoId.isNullOrBlank()) {
            firebaseRef.child(subregion)
        } else {
            firebaseRef.child(subregion).child(vehiculoId)
        }

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

    suspend fun actualizarEstado(
        programacionId: String,
        subregion: String,
        vehiculoId: String,
        nuevoEstado: String,
        observaciones: String?,
        fotosCierre: List<String>
    ): Result<Unit> {
        val current = dao.getById(programacionId) ?: return Result.failure(IllegalStateException("No encontrada"))
        if (!transicionValida(current.estado, nuevoEstado)) {
            return Result.failure(IllegalArgumentException("Transición inválida"))
        }
        val now = System.currentTimeMillis()
        val updated = current.copy(
            estado = nuevoEstado,
            observaciones = observaciones?.trim().takeIf { !it.isNullOrEmpty() } ?: current.observaciones,
            fechaEjecucion = if (nuevoEstado == ESTADO_EJECUTADA) now else current.fechaEjecucion,
            updatedAt = now
        )
        dao.upsertProgramaciones(listOf(updated))
        if (fotosCierre.isNotEmpty()) {
            dao.upsertFotos(fotosCierre.map {
                ProgramacionFotoEntity(UUID.randomUUID().toString(), programacionId, it, FOTO_TIPO_CIERRE)
            })
        }

        val remote = firebaseRef.child(subregion).child(vehiculoId).child(programacionId)
        val updates = mutableMapOf<String, Any>(
            "estado" to nuevoEstado,
            "updatedAt" to now
        )
        if (updated.observaciones != null) updates["observaciones"] = updated.observaciones
        if (updated.fechaEjecucion != null) updates["fechaEjecucion"] = updated.fechaEjecucion
        remote.updateChildren(updates).await()
        if (fotosCierre.isNotEmpty()) {
            remote.child("fotosCierre").setValue(fotosCierre).await()
        }
        return Result.success(Unit)
    }

    private fun transicionValida(actual: String, nuevo: String): Boolean = when (actual) {
        ESTADO_PENDIENTE -> nuevo == ESTADO_EN_PROCESO
        ESTADO_EN_PROCESO -> nuevo == ESTADO_EJECUTADA
        ESTADO_EJECUTADA -> false
        else -> false
    }

    companion object {
        const val ESTADO_PENDIENTE = "PENDIENTE"
        const val ESTADO_EN_PROCESO = "EN_PROCESO"
        const val ESTADO_EJECUTADA = "EJECUTADA"

        const val FOTO_TIPO_ASIGNACION = "ASIGNACION"
        const val FOTO_TIPO_CIERRE = "CIERRE"
    }
}

private fun com.google.firebase.database.DataSnapshot.toProgramacionEntity(subregion: String): ProgramacionEntity? {
    val programacionId = child("programacionId").getValue(String::class.java) ?: key ?: return null
    val vehiculoId = child("vehiculoId").getValue(String::class.java) ?: return null
    val placa = child("placa").getValue(String::class.java) ?: ""
    val localizacion = child("localizacion").getValue(String::class.java) ?: ""
    val circuito = child("circuito").getValue(String::class.java) ?: ""
    val cuenta = child("cuenta").getValue(String::class.java) ?: ""
    val actividad = child("actividad").getValue(String::class.java) ?: ""
    val estado = child("estado").getValue(String::class.java) ?: ProgramacionRepository.ESTADO_PENDIENTE
    val tecnicoId = child("tecnicoId").getValue(String::class.java) ?: ""
    val supervisorId = child("supervisorId").getValue(String::class.java) ?: ""
    val updatedAt = child("updatedAt").getValue(Long::class.java) ?: System.currentTimeMillis()

    return ProgramacionEntity(
        programacionId = programacionId,
        vehiculoId = vehiculoId,
        placa = placa,
        localizacion = localizacion,
        circuito = circuito,
        cuenta = cuenta,
        actividad = actividad,
        descripcion = child("descripcion").getValue(String::class.java),
        lat = child("lat").getValue(Double::class.java),
        lng = child("lng").getValue(Double::class.java),
        estado = estado,
        observaciones = child("observaciones").getValue(String::class.java),
        fechaAsignacion = child("fechaAsignacion").getValue(Long::class.java) ?: System.currentTimeMillis(),
        fechaEjecucion = child("fechaEjecucion").getValue(Long::class.java),
        supervisorId = supervisorId,
        tecnicoId = tecnicoId,
        subregion = child("subregion").getValue(String::class.java) ?: subregion,
        updatedAt = updatedAt
    )
}

private fun ProgramacionEntity.toFirebaseMap(): Map<String, Any?> = mapOf(
    "programacionId" to programacionId,
    "vehiculoId" to vehiculoId,
    "placa" to placa,
    "localizacion" to localizacion,
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
    "tecnicoId" to tecnicoId,
    "subregion" to subregion,
    "updatedAt" to updatedAt
)
