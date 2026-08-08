package com.markleaf.app.pdf.export

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Overlap regression for the REAL exporter cancellation model:
 *
 *   A starts → A blocks in an uncancellable segment (copyTo/save) →
 *   cancel A → B starts immediately (B's entry runs its own init) →
 *   A is released → A reaches its next cancellation checkpoint.
 *
 * Must prove: A = cancelled/stopped, B = success — and crucially that B's
 * start can NEVER re-enable A (job-local tokens; the old shared
 * `cancelRequested` boolean had exactly that hazard).
 */
class ExportCancellationOverlapTest {

    /** Mirrors the exporters' checkpoint: token OR job-cancellation stops the run. */
    private suspend fun fakeExporterRun(
        token: ExportCancellationToken,
        name: String,
        results: MutableMap<String, String>,
    ) {
        // Uncancellable segment (like copyTo / dest.save): job cancel has no effect.
        withContext(NonCancellable) { delay(500) }
        // Cancellation checkpoint (same predicate as PdfAnnotatedExporter/ChapterPackageExporter).
        if (token.isCancelled() || !currentCoroutineContext().isActive) {
            results[name] = "CANCELLED"
            return
        }
        withContext(NonCancellable) { delay(500) }
        results[name] = "DONE"
    }

    @Test
    fun cancelA_startB_aStopsAtCheckpoint_bCompletes() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val results = mutableMapOf<String, String>()

        // A starts with its own token.
        val tokenA = ExportCancellationToken()
        val jobA = scope.launch { fakeExporterRun(tokenA, "A", results) }
        advanceTimeBy(10) // A enters its uncancellable segment (still blocking)

        // cancel A (VM cancelExport: gate + token + job).
        tokenA.cancel()
        jobA.cancel()

        // B starts immediately, with its OWN token — B's entry must not
        // affect A's token in any way (old shared-flag reset hazard).
        val tokenB = ExportCancellationToken()
        val jobB = scope.launch { fakeExporterRun(tokenB, "B", results) }
        assertTrue("A's token stays cancelled", tokenA.isCancelled())
        assertFalse("B's token starts fresh", tokenB.isCancelled())
        assertTrue("tokens are distinct objects", tokenA !== tokenB)

        // Release: B completes fully; A's uncancellable segment ends, it hits
        // the checkpoint and must STOP (CANCELLED), never resume work.
        advanceUntilIdle()

        assertEquals("A:CANCELLED", "A:${results["A"]}")
        assertEquals("B:DONE", "B:${results["B"]}")
        // Order: A's checkpoint fires after its segment ends; B completes.
        assertEquals(2, results.size)
    }

    @Test
    fun tokenIsJobLocal_bStartCannotReenableCancelledA() = runTest {
        val tokenA = ExportCancellationToken()
        val tokenB = ExportCancellationToken()
        tokenA.cancel()
        // B's lifecycle (begin + later cancel) touches only tokenB.
        tokenB.cancel()
        tokenB.cancel()
        assertTrue("A stays cancelled forever", tokenA.isCancelled())
        assertTrue("B is cancelled too", tokenB.isCancelled())
        assertTrue(tokenA !== tokenB)
    }

    @Test
    fun uncancelledRunReachesDone() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val results = mutableMapOf<String, String>()
        val token = ExportCancellationToken()
        val job = scope.launch { fakeExporterRun(token, "X", results) }
        advanceUntilIdle()
        assertEquals("X:DONE", "X:${results["X"]}")
        assertEquals(mapOf("X" to "DONE"), results)
    }
}
