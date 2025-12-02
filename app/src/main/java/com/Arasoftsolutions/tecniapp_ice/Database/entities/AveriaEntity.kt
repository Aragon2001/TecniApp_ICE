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
    val atencionHoraInicioMillis: Long? = null,
    val atencionHoraFinalMillis: Long? = null,
    val kilometrajeInicio: Double? = null,
    val kilometrajeFinal: Double? = null,
    val agenciaTag: String = "",
    val vehiculoAsignado: String? = null,
    val tecnicoAsignadoUid: String? = null,
    val tecnicoAsignadoNombre: String? = null,
    val atendidoPorUid: String? = null,
    val atendidoPorNombre: String? = null,
    val materialesTexto: String? = null,

    val materialesDetalleJson: String? = null, // ✅ campo nuevo unificado
    val tecnicosAtendieronJson: String? = null,

    val cliente: String? = null,
    val localizacion: String? = null,
    val direccion: String? = null,
    val tipoAfectacion: String? = null,
    val numeroMedidor: String? = null,
    val medidorCalle: String? = null,
    val medidorPueblo: String? = null,
    val medidorMetros: String? = null,
    val medidorPoste: String? = null,
    val isSynced: Boolean = true,
    val lastUpdated: Long = 0L
) {
    constructor() : this(
        caseId = "",
        region = null,
        provincia = null,
        agencia = null,
        nombreAgencia = null,
        nise = null,
        causa = null,
        observaciones = null,
        estado = "",
        idEstadoAve = null,
        idEstadoAranda = null,
        lat = null,
        lng = null,
        clientesAfectados = null,
        fechaInicioMillis = 0L,
        horaInicioMillis = null,
        horaFinalMillis = null,
        atencionHoraInicioMillis = null,
        atencionHoraFinalMillis = null,
        kilometrajeInicio = null,
        kilometrajeFinal = null,
        agenciaTag = "",
        vehiculoAsignado = null,
        tecnicoAsignadoUid = null,
        tecnicoAsignadoNombre = null,
        atendidoPorUid = null,
        atendidoPorNombre = null,
        materialesTexto = null,

        materialesDetalleJson = null, // ✅ también en constructor vacío

        tecnicosAtendieronJson = null,
        cliente = null,
        localizacion = null,
        direccion = null,
        tipoAfectacion = null,
        numeroMedidor = null,
        medidorCalle = null,
        medidorPueblo = null,
        medidorMetros = null,
        medidorPoste = null,
        isSynced = true,
        lastUpdated = 0L
    )
}
