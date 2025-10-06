// ======================
// MedidorDao.kt
// ======================
package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.*
import com.Arasoftsolutions.tecniapp_ice.Database.entities.MedidorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedidorDao {
    // Inserta o actualiza los medidores en la base de datos
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(medidores: List<MedidorEntity>)

    // Obtiene todos los medidores guardados
    @Query("SELECT * FROM medidores")
    suspend fun getAll(): List<MedidorEntity>

    // Observa medidores por subregión
    @Query("SELECT * FROM medidores WHERE subregion = :subregionId")
    fun observarPorSubregion(subregionId: String): Flow<List<MedidorEntity>>

    @Query("SELECT * FROM medidores WHERE medidorNumber = :numero LIMIT 1")
    suspend fun buscarPorNumero(numero: String): MedidorEntity?

    @Query("SELECT COUNT(*) FROM medidores WHERE subregion = :subregionId")
    suspend fun contarPorSubregion(subregionId: String): Int
}
