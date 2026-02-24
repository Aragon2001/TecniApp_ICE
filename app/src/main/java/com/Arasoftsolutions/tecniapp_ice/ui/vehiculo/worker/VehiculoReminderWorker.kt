package com.Arasoftsolutions.tecniapp_ice.ui.vehiculo.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.ui.vehiculo.VehiculoPlacaUtils
import com.Arasoftsolutions.tecniapp_ice.notifications.VehiculoNotifications
import com.Arasoftsolutions.tecniapp_ice.ui.vehiculo.inferirTipoVehiculo
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val UMBRAL_MANTENIMIENTO_KM = 500.0
private const val UMBRAL_MANTENIMIENTO_HORAS = 25.0

class VehiculoReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repository = RoomRepository.getInstance(context)
    private val auth = FirebaseAuth.getInstance()
    private val formatoFecha = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

    override suspend fun doWork(): Result {
        val uid = auth.currentUser?.uid ?: return Result.success()
        val usuario = repository.obtenerUsuario(uid) ?: return Result.success()

        val hoy = formatoFecha.format(System.currentTimeMillis())
        val placa = usuario.placaVehiculo?.trim().orEmpty()
        val placaLong = VehiculoPlacaUtils.parsePlacaLong(placa) ?: return Result.success()
        val vehiculo = repository.obtenerVehiculoPorPlaca(placaLong) ?: return Result.success()
        if (repository.obtenerRegistroDiarioPorFecha(vehiculo.id, hoy) == null) {
            val placaVehiculo = vehiculo.placaRaw.ifBlank { vehiculo.vehiculoId }
            VehiculoNotifications.notifyRegistroPendiente(
                applicationContext,
                "Registro diario pendiente",
                "El vehículo $placaVehiculo aún no registra su lectura diaria."
            )
        }
        val tipo = inferirTipoVehiculo(vehiculo.tipo)

        val ultimoMantenimiento = repository.obtenerUltimoMantenimiento(vehiculo.id)
        val ultimoRegistro = repository.obtenerUltimoRegistroDiario(vehiculo.id)
        val valorActual = ultimoRegistro?.valor
            ?: if (tipo.usaKilometraje) vehiculo.kilometrajeActual else vehiculo.orimetroActual

        if (ultimoMantenimiento != null && valorActual != null) {
            val delta = ultimoMantenimiento.proximoMantenimiento - valorActual
            val umbral = if (tipo.usaKilometraje) UMBRAL_MANTENIMIENTO_KM else UMBRAL_MANTENIMIENTO_HORAS
            if (delta <= umbral) {
                val placaVehiculo = vehiculo.placaRaw.ifBlank { vehiculo.vehiculoId }
                val tipoAlerta = ultimoMantenimiento.tipoMantenimiento.ifBlank { "General" }
                val estadoResumen = if (delta <= 0) {
                    "Mantenimiento vencido"
                } else {
                    "Próximo mantenimiento"
                }
                val kilometrajeActual = String.format(Locale.getDefault(), "%.0f", valorActual)
                val restanteTexto = if (delta <= 0) {
                    "Vencido por ${String.format(Locale.getDefault(), "%.0f", kotlin.math.abs(delta))} ${tipo.unidadTexto}"
                } else {
                    "Restan ${String.format(Locale.getDefault(), "%.0f", delta)} ${tipo.unidadTexto}"
                }
                val mensaje = "$estadoResumen · $tipoAlerta · $restanteTexto"
                val detalleCompleto = """
                    Vehículo: $placaVehiculo
                    Lectura actual: $kilometrajeActual ${tipo.unidadTexto}
                    Tipo de alerta: $tipoAlerta
                    $restanteTexto
                """.trimIndent()
                VehiculoNotifications.notifyMantenimientoProximo(
                    applicationContext,
                    "Alerta de mantenimiento: $tipoAlerta",
                    mensaje,
                    detalleCompleto
                )
            }
        }

        return Result.success()
    }

    companion object {
        private const val UNIQUE_DAILY = "vehiculo_reminder_daily"
        private const val UNIQUE_NOW = "vehiculo_reminder_now"

        fun scheduleDaily(context: Context) {
            val delay = calculateInitialDelay()
            val request = PeriodicWorkRequestBuilder<VehiculoReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_DAILY,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun triggerNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<VehiculoReminderWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NOW,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        private fun calculateInitialDelay(): Long {
            val now = Calendar.getInstance()
            val next = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 7)
                set(Calendar.MINUTE, 30)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            return next.timeInMillis - now.timeInMillis
        }
    }
}
