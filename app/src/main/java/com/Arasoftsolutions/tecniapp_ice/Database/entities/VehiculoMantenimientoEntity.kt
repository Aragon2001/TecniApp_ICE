package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Mantenimiento de vehículo (preventivo o correctivo).
 */
@Entity(
    tableName = "vehiculo_mantenimientos",
    indices = [Index(value = ["placa"])]
)
data class VehiculoMantenimientoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placa: String,
    val vehiculoId: Int,
    val tipo: String,
    val fecha: String,
    val valorAlMomento: Double,
    val observaciones: String? = null,
    val proximoKm: Double? = null,
    val proximoHoras: Double? = null,
    val proximoFecha: String? = null,
    val registradoEn: Long = System.currentTimeMillis()
)
