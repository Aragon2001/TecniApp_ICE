package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Locale

@Keep
@Entity(tableName = "vehiculo_kilometrajes")
data class VehiculoKilometrajeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placa: String,
    val placaNormalizada: String,
    val kilometrajeFinal: Double,
    val registradoEn: Long
) {
    companion object {
        fun normalizarPlaca(raw: String?): String? {
            val limpia = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return limpia.uppercase(Locale.getDefault())
                .replace("\\s+".toRegex(), "")
        }
    }
}
