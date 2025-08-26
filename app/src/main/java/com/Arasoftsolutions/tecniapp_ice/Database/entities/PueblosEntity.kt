package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pueblos")
data class PueblosEntity(
    @PrimaryKey val id: Int,
    val nombre: String,
    val subregion: String
)
