package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.Arasoftsolutions.tecniapp_ice.Database.entities.ProgramacionEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.ProgramacionFotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgramacionDao {

    // ─── Inserción ────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgramaciones(items: List<ProgramacionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ProgramacionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFotos(items: List<ProgramacionFotoEntity>)

    // ─── Consultas originales (compatibilidad) ────────────────────────────────

    @Query("SELECT * FROM programaciones ORDER BY fechaAsignacion DESC")
    fun observeAll(): Flow<List<ProgramacionEntity>>

    @Query("SELECT * FROM programaciones WHERE subregion = :subregion ORDER BY fechaAsignacion DESC")
    fun observeBySubregion(subregion: String): Flow<List<ProgramacionEntity>>

    @Query("SELECT * FROM programaciones WHERE subregion = :subregion AND vehiculoId = :vehiculoId ORDER BY fechaAsignacion DESC")
    fun observeBySubregionVehiculo(subregion: String, vehiculoId: String): Flow<List<ProgramacionEntity>>

    @Query("SELECT * FROM programaciones WHERE programacionId = :programacionId LIMIT 1")
    suspend fun getById(programacionId: String): ProgramacionEntity?

    // ─── Vista supervisor ─────────────────────────────────────────────────────

    @Query("""
        SELECT * FROM programaciones
        WHERE agenciaTag = :agenciaTag
        AND eliminada = 0
        ORDER BY fechaAsignacion DESC
    """)
    fun observeOrdenesSupervisor(agenciaTag: String): Flow<List<ProgramacionEntity>>

    @Query("""
        SELECT * FROM programaciones
        WHERE agenciaTag = :agenciaTag
        AND vehiculoId = :vehiculo
        AND eliminada = 0
        ORDER BY fechaAsignacion DESC
    """)
    fun observeOrdenesSupervisorPorVehiculo(
        agenciaTag: String,
        vehiculo: String
    ): Flow<List<ProgramacionEntity>>

    @Query("""
        SELECT * FROM programaciones
        WHERE agenciaTag = :agenciaTag
        AND eliminada = 1
        ORDER BY fechaEliminacion DESC
    """)
    fun observeOrdenesEliminadas(agenciaTag: String): Flow<List<ProgramacionEntity>>

    // ─── Vista técnico ────────────────────────────────────────────────────────

    @Query("""
        SELECT * FROM programaciones
        WHERE agenciaTag = :agenciaTag
        AND vehiculoId = :vehiculo
        AND eliminada = 0
        AND estado IN ('PENDIENTE', 'EN_ATENCION', 'REABIERTA')
        ORDER BY fechaAsignacion DESC
    """)
    fun observeOrdenesPendientesTecnico(
        agenciaTag: String,
        vehiculo: String
    ): Flow<List<ProgramacionEntity>>

    @Query("""
        SELECT * FROM programaciones
        WHERE agenciaTag = :agenciaTag
        AND tecnicoPrincipalUid = :uid
        AND estado = 'ATENDIDA'
        AND eliminada = 0
        ORDER BY fechaFinalizacion DESC
    """)
    fun observeOrdenesAtendidasPorTecnico(
        agenciaTag: String,
        uid: String
    ): Flow<List<ProgramacionEntity>>

    @Query("""
        SELECT * FROM programaciones
        WHERE agenciaTag = :agenciaTag
        AND (vehiculoId = :vehiculo OR tecnicoPrincipalUid = :uid)
        AND eliminada = 0
        ORDER BY fechaAsignacion DESC
    """)
    fun observeTodasOrdenesTecnico(
        agenciaTag: String,
        vehiculo: String,
        uid: String
    ): Flow<List<ProgramacionEntity>>

    // ─── Operaciones de estado ────────────────────────────────────────────────

    @Query("""
        UPDATE programaciones
        SET estado = 'EN_ATENCION',
            tecnicoPrincipalUid = :uid,
            tecnicoPrincipalNombre = :nombre,
            fechaInicioAtencion = :fecha,
            updatedAt = :fecha,
            syncState = 'PENDING'
        WHERE programacionId = :id
        AND estado IN ('PENDIENTE', 'REABIERTA')
        AND eliminada = 0
    """)
    suspend fun iniciarAtencion(id: String, uid: String, nombre: String, fecha: Long)

    @Query("""
        UPDATE programaciones
        SET estado = 'ATENDIDA',
            descripcionAtencion = :descripcion,
            fotosAtencionJson = :fotosJson,
            tecnicosAtendieronJson = :tecnicosJson,
            gastoMateriales = :gastoMateriales,
            materialesJson = :materialesJson,
            fechaFinalizacion = :fecha,
            reabierta = 0,
            updatedAt = :fecha,
            syncState = 'PENDING'
        WHERE programacionId = :id
        AND tecnicoPrincipalUid = :uid
        AND estado IN ('EN_ATENCION', 'REABIERTA')
        AND eliminada = 0
    """)
    suspend fun finalizarAtencion(
        id: String,
        uid: String,
        descripcion: String,
        fotosJson: String?,
        tecnicosJson: String?,
        gastoMateriales: Boolean,
        materialesJson: String?,
        fecha: Long
    )

    @Query("""
        UPDATE programaciones
        SET estado = 'REABIERTA',
            reabierta = 1,
            fechaReapertura = :fecha,
            reabiertaPorUid = :uid,
            motivoReapertura = :motivo,
            updatedAt = :fecha,
            syncState = 'PENDING'
        WHERE programacionId = :id
        AND tecnicoPrincipalUid = :uid
        AND estado = 'ATENDIDA'
        AND eliminada = 0
    """)
    suspend fun reabrirOrden(id: String, uid: String, motivo: String?, fecha: Long)

    @Query("""
        UPDATE programaciones
        SET estado = 'CANCELADA',
            canceladaPorUid = :uid,
            fechaCancelacion = :fecha,
            motivoCancelacion = :motivo,
            updatedAt = :fecha,
            syncState = 'PENDING'
        WHERE programacionId = :id
        AND eliminada = 0
    """)
    suspend fun cancelarOrden(id: String, uid: String, motivo: String?, fecha: Long)

    @Query("""
        UPDATE programaciones
        SET estado = 'ELIMINADA',
            eliminada = 1,
            eliminadaPorUid = :uid,
            fechaEliminacion = :fecha,
            motivoEliminacion = :motivo,
            updatedAt = :fecha,
            syncState = 'PENDING'
        WHERE programacionId = :id
    """)
    suspend fun eliminarLogico(id: String, uid: String, motivo: String?, fecha: Long)

    @Query("""
        UPDATE programaciones
        SET eliminada = 0,
            estado = 'PENDIENTE',
            eliminadaPorUid = NULL,
            fechaEliminacion = NULL,
            motivoEliminacion = NULL,
            updatedAt = :fecha,
            syncState = 'PENDING'
        WHERE programacionId = :id
    """)
    suspend fun restaurarOrden(id: String, fecha: Long)

    // ─── Actualización supervisor ─────────────────────────────────────────────

    @Query("""
        UPDATE programaciones
        SET localizacion = :localizacion,
            localizacionOriginal = :localizacionOriginal,
            localizacionNormalizada = :localizacionNormalizada,
            actividad = :detalleTrabajo,
            descripcion = :detalleTrabajo,
            lat = :lat,
            lng = :lng,
            vehiculoId = :vehiculoId,
            fotosSupervisorJson = :fotosJson,
            updatedAt = :fecha,
            syncState = 'PENDING'
        WHERE programacionId = :id
        AND supervisorId = :uid
        AND eliminada = 0
    """)
    suspend fun actualizarDatosSupervisor(
        id: String,
        uid: String,
        localizacion: String,
        localizacionOriginal: String,
        localizacionNormalizada: String,
        detalleTrabajo: String,
        lat: Double?,
        lng: Double?,
        vehiculoId: String,
        fotosJson: String?,
        fecha: Long
    )

    // ─── Fotos ────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM programacion_fotos WHERE programacionId = :programacionId ORDER BY fotoId DESC")
    suspend fun getFotos(programacionId: String): List<ProgramacionFotoEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM programacion_fotos WHERE programacionId = :programacionId LIMIT 1)")
    fun observeTieneFotos(programacionId: String): Flow<Boolean>

    @Query("DELETE FROM programacion_fotos WHERE programacionId = :programacionId")
    suspend fun deleteFotosByProgramacion(programacionId: String)

    @Transaction
    suspend fun replaceFotos(programacionId: String, fotos: List<ProgramacionFotoEntity>) {
        deleteFotosByProgramacion(programacionId)
        if (fotos.isNotEmpty()) upsertFotos(fotos)
    }

    // ─── Sync ─────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM programaciones WHERE syncState = 'PENDING' ORDER BY updatedAt ASC")
    suspend fun getPendingSync(): List<ProgramacionEntity>

    @Query("UPDATE programaciones SET syncState = :state WHERE programacionId = :id")
    suspend fun updateSyncState(id: String, state: String)
}
