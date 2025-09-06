package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.*
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AveriaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AveriaDao {
  @Query("""
    SELECT * FROM averias
    WHERE (:agenciasSize=0 OR agenciaTag IN (:agencias))
      AND (:estado='' OR estado = :estado)
      AND (
        :q='' OR
        caseId LIKE '%'||:q||'%' OR
        nombreAgencia LIKE '%'||:q||'%' OR
        causa LIKE '%'||:q||'%' OR
        observaciones LIKE '%'||:q||'%' OR
        clientesAfectados LIKE '%'||:q||'%'
      )
    ORDER BY fechaInicioMillis DESC
  """)
  fun observe(agencias: List<String>, agenciasSize: Int, estado: String, q: String): Flow<List<AveriaEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<AveriaEntity>)
  @Query("SELECT caseId FROM averias") suspend fun allIds(): List<String>

  @Query("""
    UPDATE averias SET
      estado=:nuevoEstado,
      tecnicoAsignadoUid=:uid,
      tecnicoAsignadoNombre=:nombre,
      vehiculoAsignado=:vehiculo,
      horaInicioMillis=COALESCE(horaInicioMillis, :horaInicio)
    WHERE caseId=:caseId
  """)
  suspend fun marcarAsignada(caseId: String, uid: String, nombre: String?, vehiculo: String?, horaInicio: Long, nuevoEstado: String = "Asignada")

  @Query("""
    UPDATE averias SET
      estado=:nuevoEstado,
      causa=:causa,
      observaciones=:obs,
      horaInicioMillis=COALESCE(horaInicioMillis, :horaInicio),
      horaFinalMillis=:horaFinal
    WHERE caseId=:caseId
  """)
  suspend fun cerrarAveria(caseId: String, causa: String?, obs: String?, horaInicio: Long?, horaFinal: Long?, nuevoEstado: String)
}
