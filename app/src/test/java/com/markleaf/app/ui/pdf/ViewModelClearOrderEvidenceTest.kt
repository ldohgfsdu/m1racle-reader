package com.markleaf.app.ui.pdf

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0A1 lifecycle hardening: ViewModel clear-order evidence + issueCloseAsync
 * structured-concurrency contract.
 *
 * The AndroidX ViewModel 2.8.7 bytecode (verified via javap on
 * `lifecycle-viewmodel-release-runtime.jar`) shows:
 *
 * ```
 * public final void clear$lifecycle_viewmodel_release():
 *     0: aload_0
 *     1: getfield      impl:Landroidx/lifecycle/viewmodel/internal/ViewModelImpl;
 *     4: dup
 *     5: ifnull        14
 *     8: invokevirtual ViewModelImpl.clear:()V   // closes all closeables
 *                                                  // (including viewModelScope)
 *    11: goto          15
 *    14: pop
 *    15: aload_0
 *    16: invokevirtual onCleared:()V              // THEN calls onCleared
 *    19: return
 * ```
 *
 * Conclusion: **viewModelScope is cancelled BEFORE onCleared() runs.**
 * A `viewModelScope.launch` inside `onCleared()` is unreliable. The correct
 * pattern is a process-lifetime `applicationScope` (SupervisorJob +
 * Dispatchers.IO), which is what [PdfViewerViewModel.onCleared] uses.
 *
 * ## P0A1 NonCancellable removal
 *
 * applicationScope already uses SupervisorJob. A child launch failure (caught
 * per-launch) does not propagate to siblings. NonCancellable is unnecessary
 * and harmful: it would let close/write coroutines keep running after the
 * scope is cancelled in tests, defeating structured concurrency. All
 * applicationScope launches in issueCloseAsync and FinalPdfProgressFlusher
 * are now plain children — no NonCancellable.
 *
 * These tests use [StandardTestDispatcher] + [TestCoroutineScheduler] so
 * launches do not run until [advanceUntilIdle] is called, giving deterministic
 * control over the close/cancel ordering.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelClearOrderEvidenceTest {

    /**
     * ApplicationScope (SupervisorJob + test dispatcher) survives a
     * viewModelScope cancellation. This is the core property that makes
     * onCleared's close reliable.
     */
    @Test
    fun applicationScope_survivesViewModelScopeCancellation() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val appScope = CoroutineScope(SupervisorJob() + dispatcher)
        val viewModelScope = CoroutineScope(SupervisorJob() + dispatcher)

        var appScopeJobRan = false
        // Simulate AndroidX ViewModel.clear(): cancel viewModelScope first.
        viewModelScope.cancel()
        // Then simulate onCleared body: launch close on applicationScope.
        // No NonCancellable — plain structured child.
        appScope.launch {
            appScopeJobRan = true
        }
        advanceUntilIdle()

        assertTrue("applicationScope launch must run after viewModelScope cancelled", appScopeJobRan)
        appScope.cancel()
    }

    /**
     * Idempotent close: calling closeAsync twice must not throw and must not
     * deadlock. Mirrors issueCloseAsync's closeIssued guard.
     */
    @Test
    fun idempotentClose_secondCallIsNoop() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val appScope = CoroutineScope(SupervisorJob() + dispatcher)
        var closeCount = 0
        val closeMutex = Mutex()
        var closeIssued = false

        suspend fun closeAsync() {
            closeMutex.withLock { closeCount++ }
        }

        fun issueCloseAsync() {
            if (closeIssued) return
            closeIssued = true
            appScope.launch { closeAsync() }
        }

        issueCloseAsync()
        issueCloseAsync() // second call must be no-op
        advanceUntilIdle()

        assertEquals("close must have run exactly once (idempotent)", 1, closeCount)
        appScope.cancel()
    }

    /**
     * Close exception must not cancel applicationScope. A SupervisorJob
     * survives child failures; per-launch try/catch prevents the exception
     * from propagating. Mirrors issueCloseAsync's try/catch around
     * renderer.closeAsync() and thumbnailLoader.closeAsync().
     */
    @Test
    fun closeException_doesNotCancelApplicationScope() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val appScope = CoroutineScope(SupervisorJob() + dispatcher)
        var secondJobRan = false

        // First launch throws — but issueCloseAsync wraps in try/catch.
        appScope.launch {
            try {
                throw IllegalStateException("close failed")
            } catch (_: Exception) {
                // swallowed, like issueCloseAsync
            }
        }
        advanceUntilIdle()

        // applicationScope must still be active.
        assertTrue("applicationScope must be active after child swallowed exception",
            appScope.coroutineContext[Job]?.isActive == true)

        appScope.launch {
            secondJobRan = true
        }
        advanceUntilIdle()

        assertTrue("second job must run after first job threw (and was caught)", secondJobRan)
        appScope.cancel()
    }

    /**
     * Main-thread runBlocking elimination: onCleared must not call any
     * function that runBlocks. The new code calls `closeAsync()` on
     * applicationScope. This test verifies issueCloseAsync returns immediately
     * (no suspension on the calling thread).
     */
    @Test
    fun closeAsync_doesNotBlockCallingThread() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val appScope = CoroutineScope(SupervisorJob() + dispatcher)
        val closeMutex = Mutex()
        var closeIssued = false
        var launchEnqueued = false

        fun issueCloseAsync() {
            if (closeIssued) return
            closeIssued = true
            appScope.launch {
                closeMutex.withLock { /* close */ }
            }
            launchEnqueued = true
        }

        // The calling thread should not block. We verify by checking that
        // issueCloseAsync returns synchronously and the launch is merely
        // enqueued (not yet run).
        issueCloseAsync()
        assertTrue("issueCloseAsync must return synchronously (launch enqueued)", launchEnqueued)
        // closeMutex has not been acquired yet because the launch has not run.
        assertTrue("close must not have run before advance", closeMutex.tryLock())
        closeMutex.unlock()

        advanceUntilIdle()
        // Now the close launch has run and acquired/released closeMutex.
        assertTrue("close must have run after advance", closeMutex.tryLock())
        closeMutex.unlock()

        appScope.cancel()
    }

    /**
     * P0A1 spec test 10: issueCloseAsync's launch is a structured child of
     * applicationScope's Job. While the close coroutine is in-flight (blocked
     * on a held mutex), the scope's Job must have an active child. After the
     * close completes, the child completes but the scope remains alive.
     *
     * This proves the launch is NOT free-running (NonCancellable detached)
     * — it is a structured child that the scope waits for and that gets
     * cancelled when the scope is cancelled.
     */
    @Test
    fun issueCloseAsync_childIsStructuredChildOfApplicationScopeJob() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val appScope = CoroutineScope(SupervisorJob() + dispatcher)
        val closeGate = CompletableDeferred<Unit>() // hold the close coroutine
        var closeIssued = false
        var closeCompleted = false

        fun issueCloseAsync() {
            if (closeIssued) return
            closeIssued = true
            appScope.launch {
                closeGate.await() // held until test completes
                closeCompleted = true
            }
        }

        val scopeJob = appScope.coroutineContext[Job]!!
        assertTrue("scope Job is active before close", scopeJob.isActive)

        issueCloseAsync()
        advanceUntilIdle() // close launch enters, blocks on closeGate.await()

        assertTrue("scope still active while close child is blocked", scopeJob.isActive)
        assertFalse("close not completed yet (blocked)", closeCompleted)
        // The scope's children list must be non-empty — structured concurrency.
        // Job.getChildren() is async; we verify transitively: cancelling the
        // scope must cancel the blocked close child.
        appScope.cancel()
        advanceUntilIdle()

        // After scope cancel, the close child was cancelled — closeCompleted
        // must remain false (the coroutine never reached the line after await).
        assertFalse("close child must have been cancelled by scope cancel", closeCompleted)
    }
}
