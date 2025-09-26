package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.annotation.Keep
import androidx.room.*
import com.google.firebase.database.IgnoreExtraProperties

// ========== Entidad de Materiales ==========
@Keep
@IgnoreExtraProperties
@Entity(
    tableName = "materiales",
    indices = [Index(value = ["codigo"], unique = true)]
)
data class MaterialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val codigo: String = "",
    val descripcion: String = "",
    val unidad: String = ""
) {
    // Constructor vacío requerido por Firebase
    constructor() : this(0L, "", "", "")
}

// ========== Relación Avería-Material ==========
@Keep
@IgnoreExtraProperties
@Entity(
    tableName = "averia_material_uso",
    primaryKeys = ["averiaId", "materialId"]
)
data class AveriaMaterialUso(
    val averiaId: String = "",
    val materialId: Long = 0L,
    val cantidad: Double = 0.0
) {
    constructor() : this("", 0L, 0.0)
}
