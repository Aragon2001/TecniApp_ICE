package com.Arasoftsolutions.tecniapp_ice.Database.Tablas

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agencias")
data class AgenciaEntity(
    @PrimaryKey val id: Int,       // Clave primaria: ID de la agencia (ej. 1)
    val nombre: String,            // Nombre de la agencia (ej. "Agencia Central")
    val subregion: String          // Subregión de la agencia (ej. "Subregión Norte")
)