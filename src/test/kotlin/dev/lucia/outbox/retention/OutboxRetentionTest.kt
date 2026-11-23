package dev.lucia.outbox.retention

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class OutboxRetentionTest {

    private class RecordingPruner : OutboxPruner {
        var cutoff: Long? = null

        override suspend fun pruneStale(cutoff: Long): Int {
            this.cutoff = cutoff
            return 3
        }
    }

    @Test
    fun pruneComputesCutoffFromNowAndTtl() = runTest {
        val pruner = RecordingPruner()

        val removed = OutboxRetention(ttlMs = 1_000).prune(pruner, now = 10_000)

        assertEquals(3, removed)
        assertEquals(9_000L, pruner.cutoff)
    }

    @Test
    fun defaultTtlIsFourteenDays() = runTest {
        val pruner = RecordingPruner()

        OutboxRetention().prune(pruner, now = 1_000_000_000_000)

        assertEquals(1_000_000_000_000 - 14L * 24 * 60 * 60 * 1000, pruner.cutoff)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveTtl() {
        OutboxRetention(ttlMs = 0)
    }
}
