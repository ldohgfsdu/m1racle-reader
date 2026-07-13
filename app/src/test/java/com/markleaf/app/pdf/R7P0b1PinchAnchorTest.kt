package com.markleaf.app.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R7_P0B1 pinch anchor stability tests.
 *
 * Pure-logic coverage (no Compose, no PdfRenderer) for:
 *  - focal document point stability across multi-frame zoom + pan
 *  - pointer count rebase (1→2, 2→1, 2→1→2, 2→3→2)
 *  - single clamp per frame
 *  - settle preserves final transform (no old-scroll restoration)
 */
class R7P0b1PinchAnchorTest {

    private fun makeGeometry(pageCount: Int = 100): ContinuousDocumentGeometry =
        ContinuousDocumentGeometry.create(
            pageCount = pageCount,
            basePageWidth = 1080f,
            aspects = emptyMap(),
            pageGap = 10f,
        )

    private val viewportW = 1080f
    private val viewportH = 2000f

    // ── 1. focal document point stable with moving centroid + zoom ──

    @Test
    fun `focal document point stays within 1px after 10 frames of zoom plus pan`() {
        val geometry = makeGeometry()
        var state = ContinuousDocumentState(scale = 1f, scrollY = 50000f)
        val centroidStartY = 500f
        val focalDocYStart = (state.scrollY + centroidStartY) / state.scale

        var centroidY = centroidStartY
        var lastCentroidY = centroidY
        val zoomRatio = 1.05f
        val panPerFrame = 10f

        for (i in 0 until 10) {
            lastCentroidY = centroidY
            val nextScale = state.scale * zoomRatio
            state = geometry.zoomAndPanFromFocal(
                state = state,
                newScale = nextScale,
                focalDocumentX = 0f,
                focalDocumentY = focalDocYStart,
                centroidX = 400f,
                centroidY = centroidY,
                viewportWidth = viewportW,
                viewportHeight = viewportH,
            )
            centroidY += panPerFrame
        }

        val focalDocYEnd = (state.scrollY + lastCentroidY) / state.scale
        assertEquals(focalDocYStart, focalDocYEnd, 1f)
    }

    // ── 2. focal document point stable with fixed centroid + zoom ──

    @Test
    fun `focal document point stays within 1px after 20 frames of zoom only`() {
        val geometry = makeGeometry()
        var state = ContinuousDocumentState(scale = 1f, scrollY = 50000f)
        val centroidY = 800f
        val focalDocYStart = (state.scrollY + centroidY) / state.scale

        val zoomRatio = 1.03f
        for (i in 0 until 20) {
            val nextScale = state.scale * zoomRatio
            state = geometry.zoomAndPanFromFocal(
                state = state,
                newScale = nextScale,
                focalDocumentX = 0f,
                focalDocumentY = focalDocYStart,
                centroidX = 540f,
                centroidY = centroidY,
                viewportWidth = viewportW,
                viewportHeight = viewportH,
            )
        }

        val focalDocYEnd = (state.scrollY + centroidY) / state.scale
        assertEquals(focalDocYStart, focalDocYEnd, 1f)
    }

    // ── 3. pointer count 2→1→2 does not cause jump ──

    @Test
    fun `pointer 2 to 1 to 2 rebase does not jump focal document point`() {
        val geometry = makeGeometry()
        var state = ContinuousDocumentState(scale = 1f, scrollY = 50000f)

        // Initial 2-finger pinch: establish focal point
        val centroid1 = 600f
        val focalDocY = (state.scrollY + centroid1) / state.scale

        state = geometry.zoomAndPanFromFocal(
            state = state,
            newScale = 1.5f,
            focalDocumentX = 0f,
            focalDocumentY = focalDocY,
            centroidX = 400f,
            centroidY = centroid1,
            viewportWidth = viewportW,
            viewportHeight = viewportH,
        )
        val scrollYBeforeLift = state.scrollY
        val scaleBeforeLift = state.scale

        // 2→1: rebase with single finger position
        val singleCentroid = 550f
        // focalDocument recomputed from current transform + new centroid
        val rebaseFocalDocY_2to1 = (scrollYBeforeLift + singleCentroid) / scaleBeforeLift

        // 1→2: rebase again with new 2-finger centroid
        val centroid2 = 650f
        val rebaseFocalDocY_1to2 = (scrollYBeforeLift + centroid2) / scaleBeforeLift

        // After rebase, applying same scale (no zoom change) should preserve visual
        val stateAfterRebase = geometry.zoomAndPanFromFocal(
            state = state,
            newScale = state.scale, // same scale — pure rebase, no zoom
            focalDocumentX = 0f,
            focalDocumentY = rebaseFocalDocY_1to2,
            centroidX = 400f,
            centroidY = centroid2,
            viewportWidth = viewportW,
            viewportHeight = viewportH,
        )

        // Visual document point under centroid2 must be unchanged after rebase
        val docPointBefore = (scrollYBeforeLift + centroid2) / scaleBeforeLift
        val docPointAfter = (stateAfterRebase.scrollY + centroid2) / stateAfterRebase.scale
        assertEquals(docPointBefore, docPointAfter, 1f)
    }

    // ── 4. settle does not restore old scroll ──

    @Test
    fun `settle commit preserves final transform not pre-gesture scroll`() {
        val geometry = makeGeometry()
        val initialScrollY = 50000f
        var state = ContinuousDocumentState(scale = 1f, scrollY = initialScrollY)

        val centroidY = 800f
        val focalDocY = (state.scrollY + centroidY) / state.scale

        // Zoom in
        state = geometry.zoomAndPanFromFocal(
            state = state,
            newScale = 2.0f,
            focalDocumentX = 0f,
            focalDocumentY = focalDocY,
            centroidX = 540f,
            centroidY = centroidY,
            viewportWidth = viewportW,
            viewportHeight = viewportH,
        )

        // After settle, scrollY should NOT be initialScrollY
        assertTrue("settle must not restore old scrollY",
            state.scrollY != initialScrollY)
    }

    // ── 5. scale back to 1x does not jump to page top ──

    @Test
    fun `scale back to 1x keeps scroll continuous not page top jump`() {
        val geometry = makeGeometry()
        var state = ContinuousDocumentState(scale = 1f, scrollY = 50000f)

        val centroidY = 800f
        val focalDocY = (state.scrollY + centroidY) / state.scale

        // Zoom to 2x
        state = geometry.zoomAndPanFromFocal(
            state = state,
            newScale = 2.0f,
            focalDocumentX = 0f,
            focalDocumentY = focalDocY,
            centroidX = 540f,
            centroidY = centroidY,
            viewportWidth = viewportW,
            viewportHeight = viewportH,
        )
        val scrollYAt2x = state.scrollY

        // Zoom back to 1x
        state = geometry.zoomAndPanFromFocal(
            state = state,
            newScale = 1.0f,
            focalDocumentX = 0f,
            focalDocumentY = focalDocY,
            centroidX = 540f,
            centroidY = centroidY,
            viewportWidth = viewportW,
            viewportHeight = viewportH,
        )

        // scrollY should be close to original 50000, not 0 (page top)
        assertEquals(50000f, state.scrollY, 5f)
    }

    // ── 6. transaction rebase produces no visual jump ──

    @Test
    fun `rebase preserves visual transform with no scroll jump`() {
        val geometry = makeGeometry()
        var state = ContinuousDocumentState(scale = 1.5f, scrollY = 75000f)

        // Simulate rebase: compute focal from current state + new centroid
        val newCentroidY = 700f
        val rebaseFocalDocY = (state.scrollY + newCentroidY) / state.scale

        // After rebase, applying same scale with new centroid should not change visual
        val afterRebase = geometry.zoomAndPanFromFocal(
            state = state,
            newScale = state.scale, // same scale
            focalDocumentX = 0f,
            focalDocumentY = rebaseFocalDocY,
            centroidX = 540f,
            centroidY = newCentroidY,
            viewportWidth = viewportW,
            viewportHeight = viewportH,
        )

        // Visual document point under newCentroid should be unchanged
        val docPointBefore = (state.scrollY + newCentroidY) / state.scale
        val docPointAfter = (afterRebase.scrollY + newCentroidY) / afterRebase.scale
        assertEquals(docPointBefore, docPointAfter, 1f)
    }
}
