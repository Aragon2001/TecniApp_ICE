package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.*
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AveriaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AveriaDao {

@Query(
    """
    SELECT * FROM averias
    WHERE (:agenciasSize = 0 OR agenciaTag IN (:agencias))
      AND (:estado = '' OR lower(estado) = lower(:estado))
      AND (
        :q = '' OR
        caseId LIKE '%'||:q||'%' OR
        nombreAgencia LIKE '%'||:q||'%' OR
        causa LIKE '%'||:q||'%' OR
        observaciones LIKE '%'||:q||'%' OR
        clientesAfectados LIKE '%'||:q||'%'
      )
    ORDER BY fechaInicioMillis DESC
    """
)
fun observe(
    agencias: List<String>,
    agenciasSize: Int,
    estado: String,
    q: String
): Flow<List<AveriaEntity>>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<AveriaEntity>)

    @Query("SELECT caseId FROM averias")
    suspend fun allIds(): List<String>

    @Query("SELECT * FROM averias")
    suspend fun all(): List<AveriaEntity>

    @Query("SELECT * FROM averias WHERE caseId = :caseId LIMIT 1")
    suspend fun getByCaseId(caseId: String): AveriaEntity?

    @Query("SELECT * FROM averias WHERE isSynced = 0")
    suspend fun pendingSync(): List<AveriaEntity>

    @Query(
        """
        UPDATE averias SET
          estado = :nuevoEstado,
          tecnicoAsignadoUid = :uid,
          tecnicoAsignadoNombre = :nombre,
          vehiculoAsignado = :vehiculo,
          horaInicioMillis = COALESCE(horaInicioMillis, :horaInicio),
          lastUpdated = :lastUpdated,
          isSynced = 0
        WHERE caseId = :caseId
        """
    )
    suspend fun marcarAsignada(
        caseId: String,
        uid: String,
        nombre: String?,
        vehiculo: String?,
        horaInicio: Long,
        lastUpdated: Long,
        nuevoEstado: String = "Asignada"
    )

    @Query(
        """
        UPDATE averias SET
          estado = :nuevoEstado,
          causa = :causa,
          observaciones = :obs,
          horaInicioMillis = COALESCE(horaInicioMillis, :horaInicio),
          horaFinalMillis = :horaFinal,
          atencionHoraInicioMillis = :horaInicio,
          atencionHoraFinalMillis = :horaFinal,
          kilometrajeInicio = :kmInicio,
          kilometrajeFinal = :kmFinal,
          atendidoPorUid = :atendidoPorUid,
          atendidoPorNombre = :atendidoPorNombre,
          vehiculoAsignado = :vehiculo,
          materialesTexto = :materialesResumen,
          materialesDetalleJson = :materialesDetalle,
          tecnicosAtendieronJson = :tecnicosAtendieron,
          cliente = COALESCE(:cliente, cliente),
          localizacion = :localizacion,
          tipoAfectacion = :tipoAfectacion,
          numeroMedidor = :numeroMedidor,
          medidorCalle = :medidorCalle,
          medidorPueblo = :medidorPueblo,
          medidorMetros = :medidorMetros,
          medidorPoste = :medidorPoste,
          lastUpdated = :lastUpdated,
          isSynced = 0
        WHERE caseId = :caseId
        """
    )
    suspend fun actualizarAtencion(
        caseId: String,
        causa: String?,
        obs: String?,
        horaInicio: Long?,
        horaFinal: Long?,
        kmInicio: Double?,
        kmFinal: Double?,
        atendidoPorUid: String?,
        atendidoPorNombre: String?,
        vehiculo: String?,
        materialesResumen: String?,
        materialesDetalle: String?,
        tecnicosAtendieron: String?,
        cliente: String?,
        localizacion: String?,
        tipoAfectacion: String?,
        numeroMedidor: String?,
        medidorCalle: String?,
        medidorPueblo: String?,
        medidorMetros: String?,
        medidorPoste: String?,
        lastUpdated: Long,
        nuevoEstado: String
    )

    @Query(
        """
        UPDATE averias SET
          estado = 'Pendiente',
          tecnicoAsignadoUid = NULL,
          tecnicoAsignadoNombre = NULL,
          vehiculoAsignado = NULL,
          atendidoPorUid = NULL,
          atendidoPorNombre = NULL,
          materialesTexto = NULL,
          causa = NULL,
          observaciones = NULL,
          horaInicioMillis = NULL,
          horaFinalMillis = NULL,
          atencionHoraInicioMillis = NULL,
          atencionHoraFinalMillis = NULL,
          kilometrajeInicio = NULL,
          kilometrajeFinal = NULL,
          materialesDetalleJson = NULL,
          tecnicosAtendieronJson = NULL,
          localizacion = NULL,
          tipoAfectacion = NULL,
          numeroMedidor = NULL,
          medidorCalle = NULL,
          medidorPueblo = NULL,
          medidorMetros = NULL,
          medidorPoste = NULL,
          lastUpdated = :lastUpdated,
          isSynced = 0
        WHERE caseId = :caseId
        """
    )
    suspend fun revertirAPendiente(caseId: String, lastUpdated: Long)

    @Query(
        """
        UPDATE averias SET
          estado = :nuevoEstado,
          lastUpdated = :lastUpdated,
          isSynced = 0
        WHERE caseId = :caseId
        """
    )
    suspend fun marcarAnulada(
        caseId: String,
        nuevoEstado: String = "Anulada",
        lastUpdated: Long
    )

    @Query("UPDATE averias SET isSynced = 1 WHERE caseId = :caseId")
    suspend fun marcarSincronizado(caseId: String)

    @Query("DELETE FROM averias WHERE caseId = :caseId")
    suspend fun eliminarPorCaseId(caseId: String)

    @Query(
        """
        UPDATE averias SET
          direccion = :direccion,
          lastUpdated = :lastUpdated,
          isSynced = 0
        WHERE caseId = :caseId
        """
    )
    suspend fun actualizarDireccion(caseId: String, direccion: String, lastUpdated: Long)
}
