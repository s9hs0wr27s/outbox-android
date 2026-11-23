package dev.lucia.outbox

import kotlin.math.min
import kotlin.random.Random

/**
 * Capped exponential backoff with full jitter.
 *
 * Attempt `n` lands in `[ceiling / 2, ceiling)`, where the ceiling doubles from
 * [initialDelayMs] and stops at [maxDelayMs]. Jitter matters on radio wakeups:
 * without it every queued row retries on the same beat.
 */
class BackoffPolicy(
    private val initialDelayMs: Long = 30_000L,
    private val maxDelayMs: Long = MAX_DELAY_MS,
    private val maxAttempts: Int = 12,
    private val random: Random = Random.Default,
) {

    fun delayFor(attempt: Int): Long {
        val ceiling = min(maxDelayMs, initialDelayMs shl attempt.coerceIn(0, 30))
        val floor = ceiling / 2
        return if (floor >= ceiling) ceiling else random.nextLong(floor, ceiling)
    }

    /** True once [attempt] reaches [maxAttempts]; the entry is then dropped. */
    fun isExhausted(attempt: Int): Boolean = attempt >= maxAttempts

    private companion object {
        const val MAX_DELAY_MS = 6L * 60 * 60 * 1000
    }
}
