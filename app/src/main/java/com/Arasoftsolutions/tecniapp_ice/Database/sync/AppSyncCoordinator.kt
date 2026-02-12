package com.Arasoftsolutions.tecniapp_ice.Database.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AppSyncCoordinator {
    private val syncMutex = Mutex()
    private var lastSyncAt: Long = 0L

    private const val MIN_INTERVAL_MS = 20_000L

    suspend fun <T> runExclusive(block: suspend () -> T): T {
        return syncMutex.withLock { block() }
    }

    suspend fun <T> runExclusiveDebounced(block: suspend () -> T): T? {
        return syncMutex.withLock {
            val now = System.currentTimeMillis()
            if (now - lastSyncAt < MIN_INTERVAL_MS) return null
            lastSyncAt = now
            block()
        }
    }
}
