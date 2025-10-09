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

    @Query("SELECT * FROM localizaciones WHERE subregion = :subregionId AND pueblo = :puebloId")
    suspend fun obtenerPorPueblo(subregionId: String, puebloId: Int): List<LocalizacionesEntity>

    @Query("SELECT * FROM localizaciones WHERE pueblo = :puebloId")
    suspend fun obtenerPorPuebloGlobal(puebloId: Int): List<LocalizacionesEntity>

    @Query(
        "SELECT * FROM localizaciones WHERE subregion = :subregionId AND pueblo = :puebloId AND calle = :calleId"
    )
    suspend fun buscarPorCalle(
        subregionId: String,
        puebloId: Int,
        calleId: Int
    ): List<LocalizacionesEntity>

    @Query("SELECT * FROM localizaciones WHERE pueblo = :puebloId AND calle = :calleId")
    suspend fun buscarPorCalleGlobal(
        puebloId: Int,
        calleId: Int
    ): List<LocalizacionesEntity>

    @Query("SELECT * FROM localizaciones WHERE id = :localizacionId LIMIT 1")
    suspend fun buscarPorId(localizacionId: Long): LocalizacionesEntity?
}
