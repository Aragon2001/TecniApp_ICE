package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.Arasoftsolutions.tecniapp_ice.Database.entities.MaterialEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MaterialDao {

    @Query("SELECT * FROM materiales ORDER BY descripcion")
    fun observarMateriales(): Flow<List<MaterialEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MaterialEntity>)

    @Query("SELECT * FROM materiales")
    suspend fun observarMaterialesSnapshot(): List<MaterialEntity>

    @Query("SELECT * FROM materiales WHERE codigo = :codigo LIMIT 1")
    suspend fun obtenerPorCodigo(codigo: String): MaterialEntity?

    @Query("SELECT codigo FROM materiales WHERE codigo IN (:codigos)")
    suspend fun obtenerCodigos(codigos: List<String>): List<String>

    @Query("DELETE FROM materiales WHERE codigo NOT IN (:codigos)")
    suspend fun eliminarFueraDeCodigos(codigos: List<String>)

    @Query("DELETE FROM materiales")
    suspend fun limpiarTodo()
}
