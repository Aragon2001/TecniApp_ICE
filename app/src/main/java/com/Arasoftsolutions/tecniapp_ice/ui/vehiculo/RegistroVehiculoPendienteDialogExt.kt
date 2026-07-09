package com.Arasoftsolutions.tecniapp_ice.ui.vehiculo

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import android.view.ViewGroup
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputLayout
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
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

    val tvFechaHeader = dialogView.findViewById<android.widget.TextView>(R.id.tvRegistroFechaHeader)
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
    val diaSemana = hoy.format(DateTimeFormatter.ofPattern("EEEE", java.util.Locale("es", "CR")))
    tvFechaHeader?.text = "Hoy · ${diaSemana.replaceFirstChar { it.uppercase() }}"
    var registroPendienteCierre: RegistroDiarioVehiculo? = null

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
        val estadoEtm = uid?.let { repo.obtenerEstadoEtmVehiculo(it) }
        val vehiculo = estadoEtm?.vehiculo
        registroPendienteCierre = estadoEtm?.registroPendienteCierre

        // FIX 2: Firebase como fuente primaria del estado ETM
        val usuario = uid?.let { repo.obtenerUsuario(it) }
        val placaStr = usuario?.placaVehiculo?.trim().orEmpty()
        var fechaEtmFirebase: String? = null
        var kmInicioFirebase: Double? = null
        var cerradoFirebase = true

        if (placaStr.isNotBlank()) {
            try {
                val etmSnap = withTimeout(6_000L) {
                    FirebaseDatabase
                        .getInstance("https://tecniapp-ice-datosgenerales.firebaseio.com")
                        .reference.child("vehiculo_etm").child(placaStr).get().await()
                }

                fechaEtmFirebase = etmSnap.children
                    .mapNotNull { it.key }
                    .filter { it.isNotBlank() }
                    .sortedDescending()
                    .firstOrNull()

                if (fechaEtmFirebase != null) {
                    val apertura = etmSnap.child(fechaEtmFirebase).child("apertura")
                    cerradoFirebase = apertura.child("cerrado").getValue(Boolean::class.java) ?: true
                    kmInicioFirebase = apertura.child("kmInicio").getValue(Double::class.java)
                        ?: apertura.child("km").getValue(Double::class.java)
                }
            } catch (e: Exception) {
                Log.w("ETM", "No se pudo leer estado ETM de Firebase, usando Room: ${e.message}")
            }
        }

        val hayPendienteFirebase = fechaEtmFirebase != null &&
            fechaEtmFirebase < fechaHoy && !cerradoFirebase

        if (hayPendienteFirebase && registroPendienteCierre == null) {
            registroPendienteCierre = RegistroDiarioVehiculo(
                fecha        = fechaEtmFirebase!!,
                valorInicial = kmInicioFirebase,
                cerrado      = false
            )
        }

        if (!isAdded) return@launch

        val tipo = inferirTipoVehiculo(vehiculo?.tipo)
        val esMaquinaria = tipo == TipoVehiculo.MAQUINARIA_PESADA
        tilValor.hint = if (esMaquinaria) {
            getString(R.string.vehiculo_registro_km_orimetro)
        } else {
            getString(R.string.vehiculo_registro_km)
        }
        // Use kmActual (canonical field) for pre-filling the initial-km field in open mode.
        val ultimaLectura = vehiculo?.let {
            if (tipo.usaKilometraje) it.kmActual.takeIf { km -> km > 0 }
            else it.orimetroActual?.takeIf { or -> or > 0 }
        }
        if (tilValor.editText?.text.isNullOrBlank() && ultimaLectura != null) {
            val lecturaTexto = String.format(java.util.Locale.getDefault(), "%.0f", ultimaLectura)
            tilValor.editText?.setText(lecturaTexto)
            tilValor.editText?.setSelection(lecturaTexto.length)
        }
        registroPendienteCierre?.let { pendiente ->
            switchCerrar.isChecked = true
            switchCerrar.isEnabled = false
            tilFecha.editText?.setText(pendiente.fecha)
            // Initial km is read-only when closing — it was recorded when the ETM was opened.
            tilValor.isEnabled = false
            val lecturaInicial = pendiente.valorInicial
            if (lecturaInicial != null) {
                tilValor.editText?.setText(
                    String.format(java.util.Locale.getDefault(), "%.0f", lecturaInicial)
                )
            }
            // Pre-fill final km with the current vehicle reading as a convenience default.
            val kmActualVehiculo = vehiculo?.let {
                if (tipo.usaKilometraje) it.kmActual.takeIf { km -> km > 0 }
                else it.orimetroActual?.takeIf { or -> or > 0 }
            }
            kmActualVehiculo?.let { kmRef ->
                val kmRefTexto = String.format(java.util.Locale.getDefault(), "%.0f", kmRef)
                tilKmFinal.editText?.setText(kmRefTexto)
                tilKmFinal.editText?.setSelection(kmRefTexto.length)
            }
            tvModoInfo.text = "Tienes un cierre ETM pendiente. Debes registrarlo para continuar."
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
        val cierreForzado = registroPendienteCierre != null
        val cerrarRegistro = cierreForzado || switchCerrar.isChecked
        val kmFinalTexto = tilKmFinal.editText?.text?.toString()?.trim().orEmpty()
        val kmFinal = kmFinalTexto.takeIf { it.isNotBlank() }?.replace(",", ".")?.toDoubleOrNull()
        val valorInicialPendiente = registroPendienteCierre?.valorInicial
        val valorInicialRegistro = valorInicialPendiente ?: valor
        if (cerrarRegistro) {
            if (kmFinal == null || kmFinal < 1 || kmFinal < valorInicialRegistro) {
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
            try {
                val repo = RoomRepository.getInstance(requireContext())
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                val usuario = uid?.let { repo.obtenerUsuario(it) }
                val placa = usuario?.placaVehiculo?.trim().orEmpty()
                val placaLong = VehiculoPlacaUtils.parsePlacaLong(placa)
                val vehiculo = placaLong?.let { repo.obtenerVehiculoPorPlaca(it) }
                if (vehiculo != null) {
                    val tipo = inferirTipoVehiculo(vehiculo.tipo)
                    val lecturaActual = if (tipo.usaKilometraje) vehiculo.kmActual
                                        else (vehiculo.orimetroActual ?: vehiculo.kmActual)
                    if (!cierreForzado && valor < lecturaActual) {
                        tilValor.error = getString(R.string.averia_error_km_inicio_menor_ultimo, String.format(java.util.Locale.getDefault(), "%.0f", lecturaActual))
                        return@launch  // dialog permanece abierto para que el usuario corrija
                    }
                    tilValor.error = null

                    val registrosActuales = parseRegistrosDiarios(vehiculo.registrosDiariosJson)
                    val registroSinCerrarPrevio = registroPendienteCierre ?: registrosActuales.firstOrNull {
                        !it.cerrado && it.fecha < fechaHoy
                    }
                    val fechaRegistro = if (cerrarRegistro && registroSinCerrarPrevio != null) {
                        registroSinCerrarPrevio.fecha
                    } else {
                        fecha
                    }

                    val registro = RegistroDiarioVehiculo(
                        fecha = fechaRegistro,
                        valorInicial = valorInicialRegistro,
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

                    // Only auto-open a new day when closing a PREVIOUS day's ETM, not today's.
                    val shouldOpenNewDay = cerrarRegistro && registroSinCerrarPrevio != null &&
                        registroSinCerrarPrevio.fecha < fechaHoy && kmFinal != null
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
                    if (!cierreForzado && valorActualizado < lecturaActual) {
                        tilValor.error = getString(R.string.averia_error_km_inicio_menor_ultimo, String.format(java.util.Locale.getDefault(), "%.0f", lecturaActual))
                        return@launch  // dialog permanece abierto para que el usuario corrija
                    }
                    val kilometrajeActual = if (tipo.usaKilometraje) valorActualizado else vehiculo.kilometrajeActual
                    val orimetroActual = if (tipo.usaOrimetro) valorActualizado else vehiculo.orimetroActual
                    repo.actualizarRegistroDiarioVehiculo(
                        vehiculoId = vehiculo.id,
                        fecha = fechaRegistro,
                        inicial = valorInicialRegistro,
                        final = if (cerrarRegistro) kmFinal else null,
                        cerrado = cerrarRegistro && kmFinal != null,
                        kilometrajeActual = kilometrajeActual,
                        orimetroActual = orimetroActual,
                        registrosJson = registroJson
                    )
                    // FIX 3: insertarRegistroDiario con campos ETM completos
                    repo.insertarRegistroDiario(
                        RegistroDiarioEntity(
                            vehiculoId     = vehiculo.id,
                            fecha          = fechaRegistro,
                            valor          = valorInicialRegistro,
                            unidad         = tipo.unidadTexto,
                            registradoEn   = System.currentTimeMillis(),
                            registradoPor  = usuario?.nombre,
                            cerrado        = cerrarRegistro && kmFinal != null,
                            kmFinal        = if (cerrarRegistro) kmFinal else null,
                            actividad      = actividad,
                            cuenta         = cuenta,
                            numeroCaso     = numeroCaso,
                            lugar          = lugar,
                            horasLaboradas = horasLaboradas,
                            combustible    = combustible,
                            observaciones  = observaciones
                        )
                    )

                    // FIX 1: Escribir cierre/apertura en Firebase (datosgenerales tiene persistencia
                    // offline → await() resuelve inmediatamente aunque no haya red, y sube al volver).
                    val etmDb = FirebaseDatabase
                        .getInstance("https://tecniapp-ice-datosgenerales.firebaseio.com")
                        .reference

                    if (cerrarRegistro) {
                        val closingUpdates = hashMapOf<String, Any?>(
                            "vehiculo_etm/$placa/$fechaRegistro/apertura/cerrado"        to true,
                            "vehiculo_etm/$placa/$fechaRegistro/apertura/kmFinal"        to kmFinal,
                            "vehiculo_etm/$placa/$fechaRegistro/apertura/combustible"    to combustible,
                            "vehiculo_etm/$placa/$fechaRegistro/apertura/horasLaboradas" to horasLaboradas,
                            "vehiculo_etm/$placa/$fechaRegistro/apertura/actividad"      to actividad,
                            "vehiculo_etm/$placa/$fechaRegistro/apertura/cuenta"         to cuenta,
                            "vehiculo_etm/$placa/$fechaRegistro/apertura/numeroCaso"     to numeroCaso,
                            "vehiculo_etm/$placa/$fechaRegistro/apertura/lugar"          to lugar,
                            "vehiculo_etm/$placa/$fechaRegistro/apertura/observaciones"  to observaciones,
                            "vehiculo_etm/$placa/$fechaRegistro/apertura/cierreEn"       to ServerValue.TIMESTAMP,
                            "vehiculos/$placa/kmActual"                                  to (kmFinal ?: valor),
                            "vehiculos/$placa/updatedAt"                                 to ServerValue.TIMESTAMP
                        )
                        try {
                            etmDb.updateChildren(closingUpdates).await()
                        } catch (e: Exception) {
                            Log.e("ETM", "Error escribiendo cierre en Firebase: ${e.message}", e)
                        }
                    } else {
                        try {
                            etmDb.child("vehiculo_etm/$placa/$fechaHoy/apertura")
                                .updateChildren(mapOf(
                                    "cerrado"  to false,
                                    "creadoEn" to ServerValue.TIMESTAMP,
                                    "kmInicio" to valor,
                                    "km"       to valor,
                                    "tecnico"  to (usuario?.nombre ?: "")
                                )).await()
                        } catch (e: Exception) {
                            Log.e("ETM", "Error escribiendo apertura en Firebase: ${e.message}", e)
                        }
                    }

                    // FIX 4: Si se cierra un día previo, escribir también apertura del día nuevo en Firebase
                    if (shouldOpenNewDay) {
                        try {
                            etmDb.child("vehiculo_etm/$placa/$fechaHoy/apertura")
                                .updateChildren(mapOf(
                                    "cerrado"  to false,
                                    "creadoEn" to ServerValue.TIMESTAMP,
                                    "kmInicio" to kmFinal,
                                    "km"       to kmFinal,
                                    "tecnico"  to (usuario?.nombre ?: "")
                                )).await()
                        } catch (e: Exception) {
                            Log.e("ETM", "Error creando apertura nuevo día en Firebase: ${e.message}", e)
                        }
                    }
                } else if (!placa.isBlank()) {
                    // Vehículo no encontrado en Room aunque el usuario tiene placa asignada.
                    // Informa sin bloquear — se puede deber a que aún no se sincronizó.
                    if (isAdded) android.widget.Toast.makeText(
                        requireContext(),
                        "Vehículo \"$placa\" no encontrado localmente. El ETM se guardará al sincronizar.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                // Ruta exitosa (con o sin vehículo): cerrar dialog y navegar.
                dialog.dismiss()
                onRegistroGuardado()
            } catch (e: Exception) {
                // Excepción inesperada (JSON inválido, Room error, etc.) — no dejar el dialog
                // congelado: mostrar mensaje, cerrar y dejar pasar (los datos se guardan cuando
                // sea posible, ya que datosgenerales tiene persistencia offline).
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("ETM", "Error inesperado al guardar registro ETM", e)
                if (isAdded) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Error al guardar el registro: ${e.localizedMessage ?: "error inesperado"}.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    dialog.dismiss()
                    onRegistroGuardado()
                }
            }
        }
    }
    dialogView.findViewById<MaterialButton>(R.id.btnRegistroDialogSinVehiculo).setOnClickListener {
        dialog.dismiss()
        onNoVehiculo()
    }
    dialog.show()
    dialog.window?.setLayout(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
}
