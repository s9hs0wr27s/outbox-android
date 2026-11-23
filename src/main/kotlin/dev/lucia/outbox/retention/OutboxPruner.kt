package dev.lucia.outbox.retention

/**
 * Narrow pruning contract so retention logic can be tested without a Room
 * runtime; [dev.lucia.outbox.OutboxDao] is the production implementation.
 */
interface OutboxPruner {

    /** Deletes waiting rows created before [cutoff] and returns how many went. */
    suspend fun pruneStale(cutoff: Long): Int
}
