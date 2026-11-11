package com.m1racle.reader.pdf

/**
 * Everything that makes a queued render request valid or obsolete.
 *
 * A change in ANY field must re-dispatch the heavy path (cache trim + render
 * re-plan). Advancing scrollY alone, with the same visible pages, primary page,
 * direction, phase, scale, geometry and generation, MUST NOT re-dispatch.
 */
data class ContinuousDispatchSignature(
    val documentId: String,
    val pageCount: Int,
    val viewportWidthPx: Int,
    val scale: Float,
    val primaryPage: Int,
    val visiblePages: Set<Int>,
    val direction: ContinuousScrollDirection,
    val phase: ContinuousScrollPhase,
    val zoomGestureActive: Boolean,
    val entryPhaseReady: Boolean,
    val generation: Long,
)

/**
 * Decides whether [PdfViewerViewModel] needs to run the heavy continuous-mode
 * dispatch for a viewport frame. Also identifies jobs superseded by a drag
 * direction reversal.
 *
 * The gate suppresses re-entrant work only. It never invents state: the caller
 * computes the signature from the state it is about to publish, so an unchanged
 * signature means the heavy path would recompute exactly the same plan.
 *
 * Counters are diagnostics (exposed for unit tests and debug logs); [reset]
 * clears only the last signature, keeping the counters, so that a pinch or a
 * pending entry restore cannot be diagnosed as a suppression bug.
 */
class ContinuousViewportDispatchGate {
    private var last: ContinuousDispatchSignature? = null

    /** Number of frames that ran the heavy dispatch path. */
    var dispatchCount: Long = 0L
        private set

    /** Number of frames skipped because the effective signature was unchanged. */
    var suppressedCount: Long = 0L
        private set

    /** Number of non-IDLE direction changes observed between consecutive frames. */
    var reversalCount: Long = 0L
        private set

    fun shouldDispatch(signature: ContinuousDispatchSignature): Boolean {
        val previous = last
        if (previous != null) {
            if (previous.direction != signature.direction &&
                signature.direction != ContinuousScrollDirection.IDLE
            ) {
                reversalCount++
            }
            if (previous == signature) {
                suppressedCount++
                return false
            }
        }
        last = signature
        dispatchCount++
        return true
    }

    /**
     * Pages whose queued render belongs to the abandoned drag direction.
     *
     * A reversal invalidates every job queued for pages that are not currently
     * visible: they were requested because the finger was heading that way, and
     * the serial PdfRenderer mutex makes each one a delay in front of the page
     * the reader is now looking at. Visible pages are never cancelled.
     */
    fun supersededOnReversal(
        jobs: Map<Int, ContinuousRenderKey>,
        previousDirection: ContinuousScrollDirection,
        currentDirection: ContinuousScrollDirection,
        visiblePages: Set<Int>,
    ): Set<Int> {
        if (previousDirection == currentDirection) return emptySet()
        if (currentDirection == ContinuousScrollDirection.IDLE) return emptySet()
        return jobs.keys.filterTo(LinkedHashSet()) { it !in visiblePages }
    }

    /** Forget the last signature so the next frame dispatches unconditionally. */
    fun reset() {
        last = null
    }

    fun resetCounters() {
        dispatchCount = 0L
        suppressedCount = 0L
        reversalCount = 0L
    }
}
