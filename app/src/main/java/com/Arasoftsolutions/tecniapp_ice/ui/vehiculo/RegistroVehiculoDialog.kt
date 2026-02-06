package com.Arasoftsolutions.tecniapp_ice.ui.vehiculo

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.Database.utils.VehiculoPlacaUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun Fragment.showRegistroVehiculoPendienteDialog(
    onRegistroGuardado: () -> Unit,
    onNoVehiculo: () -> Unit
) {
    val dialogView = layoutInflater.inflate(R.layout.dialog_registro_vehiculo_pendiente, null)
    val dialog = MaterialAlertDialogBuilder(requireContext())
        .setView(dialogView)
        .setCancelable(false)
        .create()

    val tilFecha = dialogView.findViewById<TextInputLayout>(R.id.tilRegistroFecha)
    val tilValor = dialogView.findViewById<TextInputLayout>(R.id.tilRegistroValor)
    val tilCombustible = dialogView.findViewById<TextInputLayout>(R.id.tilRegistroCombustible)
    val tilObservaciones = dialogView.findViewById<TextInputLayout>(R.id.tilRegistroObservaciones)

    dialogView.findViewById<MaterialButton>(R.id.btnRegistroDialogRegistrar).setOnClickListener {
        val fechaTexto = tilFecha.editText?.text?.toString()?.trim().orEmpty()
        val valorTexto = tilValor.editText?.text?.toString()?.trim().orEmpty()
        val valor = valorTexto.replace(",", ".").toDoubleOrNull()
        if (valor == null || valor < 0) {
            tilValor.error = getString(R.string.mi_vehiculo_valor_invalido)
            return@setOnClickListener
        }
        tilValor.error = null

        val fecha = if (fechaTexto.isBlank()) {
            LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        } else {
            fechaTexto
        }

        val combustible = tilCombustible.editText?.text?.toString()?.trim()?.ifBlank { null }
        val observaciones = tilObservaciones.editText?.text?.toString()?.trim()?.ifBlank { null }

        viewLifecycleOwner.lifecycleScope.launch {
            val repo = RoomRepository.getInstance(requireContext())
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            val usuario = uid?.let { repo.obtenerUsuario(it) }
            val placa = usuario?.placaVehiculo?.trim().orEmpty()
            val placaLong = VehiculoPlacaUtils.parsePlacaLong(placa)
            val vehiculo = placaLong?.let { repo.obtenerVehiculoPorPlaca(it) }
            if (vehiculo != null) {
                val tipo = inferirTipoVehiculo(vehiculo.tipo)
                val registro = RegistroDiarioVehiculo(
                    fecha = fecha,
                    valorInicial = valor,
                    registradoPor = usuario?.nombre,
                    combustible = combustible,
                    observaciones = observaciones
                )
                val registrosActuales = parseRegistrosDiarios(vehiculo.registrosDiariosJson)
                    .filterNot { it.fecha == fecha }
                val nuevos = listOf(registro) + registrosActuales
                val registroJson = serializeRegistrosDiarios(nuevos)
                val kilometrajeActual = if (tipo.usaKilometraje) valor else vehiculo.kilometrajeActual
                val orimetroActual = if (tipo.usaOrimetro) valor else vehiculo.orimetroActual
                repo.actualizarRegistroDiarioVehiculo(
                    vehiculoId = vehiculo.id,
                    fecha = fecha,
                    inicial = valor,
                    final = null,
                    cerrado = false,
                    kilometrajeActual = kilometrajeActual,
                    orimetroActual = orimetroActual,
                    registrosJson = registroJson
                )
            }
            dialog.dismiss()
            onRegistroGuardado()
        }
    }
    dialogView.findViewById<MaterialButton>(R.id.btnRegistroDialogSinVehiculo).setOnClickListener {
        dialog.dismiss()
        onNoVehiculo()
    }
    dialog.show()
}
