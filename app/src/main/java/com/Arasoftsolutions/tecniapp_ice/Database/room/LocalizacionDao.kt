// ======================
// LocalizacionDao.kt
// ======================
package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.*
import com.Arasoftsolutions.tecniapp_ice.Database.entities.LocalizacionesEntity

@Dao
interface LocalizacionDao {
    // Inserta o reemplaza todas las localizaciones
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(localizaciones: List<LocalizacionesEntity>)

    // Devuelve todas las localizaciones disponibles
    @Query("SELECT * FROM localizaciones")
    suspend fun getAll(): List<LocalizacionesEntity>
}
