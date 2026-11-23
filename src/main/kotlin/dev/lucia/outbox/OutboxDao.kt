package dev.lucia.outbox

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OutboxDao {

    /** Returns the new row id, or -1 when [dedupKey] already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: OutboxEntry): Long

    /** Rows that are due at [now], oldest first. */
    @Query(
        """
        SELECT * FROM outbox
        WHERE state = 'PENDING' AND next_attempt_at <= :now
        ORDER BY created_at
        LIMIT :limit
        """,
    )
    suspend fun due(now: Long, limit: Int): List<OutboxEntry>

    @Query("UPDATE outbox SET state = 'IN_FLIGHT' WHERE id = :id")
    suspend fun markInFlight(id: Long)

    @Query(
        """
        UPDATE outbox
        SET attempts = attempts + 1, next_attempt_at = :nextAttemptAt, state = 'PENDING'
        WHERE id = :id
        """,
    )
    suspend fun reschedule(id: Long, nextAttemptAt: Long)

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun delete(id: Long)

    /** Call once at startup: rows left in flight belong to a process that died. */
    @Query("UPDATE outbox SET state = 'PENDING', next_attempt_at = 0 WHERE state = 'IN_FLIGHT'")
    suspend fun recoverInFlight(): Int

    @Query("SELECT COUNT(*) FROM outbox WHERE state = :state")
    suspend fun countByState(state: OutboxState): Int
}
