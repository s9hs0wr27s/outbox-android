package dev.lucia.outbox

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Enqueues local mutations and drains them in due order.
 *
 * Persistence is Room, so a row survives process death; the only in-memory
 * assumption is that a single drainer runs at a time. [recoverInFlight] is a
 * separate startup step rather than part of [drain] so a long drain does not
 * yank rows away from a second live drainer.
 */
class OutboxQueue(
    private val dao: OutboxDao,
    private val backoff: BackoffPolicy = BackoffPolicy(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /** Returns the row id, or -1 when [dedupKey] was already queued. */
    suspend fun enqueue(entity: String, payload: String, dedupKey: String = "$entity:$payload"): Long =
        withContext(dispatcher) {
            dao.insert(
                OutboxEntry(
                    dedupKey = dedupKey,
                    entity = entity,
                    payload = payload,
                    createdAt = clock(),
                ),
            )
        }

    /**
     * Delivers up to [limit] due entries, oldest first, and returns how many
     * [send] accepted. A throw from [send] reschedules the entry unless the
     * backoff policy is exhausted.
     */
    suspend fun drain(limit: Int = 32, send: suspend (OutboxEntry) -> Unit): Int =
        withContext(dispatcher) {
            var delivered = 0
            for (entry in dao.due(clock(), limit)) {
                dao.markInFlight(entry.id)
                val result = runCatching { send(entry) }
                if (result.isSuccess) {
                    dao.delete(entry.id)
                    delivered++
                    continue
                }
                val attempts = entry.attempts + 1
                if (backoff.isExhausted(attempts)) {
                    dao.delete(entry.id)
                } else {
                    dao.reschedule(entry.id, clock() + backoff.delayFor(attempts))
                }
            }
            delivered
        }

    /** Reset rows orphaned by a previous process. Run once at app start. */
    suspend fun recoverInFlight(): Int = withContext(dispatcher) { dao.recoverInFlight() }

    suspend fun pending(): Int = withContext(dispatcher) { dao.countByState(OutboxState.PENDING) }
}
