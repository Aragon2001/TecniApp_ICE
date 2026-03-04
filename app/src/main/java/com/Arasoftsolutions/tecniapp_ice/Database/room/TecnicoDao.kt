package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.Arasoftsolutions.tecniapp_ice.Database.entities.TecnicoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TecnicoDao {

    @Query("SELECT * FROM tecnicos ORDER BY nombre")
    fun observarTecnicos(): Flow<List<TecnicoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TecnicoEntity>)

    @Query("SELECT * FROM tecnicos")
    suspend fun observarTecnicosSnapshot(): List<TecnicoEntity>

    @Query("SELECT * FROM tecnicos WHERE cedula = :cedula LIMIT 1")
    suspend fun obtenerPorCedula(cedula: String): TecnicoEntity?

    @Query("DELETE FROM tecnicos WHERE cedula NOT IN (:cedulas)")
    suspend fun eliminarFueraDeCedulas(cedulas: List<String>)

    @Query("DELETE FROM tecnicos")
    suspend fun limpiarTodo()
}
