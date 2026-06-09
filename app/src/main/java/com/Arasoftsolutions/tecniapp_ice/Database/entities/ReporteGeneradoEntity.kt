package com.Arasoftsolutions.tecniapp_ice.Database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reporte_generado",
    indices = [
        Index("usuarioUid"),
        Index("fechaGeneracion")
    ]
)
data class ReporteGeneradoEntity(
    @PrimaryKey val id: String,
    val tipoReporte: String,
    val formato: String,          // "PDF" o "EXCEL"
    val nombreArchivo: String,
    val rutaLocal: String,
    val fechaGeneracion: Long,
    val enviadoCorreo: Boolean = false,
    val correoDestino: String? = null,
    val fechaEnvio: Long? = null,
    val usuarioUid: String = "",
    val errorEnvio: Boolean = false
)