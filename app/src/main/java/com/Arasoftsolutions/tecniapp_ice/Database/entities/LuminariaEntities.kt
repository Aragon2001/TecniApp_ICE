package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.firebase.database.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
@Entity(
    tableName = "luminaria_reparacion",
    indices = [
        Index(value = ["vehiculoId"]),
        Index(value = ["estado"])
    ]
)
data class LuminariaReparacionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val vehiculoId: Int,
    val localizacion: String,
    val cliente: String? = null,
    val contacto: String? = null,
    val observaciones: String? = null,
    val materialesJson: String? = null,
    val estado: String = LuminariaEstado.REPARADA.name,
    val ejecutorNombre: String = "",
    val ejecutorCedula: String? = null,
    val fechaRegistro: Long = System.currentTimeMillis(),
    val fechaCarga: Long = System.currentTimeMillis(),
    val fechaReparacion: Long? = null
)

enum class LuminariaEstado {
    PENDIENTE,
    REPARADA;

    companion object {
        fun fromRaw(raw: String?): LuminariaEstado {
            val cleaned = raw?.trim().orEmpty()
            return when {
                cleaned.equals(PENDIENTE.name, ignoreCase = true) -> PENDIENTE
                cleaned.equals(REPARADA.name, ignoreCase = true) -> REPARADA
                cleaned.startsWith("pend", ignoreCase = true) -> PENDIENTE
                cleaned.startsWith("repar", ignoreCase = true) -> REPARADA
                else -> REPARADA
            }
        }
    }
}
