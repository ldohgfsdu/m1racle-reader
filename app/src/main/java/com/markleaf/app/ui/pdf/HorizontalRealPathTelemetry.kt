package com.markleaf.app.ui.pdf

import com.markleaf.app.pdf.HorizontalPageTransitionDirection
import com.markleaf.app.pdf.PdfTilePlan
import com.markleaf.app.pdf.PageStripSlotRole
import com.markleaf.app.pdf.PageStripTransition
import com.markleaf.app.pdf.ThreePageStripPhysics
import com.markleaf.app.pdf.ThreePageStripState
import kotlin.math.abs

/** Read-only observation point for QA. Production behavior never reads these counters. */
internal object HorizontalRealPathTelemetry {
    private val lock = Any()
    private var value = HorizontalRealPathSnapshot()

    fun reset() = synchronized(lock) { value = HorizontalRealPathSnapshot() }
    fun snapshot(): HorizontalRealPathSnapshot = synchronized(lock) { value }

    fun onPointerDown() = synchronized(lock) { value = value.copy(pointerDown = true) }
    fun onPointerUp() = synchronized(lock) { value = value.copy(pointerDown = false) }
    fun onPageStripGestureStart(
        strip: ThreePageStripState,
        pageIndex: Int,
        offsetPx: Float,
        viewportWidthPx: Float,
    ) = synchronized(lock) {
        value = value.copy(
            pointerDown = true,
            pageStripFrames = 0,
            pageStripRawInputAbsPx = 0f,
            pageStripDisplayedDeltaAbsPx = 0f,
            pageStripReverseDeltaCount = 0,
            pageIndexAtPointerDown = pageIndex,
            pageIndexDuringPointerDown = pageIndex,
            previousSlotId = strip.previousSlotId,
            currentSlotId = strip.currentSlotId,
            nextSlotId = strip.nextSlotId,
            displayedCurrentPageX = ThreePageStripPhysics.slotTranslationPx(
                PageStripSlotRole.CURRENT,
                viewportWidthPx,
                offsetPx,
            ),
            displayedNeighborPageX = Float.NaN,
        )
    }
    fun onPageStripFrame(
        rawDeltaX: Float,
        displayedDeltaX: Float,
        strip: ThreePageStripState,
        transition: PageStripTransition,
        pageIndex: Int,
        offsetPx: Float,
        viewportWidthPx: Float,
    ) = synchronized(lock) {
        val stripActive = transition.active || abs(displayedDeltaX) > 0.01f
        val reverse = stripActive && abs(rawDeltaX) > 0.01f && abs(displayedDeltaX) > 0.01f &&
            rawDeltaX * displayedDeltaX < 0f
        val neighborRole = when (transition.direction) {
            HorizontalPageTransitionDirection.PREVIOUS -> PageStripSlotRole.PREVIOUS
            HorizontalPageTransitionDirection.NEXT -> PageStripSlotRole.NEXT
            null -> null
        }
        value = value.copy(
            pageStripFrames = value.pageStripFrames + if (stripActive) 1 else 0,
            pageStripRawInputAbsPx = value.pageStripRawInputAbsPx +
                if (stripActive) abs(rawDeltaX) else 0f,
            pageStripDisplayedDeltaAbsPx = value.pageStripDisplayedDeltaAbsPx +
                if (stripActive) abs(displayedDeltaX) else 0f,
            pageStripReverseDeltaCount = value.pageStripReverseDeltaCount + if (reverse) 1 else 0,
            pageIndexDuringPointerDown = pageIndex,
            previousSlotId = strip.previousSlotId,
            currentSlotId = strip.currentSlotId,
            nextSlotId = strip.nextSlotId,
            displayedCurrentPageX = offsetPx,
            displayedNeighborPageX = neighborRole?.let {
                ThreePageStripPhysics.slotTranslationPx(it, viewportWidthPx, offsetPx)
            } ?: Float.NaN,
        )
    }
    fun onGestureFrame() = synchronized(lock) {
        value = value.copy(gestureFrames = value.gestureFrames + 1)
    }
    fun onPlan(plan: PdfTilePlan) = synchronized(lock) {
        value = value.copy(
            tileRequestsBeforeRelease = value.tileRequestsBeforeRelease +
                if (value.pointerDown) plan.requests.size else 0,
            pageAheadRequestsBeforeRelease = value.pageAheadRequestsBeforeRelease +
                if (value.pointerDown) plan.requests.count {
                    it.priority == com.markleaf.app.pdf.TilePriority.PAGE_AHEAD
                } else 0,
        )
    }
    fun onTileReady() = synchronized(lock) {
        if (value.pointerDown) {
            value = value.copy(tileReadyBeforeRelease = value.tileReadyBeforeRelease + 1)
        }
    }
    fun onTileSwap(count: Int) = synchronized(lock) {
        if (value.pointerDown) {
            value = value.copy(tileSwapBeforeRelease = value.tileSwapBeforeRelease + count)
        }
    }
}

internal data class HorizontalRealPathSnapshot(
    val pointerDown: Boolean = false,
    val gestureFrames: Int = 0,
    val tileRequestsBeforeRelease: Int = 0,
    val tileReadyBeforeRelease: Int = 0,
    val tileSwapBeforeRelease: Int = 0,
    val pageAheadRequestsBeforeRelease: Int = 0,
    val pageStripFrames: Int = 0,
    val pageStripRawInputAbsPx: Float = 0f,
    val pageStripDisplayedDeltaAbsPx: Float = 0f,
    val pageStripReverseDeltaCount: Int = 0,
    val pageIndexAtPointerDown: Int = -1,
    val pageIndexDuringPointerDown: Int = -1,
    val previousSlotId: Int = -1,
    val currentSlotId: Int = -1,
    val nextSlotId: Int = -1,
    val displayedCurrentPageX: Float = 0f,
    val displayedNeighborPageX: Float = Float.NaN,
    val slotRotationCount: Int = 0,
    val settleActive: Boolean = false,
    val settleStartCount: Int = 0,
    val offsetRebaseCount: Int = 0,
    val pagerProgrammaticScrollCount: Int = 0,
) {
    val pageStripDragGain: Float
        get() = if (pageStripRawInputAbsPx > 0f) {
            pageStripDisplayedDeltaAbsPx / pageStripRawInputAbsPx
        } else 1f
}
