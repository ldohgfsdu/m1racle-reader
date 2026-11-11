package com.m1racle.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G1: reproduces the two hot-path defects found in the read-only audit.
 *
 * History: written RED in G1-1 against a stub gate, green since G1-2 without any
 * change to the assertions. The class name is kept so the RED run
 * (`output/pdf-follow-clarity-20261108/g1-red.xml`, tests=2 failures=2) and the
 * GREEN run can be diffed case by case.
 *
 * Defect 1 — `onContinuousViewportChanged` calls `ensurePreviewAround` on every
 * viewport frame even when the visible page set, primary page, direction and
 * phase are unchanged, so `pageCache.retainOnly` and the whole render re-plan
 * run once per frame while the finger is moving.
 *
 * Defect 2 — after a drag direction reversal, jobs keyed for the old direction
 * are never identified as superseded, so an old-direction SCROLL_PREVIEW job
 * can occupy the serial PdfRenderer mutex ahead of the newly visible page.
 */
class ContinuousViewportDispatchGateRedTest {

    private fun signature(anchorDeltaY: Float) = ContinuousDispatchSignature(
        documentId = "doc-94",
        pageCount = 12,
        viewportWidthPx = 1080,
        scale = 1f,
        primaryPage = 4,
        visiblePages = setOf(4, 5),
        direction = ContinuousScrollDirection.FORWARD,
        phase = ContinuousScrollPhase.DRAGGING,
        zoomGestureActive = false,
        entryPhaseReady = true,
        generation = 7L,
    )

    @Test
    fun fixedVisiblePagesScrollingMustNotRepeatCacheTrimmingAndTaskReplanning() {
        val gate = ContinuousViewportDispatchGate()
        // 60 consecutive frames: only scrollY advances (modelled by the anchor
        // delta); every dimension that affects job validity is unchanged.
        repeat(60) { frame ->
            gate.shouldDispatch(signature(anchorDeltaY = -24f * (frame + 1)))
        }
        assertEquals(
            "heavy dispatch must happen once for an unchanged effective signature; " +
                "current code dispatches on every scroll frame and trims the cache each time",
            1L,
            gate.dispatchCount,
        )
        assertEquals(59L, gate.suppressedCount)
    }

    @Test
    fun reversedDragMustSupersedeOldDirectionJobsOutsideTheVisibleWindow() {
        val gate = ContinuousViewportDispatchGate()
        val oldJobs = mapOf(
            12 to ContinuousRenderKey("doc-94", 12, com.m1racle.reader.core.model.QualityTier.SCROLL_PREVIEW, 540),
            13 to ContinuousRenderKey("doc-94", 13, com.m1racle.reader.core.model.QualityTier.SCROLL_PREVIEW, 540),
            4 to ContinuousRenderKey("doc-94", 4, com.m1racle.reader.core.model.QualityTier.SCROLL_PREVIEW, 540),
        )
        val superseded = gate.supersededOnReversal(
            jobs = oldJobs,
            previousDirection = ContinuousScrollDirection.FORWARD,
            currentDirection = ContinuousScrollDirection.BACKWARD,
            visiblePages = setOf(4, 5),
        )
        assertTrue(
            "old-direction pages 12 and 13 must be cancelled so the newly visible " +
                "page can take the renderer mutex immediately",
            superseded.containsAll(setOf(12, 13)),
        )
        assertTrue("current visible pages must never be cancelled", 4 !in superseded)
    }
}
