// ======================
// MedidorDao.kt
// ======================
package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.*
import com.Arasoftsolutions.tecniapp_ice.Database.entities.MedidorEntity

@Dao
interface MedidorDao {
    // Inserta o actualiza los medidores en la base de datos
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(medidores: List<MedidorEntity>)

    // Obtiene todos los medidores guardados
    @Query("SELECT * FROM medidores")
    suspend fun getAll(): List<MedidorEntity>
}
