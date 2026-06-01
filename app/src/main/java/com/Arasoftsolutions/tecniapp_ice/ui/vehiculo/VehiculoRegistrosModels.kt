package com.Arasoftsolutions.tecniapp_ice.ui.vehiculo

// ─────────────────────────────────────────────────────────────────────────────
// CORRECCIONES aplicadas en este archivo:
//
// FIX-4: RegistroDiarioEntity ahora contiene TODOS los campos ETM
//        (cerrado, kmFinal, actividad, cuenta, numeroCaso, lugar,
//        horasLaboradas, combustible, observaciones).
//
//        Antes el modelo era demasiado simple (solo fecha + valor + unidad),
//        lo que obligaba a mantener un segundo blob JSON en registrosDiariosJson.
//        Con estos campos completos, vehiculo_log se convierte en la ÚNICA
//        fuente de verdad para los registros ETM, y el blob deja de ser
//        necesario como fuente canónica (puede seguir existiendo como caché).
// ─────────────────────────────────────────────────────────────────────────────

data class RegistroDiarioEntity(
    val vehiculoId:     Int,
    val fecha:          String,
    val valor:          Double,                    // km inicio (valorInicial)
    val unidad:         String  = "km",
    val registradoEn:   Long    = System.currentTimeMillis(),
    val registradoPor:  String? = null,
    val syncStatus:     String  = "PENDING",

    // FIX-4: campos ETM que antes no existían en el modelo
    val cerrado:        Boolean = false,
    val kmFinal:        Double? = null,            // km fin (valorFinal)
    val actividad:      String? = null,
    val cuenta:         String? = null,
    val numeroCaso:     String? = null,
    val lugar:          String? = null,
    val horasLaboradas: Int?    = null,
    val combustible:    String? = null,
    val observaciones:  String? = null,
)

data class RegistroMantenimientoEntity(
    val vehiculoId:           Int,
    val tipoMantenimiento:    String,
    val valorActual:          Double,
    val unidad:               String  = "km",
    val observaciones:        String? = null,
    val proximoMantenimiento: Double,
    val creadoEn:             Long    = System.currentTimeMillis(),
    val syncStatus:           String  = "PENDING",
)