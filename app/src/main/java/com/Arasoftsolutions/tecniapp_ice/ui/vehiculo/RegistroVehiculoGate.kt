package com.Arasoftsolutions.tecniapp_ice.ui.vehiculo

import android.content.Context
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.Database.utils.VehiculoPlacaUtils
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RegistroVehiculoGateState(
    val requiereRegistro: Boolean,
    val tieneVehiculo: Boolean
)

object RegistroVehiculoGate {
    private val formatoFecha = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

    suspend fun evaluar(context: Context): RegistroVehiculoGateState {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return RegistroVehiculoGateState(
            requiereRegistro = false,
            tieneVehiculo = false
        )
        val repo = RoomRepository.getInstance(context)
        val usuario = repo.obtenerUsuario(uid) ?: return RegistroVehiculoGateState(
            requiereRegistro = false,
            tieneVehiculo = false
        )
        val placa = usuario.placaVehiculo?.trim().orEmpty()
        if (placa.isBlank()) {
            return RegistroVehiculoGateState(requiereRegistro = true, tieneVehiculo = false)
        }
        val hoy = formatoFecha.format(Date())
        val placaLong = VehiculoPlacaUtils.parsePlacaLong(placa) ?: return RegistroVehiculoGateState(
            requiereRegistro = true,
            tieneVehiculo = false
        )
        val vehiculo = repo.obtenerVehiculoPorPlaca(placaLong)
        val registroHoy = vehiculo?.registroFecha == hoy && vehiculo.registroInicial != null
        return RegistroVehiculoGateState(
            requiereRegistro = !registroHoy,
            tieneVehiculo = true
        )
    }
}
