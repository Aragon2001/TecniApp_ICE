package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.Arasoftsolutions.tecniapp_ice.Database.entities.ReporteGeneradoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReporteGeneradoDao {

    @Query("SELECT * FROM reporte_generado WHERE usuarioUid = :uid ORDER BY fechaGeneracion DESC")
    fun observarReportes(uid: String): Flow<List<ReporteGeneradoEntity>>

    @Query("SELECT * FROM reporte_generado WHERE usuarioUid = :uid ORDER BY fechaGeneracion DESC LIMIT :limit")
    suspend fun obtenerRecientes(uid: String, limit: Int = 50): List<ReporteGeneradoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(reporte: ReporteGeneradoEntity)

    @Query("UPDATE reporte_generado SET enviadoCorreo = 1, correoDestino = :correo, fechaEnvio = :ts, errorEnvio = 0 WHERE id = :id")
    suspend fun marcarEnviado(id: String, correo: String, ts: Long)

    @Query("UPDATE reporte_generado SET errorEnvio = 1 WHERE id = :id")
    suspend fun marcarErrorEnvio(id: String)

    @Query("DELETE FROM reporte_generado WHERE id = :id")
    suspend fun eliminarPorId(id: String)

    @Delete
    suspend fun eliminar(reporte: ReporteGeneradoEntity)
}