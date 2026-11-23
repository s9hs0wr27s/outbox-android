# outbox-android

A small, offline-first write-behind queue backed by Room.

Mobile apps that write to a network API lose user actions the moment the radio
drops. `outbox-android` stores each pending mutation in a Room table, then
drains that table on a background dispatcher with capped exponential backoff.
Local writes stay instant, and the queue survives process death because the
state lives on disk.

## What it gives you

- Durable queue rows with a dedup key so a retried tap does not produce two
  server-side records.
- One state machine, three states: `PENDING` -> `IN_FLIGHT` -> gone, with
  `PENDING` and a bumped `next_attempt_at` on failure.
- Full-jitter exponential backoff, capped at six hours and twelve attempts;
  attempts that exhaust the policy are dropped so the queue cannot grow forever.
- A `recoverInFlight()` hook for crashes: rows left `IN_FLIGHT` by a dead
  process are reset to `PENDING` at startup (see issue #2 for the current gap).

## minSdk notes

`minSdk 21`, `compileSdk 35`. Room 2.7 needs API 21 plus
`androidx.room:room-ktx` for the coroutine DAO; on API 21-22 keep payloads
under 1 MB since SQLite `CursorWindow` limits still apply. Nothing here needs
WorkManager, so you can drive `drain()` from whatever scheduler you already run.

## Install

Add JitPack and the module:

```kotlin
repositories { maven { url = uri("https://jitpack.io") } }
dependencies { implementation("com.github.s9hs0wr27s:outbox-android:0.1.0") }
```

Declare the entity in your database and keep the converter:

```kotlin
@Database(entities = [OutboxEntry::class], version = 1)
@TypeConverters(OutboxConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun outbox(): OutboxDao
}
```

## Usage

```kotlin
val queue = OutboxQueue(dao)

// Local write is immediate; the row is the only network intent.
queue.enqueue(entity = "note", payload = noteJson, dedupKey = "note:${note.id}")

// Drain from your scheduler; `send` is where the HTTP call goes.
val delivered = queue.drain { entry -> api.push(entry.entity, entry.payload) }
```

`send` throwing is the failure signal: the entry is rescheduled, never lost
until the policy gives up. Return normally and the row is deleted in the same
transaction-less sequence.

## Screenshots

The sample screen (note list with a "queued" badge) and the offline toggle in
the debug drawer are the two shots worth capturing for the README; drop them in
`docs/` once the sample app ships.

## Layout

```
src/main/kotlin/dev/lucia/outbox/
  OutboxEntry.kt    entity, state enum, Room type converters
  OutboxDao.kt      insert/due/reschedule/delete plus in-flight recovery
  BackoffPolicy.kt  capped exponential backoff with full jitter
  OutboxQueue.kt    enqueue + drain orchestration
```

## License

MIT, see [LICENSE](LICENSE).
