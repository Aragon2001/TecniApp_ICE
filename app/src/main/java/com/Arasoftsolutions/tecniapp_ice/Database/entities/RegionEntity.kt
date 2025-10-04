package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.database.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
@Entity(tableName = "regiones")
data class RegionEntity(
    @PrimaryKey val id: String = "",
    val nombre: String = ""
) {
    constructor() : this("", "")
}
