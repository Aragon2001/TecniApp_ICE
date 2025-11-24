package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.firebase.database.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
@Entity(
    tableName = "inventario_material",
    indices = [Index(value = ["vehiculoId", "codigoMaterial"], unique = true)]
)
data class InventarioItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val vehiculoId: Int,
    val codigoMaterial: String,
    val descripcionMaterial: String = "",
    val cantidadDisponible: Double = 0.0
)

@Keep
@IgnoreExtraProperties
@Entity(tableName = "luminaria_reparacion")
data class LuminariaReparacionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val vehiculoId: Int,
    val localizacion: String,
    val codigoMaterial: String,
    val descripcionMaterial: String = "",
    val cantidadUtilizada: Double = 0.0,
    val fechaRegistro: Long = System.currentTimeMillis()
)

data class InventarioConVehiculo(
    @Embedded val item: InventarioItemEntity,
    @ColumnInfo(name = "vehiculoPlaca") val vehiculoPlaca: Long?,
    @ColumnInfo(name = "vehiculoAgencia") val vehiculoAgencia: String?
)
