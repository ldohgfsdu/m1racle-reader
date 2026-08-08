package com.markleaf.app.ui.viewer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch

/**
 * Latest-wins reading-position persistence with a single bounded final flush.
 *
 * Two distinct behaviors, per the Phase-3 split:
 * - **High-frequency saves** (scroll settle, page change): fully cancellable,
 *   single-slot. A newer position cancels the in-flight save, so stale writes
 *   never pile up and the newest position always wins.
 * - **Final flush** (document close / ViewModel clear): the ONLY bounded
 *   [NonCancellable] write. It persists the last known position when the
 *   in-flight save has not completed (e.g. the scope was already cancelled),
 *   and runs at most once.
 */
class ReadingPositionSaver(
    private val scope: CoroutineScope,
    private val write: suspend (lastScrollIndex: Int, lastScrollOffset: Int, readingProgress: Float) -> Unit,
) {
    private var saveJob: Job? = null
    private var lastKnown: Triple<Int, Int, Float>? = null

    fun save(lastScrollIndex: Int, lastScrollOffset: Int, readingProgress: Float) {
        lastKnown = Triple(lastScrollIndex, lastScrollOffset, readingProgress)
        saveJob?.cancel()
        saveJob = scope.launch {
            try {
                write(lastScrollIndex, lastScrollOffset, readingProgress)
            } catch (_: Exception) {
                // Never crash on background save.
            }
        }
    }

    /**
     * Bounded final flush: writes the last known position exactly when the
     * newest in-flight save has not completed successfully. A cancelled job
     * counts as NOT persisted (`isCompleted` alone is true for cancelled
     * jobs). No-op once the latest value is already persisted, so at most one
     * extra write can ever happen here.
     * Production scope: NonCancellable + Dispatchers.IO; tests inject their own.
     */
    fun flushFinal(flushScope: CoroutineScope = CoroutineScope(NonCancellable + Dispatchers.IO)) {
        val last = lastKnown ?: return
        val job = saveJob
        if (job != null && !job.isCancelled && job.isCompleted) return // already persisted
        flushScope.launch {
            try {
                write(last.first, last.second, last.third)
            } catch (_: Exception) {
                // Ignore persistence errors.
            }
        }
    }
}
