// ======================
// SubregionDao.kt
// ======================
package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.*
import com.Arasoftsolutions.tecniapp_ice.Database.entities.SubregionesEntity

@Dao
interface SubregionDao {
    // Inserta o actualiza subregiones
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(subregiones: List<SubregionesEntity>)

    // Devuelve todas las subregiones disponibles
    @Query("SELECT * FROM subregiones")
    suspend fun getAll(): List<SubregionesEntity>
}