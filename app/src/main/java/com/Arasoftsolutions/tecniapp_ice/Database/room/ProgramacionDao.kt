package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.Arasoftsolutions.tecniapp_ice.Database.entities.ProgramacionEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.ProgramacionFotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgramacionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgramaciones(items: List<ProgramacionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFotos(items: List<ProgramacionFotoEntity>)

    @Query("SELECT * FROM programaciones ORDER BY fechaAsignacion DESC")
    fun observeAll(): Flow<List<ProgramacionEntity>>

    @Query("SELECT * FROM programaciones WHERE subregion = :subregion ORDER BY fechaAsignacion DESC")
    fun observeBySubregion(subregion: String): Flow<List<ProgramacionEntity>>

    @Query("SELECT * FROM programaciones WHERE subregion = :subregion AND vehiculoId = :vehiculoId ORDER BY fechaAsignacion DESC")
    fun observeBySubregionVehiculo(subregion: String, vehiculoId: String): Flow<List<ProgramacionEntity>>

    @Query("SELECT * FROM programaciones WHERE programacionId = :programacionId LIMIT 1")
    suspend fun getById(programacionId: String): ProgramacionEntity?

    @Query("SELECT * FROM programacion_fotos WHERE programacionId = :programacionId ORDER BY fotoId DESC")
    suspend fun getFotos(programacionId: String): List<ProgramacionFotoEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM programacion_fotos WHERE programacionId = :programacionId LIMIT 1)")
    fun observeTieneFotos(programacionId: String): Flow<Boolean>

    @Query("DELETE FROM programacion_fotos WHERE programacionId = :programacionId")
    suspend fun deleteFotosByProgramacion(programacionId: String)

    @Transaction
    suspend fun replaceFotos(programacionId: String, fotos: List<ProgramacionFotoEntity>) {
        deleteFotosByProgramacion(programacionId)
        if (fotos.isNotEmpty()) upsertFotos(fotos)
    }
}
