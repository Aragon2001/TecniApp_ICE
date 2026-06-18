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

data class InventarioConVehiculo(
    @Embedded val item: InventarioItemEntity,
    @ColumnInfo(name = "vehiculoPlaca") val vehiculoPlaca: Long?,
    @ColumnInfo(name = "vehiculoAgencia") val vehiculoAgencia: String?
)

@Keep
@IgnoreExtraProperties
@Entity(tableName = "inventario_movimiento_averia")
data class InventarioMovimientoAveriaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val averiaId: String,
    val vehiculoId: Int,
    val materialCodigo: String,
    val cantidad: Double,
    val fechaRegistro: Long,
    val tecnicoUid: String? = null,
    val tecnicoNombre: String? = null
)
