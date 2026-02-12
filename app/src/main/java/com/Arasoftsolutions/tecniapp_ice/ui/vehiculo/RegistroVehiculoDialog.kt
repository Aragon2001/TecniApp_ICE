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
    val etFecha = tilFecha.editText

    val hoy = LocalDate.now()
    val fechaHoy = hoy.format(DateTimeFormatter.ISO_DATE)
    etFecha?.setText(fechaHoy)

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

    viewLifecycleOwner.lifecycleScope.launch {
        val repo = RoomRepository.getInstance(requireContext())
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val placa = uid?.let { repo.obtenerUsuario(it) }?.placaVehiculo?.trim().orEmpty()
        val placaLong = VehiculoPlacaUtils.parsePlacaLong(placa)
        val vehiculo = placaLong?.let { repo.obtenerVehiculoPorPlaca(it) }
        val tipo = inferirTipoVehiculo(vehiculo?.tipo)
        val esMaquinaria = tipo == TipoVehiculo.MAQUINARIA_PESADA
        tilValor.hint = if (esMaquinaria) {
            getString(R.string.vehiculo_registro_km_orimetro)
        } else {
            getString(R.string.vehiculo_registro_km)
        }
    }

    dialogView.findViewById<MaterialButton>(R.id.btnRegistroDialogRegistrar).setOnClickListener {
        val valorTexto = tilValor.editText?.text?.toString()?.trim().orEmpty()
        val valor = valorTexto.replace(",", ".").toDoubleOrNull()
        if (valor == null || valor < 1) {
            tilValor.error = getString(R.string.mi_vehiculo_valor_invalido)
            return@setOnClickListener
        }
        tilValor.error = null
        val cerrarRegistro = switchCerrar.isChecked
        val kmFinalTexto = tilKmFinal.editText?.text?.toString()?.trim().orEmpty()
        val kmFinal = kmFinalTexto.takeIf { it.isNotBlank() }?.replace(",", ".")?.toDoubleOrNull()
        if (cerrarRegistro) {
            if (kmFinal == null || kmFinal < 1 || kmFinal < valor) {
                tilKmFinal.error = getString(R.string.averia_error_km_final_menor)
                return@setOnClickListener
            }
            tilKmFinal.error = null
        } else {
            tilKmFinal.error = null
        }

        val fecha = fechaHoy

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

                val registrosActuales = parseRegistrosDiarios(vehiculo.registrosDiariosJson)
                val registroAyerSinCerrar = registrosActuales.firstOrNull {
                    !it.cerrado && it.fecha == hoy.minusDays(1).format(DateTimeFormatter.ISO_DATE)
                }
                val fechaRegistro = if (cerrarRegistro && registroAyerSinCerrar != null) {
                    registroAyerSinCerrar.fecha
                } else {
                    fecha
                }

                val registro = RegistroDiarioVehiculo(
                    fecha = fechaRegistro,
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
                val registrosFiltrados = registrosActuales
                    .filterNot { it.fecha == fechaRegistro }
                val nuevosCorregidos = listOf(registro) + registrosFiltrados

                val shouldOpenNewDay = cerrarRegistro && registroAyerSinCerrar != null && kmFinal != null
                val registrosConNuevoDia = if (shouldOpenNewDay) {
                    val nuevoDia = RegistroDiarioVehiculo(
                        fecha = fechaHoy,
                        valorInicial = kmFinal,
                        valorFinal = null,
                        cerrado = false,
                        registradoPor = usuario?.nombre,
                    )
                    listOf(nuevoDia) + nuevosCorregidos
                } else {
                    nuevosCorregidos
                }
                val registroJson = serializeRegistrosDiarios(registrosConNuevoDia)
                val valorActualizado = if (cerrarRegistro) (kmFinal ?: valor) else valor
                val kilometrajeActual = if (tipo.usaKilometraje) valorActualizado else vehiculo.kilometrajeActual
                val orimetroActual = if (tipo.usaOrimetro) valorActualizado else vehiculo.orimetroActual
                repo.actualizarRegistroDiarioVehiculo(
                    vehiculoId = vehiculo.id,
                    fecha = fechaRegistro,
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
                        fecha = fechaRegistro,
                        valor = valorActualizado,
                        unidad = tipo.unidadTexto,
                        registradoEn = System.currentTimeMillis(),
                        registradoPor = usuario?.nombre
                    )
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
