package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculoKilometrajeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehiculoKilometrajeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(registro: VehiculoKilometrajeEntity)

    @Query(
        "SELECT * FROM vehiculo_kilometrajes WHERE placaNormalizada = :placaNormalizada ORDER BY registradoEn DESC LIMIT 1"
    )
    fun observarUltimo(placaNormalizada: String): Flow<VehiculoKilometrajeEntity?>

    @Query(
        "SELECT * FROM vehiculo_kilometrajes WHERE placaNormalizada = :placaNormalizada ORDER BY registradoEn DESC LIMIT 1"
    )
    suspend fun obtenerUltimo(placaNormalizada: String): VehiculoKilometrajeEntity?
}
