package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.database.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
@Entity(tableName = "agencias")
data class AgenciaEntity(
    @PrimaryKey val id: String = "",
    val nombre: String = "",
    val regionId: String? = null,
    val subregion: String? = null
) {
    constructor() : this("", "", null, null)
}
