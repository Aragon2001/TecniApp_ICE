package com.Arasoftsolutions.tecniapp_ice.ui.vehiculo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculoEntity
import com.Arasoftsolutions.tecniapp_ice.Database.entities.VehiculoLogEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.Database.sync.SyncStatus
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val INTERVALO_MANTENIMIENTO_KM = 5000.0
private const val INTERVALO_MANTENIMIENTO_HORAS = 250.0
private const val AVISO_MANTENIMIENTO_DELTA_KM = 500.0
private const val AVISO_MANTENIMIENTO_DELTA_HORAS = 25.0


data class MantenimientoCardUi(
    val titulo: String,
    val detalle: String,
    val estado: EstadoVehiculo
)

data class UsoMensualUi(
    val mes: String,
    val porcentaje: Int,
    val total: Double
)

data class MiVehiculoUiState(
    val vehiculo: VehiculoEntity? = null,
    val tipoVehiculo: TipoVehiculo = TipoVehiculo.LIVIANO,
    val estado: EstadoVehiculo = EstadoVehiculo.OPTIMO,
    val estadoMensaje: String = "",
    val valorActual: Double? = null,
    val unidad: String = "km",
    val mantenimientoCards: List<MantenimientoCardUi> = emptyList(),
    val usoMensual: List<UsoMensualUi> = emptyList(),
    val kmHoy: Double = 0.0,
    val mantenimientosMes: Int = 0,
    val alertasCount: Int = 0,
    val motivacion: String = "",
    val isLoading: Boolean = false
)

enum class EstadoVehiculo {
    OPTIMO,
    ATENCION,
    VENCIDO
}

class MiVehiculoViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = RoomRepository.getInstance(app)
    private val auth = FirebaseAuth.getInstance()
    private val vehiculoSyncService = VehiculoSyncService(repository)

    private val _uiState = MutableStateFlow(MiVehiculoUiState(isLoading = true))
    val uiState: StateFlow<MiVehiculoUiState> = _uiState.asStateFlow()

    private val _eventos = MutableSharedFlow<String>()
    val eventos: SharedFlow<String> = _eventos

    private val formatoFecha = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    private val formatoMes = SimpleDateFormat("MMM", Locale.getDefault())

    init {
        cargarDashboard()
    }

    private fun fechaHoy(): String = formatoFecha.format(System.currentTimeMillis())

    private fun cargarDashboard() {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val usuario = repository.obtenerUsuario(uid) ?: return@launch
            val placaStr = usuario.placaVehiculo?.trim().orEmpty()
            if (placaStr.isBlank()) {
                _uiState.value = MiVehiculoUiState(isLoading = false)
                return@launch
            }
            val placaLong = VehiculoPlacaUtils.parsePlacaLong(placaStr) ?: return@launch

            repository.observarVehiculoPorPlaca(placaLong)
                .filterNotNull()
                .distinctUntilChanged()
                .flatMapLatest { vehiculo ->
                    val tipo = inferirTipoVehiculo(vehiculo.tipo)
                    repository.observarRegistrosDiarios(vehiculo.id)
                        .combine(repository.observarMantenimientos(vehiculo.id)) { registros, mantenimientos ->
                            Triple(vehiculo, tipo, Pair(registros, mantenimientos))
                        }
                }
                .onStart { _uiState.value = _uiState.value.copy(isLoading = true) }
                .collect { (vehiculo, tipo, data) ->
                    val (registros, mantenimientos) = data
                    val unidad = tipo.unidadTexto
                    val valorActual = registros.firstOrNull()?.valor
                        ?: if (tipo.usaKilometraje) vehiculo.kilometrajeActual else vehiculo.orimetroActual

                    val ultimoMantenimiento = mantenimientos.firstOrNull()
                    val estado = calcularEstado(valorActual, ultimoMantenimiento, tipo)
                    val estadoMensaje = construirMensajeEstado(estado)
                    val cards = construirCards(ultimoMantenimiento, valorActual, unidad, estado)
                    val grafica = construirUsoMensual(registros)
                    val kmHoy = calcularUsoHoy(registros)
                    val mantenimientosMes = contarMantenimientosMes(mantenimientos)
                    val alertas = calcularAlertas(valorActual, ultimoMantenimiento, tipo)

                    _uiState.value = MiVehiculoUiState(
                        vehiculo = vehiculo,
                        tipoVehiculo = tipo,
                        estado = estado,
                        estadoMensaje = estadoMensaje,
                        valorActual = valorActual,
                        unidad = unidad,
                        mantenimientoCards = cards,
                        usoMensual = grafica,
                        kmHoy = kmHoy,
                        mantenimientosMes = mantenimientosMes,
                        alertasCount = alertas,
                        motivacion = "Cuidar tu vehículo es cuidar tu seguridad.",
                        isLoading = false
                    )
                }
        }
    }

    fun registrarMantenimiento(valor: Double, tipoMantenimiento: String, observaciones: String?) {
        viewModelScope.launch {
            val state = _uiState.value
            val vehiculo = state.vehiculo ?: return@launch
            if (valor < 0) {
                _eventos.emit("Ingresa un valor válido para continuar.")
                return@launch
            }
            val lecturaActual = state.valorActual ?: 0.0
            if (valor < lecturaActual) {
                _eventos.emit("No se permite registrar una lectura menor a la actual (${formatearValor(lecturaActual, state.unidad)}).")
                return@launch
            }

            val unidad = state.unidad
            val intervalo = if (state.tipoVehiculo.usaKilometraje) {
                INTERVALO_MANTENIMIENTO_KM
            } else {
                INTERVALO_MANTENIMIENTO_HORAS
            }
            val proximo = valor + intervalo

            val registroDiario = RegistroDiarioEntity(
                vehiculoId = vehiculo.id,
                fecha = fechaHoy(),
                valor = valor,
                unidad = unidad,
                registradoEn = System.currentTimeMillis(),
                registradoPor = auth.currentUser?.displayName,
                syncStatus = SyncStatus.PENDING
            )
            val registroMantenimiento = RegistroMantenimientoEntity(
                vehiculoId = vehiculo.id,
                tipoMantenimiento = tipoMantenimiento,
                valorActual = valor,
                unidad = unidad,
                observaciones = observaciones,
                proximoMantenimiento = proximo,
                creadoEn = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING
            )

            repository.insertarRegistroDiario(registroDiario)
            repository.insertarRegistroMantenimiento(registroMantenimiento)
            repository.actualizarMantenimiento(
                vehiculoId = vehiculo.id,
                mantenimientoUltimo = "${tipoMantenimiento} • ${formatearValor(valor, unidad)}",
                mantenimientoProximo = "${formatearValor(proximo, unidad)}"
            )

            syncAhora()
        }
    }

    private fun calcularEstado(
        valorActual: Double?,
        ultimoMantenimiento: RegistroMantenimientoEntity?,
        tipo: TipoVehiculo
    ): EstadoVehiculo {
        if (valorActual == null || ultimoMantenimiento == null) return EstadoVehiculo.ATENCION
        val delta = ultimoMantenimiento.proximoMantenimiento - valorActual
        val umbral = if (tipo.usaKilometraje) AVISO_MANTENIMIENTO_DELTA_KM else AVISO_MANTENIMIENTO_DELTA_HORAS
        return when {
            delta <= 0 -> EstadoVehiculo.VENCIDO
            delta <= umbral -> EstadoVehiculo.ATENCION
            else -> EstadoVehiculo.OPTIMO
        }
    }

    private fun construirMensajeEstado(estado: EstadoVehiculo): String = when (estado) {
        EstadoVehiculo.OPTIMO -> "Tu vehículo está en óptimas condiciones."
        EstadoVehiculo.ATENCION -> "Se acerca tu mantenimiento. Planea tu próxima parada."
        EstadoVehiculo.VENCIDO -> "Tu mantenimiento está vencido. Prioriza esta tarea."
    }

    private fun construirCards(
        ultimo: RegistroMantenimientoEntity?,
        valorActual: Double?,
        unidad: String,
        estado: EstadoVehiculo
    ): List<MantenimientoCardUi> {
        val ultimaLinea = ultimo?.let {
            "${it.tipoMantenimiento} • ${formatearValor(it.valorActual, unidad)}"
        } ?: "Aún no registras mantenimientos"
        val proximaLinea = ultimo?.let {
            "Próximo: ${formatearValor(it.proximoMantenimiento, unidad)}"
        } ?: "Agrega tu primer mantenimiento"
        val estadoDetalle = valorActual?.let { "Actual: ${formatearValor(it, unidad)}" } ?: "Sin lectura reciente"

        return listOf(
            MantenimientoCardUi(
                titulo = "Último mantenimiento",
                detalle = ultimaLinea,
                estado = estado
            ),
            MantenimientoCardUi(
                titulo = "Próximo mantenimiento",
                detalle = proximaLinea,
                estado = estado
            ),
            MantenimientoCardUi(
                titulo = "Lectura actual",
                detalle = estadoDetalle,
                estado = estado
            )
        )
    }

    private fun construirUsoMensual(registros: List<RegistroDiarioEntity>): List<UsoMensualUi> {
        if (registros.isEmpty()) return emptyList()
        val calendar = Calendar.getInstance()
        val months = (0..5).map { offset ->
            calendar.clone() as Calendar
        }.mapIndexed { index, cal ->
            cal.add(Calendar.MONTH, -index)
            cal
        }

        return months.map { cal ->
            val month = cal.get(Calendar.MONTH)
            val year = cal.get(Calendar.YEAR)
            val registrosMes = registros.filter { registro ->
                val parsed = runCatching { formatoFecha.parse(registro.fecha) }.getOrNull()
                val regCal = Calendar.getInstance().apply { if (parsed != null) time = parsed }
                regCal.get(Calendar.MONTH) == month && regCal.get(Calendar.YEAR) == year
            }.sortedBy { it.fecha }
            val total = if (registrosMes.size >= 2) {
                registrosMes.last().valor - registrosMes.first().valor
            } else {
                0.0
            }
            val porcentaje = ((total / 1000.0) * 100).coerceIn(0.0, 100.0).toInt()
            UsoMensualUi(
                mes = formatoMes.format(cal.time).uppercase(Locale.getDefault()),
                porcentaje = porcentaje,
                total = total
            )
        }.reversed()
    }


    private fun calcularUsoHoy(registros: List<RegistroDiarioEntity>): Double {
        val hoy = fechaHoy()
        val delDia = registros.filter { it.fecha == hoy }.sortedBy { it.registradoEn }
        if (delDia.isEmpty()) return 0.0
        val min = delDia.minOf { it.valor }
        val max = delDia.maxOf { it.valor }
        return (max - min).coerceAtLeast(0.0)
    }

    private fun contarMantenimientosMes(mantenimientos: List<RegistroMantenimientoEntity>): Int {
        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH)
        val year = cal.get(Calendar.YEAR)
        return mantenimientos.count { item ->
            val d = Date(item.creadoEn)
            val c = Calendar.getInstance().apply { time = d }
            c.get(Calendar.MONTH) == month && c.get(Calendar.YEAR) == year
        }
    }

    private fun calcularAlertas(
        valorActual: Double?,
        ultimoMantenimiento: RegistroMantenimientoEntity?,
        tipo: TipoVehiculo
    ): Int {
        if (valorActual == null || ultimoMantenimiento == null) return 1
        val delta = ultimoMantenimiento.proximoMantenimiento - valorActual
        val umbral = if (tipo.usaKilometraje) AVISO_MANTENIMIENTO_DELTA_KM else AVISO_MANTENIMIENTO_DELTA_HORAS
        return when {
            delta <= 0 -> 2
            delta <= umbral -> 1
            else -> 0
        }
    }


    fun crearVehiculo(placaRaw: String, subregion: String?, tipo: String, agencia: String) {
        viewModelScope.launch {
            val vehiculoId = placaRaw.trim().uppercase(Locale.ROOT)
            val vehiculo = VehiculoEntity(
                vehiculoId = vehiculoId,
                placaRaw = placaRaw.trim().uppercase(Locale.ROOT),
                subregion = subregion,
                tipo = tipo,
                agencia = agencia,
                kmActual = 0.0,
                kilometrajeActual = 0.0,
                updatedAt = System.currentTimeMillis()
            )
            repository.upsertVehiculo(vehiculo)
            vehiculoSyncService.syncVehiculo(vehiculo)
        }
    }

    fun registrarKm(km: Double) {
        viewModelScope.launch {
            val state = _uiState.value
            val vehiculo = state.vehiculo ?: return@launch
            val actual = state.valorActual ?: 0.0
            if (km < actual) {
                _eventos.emit("No se permite registrar una lectura menor a la actual (${formatearValor(actual, state.unidad)}).")
                return@launch
            }
            val now = System.currentTimeMillis()
            repository.addLogAndUpdateKm(
                VehiculoLogEntity(
                    logId = "km_${vehiculo.vehiculoId}_$now",
                    vehiculoId = vehiculo.vehiculoId,
                    tipo = "KM",
                    timestamp = now,
                    km = km,
                    payloadJson = "{}",
                    syncState = "PENDING"
                )
            )
            syncAhora()
        }
    }

    fun registrarDiario(valor: Double, observaciones: String?) {
        viewModelScope.launch {
            val state = _uiState.value
            val vehiculo = state.vehiculo ?: return@launch
            val actual = state.valorActual ?: 0.0
            if (valor < actual) {
                _eventos.emit("No se permite registrar una lectura menor a la actual (${formatearValor(actual, state.unidad)}).")
                return@launch
            }
            val now = System.currentTimeMillis()
            repository.addLogAndUpdateKm(
                VehiculoLogEntity(
                    logId = "diario_${vehiculo.vehiculoId}_$now",
                    vehiculoId = vehiculo.vehiculoId,
                    tipo = "DIARIO",
                    timestamp = now,
                    km = valor,
                    payloadJson = org.json.JSONObject().put("observaciones", observaciones.orEmpty()).toString(),
                    syncState = "PENDING"
                )
            )
            syncAhora()
        }
    }
    fun syncAhora() {
        viewModelScope.launch {
            _uiState.value.vehiculo?.let { vehiculoSyncService.syncVehiculo(it) }
        }
    }
    private fun formatearValor(valor: Double, unidad: String): String {
        return String.format(Locale.getDefault(), "%.0f %s", valor, unidad)
    }
}
