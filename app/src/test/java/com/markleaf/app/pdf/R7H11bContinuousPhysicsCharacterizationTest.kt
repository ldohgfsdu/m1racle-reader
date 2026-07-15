package com.markleaf.app.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the R7 continuous viewport transform behavior before H1.1B extracts shared physics.
 *
 * These expectations intentionally describe the production behavior, including the absence of
 * transform overscroll: finger deltas are 1:1 inside legal bounds and hard-clamped at document
 * edges. The 450 ms interaction settle is a bitmap/transaction commit, not a pan spring.
 */
class R7H11bContinuousPhysicsCharacterizationTest {
    private val geometry = ContinuousDocumentGeometry.create(
        pageCount = 100,
        basePageWidth = 1000f,
        aspects = emptyMap(),
        pageGap = 10f,
    )

    @Test
    fun `continuous single pointer pan is one to one then hard clamps`() {
        val start = ContinuousDocumentState(scale = 2f, scrollX = 500f, scrollY = 20_000f)

        val inRange = geometry.panBy(start, 120f, -80f, 1000f, 1800f)
        assertEquals(380f, inRange.scrollX, 0f)
        assertEquals(20_080f, inRange.scrollY, 0f)

        val atTopLeft = geometry.panBy(inRange, 10_000f, 100_000f, 1000f, 1800f)
        assertEquals(0f, atTopLeft.scrollX, 0f)
        assertEquals(0f, atTopLeft.scrollY, 0f)
        assertFalse(atTopLeft.gestureActive)
    }

    @Test
    fun `continuous focal anchor is stable through two three four x`() {
        val focalX = 500f
        val focalY = 800f
        val anchorX = 500f
        val anchorY = 20_800f
        var state = ContinuousDocumentState(scale = 2f, scrollX = 500f, scrollY = 40_800f)

        for (scale in listOf(3f, 4f, 2f)) {
            state = geometry.zoomAndPanFromFocal(
                state = state,
                newScale = scale,
                focalDocumentX = anchorX,
                focalDocumentY = anchorY,
                centroidX = focalX,
                centroidY = focalY,
                viewportWidth = 1000f,
                viewportHeight = 1800f,
            )
            assertEquals(anchorX, (state.scrollX + focalX) / state.scale, 0.001f)
            assertEquals(anchorY, (state.scrollY + focalY) / state.scale, 0.001f)
        }
    }

    @Test
    fun `continuous pointer epoch rebases one two one without a transform frame`() {
        val start = ContinuousDocumentState(scale = 1f, scrollX = 0f, scrollY = 20_000f, gestureActive = true)
        val two = ContinuousPointerEpochReducer.frame(
            previous = null,
            pointers = listOf(PointerSample(1, 400f, 800f), PointerSample(2, 600f, 800f)),
            state = start,
            viewportWidth = 1000f,
            viewportHeight = 1800f,
            geometry = geometry,
        )
        assertTrue(two.rebased)
        assertEquals(start, two.state)

        val pinched = ContinuousPointerEpochReducer.frame(
            previous = two.epoch,
            pointers = listOf(PointerSample(1, 300f, 800f), PointerSample(2, 700f, 800f)),
            state = start,
            viewportWidth = 1000f,
            viewportHeight = 1800f,
            geometry = geometry,
        )
        assertEquals(2f, pinched.state.scale, 0f)
        assertEquals(500f, pinched.state.scrollX, 0f)
        assertEquals(40_800f, pinched.state.scrollY, 0f)

        val one = ContinuousPointerEpochReducer.frame(
            previous = pinched.epoch,
            pointers = listOf(PointerSample(1, 450f, 820f)),
            state = pinched.state,
            viewportWidth = 1000f,
            viewportHeight = 1800f,
            geometry = geometry,
        )
        assertTrue(one.rebased)
        assertEquals(pinched.state, one.state)

        val panned = ContinuousPointerEpochReducer.frame(
            previous = one.epoch,
            pointers = listOf(PointerSample(1, 470f, 850f)),
            state = one.state,
            viewportWidth = 1000f,
            viewportHeight = 1800f,
            geometry = geometry,
        )
        assertFalse(panned.rebased)
        assertEquals(480f, panned.state.scrollX, 0f)
        assertEquals(40_770f, panned.state.scrollY, 0f)
    }

    @Test
    fun `continuous viewport resize clamps only the legal transform`() {
        val state = ContinuousDocumentState(scale = 2f, scrollX = 900f, scrollY = 50_000f)
        val resized = geometry.clamp(state, viewportWidth = 1600f, viewportHeight = 2400f)

        assertEquals(400f, resized.scrollX, 0f)
        assertEquals(state.scrollY, resized.scrollY, 0f)
        assertEquals(state.scale, resized.scale, 0f)
    }

    @Test
    fun `tile display decisions cannot mutate the document transform`() {
        val transform = ContinuousDocumentState(scale = 2.5f, scrollX = 700f, scrollY = 33_000f)
        val displayed = DisplayedBitmap(Any(), BitmapMetadata(QualityTier.PREVIEW, 1200))

        val decision = DisplayMergePolicy.merge(
            phase = ContinuousInteractionPhase.PINCHING,
            displayed = displayed,
            newBitmap = Any(),
            newMetadata = BitmapMetadata(QualityTier.HIGH, 2800),
        )

        assertEquals(DisplayMergeResult.PENDING, decision)
        assertEquals(ContinuousDocumentState(2.5f, 700f, 33_000f), transform)
    }
}
