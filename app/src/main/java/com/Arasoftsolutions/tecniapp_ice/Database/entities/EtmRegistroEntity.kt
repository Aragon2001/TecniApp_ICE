package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Registro diario ETM (Equipo / Transporte / Maquinaria).
 * Valor = kilometraje (km) o orímetro (horas) según tipo de vehículo.
 */
@Entity(
    tableName = "etm_registros",
    indices = [Index(value = ["placa", "fecha"], unique = true)]
)
data class EtmRegistroEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placa: String,
    val vehiculoId: Int,
    val fecha: String,
    val valorInicial: Double,
    val valorFinal: Double? = null,
    val tecnicoUid: String,
    val tecnicoNombre: String?,
    val observaciones: String? = null,
    val cerrado: Boolean = false,
    val registradoEn: Long = System.currentTimeMillis()
) {
    val diferencia: Double?
        get() = valorFinal?.let { it - valorInicial }
}
