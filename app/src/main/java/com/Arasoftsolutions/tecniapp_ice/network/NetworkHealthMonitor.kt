package com.Arasoftsolutions.tecniapp_ice.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Singleton que clasifica la calidad real de la conexión de red en 5 estados.
 *
 * Usa NET_CAPABILITY_VALIDATED (portal cautivo resuelto) + una sonda TCP a 8.8.8.8:53
 * para medir latencia real. Detecta inestabilidad por desconexiones rápidas repetidas.
 *
 * Los Workers y la pantalla de login deben consultar [health] o [isUsable]/[isStable]
 * antes de intentar operaciones de red críticas.
 */
class NetworkHealthMonitor private constructor(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cm = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _health = MutableStateFlow(NetworkHealth.OFFLINE)
    val health: StateFlow<NetworkHealth> = _health.asStateFlow()

    private var lastLostMs = 0L
    private var rapidLossCount = 0

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            scope.launch { probe() }
        }

        override fun onLost(network: Network) {
            val now = System.currentTimeMillis()
            if (now - lastLostMs < 10_000L) rapidLossCount++ else rapidLossCount = 0
            lastLostMs = now
            _health.value = NetworkHealth.OFFLINE
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            scope.launch { probe() }
        }
    }

    init {
        runCatching {
            val req = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(req, callback)
        }
        scope.launch {
            probe()
            while (isActive) {
                delay(30_000L)
                probe()
            }
        }
    }

    private suspend fun probe() {
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }

        if (network == null || caps == null ||
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        ) {
            _health.value = NetworkHealth.OFFLINE
            return
        }

        // El sistema ya validó acceso a internet (resolvió captive portal)
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            _health.value = NetworkHealth.CONNECTED_NO_INTERNET
            return
        }

        // Conexión válida pero con historial de desconexiones rápidas
        if (rapidLossCount >= 2) {
            rapidLossCount = 0
            _health.value = NetworkHealth.UNSTABLE
            return
        }

        val latencyMs = measureLatency()
        _health.value = when {
            latencyMs < 0 -> NetworkHealth.CONNECTED_NO_INTERNET
            latencyMs > 1500 -> NetworkHealth.SLOW
            else -> NetworkHealth.STABLE
        }
    }

    private suspend fun measureLatency(): Long = withContext(Dispatchers.IO) {
        return@withContext try {
            val t = System.currentTimeMillis()
            Socket().use { it.connect(InetSocketAddress("8.8.8.8", 53), 2000) }
            System.currentTimeMillis() - t
        } catch (_: Exception) {
            -1L
        }
    }

    /** True si la conexión es utilizable (SLOW, UNSTABLE o STABLE). */
    val isUsable: Boolean get() = _health.value.isUsable

    /** True solo si la conexión es STABLE (apta para login y descarga completa). */
    val isStable: Boolean get() = _health.value.isStable

    companion object {
        @Volatile
        private var INSTANCE: NetworkHealthMonitor? = null

        fun getInstance(context: Context): NetworkHealthMonitor =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkHealthMonitor(context.applicationContext).also { INSTANCE = it }
            }
    }
}
