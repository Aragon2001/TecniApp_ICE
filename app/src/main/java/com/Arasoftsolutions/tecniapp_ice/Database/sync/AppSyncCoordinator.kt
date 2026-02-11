package com.Arasoftsolutions.tecniapp_ice.Database.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AppSyncCoordinator {
    private val syncMutex = Mutex()

    suspend fun <T> runExclusive(block: suspend () -> T): T {
        return syncMutex.withLock { block() }
    }
}
