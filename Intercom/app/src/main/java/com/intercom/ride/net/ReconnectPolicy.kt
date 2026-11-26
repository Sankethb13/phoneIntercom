package com.intercom.ride.net

/**
 * Backoff for automatic reconnection. Riders drift apart briefly at lights or
 * in traffic, so retries start fast and keep going for a few minutes.
 */
class ReconnectPolicy(
    private val initialMs: Long = 1_000,
    private val maxMs: Long = 8_000,
    val maxAttempts: Int = 40,
) {
    fun delayFor(attempt: Int): Long {
        if (attempt <= 1) return initialMs
        val exp = initialMs shl (attempt - 1).coerceAtMost(10)
        return exp.coerceAtMost(maxMs)
    }

    fun shouldRetry(attempt: Int): Boolean = attempt <= maxAttempts
}
