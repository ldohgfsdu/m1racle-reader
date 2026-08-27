package com.m1racle.reader.pdf

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0A1 final-flush seal correction: race-condition tests.
 *
 * Uses [StandardTestDispatcher] + [TestCoroutineScheduler] so that
 * `applicationScope.launch` does not run until [advanceUntilIdle] is called.
 * This lets us reproduce the exact ordering that broke the old
 * `flush(final) + close()` pattern: the close ran synchronously before the
 * flush coroutine ever entered the mutex, so the flush coroutine observed
 * `closed` and bailed out, dropping the final snapshot.
 *
 * The new [FinalPdfProgressFlusher.sealAndFlushFinal] atomically transitions
 * to SEALED_FLUSHING and submits a final write that owns its own CLOSED
 * transition — it cannot be cancelled by a subsequent "close" because there
 * is no separate close call.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FinalPdfProgressFlusherTest {

    private fun makeSnapshot(
        gen: Long,
        page: Int = 0,
        scale: Float = 1f,
        docId: String = "doc-1",
        pageCount: Int = 100,
    ): FinalPdfProgressSnapshot {
        val pos = ReadingPosition(
            pageIndex = page,
            intraPageFraction = 0.5f,
            source = ReadingPositionSource.USER_CONTINUOUS_SCROLL,
            generation = gen,
        )
        val session = PdfViewportSessionState(
            version = 1,
            readingMode = PdfReadingMode.default(),
            pageIndex = page,
            pageAnchorXFraction = 0f,
            pageAnchorYFraction = 0f,
            viewportAnchorXFraction = 0.5f,
            viewportAnchorYFraction = 0.5f,
            scale = scale,
            generation = gen,
        )
        return FinalPdfProgressSnapshot(
            documentId = docId,
            readingPosition = pos,
            viewportSession = session,
            pageCount = pageCount,
            generation = gen,
        )
    }

    private fun makeNullSnapshot(gen: Long, docId: String = "doc-1"): FinalPdfProgressSnapshot =
        FinalPdfProgressSnapshot(
            documentId = docId,
            readingPosition = null,
            viewportSession = null,
            pageCount = 0,
            generation = gen,
        )

    /**
     * Build a flusher backed by a [StandardTestDispatcher] so launches do not
     * run until [advanceUntilIdle] is invoked. Returns the scope, scheduler,
     * and a thread-safe write log.
     */
    private class FlusherHarness(
        val scheduler: TestCoroutineScheduler,
        val scope: CoroutineScope,
        val flusher: FinalPdfProgressFlusher,
        val written: MutableList<Long>,
        val writeMutex: Mutex,
        val writeFnBlockers: MutableMap<Long, CompletableDeferred<Unit>>, // gen -> gate
    )

    /**
     * Build a flusher backed by a [StandardTestDispatcher] sharing the
     * [runTest] scheduler, so launches do not run until [advanceUntilIdle]
     * is invoked.
     */
    private fun makeHarness(
        testScheduler: TestCoroutineScheduler,
        writeFn: (suspend (FinalPdfProgressSnapshot) -> Unit)? = null,
    ): FlusherHarness {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val written = mutableListOf<Long>()
        val writeMutex = Mutex()
        val writeFnBlockers = mutableMapOf<Long, CompletableDeferred<Unit>>()
        val actualWriteFn: suspend (FinalPdfProgressSnapshot) -> Unit = if (writeFn != null) writeFn else { snapshot ->
            // Record entry into writeFn BEFORE awaiting the gate, so tests can
            // observe that the writer reached writeFn for this generation.
            writeMutex.withLock { written.add(snapshot.generation) }
            writeFnBlockers[snapshot.generation]?.await()
        }
        val flusher = FinalPdfProgressFlusher(scope, actualWriteFn)
        return FlusherHarness(testScheduler, scope, flusher, written, writeMutex, writeFnBlockers)
    }

    /**
     * Install a gate that blocks writeFn for [gen] until the returned deferred
     * is completed. The gate starts un-completed (blocking).
     */
    private fun holdGen(h: FlusherHarness, gen: Long): CompletableDeferred<Unit> {
        val gate = CompletableDeferred<Unit>()
        h.writeFnBlockers[gen] = gate
        return gate
    }

    // ─── 1. flushThenImmediateClose_oldImplementationLosesSnapshot ────────────
    //
    // Reproduces the old failing pattern: submit a normal flush, do NOT let
    // the launch run, then seal. Under the old `flush + close` design the
    // close would set closed=true before the flush launch entered the mutex,
    // so the final snapshot was dropped. With sealAndFlushFinal the final
    // snapshot is guaranteed to reach the mutex and write.
    @Test
    fun flushThenImmediateClose_oldImplementationLosesSnapshot() = runTest {
        val h = makeHarness(testScheduler)
        // Normal flush (gen=1) — launch queued but not executed.
        h.flusher.flush(makeSnapshot(gen = 1, page = 5))
        // Immediately seal with final (gen=2) — no advance between.
        h.flusher.sealAndFlushFinal(makeSnapshot(gen = 2, page = 7))
        advanceUntilIdle()

        // The final snapshot (gen=2) MUST have written. The normal flush
        // (gen=1) is rejected inside the mutex because state != OPEN after
        // seal. Either way, gen=2 wins.
        assertTrue("final snapshot gen=2 must have written", 2L in h.written)
        assertEquals(2L, h.flusher.flushedGenerationForTest())
        assertEquals(FinalPdfProgressFlusher.FlusherState.CLOSED, h.flusher.stateForTest())
        h.scope.cancel()
    }

    // ─── 2. sealAndFlushFinal_rejectsLaterNormalFlush ─────────────────────────
    @Test
    fun sealAndFlushFinal_rejectsLaterNormalFlush() = runTest {
        val h = makeHarness(testScheduler)
        h.flusher.sealAndFlushFinal(makeSnapshot(gen = 3, page = 4))
        advanceUntilIdle() // final writes, state → CLOSED
        // Late normal flush must be rejected.
        h.flusher.flush(makeSnapshot(gen = 10, page = 99))
        advanceUntilIdle()

        assertEquals(listOf(3L), h.written)
        assertEquals(3L, h.flusher.flushedGenerationForTest())
        assertEquals(FinalPdfProgressFlusher.FlusherState.CLOSED, h.flusher.stateForTest())
        h.scope.cancel()
    }

    // ─── 3. sealAndFlushFinal_runsWhenLaunchWasInitiallyQueued ────────────────
    //
    // sealAndFlushFinal submits a launch that does not run immediately (test
    // dispatcher). The launch must still run after advanceUntilIdle and write
    // the final snapshot.
    @Test
    fun sealAndFlushFinal_runsWhenLaunchWasInitiallyQueued() = runTest {
        val h = makeHarness(testScheduler)
        h.flusher.sealAndFlushFinal(makeSnapshot(gen = 5, page = 12))
        // State is SEALED_FLUSHING but launch has not run yet.
        assertEquals(FinalPdfProgressFlusher.FlusherState.SEALED_FLUSHING, h.flusher.stateForTest())
        assertTrue("no write before advance", h.written.isEmpty())

        advanceUntilIdle()

        assertEquals(listOf(5L), h.written)
        assertEquals(5L, h.flusher.flushedGenerationForTest())
        assertEquals(FinalPdfProgressFlusher.FlusherState.CLOSED, h.flusher.stateForTest())
        h.scope.cancel()
    }

    // ─── 4. onStopGen1_disposeGen2_finalGen3_onlyGen3Wins ─────────────────────
    //
    // onStop (gen=1) and dispose (gen=2) are normal flushes; onCleared fires
    // final (gen=3) via sealAndFlushFinal. The final write must land; earlier
    // generations may or may not write depending on ordering, but the final
    // flushedGeneration must be 3 and the state must be CLOSED.
    @Test
    fun onStopGen1_disposeGen2_finalGen3_onlyGen3Wins() = runTest {
        val h = makeHarness(testScheduler)
        h.flusher.flush(makeSnapshot(gen = 1, page = 5))     // onStop
        advanceUntilIdle() // gen=1 writes
        h.flusher.flush(makeSnapshot(gen = 2, page = 5))     // dispose
        advanceUntilIdle() // gen=2 writes
        h.flusher.sealAndFlushFinal(makeSnapshot(gen = 3, page = 5)) // onCleared final
        advanceUntilIdle()

        assertTrue("gen=1 wrote", 1L in h.written)
        assertTrue("gen=2 wrote", 2L in h.written)
        assertTrue("gen=3 (final) wrote", 3L in h.written)
        assertEquals(3L, h.flusher.flushedGenerationForTest())
        assertEquals(FinalPdfProgressFlusher.FlusherState.CLOSED, h.flusher.stateForTest())
        h.scope.cancel()
    }

    // ─── 5. oldWriteAlreadyInMutex_finalArrivesLater_finalOverridesOld ────────
    //
    // A normal flush (gen=1) has already entered the mutex and is writing
    // (blocked on a held lock). Then sealAndFlushFinal(gen=2) is called. The
    // final launch queues behind the mutex. When gen=1 finishes and releases
    // the mutex, the final enters and writes gen=2. Final must write.
    @Test
    fun oldWriteAlreadyInMutex_finalArrivesLater_finalOverridesOld() = runTest {
        val h = makeHarness(testScheduler)
        val holdGen1 = holdGen(h, 1L)
        h.flusher.flush(makeSnapshot(gen = 1, page = 3))
        // Run until gen=1's writeFn is blocked inside the mutex.
        advanceUntilIdle()
        assertTrue("gen=1 entered mutex and is blocked", h.flusher.flushedGenerationForTest() == -1L)

        // Now seal with final gen=2 while gen=1 still holds the mutex.
        h.flusher.sealAndFlushFinal(makeSnapshot(gen = 2, page = 8))
        assertEquals(FinalPdfProgressFlusher.FlusherState.SEALED_FLUSHING, h.flusher.stateForTest())

        // Release gen=1 — it finishes writing, releases flusher mutex.
        holdGen1.complete(Unit)
        advanceUntilIdle()

        assertTrue("gen=1 wrote", 1L in h.written)
        assertTrue("gen=2 (final) wrote", 2L in h.written)
        assertEquals(2L, h.flusher.flushedGenerationForTest())
        assertEquals(FinalPdfProgressFlusher.FlusherState.CLOSED, h.flusher.stateForTest())
        h.scope.cancel()
    }

    // ─── 6. finalAlreadyInMutex_oldArrivesLater_oldRejectedByGeneration ───────
    //
    // final (gen=2) enters the mutex first (blocked writing). A normal
    // flush(gen=1) is submitted afterwards. When gen=2 finishes, gen=1's
    // launch enters the mutex but is rejected by the state guard (state !=
    // OPEN) — even before the generation guard. Either way gen=1 must NOT
    // overwrite gen=2.
    @Test
    fun finalAlreadyInMutex_oldArrivesLater_oldRejectedByGeneration() = runTest {
        val h = makeHarness(testScheduler)
        val holdFinal = holdGen(h, 2L)
        h.flusher.sealAndFlushFinal(makeSnapshot(gen = 2, page = 8))
        advanceUntilIdle() // final launch enters mutex, blocks on holdFinal
        assertEquals(FinalPdfProgressFlusher.FlusherState.SEALED_FLUSHING, h.flusher.stateForTest())

        // Submit a normal flush with lower gen while final is in the mutex.
        h.flusher.flush(makeSnapshot(gen = 1, page = 3))
        // Release the final write.
        holdFinal.complete(Unit)
        advanceUntilIdle()

        assertTrue("final gen=2 wrote", 2L in h.written)
        assertFalse("old gen=1 must NOT write (state guard / generation guard)", 1L in h.written)
        assertEquals(2L, h.flusher.flushedGenerationForTest())
        assertEquals(FinalPdfProgressFlusher.FlusherState.CLOSED, h.flusher.stateForTest())
        h.scope.cancel()
    }

    // ─── 7. sealAndFlushFinalCalledTwice_writesOnlyOnceHighestGeneration ──────
    //
    // Two sealAndFlushFinal calls back-to-back (before any launch runs). The
    // higher generation replaces pendingFinal; only one write occurs, with
    // the higher generation.
    @Test
    fun sealAndFlushFinalCalledTwice_writesOnlyOnceHighestGeneration() = runTest {
        val h = makeHarness(testScheduler)
        h.flusher.sealAndFlushFinal(makeSnapshot(gen = 5, page = 1))
        // Second call with higher gen — should replace pendingFinal.
        h.flusher.sealAndFlushFinal(makeSnapshot(gen = 10, page = 2))
        assertEquals(FinalPdfProgressFlusher.FlusherState.SEALED_FLUSHING, h.flusher.stateForTest())
        // No writes yet (launch not advanced).
        assertTrue(h.written.isEmpty())

        advanceUntilIdle()

        assertEquals("only one write", 1, h.written.size)
        assertEquals("write is the highest generation", 10L, h.written[0])
        assertEquals(10L, h.flusher.flushedGenerationForTest())
        assertEquals(FinalPdfProgressFlusher.FlusherState.CLOSED, h.flusher.stateForTest())
        h.scope.cancel()
    }

    // ─── 8. finalWriteThrows_stateDoesNotStickInSealedFlushing ────────────────
    //
    // If writeFn throws, the final write coroutine catches the exception,
    // logs it, and transitions state to CLOSED. The flusher must not be
    // permanently stuck in SEALED_FLUSHING.
    @Test
    fun finalWriteThrows_stateDoesNotStickInSealedFlushing() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val written = mutableListOf<Long>()
        val writeMutex = Mutex()
        val flusher = FinalPdfProgressFlusher(scope) { snapshot ->
            writeMutex.withLock { written.add(snapshot.generation) }
            throw IllegalStateException("simulated DB failure")
        }

        flusher.sealAndFlushFinal(makeSnapshot(gen = 7, page = 1))
        advanceUntilIdle()

        assertTrue("writeFn was called (and threw)", 7L in written)
        assertEquals("flushedGeneration not advanced on failure", -1L, flusher.flushedGenerationForTest())
        assertNotEquals(
            "state must NOT be SEALED_FLUSHING after throw",
            FinalPdfProgressFlusher.FlusherState.SEALED_FLUSHING,
            flusher.stateForTest(),
        )
        assertEquals(FinalPdfProgressFlusher.FlusherState.CLOSED, flusher.stateForTest())
        scope.cancel()
    }

    // ─── 9. applicationScopeCancelled_taskCancelledByStructuredConcurrency ────
    //
    // Without NonCancellable, a pending launch is a structured child of
    // applicationScope. Cancelling the scope cancels the launch — writeFn is
    // never called. This proves the flusher relies on structured concurrency,
    // not on NonCancellable "free-running" coroutines.
    @Test
    fun applicationScopeCancelled_taskCancelledByStructuredConcurrency() = runTest {
        val h = makeHarness(testScheduler)
        h.flusher.sealAndFlushFinal(makeSnapshot(gen = 4, page = 1))
        // Do NOT advance — the launch is still queued.
        assertEquals(FinalPdfProgressFlusher.FlusherState.SEALED_FLUSHING, h.flusher.stateForTest())
        assertTrue(h.written.isEmpty())

        // Cancel the scope. The pending launch must be cancelled.
        h.scope.cancel()
        advanceUntilIdle()

        assertTrue("writeFn must NOT have run after scope cancel", h.written.isEmpty())
        // State remains SEALED_FLUSHING because the coroutine was cancelled
        // before it could transition to CLOSED. This is acceptable: the scope
        // is dead, the flusher is unreachable.
        assertNotEquals(
            "state must not be CLOSED (coroutine was cancelled before completing)",
            FinalPdfProgressFlusher.FlusherState.CLOSED,
            h.flusher.stateForTest(),
        )
    }

    // ─── 10. flusherLaunchIsStructuredChildOfApplicationScope ─────────────────
    //
    // The launch issued by sealAndFlushFinal is a structured child of
    // applicationScope's Job. We verify this by checking that the scope has
    // an active child job while the final write is pending (blocked on a
    // held lock), and that the child completes after the write finishes.
    @Test
    fun flusherLaunchIsStructuredChildOfApplicationScope() = runTest {
        val h = makeHarness(testScheduler)
        val holdFinal = holdGen(h, 9L)
        val scopeJob = h.scope.coroutineContext[Job]!!
        assertTrue("scope Job is active", scopeJob.isActive)

        h.flusher.sealAndFlushFinal(makeSnapshot(gen = 9, page = 1))
        advanceUntilIdle() // final launch enters mutex, blocks on holdFinal

        // While the final write is blocked, the scope must have at least one
        // active child (the final write coroutine). Structured concurrency:
        // scope waits for children.
        assertTrue("scope still active while child is running", scopeJob.isActive)

        // Release the final write.
        holdFinal.complete(Unit)
        advanceUntilIdle()

        assertTrue("final gen=9 wrote", 9L in h.written)
        assertEquals(FinalPdfProgressFlusher.FlusherState.CLOSED, h.flusher.stateForTest())
        // scope itself is still alive (we only cancel it explicitly).
        assertTrue("scope still active after child completed", scopeJob.isActive)
        h.scope.cancel()
    }

    // ─── Extra: singleFlush_writesPositionAndSessionOnce (retained from P0A) ──
    @Test
    fun singleFlush_writesPositionAndSessionOnce() = runTest {
        val h = makeHarness(testScheduler)
        h.flusher.flush(makeSnapshot(gen = 1, page = 5, scale = 2f))
        advanceUntilIdle()

        assertEquals(listOf(1L), h.written)
        assertEquals(1L, h.flusher.flushedGenerationForTest())
        h.scope.cancel()
    }

    // ─── Extra: nullFields_stillFlushesGeneration ─────────────────────────────
    @Test
    fun nullFields_stillFlushesGeneration() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        var called = false
        val writeMutex = Mutex()
        val flusher = FinalPdfProgressFlusher(scope) { snapshot ->
            writeMutex.withLock {
                called = true
                assertTrue("readingPosition is null", snapshot.readingPosition == null)
                assertTrue("viewportSession is null", snapshot.viewportSession == null)
            }
        }

        flusher.flush(makeNullSnapshot(gen = 1))
        advanceUntilIdle()

        assertTrue("writeFn was called", called)
        scope.cancel()
    }

    // ─── Extra: idempotent same-generation seal (regression) ───────────────────
    @Test
    fun sameGenerationSeal_isIdempotent() = runTest {
        val h = makeHarness(testScheduler)
        h.flusher.sealAndFlushFinal(makeSnapshot(gen = 4, page = 1))
        h.flusher.sealAndFlushFinal(makeSnapshot(gen = 4, page = 1)) // same gen → no-op merge
        advanceUntilIdle()

        assertEquals("only one write", 1, h.written.size)
        assertEquals(4L, h.written[0])
        assertEquals(FinalPdfProgressFlusher.FlusherState.CLOSED, h.flusher.stateForTest())
        h.scope.cancel()
    }

    // ═════════════════════════════════════════════════════════════════════════
    // P0A2: in-flight higher-final writer loop tests
    // ═════════════════════════════════════════════════════════════════════════

    // ─── P0A2-1. inFlightHigherFinal_arrivesDuringWrite_writerContinuesToHigher ─
    //
    // The exact race the P0A2 spec calls out:
    //   1. sealAndFlushFinal(gen3)
    //   2. writer reads gen3, enters blocking writeFn
    //   3. sealAndFlushFinal(gen4)
    //   4. gen3 write completes
    //   5. writer must NOT directly CLOSED; must continue to write gen4
    //   6. final flushedGeneration must be 4
    @Test
    fun inFlightHigherFinal_arrivesDuringWrite_writerContinuesToHigher() = runTest {
        val h = makeHarness(testScheduler)
        val holdGen3 = holdGen(h, 3L)

        h.flusher.sealAndFlushFinal(makeSnapshot(gen = 3, page = 1))
        // Advance until the writer is blocked inside writeFn for gen3.
        advanceUntilIdle()
        assertEquals(FinalPdfProgressFlusher.FlusherState.SEALED_FLUSHING, h.flusher.stateForTest())
        assertEquals(listOf(3L), h.written) // gen3 entered writeFn (recorded before gate)

        // While gen3 is still writing, submit a higher final.
        h.flusher.sealAndFlushFinal(makeSnapshot(gen = 4, page = 2))
        assertEquals(FinalPdfProgressFlusher.FlusherState.SEALED_FLUSHING, h.flusher.stateForTest())

        // Release gen3 — writer must continue to gen4, then CLOSED.
        holdGen3.complete(Unit)
        advanceUntilIdle()

        assertTrue("gen3 wrote", 3L in h.written)
        assertTrue("gen4 wrote (writer loop continued)", 4L in h.written)
        assertEquals("final flushedGeneration must be 4", 4L, h.flusher.flushedGenerationForTest())
        assertEquals(FinalPdfProgressFlusher.FlusherState.CLOSED, h.flusher.stateForTest())
        h.scope.cancel()
    }

    // ─── P0A2-2. gen3Then4Then5DuringWrite_finalGen5Wins ──────────────────────
    //
    // gen3 writing, gen4 arrives, gen5 arrives; release gen3; final must be 5.
    @Test
    fun gen3Then4Then5DuringWrite_finalGen5Wins() = runTest {
        val h = makeHarness(testScheduler)
        val holdGen3 = holdGen(h, 3L)

        h.flusher.sealAndFlushFinal(makeSnapshot(gen = 3, page = 1))
        advanceUntilIdle() // writer blocked on gen3
        assertEquals(listOf(3L), h.written)

        h.flusher.sealAndFlushFinal(makeSnapshot(gen = 4, page = 2))
        h.flusher.sealAndFlushFinal(makeSnapshot(gen = 5, page = 3))
        assertEquals(FinalPdfProgressFlusher.FlusherState.SEALED_FLUSHING, h.flusher.stateForTest())

        holdGen3.complete(Unit)
        advanceUntilIdle()

        assertTrue("gen3 wrote", 3L in h.written)
        assertTrue("gen5 wrote (highest pending)", 5L in h.written)
        assertFalse("gen4 must NOT write (superseded by gen5 before writer loop reached it)", 4L in h.written)
        assertEquals(5L, h.flusher.flushedGenerationForTest())
        assertEquals(FinalPdfProgressFlusher.FlusherState.CLOSED, h.flusher.stateForTest())
        h.scope.cancel()
    }

    // ─── P0A2-3. lowerGenerationDuringWrite_doesNotTriggerDuplicateWrite ──────
    //
    // gen5 writing; gen3 (lower) arrives; writer must NOT re-write gen3.
    @Test
    fun lowerGenerationDuringWrite_doesNotTriggerDuplicateWrite() = runTest {
        val h = makeHarness(testScheduler)
        val holdGen5 = holdGen(h, 5L)

        h.flusher.sealAndFlushFinal(makeSnapshot(gen = 5, page = 1))
        advanceUntilIdle() // writer blocked on gen5
        assertEquals(listOf(5L), h.written)

        // Lower generation arrives while gen5 is writing — must be ignored.
        h.flusher.sealAndFlushFinal(makeSnapshot(gen = 3, page = 2))

        holdGen5.complete(Unit)
        advanceUntilIdle()

        assertEquals("only gen5 written once", listOf(5L), h.written)
        assertEquals(5L, h.flusher.flushedGenerationForTest())
        assertEquals(FinalPdfProgressFlusher.FlusherState.CLOSED, h.flusher.stateForTest())
        h.scope.cancel()
    }

    // ─── P0A2-4. higherFinalArrivesAsWriterPreparesClosed_doesNotLose ────────
    //
    // gen3 writer is in the post-write synchronized block about to transition
    // to CLOSED. Before it does, a higher gen4 arrives. The writer loop must
    // observe the higher pending and continue to gen4.
    //
    // We approximate this race deterministically: block gen3, submit gen4
    // while gen3 is blocked, then release gen3. The writer's post-write
    // synchronized block will see pendingFinal=gen4 and continue.
    @Test
    fun higherFinalArrivesAsWriterPreparesClosed_doesNotLose() = runTest {
        val h = makeHarness(testScheduler)
        val holdGen3 = holdGen(h, 3L)

        h.flusher.sealAndFlushFinal(makeSnapshot(gen = 3, page = 1))
        advanceUntilIdle() // writer blocked on gen3

        // Submit gen4 while gen3 write is still in flight.
        h.flusher.sealAndFlushFinal(makeSnapshot(gen = 4, page = 2))

        // Release gen3. The writer completes gen3, then its post-write
        // synchronized block checks pendingFinal and finds gen4 (higher).
        // It must NOT transition to CLOSED; it must loop and write gen4.
        holdGen3.complete(Unit)
        advanceUntilIdle()

        assertTrue("gen3 wrote", 3L in h.written)
        assertTrue("gen4 wrote (writer did not prematurely CLOSED)", 4L in h.written)
        assertEquals(4L, h.flusher.flushedGenerationForTest())
        assertEquals(FinalPdfProgressFlusher.FlusherState.CLOSED, h.flusher.stateForTest())
        h.scope.cancel()
    }

    // ─── P0A2-5. writeThrowsButHigherPendingPending_writerContinuesToHigher ──
    //
    // Spec section 一.4: if writeFn throws and a higher-generation final is
    // pending, the writer must continue to attempt the higher one. The
    // failure semantics: we do NOT retry the same failed generation, but a
    // strictly-higher pending final is a different snapshot and will be
    // attempted.
    //
    // Fixed semantics (locked by this test):
    //   - gen3 write throws → log, do NOT advance flushedGeneration.
    //   - gen4 is pending → writer loop continues, writes gen4.
    //   - gen4 succeeds → flushedGeneration = 4, state = CLOSED.
    @Test
    fun writeThrowsButHigherPending_writerContinuesToHigher() = runTest {
        val scheduler = testScheduler
        val dispatcher = StandardTestDispatcher(scheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val written = mutableListOf<Long>()
        val writeMutex = Mutex()
        val gate3 = CompletableDeferred<Unit>()

        val flusher = FinalPdfProgressFlusher(scope) { snapshot ->
            if (snapshot.generation == 3L) {
                // Record entry, then block so we can submit gen4 mid-flight.
                writeMutex.withLock { written.add(snapshot.generation) }
                gate3.await()
                throw IllegalStateException("gen3 DB failure")
            }
            // gen4 succeeds
            writeMutex.withLock { written.add(snapshot.generation) }
        }

        flusher.sealAndFlushFinal(makeSnapshot(gen = 3, page = 1))
        advanceUntilIdle() // writer blocked on gate3 inside writeFn
        assertEquals(listOf(3L), written)

        // Submit higher gen4 while gen3 is still "writing" (about to throw).
        flusher.sealAndFlushFinal(makeSnapshot(gen = 4, page = 2))

        // Release gen3 → it throws. Writer must continue to gen4.
        gate3.complete(Unit)
        advanceUntilIdle()

        assertTrue("gen3 writeFn was called (and threw)", 3L in written)
        assertTrue("gen4 wrote after gen3 threw", 4L in written)
        assertEquals("flushedGeneration is gen4 (gen3 did not advance it)", 4L, flusher.flushedGenerationForTest())
        assertEquals(FinalPdfProgressFlusher.FlusherState.CLOSED, flusher.stateForTest())
        scope.cancel()
    }
}
