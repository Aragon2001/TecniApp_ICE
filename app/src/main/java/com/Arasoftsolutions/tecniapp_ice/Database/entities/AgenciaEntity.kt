package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.database.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
@Entity(tableName = "Agencias")
data class AgenciaEntity(
    @PrimaryKey val id: Int = 0,
    val nombre: String = "",
    val subregion: String = ""
) {
    constructor() : this(0, "", "")
}
