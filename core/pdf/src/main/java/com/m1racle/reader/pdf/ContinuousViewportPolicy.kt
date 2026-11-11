package com.m1racle.reader.pdf

enum class ContinuousScrollDirection { FORWARD, BACKWARD, IDLE }
enum class ContinuousScrollPhase { IDLE, DRAGGING, FLINGING }

data class ContinuousViewportSnapshot(
    val visiblePages: Set<Int>,
    val primaryPage: Int,
    val direction: ContinuousScrollDirection,
    val phase: ContinuousScrollPhase,
    /** Scroll-Y delta (document px) since the previous snapshot — chrome hysteresis input. */
    val scrollDeltaY: Float = 0f,
    /** Instant scroll velocity (dp/s, positive = forward) — fling fast-path input. */
    val velocityYDpPerSec: Float = 0f,
    /**
     * Ground truth: the primary page's top edge on screen (viewport coords,
     * px). Derived from the page placement transform: screenY = pageTop*scale
     * - scrollY. Content moving up makes this value SMALLER.
     */
    val contentAnchorScreenY: Float = 0f,
    /**
     * Measured cross-frame screen displacement of the SAME primary page's top
     * edge (px). < 0 = PDF content visibly moves toward the screen top;
     * > 0 = content moves toward the screen bottom. 0 = re-baseline frame
     * (primary page changed) or no motion. This is the ONLY direction source
     * the chrome adapter may use — raw scrollDeltaY sign is never semantic.
     */
    val contentAnchorDeltaY: Float = 0f,
)

object ContinuousViewportPolicy {
    fun previewPages(
        visible: Set<Int>, primary: Int, pageCount: Int,
        direction: ContinuousScrollDirection, phase: ContinuousScrollPhase,
    ): Set<Int> {
        if (pageCount <= 0) return emptySet()
        val (ahead, behind) = when (phase) {
            ContinuousScrollPhase.IDLE -> 2 to 2
            ContinuousScrollPhase.DRAGGING -> 6 to 2
            ContinuousScrollPhase.FLINGING -> 10 to 3
        }
        val forward = direction != ContinuousScrollDirection.BACKWARD
        return (visible + ((if (forward) primary - behind else primary - ahead)..
            (if (forward) primary + ahead else primary + behind)))
            .filter { it in 0 until pageCount }.toSet()
    }

    fun previewRequestOrder(snapshot: ContinuousViewportSnapshot, pageCount: Int): List<Int> {
        val keep = previewPages(
            snapshot.visiblePages, snapshot.primaryPage, pageCount,
            snapshot.direction, snapshot.phase,
        )
        val forward = snapshot.direction != ContinuousScrollDirection.BACKWARD
        return keep.sortedWith(
            compareBy<Int> { if (it in snapshot.visiblePages) 0 else 1 }
                .thenBy { if ((it >= snapshot.primaryPage) == forward) 0 else 1 }
                .thenBy { kotlin.math.abs(it - snapshot.primaryPage) },
        )
    }

    const val MAX_RETAINED_PAGES = 18

    fun boundedPreviewPages(snapshot: ContinuousViewportSnapshot, pageCount: Int): Set<Int> {
        val ordered = previewRequestOrder(snapshot, pageCount)
        return (snapshot.visiblePages + ordered.take(MAX_RETAINED_PAGES)).toSet()
    }
}
