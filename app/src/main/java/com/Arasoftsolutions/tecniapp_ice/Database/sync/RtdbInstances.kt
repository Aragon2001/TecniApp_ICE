package com.Arasoftsolutions.tecniapp_ice.Database.sync

import com.google.firebase.database.FirebaseDatabase

object RtdbInstances {
    private const val DATOS_GENERALES_URL = "https://tecniapp-ice-datosgenerales.firebaseio.com"

    fun datosGenerales(): FirebaseDatabase = FirebaseDatabase.getInstance(DATOS_GENERALES_URL)
}
