// ======================
// PuebloDao.kt
// ======================
package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.*
import com.Arasoftsolutions.tecniapp_ice.Database.entities.PueblosEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PuebloDao {
    // Inserta o reemplaza los pueblos
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pueblos: List<PueblosEntity>)

    // Devuelve todos los pueblos almacenados
    @Query("SELECT * FROM pueblos")
    suspend fun getAll(): List<PueblosEntity>

    @Query("SELECT * FROM pueblos ORDER BY nombre")
    fun observarTodos(): Flow<List<PueblosEntity>>

    // Observa pueblos filtrados por subregión normalizada
    @Query("SELECT * FROM pueblos WHERE subregion_id_normalizado = :subregionId ORDER BY nombre")
    fun observarPorSubregion(subregionId: String): Flow<List<PueblosEntity>>

    @Query("SELECT * FROM pueblos WHERE id = :puebloId LIMIT 1")
    suspend fun buscarPorId(puebloId: Int): PueblosEntity?

    @Query("SELECT id FROM pueblos WHERE subregion_id_normalizado = :subregionId")
    suspend fun obtenerIdsPorSubregion(subregionId: String): List<Int>

    @Query("DELETE FROM pueblos WHERE subregion_id_normalizado = :subregionId")
    suspend fun limpiarSubregion(subregionId: String)

    @Query("DELETE FROM pueblos WHERE subregion_id_normalizado != :subregionId")
    suspend fun eliminarFueraDeSubregion(subregionId: String)
}
