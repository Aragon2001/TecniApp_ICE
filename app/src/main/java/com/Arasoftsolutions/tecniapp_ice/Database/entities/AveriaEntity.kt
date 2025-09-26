package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.annotation.Keep
import androidx.room.*
import com.google.firebase.database.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
@Entity(
    tableName = "averias",
    indices = [
        Index(value = ["caseId"], unique = true),
        Index(value = ["estado"]),
        Index(value = ["agenciaTag"]),
        Index(value = ["fechaInicioMillis"])
    ]
)
data class AveriaEntity(
    @PrimaryKey val caseId: String = "",
    val region: String? = null,
    val provincia: Int? = null,
    val agencia: String? = null,
    val nombreAgencia: String? = null,
    val nise: String? = null,
    val causa: String? = null,
    val observaciones: String? = null,
    val estado: String = "",
    val idEstadoAve: Int? = null,
    val idEstadoAranda: Int? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val clientesAfectados: String? = null,
    val fechaInicioMillis: Long = 0L,
    val horaInicioMillis: Long? = null,
    val horaFinalMillis: Long? = null,
    val agenciaTag: String = "",
    val vehiculoAsignado: String? = null,
    val tecnicoAsignadoUid: String? = null,
    val tecnicoAsignadoNombre: String? = null
) {
    constructor() : this(
        "", null, null, null, null, null, null, null,
        "", null, null, null, null, null, 0L, null, null,
        "", null, null, null
    )
}
