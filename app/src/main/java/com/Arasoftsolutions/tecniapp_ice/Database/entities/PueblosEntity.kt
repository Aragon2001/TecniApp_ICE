package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.firebase.database.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
@Entity(
    tableName = "pueblos",
    indices = [
        Index(value = ["subregion_id_normalizado"])
    ]
)
data class PueblosEntity(
    @PrimaryKey val id: Int = 0,
    val nombre: String = "",
    val subregion: String = "",
    val subregion_id_normalizado: String = ""
) {
    constructor() : this(0, "", "", "")
}
