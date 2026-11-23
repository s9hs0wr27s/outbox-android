package dev.lucia.outbox.retention

/**
 * Removes queue rows that stopped being deliverable, for example mutations for
 * an entity type retired server-side. Only rows still [PENDING][dev.lucia.outbox.OutboxState.PENDING]
 * and older than [ttlMs] are dropped; in-flight rows belong to a live drainer.
 *
 * A two-week default keeps a failed sync from being silently discarded after a
 * weekend outage while still bounding the table.
 */
class OutboxRetention(private val ttlMs: Long = DEFAULT_TTL_MS) {

    init {
        require(ttlMs > 0) { "ttlMs must be positive, was $ttlMs" }
    }

    fun cutoff(now: Long): Long = now - ttlMs

    suspend fun prune(pruner: OutboxPruner, now: Long): Int = pruner.pruneStale(cutoff(now))

    private companion object {
        const val DEFAULT_TTL_MS = 14L * 24 * 60 * 60 * 1000
    }
}
