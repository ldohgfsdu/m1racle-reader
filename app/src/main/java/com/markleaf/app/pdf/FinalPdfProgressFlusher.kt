package com.markleaf.app.pdf

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * P0A2 final-flush concurrency closeout: atomic seal-and-flush state machine
 * with a unified synchronization domain and an in-flight-aware writer loop.
 *
 * ## P0A2 changes
 *
 * 1. **In-flight higher-final race fix.** The previous writer read
 *    [pendingFinal] once, wrote it, and unconditionally transitioned to
 *    CLOSED. If a higher-generation [sealAndFlushFinal] arrived while the
 *    writer was inside [writeFn], the higher generation was silently dropped.
 *    The writer now loops: after each write it re-checks [pendingFinal] under
 *    [sealLock]; if a strictly-higher generation has arrived, it writes that
 *    too. Only when no higher generation is pending does it transition to
 *    CLOSED.
 *
 * 2. **Unified synchronization domain (spec section 二, option A).** All
 *    accesses to [state], [pendingFinal], and [flushedGeneration] are
 *    serialized through [sealLock] (a plain Java monitor). The coroutine
 *    [mutex] serializes only [writeFn] execution — it is NOT used to guard
 *    flusher state. This closes the previous split-lock gap where
 *    `sealAndFlushFinal` used `synchronized(sealLock)` but `runFinalWrite`
 *    relied on the coroutine `Mutex` to access the same fields.
 *
 *    Happens-before: every `synchronized(sealLock)` block performs a
 *    monitor-enter on the same object. Java Memory Model §17.1 guarantees
 *    that all actions in a synchronized block happen-before every subsequent
 *    synchronized block on the same monitor. Therefore:
 *    - A write to `state`/`pendingFinal`/`flushedGeneration` inside
 *      `synchronized(sealLock)` in `sealAndFlushFinal` is visible to the
 *      next `synchronized(sealLock)` block in `runFinalWrite`.
 *    - A write to `flushedGeneration` inside `runFinalWrite`'s
 *      `synchronized(sealLock)` block is visible to the next `flush` or
 *      `sealAndFlushFinal` call.
 *    The coroutine `Mutex` provides no happens-before guarantee for these
 *    fields; it is used ONLY to ensure at most one [writeFn] executes at a
 *    time (writeFn may not be reentrant; Room transactions are not
 *    concurrent on the same connection).
 *
 *    **No suspend function is called inside `synchronized(sealLock)`.**
 *    [writeFn] (suspend) is called outside the monitor, between locked
 *    sections that only read/write volatile fields.
 *
 * 3. **Database atomicity (spec section 三).** The caller's [writeFn] must
 *    persist readingPosition + viewportSession atomically. The coroutine
 *    [mutex] serializes writeFn calls but does NOT make the underlying DB
 *    writes atomic. If writeFn issues two separate DAO updates, a crash
 *    between them leaves inconsistent state. The VM's writeFn now wraps
 *    both updates in a Room withTransaction (see PdfViewerViewModel).
 *
 * ## State machine
 *
 * ```
 * OPEN ──flush()──────────────────────────► OPEN
 * OPEN ──sealAndFlushFinal(s)─────────────► SEALED_FLUSHING (writer launched)
 * SEALED_FLUSHING ──flush()────────────────► (rejected) SEALED_FLUSHING
 * SEALED_FLUSHING ──sealAndFlushFinal(s')──► (merge/replace pending if s'.gen > s.gen)
 * SEALED_FLUSHING ──writer: no higher pending──► CLOSED
 * SEALED_FLUSHING ──writer: writeFn throws──► CLOSED  (or continue if higher pending)
 * CLOSED ──anything────────────────────────► (rejected) CLOSED
 * ```
 *
 * ## NonCancellable
 *
 * applicationScope uses SupervisorJob + Dispatchers.IO. Child launch failures
 * are caught per-launch and do not propagate. NonCancellable is unnecessary
 * and harmful; all launches are plain structured children.
 *
 * ## Process death caveat
 *
 * If the process is force-killed while a final write is pending, the async
 * write is not guaranteed to complete. Primary paths (onStop / dispose /
 * back-navigation) persisted earlier through [flush].
 */
class FinalPdfProgressFlusher(
    private val applicationScope: CoroutineScope,
    private val writeFn: suspend (FinalPdfProgressSnapshot) -> Unit,
) {
    enum class FlusherState {
        OPEN,
        SEALED_FLUSHING,
        CLOSED,
    }

    /**
     * Serializes all access to [state], [pendingFinal], [flushedGeneration].
     * See class kdoc for the happens-before proof.
     */
    private val sealLock = Any()

    /** Serializes [writeFn] execution only. Does NOT guard flusher state. */
    private val mutex = Mutex()

    private var state: FlusherState = FlusherState.OPEN

    /** Last generation that was successfully written. -1 = nothing written yet. */
    private var flushedGeneration: Long = -1L

    /**
     * The highest-generation final snapshot accepted by [sealAndFlushFinal].
     * The writer loop reads this under [sealLock] after each write to decide
     * whether to continue or transition to CLOSED.
     */
    private var pendingFinal: FinalPdfProgressSnapshot? = null

    /**
     * Submit a non-final snapshot (onStop / dispose / back-nav). Returns
     * immediately; the write runs on applicationScope. Stale generations
     * (<= flushedGeneration) and post-seal submissions are dropped.
     */
    fun flush(snapshot: FinalPdfProgressSnapshot) {
        synchronized(sealLock) {
            if (state != FlusherState.OPEN) return
            if (snapshot.generation <= flushedGeneration) return
        }
        applicationScope.launch {
            mutex.withLock {
                val shouldWrite = synchronized(sealLock) {
                    // Re-check under the monitor: state may have transitioned
                    // to SEALED_FLUSHING while we waited for the mutex. If so,
                    // defer to the final write path.
                    state == FlusherState.OPEN &&
                        snapshot.generation > flushedGeneration
                }
                if (!shouldWrite) return@withLock
                try {
                    writeFn(snapshot)
                } catch (e: Exception) {
                    safeLog("flush failed gen=${snapshot.generation}", e)
                    return@withLock
                }
                synchronized(sealLock) {
                    if (snapshot.generation > flushedGeneration) {
                        flushedGeneration = snapshot.generation
                    }
                }
            }
        }
    }

    /**
     * Atomically seal the flusher and submit the final snapshot. The final
     * snapshot is guaranteed to reach [writeFn] (it is not subject to the
     * `state != OPEN` rejection that [flush] is). The state transitions to
     * CLOSED only after the writer loop drains all strictly-higher pending
     * finals.
     *
     * Contract:
     * - Idempotent: a second call merges with the first. If the new
     *   generation is strictly higher than pendingFinal, pendingFinal is
     *   replaced; otherwise no-op.
     * - After this call, [flush] is rejected.
     * - If writeFn throws and no higher-generation final is pending, state
     *   transitions to CLOSED. If a higher-generation final is pending, the
     *   writer loop continues to attempt it.
     * - Does not block the calling thread.
     */
    fun sealAndFlushFinal(snapshot: FinalPdfProgressSnapshot) {
        val launchWriter: Boolean
        synchronized(sealLock) {
            when (state) {
                FlusherState.CLOSED -> return
                FlusherState.SEALED_FLUSHING -> {
                    val current = pendingFinal
                    if (current == null || snapshot.generation > current.generation) {
                        pendingFinal = snapshot
                    }
                    return
                }
                FlusherState.OPEN -> {
                    state = FlusherState.SEALED_FLUSHING
                    pendingFinal = snapshot
                    launchWriter = true
                }
            }
        }
        if (launchWriter) {
            applicationScope.launch {
                runFinalWriteLoop()
            }
        }
    }

    /**
     * Writer loop. Repeatedly:
     *   1. Under [sealLock], read the current pendingFinal and generation guard.
     *   2. If no pending final or generation <= flushedGeneration, transition
     *      to CLOSED and exit.
     *   3. Outside the monitor, under [mutex], call [writeFn].
     *   4. Under [sealLock], advance flushedGeneration, clear pendingFinal if
     *      it is still the same snapshot (a higher one may have arrived).
     *   5. Loop: re-check pendingFinal. If a strictly-higher generation has
     *      arrived, write it too. Otherwise transition to CLOSED.
     *
     * On writeFn exception: log, then re-check pendingFinal. If a
     * strictly-higher generation is pending, continue the loop (retry the
     * higher one). Otherwise transition to CLOSED — we do not retry the same
     * failed generation.
     */
    private suspend fun runFinalWriteLoop() {
        mutex.withLock {
            while (true) {
                // Read the current pendingFinal under sealLock. If null or
                // already-flushed, transition to CLOSED and exit the loop.
                var toWrite: FinalPdfProgressSnapshot? = null
                var exitLoop = false
                synchronized(sealLock) {
                    if (state == FlusherState.CLOSED) {
                        exitLoop = true
                    } else {
                        val current = pendingFinal
                        if (current == null) {
                            state = FlusherState.CLOSED
                            exitLoop = true
                        } else if (current.generation <= flushedGeneration) {
                            // Already written by a prior iteration or a normal flush.
                            pendingFinal = null
                            state = FlusherState.CLOSED
                            exitLoop = true
                        } else {
                            toWrite = current
                        }
                    }
                }
                if (exitLoop) return@withLock

                val snapshot = toWrite!!
                var threw = false
                try {
                    writeFn(snapshot)
                } catch (e: Exception) {
                    threw = true
                    safeLog("final flush failed gen=${snapshot.generation}", e)
                }

                var continueToHigher = false
                synchronized(sealLock) {
                    if (!threw && snapshot.generation > flushedGeneration) {
                        flushedGeneration = snapshot.generation
                    }
                    // Clear pendingFinal only if it is still the same snapshot
                    // (a higher one may have arrived during writeFn).
                    val current = pendingFinal
                    if (current != null && current.generation <= snapshot.generation) {
                        pendingFinal = null
                    }
                    // Decide whether to continue.
                    val next = pendingFinal
                    if (next == null || next.generation <= flushedGeneration) {
                        // No higher pending. Transition to CLOSED.
                        pendingFinal = null
                        state = FlusherState.CLOSED
                        // exitLoop stays false; we return below.
                    } else {
                        // Higher pending exists. Continue the loop to write it.
                        // If the current write threw, we still attempt the higher
                        // generation (it is a different snapshot, may succeed).
                        continueToHigher = true
                    }
                }
                if (!continueToHigher) return@withLock
            }
        }
    }

    /** Test-only: current flushed generation. */
    internal fun flushedGenerationForTest(): Long = synchronized(sealLock) { flushedGeneration }

    /** Test-only: current state. */
    internal fun stateForTest(): FlusherState = synchronized(sealLock) { state }

    /**
     * Log without throwing in JVM unit tests where android.util.Log is not
     * mocked. The default Android mock throws RuntimeException("not mocked")
     * on any Log call; this helper swallows it so the flusher's catch block
     * does not itself throw during tests.
     */
    private fun safeLog(message: String, e: Throwable) {
        try {
            Log.w(TAG, message, e)
        } catch (_: RuntimeException) {
            // android.util.Log not mocked in JVM tests; swallow.
        }
    }

    private companion object {
        private const val TAG = "FinalPdfProgressFlusher"
    }
}

/**
 * Immutable snapshot of the final stable progress to persist.
 *
 * @param documentId Target document.
 * @param readingPosition Last stable reading position; null = skip position write.
 * @param viewportSession Last stable viewport session; null = skip session write.
 * @param pageCount Page count for progress computation.
 * @param generation Monotonic snapshot version assigned by the caller;
 *   the flusher rejects snapshots with generation <= last flushed.
 */
data class FinalPdfProgressSnapshot(
    val documentId: String,
    val readingPosition: ReadingPosition?,
    val viewportSession: PdfViewportSessionState?,
    val pageCount: Int,
    val generation: Long,
)
