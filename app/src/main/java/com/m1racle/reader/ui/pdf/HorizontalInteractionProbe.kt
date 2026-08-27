package com.m1racle.reader.ui.pdf

import com.m1racle.reader.pdf.HorizontalPanState
import com.m1racle.reader.pdf.PdfGestureFrameSummary
import com.m1racle.reader.pdf.PageStripSlotRole
import com.m1racle.reader.pdf.PageStripTransition
import com.m1racle.reader.pdf.ThreePageStripPhysics
import com.m1racle.reader.pdf.ThreePageStripState
import com.m1racle.reader.pdf.ZoomPanPoint
import com.m1racle.reader.pdf.ZoomedPageTransitionPhase
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Optional QA observer. Production behavior never reads from this probe. */
internal class HorizontalInteractionProbe {
    private val lock = Any()
    private var value = HorizontalInteractionSnapshot()

    fun snapshot(): HorizontalInteractionSnapshot = synchronized(lock) { value }

    fun recordTransform(scale: Float, pan: HorizontalPanState) = synchronized(lock) {
        value = value.copy(
            scale = scale,
            pan = pan,
            maxVisualOverscrollXPx = max(value.maxVisualOverscrollXPx, abs(pan.visualOverscrollX)),
            maxVisualOverscrollYPx = max(value.maxVisualOverscrollYPx, abs(pan.visualOverscrollY)),
            minVisualOverscrollXPx = min(value.minVisualOverscrollXPx, pan.visualOverscrollX),
            maxPositiveVisualOverscrollXPx = max(
                value.maxPositiveVisualOverscrollXPx,
                pan.visualOverscrollX,
            ),
            minVisualOverscrollYPx = min(value.minVisualOverscrollYPx, pan.visualOverscrollY),
            maxPositiveVisualOverscrollYPx = max(
                value.maxPositiveVisualOverscrollYPx,
                pan.visualOverscrollY,
            ),
        )
    }

    fun recordPointerRebase(before: HorizontalPanState, after: HorizontalPanState) = synchronized(lock) {
        val jump = kotlin.math.hypot(
            (after.displayOffsetX - before.displayOffsetX).toDouble(),
            (after.displayOffsetY - before.displayOffsetY).toDouble(),
        ).toFloat()
        value = value.copy(pointerTransitionMaxJumpPx = max(value.pointerTransitionMaxJumpPx, jump))
    }

    fun recordFocalAnchorDrift(driftPx: Float) = synchronized(lock) {
        value = value.copy(maxFocalAnchorDriftPx = max(value.maxFocalAnchorDriftPx, driftPx))
    }

    fun recordPanFrame(
        rawDeltaPx: ZoomPanPoint,
        legalConsumedPx: ZoomPanPoint,
        residualPx: ZoomPanPoint,
    ) = synchronized(lock) {
        value = value.copy(
            inRangeRawXAbsPx = value.inRangeRawXAbsPx +
                if (abs(residualPx.x) < 0.01f) abs(rawDeltaPx.x) else 0f,
            inRangeLegalXAbsPx = value.inRangeLegalXAbsPx +
                if (abs(residualPx.x) < 0.01f) abs(legalConsumedPx.x) else 0f,
            inRangeRawYAbsPx = value.inRangeRawYAbsPx +
                if (abs(residualPx.y) < 0.01f) abs(rawDeltaPx.y) else 0f,
            inRangeLegalYAbsPx = value.inRangeLegalYAbsPx +
                if (abs(residualPx.y) < 0.01f) abs(legalConsumedPx.y) else 0f,
        )
    }

    fun recordPageTransition(state: PageStripTransition, offsetPx: Float) = synchronized(lock) {
        value = value.copy(
            handoffPhase = state.phase,
            handoffGeneration = state.gestureGeneration,
            handoffAccumulatedPx = max(value.handoffAccumulatedPx, abs(offsetPx)),
            pageTransitionPhase = state.phase,
            pageTransitionOffsetPx = offsetPx,
        )
    }

    fun beginPageStripGesture(
        strip: ThreePageStripState,
        pageIndex: Int,
        offsetPx: Float,
        viewportWidthPx: Float,
    ) = synchronized(lock) {
        value = value.copy(
            pointerDown = true,
            pageIndex = pageIndex,
            pageIndexAtPointerDown = pageIndex,
            pageIndexChangeCount = 0,
            pageChangeCount = 0,
            previousSlotId = strip.previousSlotId,
            currentSlotId = strip.currentSlotId,
            nextSlotId = strip.nextSlotId,
            slotRotationCount = 0,
            settleActive = false,
            settleStartCount = 0,
            offsetRebaseCount = 0,
            pagerProgrammaticScrollCount = 0,
            pageStripRawInputAbsPx = 0f,
            pageStripDisplayedDeltaAbsPx = 0f,
            reverseDeltaCount = 0,
            displayedCurrentPageX = ThreePageStripPhysics.slotTranslationPx(
                PageStripSlotRole.CURRENT,
                viewportWidthPx,
                offsetPx,
            ),
            displayedNeighborPageX = Float.NaN,
            slotRotationVisualJumpPx = 0f,
        )
    }

    fun recordPageStripFrame(
        rawDeltaX: Float,
        displayedDeltaX: Float,
        strip: ThreePageStripState,
        transition: PageStripTransition,
        offsetPx: Float,
        viewportWidthPx: Float,
    ) = synchronized(lock) {
        val neighborRole = when (transition.direction) {
            com.m1racle.reader.pdf.HorizontalPageTransitionDirection.PREVIOUS ->
                PageStripSlotRole.PREVIOUS
            com.m1racle.reader.pdf.HorizontalPageTransitionDirection.NEXT -> PageStripSlotRole.NEXT
            null -> null
        }
        val reversed = abs(rawDeltaX) > 0.01f && abs(displayedDeltaX) > 0.01f &&
            rawDeltaX * displayedDeltaX < 0f
        val stripActive = transition.active || abs(displayedDeltaX) > 0.01f
        value = value.copy(
            previousSlotId = strip.previousSlotId,
            currentSlotId = strip.currentSlotId,
            nextSlotId = strip.nextSlotId,
            pageStripRawInputAbsPx = value.pageStripRawInputAbsPx +
                if (stripActive) abs(rawDeltaX) else 0f,
            pageStripDisplayedDeltaAbsPx = value.pageStripDisplayedDeltaAbsPx +
                if (stripActive) abs(displayedDeltaX) else 0f,
            reverseDeltaCount = value.reverseDeltaCount + if (stripActive && reversed) 1 else 0,
            displayedCurrentPageX = ThreePageStripPhysics.slotTranslationPx(
                PageStripSlotRole.CURRENT,
                viewportWidthPx,
                offsetPx,
            ),
            displayedNeighborPageX = neighborRole?.let {
                ThreePageStripPhysics.slotTranslationPx(it, viewportWidthPx, offsetPx)
            } ?: Float.NaN,
        )
    }

    fun recordPageStripPointerUp() = synchronized(lock) {
        value = value.copy(pointerDown = false)
    }

    fun recordStripSettleStart() = synchronized(lock) {
        value = value.copy(
            settleActive = true,
            settleStartCount = value.settleStartCount + 1,
        )
    }

    fun recordSlotRotation(
        strip: ThreePageStripState,
        visualJumpPx: Float,
    ) = synchronized(lock) {
        value = value.copy(
            previousSlotId = strip.previousSlotId,
            currentSlotId = strip.currentSlotId,
            nextSlotId = strip.nextSlotId,
            slotRotationCount = value.slotRotationCount + 1,
            slotRotationVisualJumpPx = max(value.slotRotationVisualJumpPx, abs(visualJumpPx)),
        )
    }

    fun recordOffsetRebase() = synchronized(lock) {
        value = value.copy(offsetRebaseCount = value.offsetRebaseCount + 1)
    }

    fun recordStripSurfaces(
        previousBaseReady: Boolean,
        currentBaseReady: Boolean,
        nextBaseReady: Boolean,
        visibleNeighborBitmapId: Int,
    ) = synchronized(lock) {
        value = value.copy(
            previousBaseReady = previousBaseReady,
            currentBaseReady = currentBaseReady,
            nextBaseReady = nextBaseReady,
            visibleNeighborBitmapId = visibleNeighborBitmapId,
        )
    }

    fun recordStripSettle(targetPx: Float, settledPx: Float, rebasedPx: Float) = synchronized(lock) {
        value = value.copy(
            stripSettleTargetPx = targetPx,
            stripSettleFinalPx = settledPx,
            pageTransitionOffsetPx = rebasedPx,
            settleActive = false,
        )
    }

    fun recordGestureSummary(summary: PdfGestureFrameSummary) = synchronized(lock) {
        value = value.copy(
            gestureFrameCount = summary.frameCount,
            inputLatencyP50Ms = summary.inputLatencyP50Ms,
            inputLatencyP95Ms = summary.inputLatencyP95Ms,
            maxInputLatencyMs = summary.maxInputLatencyMs,
            maxPageTransitionOffsetPx = summary.maxPageTransitionOffsetPx,
        )
    }

    fun recordSpring(durationMs: Long) = synchronized(lock) {
        value = value.copy(springDurationMs = durationMs)
    }

    fun recordPageCommit(outgoingScale: Float, targetScale: Float, targetPage: Int) = synchronized(lock) {
        val pageActuallyChanged = targetPage != value.pageIndex
        value = value.copy(
            pageIndex = targetPage,
            pageChangeCount = value.pageChangeCount + if (pageActuallyChanged) 1 else 0,
            pageIndexChangeCount = value.pageIndexChangeCount + if (pageActuallyChanged) 1 else 0,
            outgoingScaleAfterCommit = targetScale,
            targetScaleAfterCommit = targetScale,
            outgoingScaleBeforeCommit = outgoingScale,
            handoffPhase = ZoomedPageTransitionPhase.SETTLED,
            pageIndexChangeTimeNanos = System.nanoTime(),
        )
    }

    fun recordPage(page: Int) = synchronized(lock) {
        value = value.copy(pageIndex = page)
    }

    fun recordLayout(
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        pageTopPx: Float,
        fittedPageWidthPx: Float,
        fittedPageHeightPx: Float,
    ) = synchronized(lock) {
        value = value.copy(
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx,
            pageTopPx = pageTopPx,
            fittedPageWidthPx = fittedPageWidthPx,
            fittedPageHeightPx = fittedPageHeightPx,
        )
    }
}

internal data class HorizontalInteractionSnapshot(
    val scale: Float = 1f,
    val pan: HorizontalPanState = HorizontalPanState(),
    val handoffPhase: ZoomedPageTransitionPhase = ZoomedPageTransitionPhase.IDLE,
    val handoffGeneration: Long = 0L,
    val handoffAccumulatedPx: Float = 0f,
    val pageTransitionPhase: ZoomedPageTransitionPhase = ZoomedPageTransitionPhase.IDLE,
    val pageTransitionOffsetPx: Float = 0f,
    val stripSettleTargetPx: Float = 0f,
    val stripSettleFinalPx: Float = 0f,
    val gestureFrameCount: Int = 0,
    val inputLatencyP50Ms: Long = 0L,
    val inputLatencyP95Ms: Long = 0L,
    val maxInputLatencyMs: Long = 0L,
    val maxPageTransitionOffsetPx: Float = 0f,
    val pageIndexChangeTimeNanos: Long = 0L,
    val previousBaseReady: Boolean = false,
    val currentBaseReady: Boolean = false,
    val nextBaseReady: Boolean = false,
    val visibleNeighborBitmapId: Int = -1,
    val pageIndex: Int = 0,
    val pageChangeCount: Int = 0,
    val pointerDown: Boolean = false,
    val pageIndexAtPointerDown: Int = 0,
    val pageIndexChangeCount: Int = 0,
    val previousSlotId: Int = -1,
    val currentSlotId: Int = -1,
    val nextSlotId: Int = -1,
    val slotRotationCount: Int = 0,
    val settleActive: Boolean = false,
    val settleStartCount: Int = 0,
    val offsetRebaseCount: Int = 0,
    val pagerProgrammaticScrollCount: Int = 0,
    val displayedCurrentPageX: Float = 0f,
    val displayedNeighborPageX: Float = Float.NaN,
    val pageStripRawInputAbsPx: Float = 0f,
    val pageStripDisplayedDeltaAbsPx: Float = 0f,
    val reverseDeltaCount: Int = 0,
    val slotRotationVisualJumpPx: Float = 0f,
    val outgoingScaleBeforeCommit: Float = 1f,
    val outgoingScaleAfterCommit: Float = 1f,
    val targetScaleAfterCommit: Float = 1f,
    val maxVisualOverscrollXPx: Float = 0f,
    val maxVisualOverscrollYPx: Float = 0f,
    val minVisualOverscrollXPx: Float = 0f,
    val maxPositiveVisualOverscrollXPx: Float = 0f,
    val minVisualOverscrollYPx: Float = 0f,
    val maxPositiveVisualOverscrollYPx: Float = 0f,
    val springDurationMs: Long = 0L,
    val pointerTransitionMaxJumpPx: Float = 0f,
    val maxFocalAnchorDriftPx: Float = 0f,
    val viewportWidthPx: Float = 0f,
    val viewportHeightPx: Float = 0f,
    val pageTopPx: Float = 0f,
    val fittedPageWidthPx: Float = 0f,
    val fittedPageHeightPx: Float = 0f,
    val inRangeRawXAbsPx: Float = 0f,
    val inRangeLegalXAbsPx: Float = 0f,
    val inRangeRawYAbsPx: Float = 0f,
    val inRangeLegalYAbsPx: Float = 0f,
) {
    val legalDragGainX: Float
        get() = if (inRangeRawXAbsPx > 0f) inRangeLegalXAbsPx / inRangeRawXAbsPx else 1f
    val legalDragGainY: Float
        get() = if (inRangeRawYAbsPx > 0f) inRangeLegalYAbsPx / inRangeRawYAbsPx else 1f
    val pageStripDragGainX: Float
        get() = if (pageStripRawInputAbsPx > 0f) {
            pageStripDisplayedDeltaAbsPx / pageStripRawInputAbsPx
        } else 1f

}
