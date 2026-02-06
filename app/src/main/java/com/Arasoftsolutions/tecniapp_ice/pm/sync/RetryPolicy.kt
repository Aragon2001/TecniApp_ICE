package com.Arasoftsolutions.tecniapp_ice.pm.sync

import kotlin.math.min
import kotlin.random.Random

class RetryPolicy(
    private val baseDelayMs: Long = 5_000,
    private val maxDelayMs: Long = 10 * 60_000,
    private val jitterMs: Long = 2_000
) {
    fun nextDelay(retryCount: Int): Long {
        val exponential = baseDelayMs * (1L shl min(retryCount, 6))
        val jitter = Random.nextLong(0, jitterMs + 1)
        return min(exponential + jitter, maxDelayMs)
    }
}
