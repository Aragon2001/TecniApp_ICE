package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.database.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
@Entity(tableName = "medidores")
data class MedidorEntity(
    @PrimaryKey val medidorNumber: String = "", // Número del medidor (ej. "714")
    val calle: String? = null,                  // Calle (ej. "950")
    val cliente: String? = null,                // Cliente (ej. "GAS NACIONAL ZETA S.A.")
    val localizacion: Long? = null,           // Localización (ej. "34095000114")
    val metros: String? = null,                 // Metros (ej. "14")
    val poste: String? = null,                  // Poste (ej. "001")
    val pueblo: String? = null,                 // Pueblo (ej. "340")
    val subregion: String? = null               // Subregión
) {
    constructor() : this(
        medidorNumber = "",
        calle = null,
        cliente = null,
        localizacion = null,
        metros = null,
        poste = null,
        pueblo = null,
        subregion = null
    )
}
