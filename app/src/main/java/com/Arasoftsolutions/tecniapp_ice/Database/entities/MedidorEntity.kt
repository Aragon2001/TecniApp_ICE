package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medidores")
data class MedidorEntity(
    @PrimaryKey val medidorNumber: String, // Clave primaria: Número del medidor (ej. "714")
    val calle: String?,                    // Calle (ej. "950")
    val cliente: String?,                  // Cliente (ej. "GAS NACIONAL ZETA S.A.")
    val localizacion: String?,            // Localización (ej. "34095000114")
    val metros: String?,                   // Metros (ej. "14")
    val poste: String?,                    // Poste (ej. "001")
    val pueblo: String?,                   // Pueblo (ej. "340")
    // Subregión del medidor para filtrado.
    val subregion: String? = null
)
