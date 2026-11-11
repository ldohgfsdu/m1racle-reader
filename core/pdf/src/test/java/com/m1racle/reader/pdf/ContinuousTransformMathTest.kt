package com.m1racle.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousTransformMathTest {
    private val viewport = TransformSize(1000f, 1600f)

    @Test fun gestureRatioBelowOneShrinksFromThree() {
        assertEquals(2.1f, ContinuousTransformMath.targetScale(3f, 0.7f), 0.0001f)
        assertEquals(1f, ContinuousTransformMath.targetScale(3f, 0.1f), 0.0001f)
    }

    @Test fun targetScaleClampsOnlyFinalProduct() {
        assertEquals(3f, ContinuousTransformMath.targetScale(2f, 2f), 0f)
        assertEquals(1f, ContinuousTransformMath.targetScale(2f, 0.2f), 0f)
    }

    @Test fun leftTopAnchorStaysFixed() = assertAnchor(TransformPoint(0f, 0f))
    @Test fun centerAnchorStaysFixed() = assertAnchor(TransformPoint(500f, 800f))
    @Test fun rightBottomAnchorStaysFixed() = assertAnchor(TransformPoint(1000f, 1600f))

    private fun assertAnchor(centroid: TransformPoint) {
        val next = ContinuousTransformMath.anchoredOffset(
            1f, 2f, TransformOffset(0f, 0f), centroid,
        )
        val contentX = centroid.x
        val contentY = centroid.y
        assertEquals(centroid.x, contentX * 2f + next.x, 0.001f)
        assertEquals(centroid.y, contentY * 2f + next.y, 0.001f)
    }

    @Test fun zoomInThenOutHasNoAnchorDrift() {
        val centroid = TransformPoint(270f, 940f)
        val zoomed = ContinuousTransformMath.anchoredOffset(
            1f, 2.6f, TransformOffset(0f, 0f), centroid,
        )
        val restored = ContinuousTransformMath.anchoredOffset(2.6f, 1f, zoomed, centroid)
        assertTrue(ContinuousTransformMath.approximatelySame(restored, TransformOffset(0f, 0f), 0.01f))
    }

    @Test fun scaleOneAlwaysZerosOffset() {
        assertEquals(
            TransformOffset(0f, 0f),
            ContinuousTransformMath.clampOffset(TransformOffset(-300f, -400f), 1f, viewport),
        )
    }

    @Test fun offsetsClampAtAllEdges() {
        assertEquals(
            TransformOffset(-1000f, -1600f),
            ContinuousTransformMath.clampOffset(TransformOffset(-5000f, -5000f), 2f, viewport),
        )
        assertEquals(
            TransformOffset(0f, 0f),
            ContinuousTransformMath.clampOffset(TransformOffset(100f, 100f), 2f, viewport),
        )
    }

    @Test fun boundsUseDisplayedPageSizeNotOnlyViewport() {
        val page = TransformSize(900f, 1200f)
        val result = ContinuousTransformMath.clampOffset(
            TransformOffset(-5000f, -5000f), 2f, viewport, page,
        )
        assertEquals(-800f, result.x, 0f)
        assertEquals(-800f, result.y, 0f)
    }

    @Test fun internalVerticalPanConsumesAllDeltaBeforeBoundary() {
        val result = ContinuousTransformMath.consumePan(
            TransformOffset(-200f, -500f), TransformOffset(0f, -100f), 2f, viewport,
        )
        assertEquals(-600f, result.offset.y, 0f)
        assertEquals(0f, result.remainder.y, 0f)
    }

    @Test fun verticalBoundaryReturnsRemainderToList() {
        val result = ContinuousTransformMath.consumePan(
            TransformOffset(-200f, -1600f), TransformOffset(0f, -120f), 2f, viewport,
        )
        assertEquals(-1600f, result.offset.y, 0f)
        assertEquals(-120f, result.remainder.y, 0f)
        assertEquals(60f, ContinuousTransformMath.listScrollDelta(result.remainder.y, 2f), 0f)
    }

    @Test fun panDoesNotChangeScaleState() {
        val before = 2.25f
        ContinuousTransformMath.consumePan(
            TransformOffset(-100f, -100f), TransformOffset(20f, 30f), before, viewport,
        )
        assertEquals(2.25f, before, 0f)
    }
}
