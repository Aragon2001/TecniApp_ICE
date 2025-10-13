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

    @Query("SELECT * FROM localizaciones WHERE pueblo = :puebloId ORDER BY calle, direccion")
    fun observarPorPueblo(puebloId: Int): Flow<List<LocalizacionesEntity>>

    @Query("SELECT * FROM localizaciones WHERE pueblo IN (:puebloIds)")
    fun observarPorPueblos(puebloIds: List<Int>): Flow<List<LocalizacionesEntity>>

    @Query("SELECT * FROM localizaciones WHERE pueblo = :puebloId")
    suspend fun obtenerPorPueblo(puebloId: Int): List<LocalizacionesEntity>

    @Query("SELECT * FROM localizaciones")
    fun observarTodas(): Flow<List<LocalizacionesEntity>>

    @Query("SELECT * FROM localizaciones WHERE pueblo = :puebloId AND calle = :calleId")
    suspend fun buscarPorCalle(
        puebloId: Int,
        calleId: Int
    ): List<LocalizacionesEntity>

    @Query("SELECT * FROM localizaciones WHERE id = :localizacionId LIMIT 1")
    suspend fun buscarPorId(localizacionId: Long): LocalizacionesEntity?

    @Query("DELETE FROM localizaciones")
    suspend fun limpiarTodo()

    @Query("DELETE FROM localizaciones WHERE id = :id")
    suspend fun eliminarPorId(id: Int)
}
