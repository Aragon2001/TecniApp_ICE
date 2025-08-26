package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subregiones")
data class SubregionesEntity(
    @PrimaryKey val id: Int,
    val nombre: String
)
