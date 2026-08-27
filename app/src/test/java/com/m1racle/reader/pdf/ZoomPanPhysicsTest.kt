package com.m1racle.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomPanPhysicsTest {
    private val bounds = ZoomPanBounds(-100f, 100f, -80f, 0f)
    private val noOverscroll = AxisOverscrollPolicy.Disabled
    private val xOverscroll = AxisOverscrollPolicy(true, false, 28f)

    @Test
    fun `legal range consumes raw delta one to one`() {
        val result = ZoomPanPhysics.applyPan(
            ZoomPanTransform(2f, ZoomPanPoint(10f, -20f)),
            ZoomPanPoint(24f, -18f),
            bounds,
            noOverscroll,
        )
        assertEquals(ZoomPanPoint(34f, -38f), result.transform.legalOffsetPx)
        assertEquals(ZoomPanPoint(24f, -18f), result.legalConsumedPx)
        assertEquals(ZoomPanPoint.Zero, result.residualPx)
    }

    @Test
    fun `only edge residual enters resistance`() {
        val result = ZoomPanPhysics.applyPan(
            ZoomPanTransform(2f, ZoomPanPoint(90f, -20f)),
            ZoomPanPoint(30f, 0f),
            bounds,
            xOverscroll,
        )
        assertEquals(100f, result.transform.legalOffsetPx.x, 0f)
        assertEquals(10f, result.legalConsumedPx.x, 0f)
        assertEquals(20f, result.residualPx.x, 0f)
        assertEquals(20f, result.transform.rawOverscrollPx.x, 0f)
        assertEquals(ZoomPanPhysics.rubberBand(20f, 28f), result.transform.visualOverscrollPx.x, 0f)
    }

    @Test
    fun `legal and visual offsets remain separate across frames`() {
        val first = ZoomPanPhysics.applyPan(
            ZoomPanTransform(2f, ZoomPanPoint(100f, -20f)),
            ZoomPanPoint(20f, 0f),
            bounds,
            xOverscroll,
        ).transform
        val second = ZoomPanPhysics.applyPan(first, ZoomPanPoint(10f, 0f), bounds, xOverscroll).transform

        assertEquals(100f, first.legalOffsetPx.x, 0f)
        assertEquals(100f, second.legalOffsetPx.x, 0f)
        assertEquals(30f, second.rawOverscrollPx.x, 0f)
        assertEquals(100f + second.visualOverscrollPx.x, second.displayOffsetPx.x, 0f)
    }

    @Test
    fun `reverse motion unwinds visual pull before changing legal offset`() {
        val pulled = ZoomPanPhysics.applyPan(
            ZoomPanTransform(2f, ZoomPanPoint(100f, -20f)),
            ZoomPanPoint(20f, 0f),
            bounds,
            xOverscroll,
        ).transform
        val partial = ZoomPanPhysics.applyPan(pulled, ZoomPanPoint(-10f, 0f), bounds, xOverscroll)

        assertEquals(100f, partial.transform.legalOffsetPx.x, 0f)
        assertEquals(10f, partial.transform.rawOverscrollPx.x, 0f)
        assertEquals(-10f, partial.residualPx.x, 0f)
    }

    @Test
    fun `rubber band is monotonic symmetric and saturating`() {
        val values = listOf(1f, 10f, 100f, 10_000f).map {
            ZoomPanPhysics.rubberBand(it, 28f)
        }
        assertTrue(values.zipWithNext().all { (a, b) -> b > a })
        assertTrue(values.last() in 27.9f..28f)
        assertEquals(-values[2], ZoomPanPhysics.rubberBand(-100f, 28f), 0.0001f)
    }

    @Test
    fun `disabled axis hard clamps residual without display overscroll`() {
        val result = ZoomPanPhysics.applyPan(
            ZoomPanTransform(2f, ZoomPanPoint(100f, 0f)),
            ZoomPanPoint(50f, 60f),
            bounds,
            noOverscroll,
        )
        assertEquals(ZoomPanPoint(100f, 0f), result.transform.legalOffsetPx)
        assertEquals(ZoomPanPoint.Zero, result.transform.visualOverscrollPx)
        assertEquals(ZoomPanPoint(50f, 60f), result.residualPx)
    }

    @Test
    fun `spring target clears visual only and preserves legal offset`() {
        val pulled = ZoomPanPhysics.applyPan(
            ZoomPanTransform(2f, ZoomPanPoint(100f, -40f)),
            ZoomPanPoint(50f, 0f),
            bounds,
            xOverscroll,
        ).transform
        val target = ZoomPanPhysics.springTarget(pulled)

        assertEquals(pulled.scale, target.scale, 0f)
        assertEquals(pulled.legalOffsetPx, target.legalOffsetPx)
        assertEquals(ZoomPanPoint.Zero, target.rawOverscrollPx)
        assertEquals(ZoomPanPoint.Zero, target.visualOverscrollPx)
    }

    @Test
    fun `scale change preserves legal focal anchor and drops visual pull`() {
        val origin = ZoomPanPoint(500f, 8f)
        val focal = ZoomPanPoint(620f, 380f)
        val base = ZoomPanTransform(
            scale = 2f,
            legalOffsetPx = ZoomPanPoint(-100f, -50f),
            rawOverscrollPx = ZoomPanPoint(40f, 0f),
            visualOverscrollPx = ZoomPanPoint(20f, 0f),
        )
        val anchor = ZoomPanPhysics.captureFocalAnchor(base, focal, origin)
        val next = ZoomPanPhysics.applyScaleFromAnchor(
            transform = base,
            newScale = 3f,
            anchor = anchor,
            focalViewportPx = focal,
            transformOriginViewportPx = origin,
            newBounds = ZoomPanBounds(-1000f, 1000f, -1000f, 0f),
            scaleRange = ZoomPanPhysicsProfile.ScaleRange,
        )

        assertEquals(
            focal.x,
            origin.x + anchor.contentX * next.scale + next.legalOffsetPx.x,
            0.001f,
        )
        assertEquals(
            focal.y,
            origin.y + anchor.contentY * next.scale + next.legalOffsetPx.y,
            0.001f,
        )
        assertEquals(ZoomPanPoint.Zero, next.visualOverscrollPx)
    }

    @Test
    fun `shared scale range is continuous through two three four x`() {
        assertEquals(2f, ZoomPanPhysics.clampScale(2f, ZoomPanPhysicsProfile.ScaleRange), 0f)
        assertEquals(3f, ZoomPanPhysics.clampScale(3f, ZoomPanPhysicsProfile.ScaleRange), 0f)
        assertEquals(4f, ZoomPanPhysics.clampScale(4f, ZoomPanPhysicsProfile.ScaleRange), 0f)
        assertEquals(4f, ZoomPanPhysics.clampScale(9f, ZoomPanPhysicsProfile.ScaleRange), 0f)
    }

    @Test
    fun `one to two and two to one pointer changes are rebase only frames`() {
        val transform = ZoomPanTransform(2f, ZoomPanPoint(-300f, -20_000f))
        fun frame(previous: ZoomPanPointerEpoch?, pointers: List<ZoomPanPointer>) =
            ZoomPanPointerEpochReducer.frame(
                previous = previous,
                pointers = pointers,
                transform = transform,
                transformOriginViewportPx = ZoomPanPoint.Zero,
                boundsForScale = { ZoomPanBounds(-3000f, 0f, -200_000f, 0f) },
                scaleRange = ZoomPanPhysicsProfile.ScaleRange,
                overscrollPolicy = noOverscroll,
            )

        val one = frame(null, listOf(ZoomPanPointer(1, ZoomPanPoint(400f, 800f))))
        val two = frame(
            one.epoch,
            listOf(
                ZoomPanPointer(1, ZoomPanPoint(390f, 800f)),
                ZoomPanPointer(2, ZoomPanPoint(610f, 800f)),
            ),
        )
        val backToOne = frame(two.epoch, listOf(ZoomPanPointer(1, ZoomPanPoint(450f, 820f))))

        assertTrue(one.rebased)
        assertTrue(two.rebased)
        assertTrue(backToOne.rebased)
        assertEquals(transform, one.transform)
        assertEquals(transform, two.transform)
        assertEquals(transform, backToOne.transform)
    }

    @Test
    fun `stable pointer pair keeps focal anchor while centroid moves`() {
        val bounds = ZoomPanBounds(-3000f, 0f, -200_000f, 0f)
        val baseTransform = ZoomPanTransform(1f, ZoomPanPoint(0f, -20_000f))
        val base = ZoomPanPointerEpochReducer.frame(
            null,
            listOf(
                ZoomPanPointer(1, ZoomPanPoint(400f, 800f)),
                ZoomPanPointer(2, ZoomPanPoint(600f, 800f)),
            ),
            baseTransform,
            ZoomPanPoint.Zero,
            { bounds },
            ZoomPanPhysicsProfile.ScaleRange,
            noOverscroll,
        )
        val next = ZoomPanPointerEpochReducer.frame(
            base.epoch,
            listOf(
                ZoomPanPointer(1, ZoomPanPoint(300f, 820f)),
                ZoomPanPointer(2, ZoomPanPoint(700f, 820f)),
            ),
            baseTransform,
            ZoomPanPoint.Zero,
            { bounds },
            ZoomPanPhysicsProfile.ScaleRange,
            noOverscroll,
        )

        assertFalse(next.rebased)
        assertEquals(2f, next.transform.scale, 0f)
        assertEquals(base.epoch!!.focalAnchor.contentX, (500f - next.transform.legalOffsetPx.x) / 2f, 0.001f)
        assertEquals(base.epoch!!.focalAnchor.contentY, (820f - next.transform.legalOffsetPx.y) / 2f, 0.001f)
    }

    @Test
    fun `new pointer rebase preserves current animation frame until adapter cancels it`() {
        val springFrame = ZoomPanTransform(
            scale = 2f,
            legalOffsetPx = ZoomPanPoint(100f, -20f),
            rawOverscrollPx = ZoomPanPoint(10f, 0f),
            visualOverscrollPx = ZoomPanPoint(8f, 0f),
        )
        val frame = ZoomPanPointerEpochReducer.frame(
            previous = null,
            pointers = listOf(ZoomPanPointer(9, ZoomPanPoint(300f, 400f))),
            transform = springFrame,
            transformOriginViewportPx = ZoomPanPoint.Zero,
            boundsForScale = { bounds },
            scaleRange = ZoomPanPhysicsProfile.ScaleRange,
            overscrollPolicy = xOverscroll,
        )
        assertTrue(frame.rebased)
        assertEquals(springFrame, frame.transform)
    }

    @Test
    fun `viewport resize clamps legal position without folding visual pull`() {
        val transform = ZoomPanTransform(
            scale = 2f,
            legalOffsetPx = ZoomPanPoint(700f, -900f),
            rawOverscrollPx = ZoomPanPoint(10f, 0f),
            visualOverscrollPx = ZoomPanPoint(8f, 0f),
        )
        val clamped = ZoomPanPhysics.clampToBounds(
            transform,
            ZoomPanBounds(-400f, 400f, -600f, 0f),
        )
        assertEquals(ZoomPanPoint(400f, -600f), clamped.legalOffsetPx)
        assertEquals(transform.rawOverscrollPx, clamped.rawOverscrollPx)
        assertEquals(transform.visualOverscrollPx, clamped.visualOverscrollPx)
    }

    @Test
    fun `direct contact legal motion remains exact and independent of release animation`() {
        val start = ZoomPanTransform(
            scale = 2f,
            legalOffsetPx = ZoomPanPoint(-100f, -200f),
        )
        val result = ZoomPanPhysics.applyPan(
            transform = start,
            rawDeltaPx = ZoomPanPoint(37f, -29f),
            bounds = ZoomPanBounds(-500f, 500f, -800f, 0f),
            policy = AxisOverscrollPolicy.Disabled,
        )
        assertEquals(ZoomPanPoint(37f, -29f), result.legalConsumedPx)
        assertEquals(ZoomPanPoint.Zero, result.residualPx)
        assertEquals(ZoomPanPoint(-63f, -229f), result.transform.legalOffsetPx)
    }

    @Test
    fun `horizontal pager ownership remains outside shared transform physics`() {
        assertEquals(HorizontalGestureOwner.PAGE_STRIP, HorizontalZoomPanPhysics.owner(1.01f))
        assertEquals(HorizontalGestureOwner.INNER_PAN, HorizontalZoomPanPhysics.owner(1.011f))
    }
}
