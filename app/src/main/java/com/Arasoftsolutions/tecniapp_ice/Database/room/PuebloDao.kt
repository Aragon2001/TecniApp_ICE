// ======================
// PuebloDao.kt
// ======================
package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.*
import com.Arasoftsolutions.tecniapp_ice.Database.entities.PueblosEntity

@Dao
interface PuebloDao {
    // Inserta o reemplaza los pueblos
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pueblos: List<PueblosEntity>)

    // Devuelve todos los pueblos almacenados
    @Query("SELECT * FROM pueblos")
    suspend fun getAll(): List<PueblosEntity>
}
