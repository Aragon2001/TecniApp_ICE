package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.firebase.database.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
@Entity(
    tableName = "tecnicos",
    indices = [Index(value = ["nombre"], unique = false)]
)
data class TecnicoEntity(
    @PrimaryKey val cedula: String = "",
    val nombre: String = ""
) {
    constructor() : this("", "")
}
