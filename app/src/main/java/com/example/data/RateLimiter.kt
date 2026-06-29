package com.example.data

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A highly robust, thread-safe Token Bucket Rate Limiter.
 * Can limit traffic per-client IP address to prevent denial-of-service,
 * API abuse, or performance degradation of the localized edge server.
 */
class TokenBucketRateLimiter(
    val capacity: Double = 50.0,
    val refillRatePerSecond: Double = 5.0
) {
    // Thread-safe map of client IPs to their token buckets
    private val clientBuckets = ConcurrentHashMap<String, TokenBucket>()

    // Statistics flows for UI monitoring or diagnostics
    private val _totalRequestsBlocked = MutableStateFlow(0L)
    val totalRequestsBlocked: StateFlow<Long> = _totalRequestsBlocked.asStateFlow()

    private val _activeClientsCount = MutableStateFlow(0)
    val activeClientsCount: StateFlow<Int> = _activeClientsCount.asStateFlow()

    /**
     * Inner class representing a single token bucket.
     */
    inner class TokenBucket {
        private var tokens: Double = capacity
        private var lastRefillTimeNanos: Long = System.nanoTime()

        @Synchronized
        fun tryConsume(amount: Double = 1.0): Boolean {
            val now = System.nanoTime()
            val elapsedSeconds = (now - lastRefillTimeNanos).toDouble() / 1_000_000_000.0
            lastRefillTimeNanos = now

            // Add newly generated tokens up to maximum capacity
            tokens = (tokens + elapsedSeconds * refillRatePerSecond).coerceAtMost(capacity)

            return if (tokens >= amount) {
                tokens -= amount
                true
            } else {
                false
            }
        }

        @Synchronized
        fun getTokensLeft(): Double {
            val now = System.nanoTime()
            val elapsedSeconds = (now - lastRefillTimeNanos).toDouble() / 1_000_000_000.0
            return (tokens + elapsedSeconds * refillRatePerSecond).coerceAtMost(capacity)
        }
    }

    /**
     * Checks if a request from a specific client IP is allowed.
     * Returns true if allowed, false if rate-limited.
     */
    fun tryAcquire(clientIp: String): Boolean {
        val bucket = clientBuckets.computeIfAbsent(clientIp) {
            _activeClientsCount.value = clientBuckets.size + 1
            TokenBucket()
        }

        val allowed = bucket.tryConsume(1.0)
        if (!allowed) {
            _totalRequestsBlocked.value += 1
        }
        return allowed
    }

    /**
     * Returns the current remaining tokens for a client IP.
     */
    fun getTokensLeft(clientIp: String): Double {
        return clientBuckets[clientIp]?.getTokensLeft() ?: capacity
    }

    /**
     * Resets rate limiter stats and active client buckets.
     */
    fun reset() {
        clientBuckets.clear()
        _activeClientsCount.value = 0
        _totalRequestsBlocked.value = 0L
    }
}
