// ======================
// LocalizacionDao.kt
// ======================
package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.*
import com.Arasoftsolutions.tecniapp_ice.Database.entities.LocalizacionesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalizacionDao {
    // Inserta o reemplaza todas las localizaciones
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(localizaciones: List<LocalizacionesEntity>)

    // Devuelve todas las localizaciones disponibles
    @Query("SELECT * FROM localizaciones")
    suspend fun getAll(): List<LocalizacionesEntity>

    // Observa localizaciones filtradas por subregión
    @Query("SELECT * FROM localizaciones WHERE subregion = :subregionId")
    fun observarPorSubregion(subregionId: String): Flow<List<LocalizacionesEntity>>
}
