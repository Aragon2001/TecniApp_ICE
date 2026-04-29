package com.Arasoftsolutions.tecniapp_ice.Database.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AppSyncCoordinator {
    private val defaultMutex = Mutex()
    private val syncMutexByKey = mutableMapOf<String, Mutex>()
    private val lastSyncAtByKey = mutableMapOf<String, Long>()

    private const val MIN_INTERVAL_MS = 20_000L

    suspend fun <T> runExclusive(block: suspend () -> T): T {
        return defaultMutex.withLock { block() }
    }

    suspend fun <T> runExclusive(key: String, block: suspend () -> T): T {
        val mutex = mutexForKey(key)
        return mutex.withLock { block() }
    }

    suspend fun <T> runExclusiveDebounced(block: suspend () -> T): T? {
        return defaultMutex.withLock {
            val now = System.currentTimeMillis()
            val lastSyncAt = lastSyncAtByKey[DEFAULT_KEY] ?: 0L
            if (now - lastSyncAt < MIN_INTERVAL_MS) return null
            lastSyncAtByKey[DEFAULT_KEY] = now
            block()
        }
    }

    suspend fun <T> runExclusiveDebounced(
        key: String,
        minIntervalMs: Long = MIN_INTERVAL_MS,
        block: suspend () -> T
    ): T? {
        val mutex = mutexForKey(key)
        return mutex.withLock {
            val now = System.currentTimeMillis()
            val lastSyncAt = lastSyncAtByKey[key] ?: 0L
            if (now - lastSyncAt < minIntervalMs) return null
            lastSyncAtByKey[key] = now
            block()
        }
    }

    private fun mutexForKey(key: String): Mutex = synchronized(syncMutexByKey) {
        syncMutexByKey.getOrPut(key) { Mutex() }
    }

    private const val DEFAULT_KEY = "__global__"
}
