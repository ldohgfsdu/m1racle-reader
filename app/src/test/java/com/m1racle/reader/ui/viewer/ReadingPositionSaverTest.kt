package com.m1racle.reader.ui.viewer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioral tests for latest-wins position persistence with bounded final
 * flush. Failure mode under test: every scroll-settle previously launched an
 * uncancellable write (NonCancellable + IO), so rapid settles piled up
 * stale uncancellable DB writes that kept running after scope cancellation.
 */
class ReadingPositionSaverTest {

    private class Harness(dispatcher: kotlinx.coroutines.CoroutineDispatcher) {
        val scope = CoroutineScope(dispatcher)
        val writes = mutableListOf<Triple<Int, Int, Float>>()
        var inFlight = 0
        var maxInFlight = 0

        val saver = ReadingPositionSaver<ScrollPosition>(scope) { (index, offset, progress) ->
            inFlight++
            maxInFlight = maxOf(maxInFlight, inFlight)
            try {
                delay(100) // suspend like a Room write
                writes += Triple(index, offset, progress)
            } finally {
                inFlight--
            }
        }
    }

    @Test
    fun position2WhilePosition1InFlight_finalStateIsPosition2() = runTest {
        // Case 1 + 2: latest wins — the cancelled older write must not be the
        // final state; only position 2 is persisted.
        val dispatcher = StandardTestDispatcher(testScheduler)
        val h = Harness(dispatcher)
        h.saver.save(ScrollPosition(1, 0, 0.1f))
        dispatcher.scheduler.advanceTimeBy(10) // let save(1) start its write
        h.saver.save(ScrollPosition(2, 5, 0.2f)) // cancels job 1, starts job 2
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(Triple(2, 5, 0.2f)), h.writes)
    }

    @Test
    fun rapidSettles_neverPileUpUncancellableWrites() = runTest {
        // Case 3: 10 rapid settles → at most one in-flight write at a time.
        val dispatcher = StandardTestDispatcher(testScheduler)
        val h = Harness(dispatcher)
        repeat(10) { i -> h.saver.save(ScrollPosition(i, i * 10, i / 10f)) }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, h.maxInFlight)
        // Only the newest position survives (all earlier jobs cancelled).
        assertEquals(listOf(Triple(9, 90, 0.9f)), h.writes)
    }

    @Test
    fun flushFinal_writesLastKnown_whenInFlightOrCancelled() = runTest {
        // Case 4: document close while a save is in flight → the final flush
        // persists the last known position (bounded NonCancellable write).
        val dispatcher = StandardTestDispatcher(testScheduler)
        val h = Harness(dispatcher)
        h.saver.save(ScrollPosition(3, 7, 0.4f)) // not yet run
        h.scope.cancel()
        h.saver.flushFinal(CoroutineScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()
        assertEquals(listOf(Triple(3, 7, 0.4f)), h.writes)
    }

    @Test
    fun flushFinal_skips_whenLatestAlreadyPersisted() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val h = Harness(dispatcher)
        h.saver.save(ScrollPosition(1, 0, 0.1f))
        dispatcher.scheduler.advanceUntilIdle() // write completed
        h.saver.flushFinal(CoroutineScope(Dispatchers.Unconfined))
        advanceUntilIdle()
        // Exactly one write — the flush must not duplicate it.
        assertEquals(listOf(Triple(1, 0, 0.1f)), h.writes)
    }

    @Test
    fun lateUncancellableOldWrite_doesNotOverwriteNewerPosition() = runTest {
        // Adversarial: the storage write does NOT respond to cancellation
        // (blocking IO / committed transaction). The old write completes LATE,
        // after the newer save; the final persisted state must still be the
        // newer position — never the stale one (single-writer serial commit:
        // position2 is committed after position1, so it wins on disk).
        val dispatcher = StandardTestDispatcher(testScheduler)
        val writes = mutableListOf<Triple<Int, Int, Float>>()
        val saver = ReadingPositionSaver<ScrollPosition>(CoroutineScope(dispatcher)) { (index, offset, progress) ->
            try {
                delay(200) // long storage transaction
            } catch (_: CancellationException) {
                // Simulate a transaction that ignores cancellation and commits anyway.
            }
            writes += Triple(index, offset, progress)
        }
        saver.save(ScrollPosition(1, 0, 0.1f))
        dispatcher.scheduler.advanceTimeBy(10) // write(1) started, will NOT abort
        saver.save(ScrollPosition(2, 5, 0.2f)) // cancels job 1 (ignored by write), starts job 2
        dispatcher.scheduler.advanceUntilIdle()
        // Old write committed late, but the newer write committed after it:
        assertEquals(2, writes.size)
        assertEquals(Triple(2, 5, 0.2f), writes.last())
    }

    @Test
    fun afterScopeCancellation_noFurtherWrites() = runTest {
        // Case 5: scope cancelled → save() must not start any new write.
        val dispatcher = StandardTestDispatcher(testScheduler)
        val h = Harness(dispatcher)
        h.scope.cancel()
        h.saver.save(ScrollPosition(99, 0, 0.9f))
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(h.writes.isEmpty())
    }

    @Test
    fun failedSave_isRetriedOnFinalFlush() = runTest {
        var attempts = 0
        val saved = mutableListOf<Int>()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val saver = ReadingPositionSaver<ScrollPosition>(scope) { (index, _, _) ->
            if (++attempts == 1) throw java.io.IOException("disk temporarily unavailable")
            saved += index
        }
        saver.save(ScrollPosition(7, 0, 0.7f))
        advanceUntilIdle()
        saver.flushFinal(scope)
        advanceUntilIdle()
        assertEquals(listOf(7), saved)
        assertEquals(2, attempts)
    }

    @Test
    fun simultaneousLifecycleFlushes_commitOnlyOnce() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val h = Harness(dispatcher)
        h.saver.save(ScrollPosition(3, 7, 0.4f))
        h.scope.cancel()
        val flushScope = CoroutineScope(dispatcher)
        repeat(3) { h.saver.flushFinal(flushScope) }
        advanceUntilIdle()
        assertEquals(listOf(Triple(3, 7, 0.4f)), h.writes)
    }

    @Test
    fun backgroundFlush_cannotOverwritePositionAfterResume() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val writes = mutableListOf<Int>()
        val saver = ReadingPositionSaver<ScrollPosition>(CoroutineScope(dispatcher)) { (index, _, _) ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                delay(if (index == 1) 500 else 10)
                writes += index
            }
        }
        saver.save(ScrollPosition(1, 0, 0.1f))
        testScheduler.advanceTimeBy(1)
        saver.flushFinal(CoroutineScope(dispatcher))
        saver.save(ScrollPosition(2, 0, 0.2f))
        advanceUntilIdle()
        assertEquals(listOf(1, 2), writes)
    }
}
