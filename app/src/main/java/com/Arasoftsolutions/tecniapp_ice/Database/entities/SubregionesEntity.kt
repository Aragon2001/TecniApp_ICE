package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.database.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
@Entity(tableName = "subregiones")
data class SubregionesEntity(
    @PrimaryKey val id: Int = 0,
    val nombre: String = ""
) {
    constructor() : this(0, "")
}
