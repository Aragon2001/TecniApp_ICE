package com.Arasoftsolutions.tecniapp_ice.ui.vehiculo

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.Database.entities.RegistroDiarioEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.Database.utils.VehiculoPlacaUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.Arasoftsolutions.tecniapp_ice.ui.vehiculo.worker.VehiculoSyncWorker

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
    val tilKmFinal = dialogView.findViewById<TextInputLayout>(R.id.tilRegistroKmFinal)
    val tilCombustible = dialogView.findViewById<TextInputLayout>(R.id.tilRegistroCombustible)
    val tilHoras = dialogView.findViewById<TextInputLayout>(R.id.tilRegistroHoras)
    val tilActividad = dialogView.findViewById<TextInputLayout>(R.id.tilRegistroActividad)
    val tilCuenta = dialogView.findViewById<TextInputLayout>(R.id.tilRegistroCuenta)
    val tilCaso = dialogView.findViewById<TextInputLayout>(R.id.tilRegistroCaso)
    val tilLugar = dialogView.findViewById<TextInputLayout>(R.id.tilRegistroLugar)
    val tilObservaciones = dialogView.findViewById<TextInputLayout>(R.id.tilRegistroObservaciones)
    val switchCerrar = dialogView.findViewById<MaterialSwitch>(R.id.switchRegistroCerrar)
    val layoutCierre = dialogView.findViewById<android.view.View>(R.id.layoutRegistroCierre)
    val tvModoInfo = dialogView.findViewById<android.widget.TextView>(R.id.tvRegistroModoInfo)

    fun syncModoCierreUI(cerrar: Boolean) {
        layoutCierre.visibility = if (cerrar) android.view.View.VISIBLE else android.view.View.GONE
        tvModoInfo.text = if (cerrar) {
            getString(R.string.registro_pendiente_modo_cierre_info)
        } else {
            getString(R.string.registro_pendiente_modo_inicial_info)
        }
    }

    syncModoCierreUI(false)
    switchCerrar.setOnCheckedChangeListener { _, checked ->
        syncModoCierreUI(checked)
    }

    dialogView.findViewById<MaterialButton>(R.id.btnRegistroDialogRegistrar).setOnClickListener {
        val fechaTexto = tilFecha.editText?.text?.toString()?.trim().orEmpty()
        val valorTexto = tilValor.editText?.text?.toString()?.trim().orEmpty()
        val valor = valorTexto.replace(",", ".").toDoubleOrNull()
        if (valor == null || valor < 0) {
            tilValor.error = getString(R.string.mi_vehiculo_valor_invalido)
            return@setOnClickListener
        }
        tilValor.error = null
        val cerrarRegistro = switchCerrar.isChecked
        val kmFinalTexto = tilKmFinal.editText?.text?.toString()?.trim().orEmpty()
        val kmFinal = kmFinalTexto.takeIf { it.isNotBlank() }?.replace(",", ".")?.toDoubleOrNull()
        if (cerrarRegistro) {
            if (kmFinal == null || kmFinal < valor) {
                tilKmFinal.error = getString(R.string.averia_error_km_final_menor)
                return@setOnClickListener
            }
            tilKmFinal.error = null
        } else {
            tilKmFinal.error = null
        }

        val fecha = if (fechaTexto.isBlank()) {
            LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        } else {
            fechaTexto
        }

        val combustible = tilCombustible.editText?.text?.toString()?.trim()?.ifBlank { null }
        val horasTexto = tilHoras.editText?.text?.toString()?.trim().orEmpty()
        val horasLaboradas = horasTexto.takeIf { it.isNotBlank() }?.toIntOrNull()
        if (horasTexto.isNotBlank() && (horasLaboradas == null || horasLaboradas !in 1..10)) {
            tilHoras.error = getString(R.string.mi_vehiculo_horas_invalidas)
            return@setOnClickListener
        }
        tilHoras.error = null
        val actividad = tilActividad.editText?.text?.toString()?.trim()?.ifBlank { null }
        val cuenta = tilCuenta.editText?.text?.toString()?.trim()?.ifBlank { null }
        val numeroCaso = tilCaso.editText?.text?.toString()?.trim()?.ifBlank { null }
        val lugar = tilLugar.editText?.text?.toString()?.trim()?.ifBlank { null }
        val observaciones = tilObservaciones.editText?.text?.toString()?.trim()?.ifBlank { null }

        if (cerrarRegistro) {
            val camposCierreInvalidos =
                horasLaboradas == null || actividad.isNullOrBlank() || lugar.isNullOrBlank()
            if (camposCierreInvalidos) {
                tilHoras.error = if (horasLaboradas == null) getString(R.string.mi_vehiculo_horas_invalidas) else null
                tilActividad.error = if (actividad.isNullOrBlank()) getString(R.string.registro_pendiente_cierre_campos_requeridos) else null
                tilLugar.error = if (lugar.isNullOrBlank()) getString(R.string.registro_pendiente_cierre_campos_requeridos) else null
                return@setOnClickListener
            }
        } else {
            tilActividad.error = null
            tilLugar.error = null
        }

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
                    valorFinal = if (cerrarRegistro) kmFinal else null,
                    cerrado = cerrarRegistro && kmFinal != null,
                    registradoPor = usuario?.nombre,
                    combustible = combustible,
                    observaciones = observaciones,
                    actividad = actividad,
                    cuenta = cuenta,
                    numeroCaso = numeroCaso,
                    lugar = lugar,
                    horasLaboradas = horasLaboradas
                )
                val registrosActuales = parseRegistrosDiarios(vehiculo.registrosDiariosJson)
                    .filterNot { it.fecha == fecha }
                val nuevos = listOf(registro) + registrosActuales
                val registroJson = serializeRegistrosDiarios(nuevos)
                val valorActualizado = if (cerrarRegistro) (kmFinal ?: valor) else valor
                val kilometrajeActual = if (tipo.usaKilometraje) valorActualizado else vehiculo.kilometrajeActual
                val orimetroActual = if (tipo.usaOrimetro) valorActualizado else vehiculo.orimetroActual
                repo.actualizarRegistroDiarioVehiculo(
                    vehiculoId = vehiculo.id,
                    fecha = fecha,
                    inicial = valor,
                    final = if (cerrarRegistro) kmFinal else null,
                    cerrado = cerrarRegistro && kmFinal != null,
                    kilometrajeActual = kilometrajeActual,
                    orimetroActual = orimetroActual,
                    registrosJson = registroJson
                )
                repo.insertarRegistroDiario(
                    RegistroDiarioEntity(
                        vehiculoId = vehiculo.id,
                        fecha = fecha,
                        valor = valorActualizado,
                        unidad = tipo.unidadTexto,
                        registradoEn = System.currentTimeMillis(),
                        registradoPor = usuario?.nombre
                    )
                )
                VehiculoSyncWorker.triggerNow(requireContext())
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
