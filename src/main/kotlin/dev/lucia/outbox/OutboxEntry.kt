package dev.lucia.outbox

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

/** Lifecycle of a single queued mutation. */
enum class OutboxState { PENDING, IN_FLIGHT, FAILED }

/**
 * One durable write intent. [dedupKey] is unique, so re-enqueuing the same
 * logical mutation (a retried tap, a replayed editor save) is a no-op.
 */
@Entity(
    tableName = "outbox",
    indices = [
        Index(value = ["dedup_key"], unique = true),
        Index(value = ["next_attempt_at"]),
    ],
)
data class OutboxEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "dedup_key") val dedupKey: String,
    @ColumnInfo(name = "entity") val entity: String,
    @ColumnInfo(name = "payload") val payload: String,
    @ColumnInfo(name = "state") val state: OutboxState = OutboxState.PENDING,
    @ColumnInfo(name = "attempts") val attempts: Int = 0,
    @ColumnInfo(name = "next_attempt_at") val nextAttemptAt: Long = 0L,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** Room stores [OutboxState] as its ordinal-free name. */
class OutboxConverters {
    @TypeConverter
    fun toState(value: String): OutboxState = OutboxState.valueOf(value)

    @TypeConverter
    fun fromState(state: OutboxState): String = state.name
}
