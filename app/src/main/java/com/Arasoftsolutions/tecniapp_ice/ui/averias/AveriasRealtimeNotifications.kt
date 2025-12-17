package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.content.Context
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AveriaEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

object AveriasRealtimeNotifications {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isStarted = false
    private var repository: AveriasRepository? = null
    private var roomRepository: RoomRepository? = null
    private val auth by lazy { FirebaseAuth.getInstance() }

    fun start(context: Context) {
        if (isStarted) return
        val appContext = context.applicationContext
        val db = AppDatabase.getInstance(appContext)
        val repo = AveriasRepository(db)
        repository = repo
        roomRepository = RoomRepository.getInstance(appContext)
        repo.startRealtimeListener(
            onNewAverias = { nuevas ->
                scope.launch { handleNewAverias(appContext, nuevas) }
            },
            suppressInitialNotification = true
        )
        isStarted = true
    }

    fun stop() {
        repository?.stopRealtimeListener()
        repository = null
        scope.coroutineContext.cancelChildren()
        isStarted = false
    }

    private suspend fun handleNewAverias(context: Context, nuevas: List<AveriaEntity>) {
        if (nuevas.isEmpty()) return
        val roomRepo = roomRepository ?: RoomRepository.getInstance(context)
        val usuario = auth.currentUser?.uid?.let { uid ->
            runCatching { roomRepo.obtenerUsuario(uid) }.getOrNull()
        }
        val regionObjetivo = usuario?.regionNombre?.takeIf { !it.isNullOrBlank() }
            ?: usuario?.region?.takeIf { !it.isNullOrBlank() }
        val regionNormalizada = regionObjetivo?.let { normalizeAveriaText(it) }
        val filters = AveriaNotificationPreferences.normalizedAgencies(context)
        val result = AveriasRepository.SyncResult(nuevas, emptyList())
        handleSyncResults(context, result, regionNormalizada, filters)
    }

    private suspend fun handleSyncResults(
        context: Context,
        result: AveriasRepository.SyncResult,
        regionNormalizada: String?,
        filters: Set<String>
    ) {
        val notificationsEnabled = AveriaNotificationPreferences.areNotificationsEnabled(context)
        if (!notificationsEnabled) return
        val porRegionNuevas = filterAveriasByRegion(result.nuevas, regionNormalizada)
        val nuevasFiltradas = filterAveriasByAgencies(porRegionNuevas, filters)
        if (nuevasFiltradas.isNotEmpty()) {
            AveriaNotificationDispatcher.notifyNewCases(context, nuevasFiltradas)
        }

        val porRegionResueltas = filterAveriasByRegion(result.resueltas, regionNormalizada)
        val resueltasFiltradas = filterAveriasByAgencies(porRegionResueltas, filters)
        if (resueltasFiltradas.isNotEmpty()) {
            AveriaNotificationDispatcher.notifyResolvedCases(context, resueltasFiltradas)
        }
    }
}
