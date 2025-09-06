package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.room.*

@Entity(tableName = "materiales", indices = [Index(value = ["codigo"], unique = true)])
data class MaterialEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val codigo: String,
  val descripcion: String,
  val unidad: String
)

@Entity(tableName = "averia_material_uso", primaryKeys = ["averiaId","materialId"])
data class AveriaMaterialUso(
  val averiaId: String,
  val materialId: Long,
  val cantidad: Double
)
