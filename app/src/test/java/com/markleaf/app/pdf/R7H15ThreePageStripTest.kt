package com.markleaf.app.pdf

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class R7H15ThreePageStripTest {
    private val width = 1_080f

    @Test
    fun `middle page owns previous current next in one coordinate space`() {
        val slots = ThreePageStripState(currentPage = 1, pageCount = 3).slots()
        assertEquals(listOf(0, 1, 2), slots.map { it.pageIndex })
        assertEquals(
            listOf(-width, 0f, width),
            slots.map { ThreePageStripPhysics.slotTranslationPx(it.role, width, 0f) },
        )
    }

    @Test
    fun `document boundaries have no fake outside page`() {
        val first = ThreePageStripState(currentPage = 0, pageCount = 3).slots()
        val last = ThreePageStripState(currentPage = 2, pageCount = 3).slots()
        assertNull(first.first { it.role == PageStripSlotRole.PREVIOUS }.pageIndex)
        assertNull(last.first { it.role == PageStripSlotRole.NEXT }.pageIndex)
    }

    @Test
    fun `REAL THREE PAGE STRIP PREVIEW follows first edge residual then raw finger one to one`() {
        val first = ThreePageStripPhysics.drag(
            transition = ThreePageStripPhysics.idle(7L),
            displayedOffsetPx = 0f,
            rawDeltaX = -24f,
            rawDeltaY = 1f,
            edgeResidualX = -9f,
            currentPage = 1,
            pageCount = 3,
            viewportWidthPx = width,
            previousBaseReady = true,
            nextBaseReady = true,
        )
        val second = ThreePageStripPhysics.drag(
            transition = first.transition,
            displayedOffsetPx = first.offsetPx,
            rawDeltaX = -31f,
            rawDeltaY = 0f,
            edgeResidualX = -31f,
            currentPage = 1,
            pageCount = 3,
            viewportWidthPx = width,
            previousBaseReady = true,
            nextBaseReady = true,
        )
        assertEquals(ZoomedPageTransitionPhase.EDGE_PREVIEW, second.transition.phase)
        assertEquals(2, second.transition.targetPage)
        assertEquals(-40f, second.offsetPx, 0f)
    }

    @Test
    fun `REAL NO GRAY EXPOSURE refuses strip movement without neighbour base`() {
        val blocked = nextDrag(nextBaseReady = false, delta = -48f)
        assertTrue(blocked.blockedByMissingBase)
        assertEquals(0f, blocked.offsetPx, 0f)
        assertEquals(ZoomedPageTransitionPhase.IDLE, blocked.transition.phase)
    }

    @Test
    fun `page index remains stable throughout preview and release decision`() {
        val strip = ThreePageStripState(currentPage = 1, pageCount = 3)
        val preview = nextDrag(nextBaseReady = true, delta = -600f)
        val release = ThreePageStripPhysics.release(
            preview.transition,
            preview.offsetPx,
            width,
            releaseVelocityXPxPerSecond = 0f,
            minimumFlingVelocityPxPerSecond = 50f,
        )
        assertEquals(1, strip.currentPage)
        assertEquals(ZoomedPageTransitionPhase.COMMITTING, release.phase)
        assertEquals(1, strip.currentPage)
    }

    @Test
    fun `REAL PAGE STRIP CANCEL settles to exact zero and never between pages`() {
        val preview = nextDrag(nextBaseReady = true, delta = -120f)
        val release = ThreePageStripPhysics.release(
            preview.transition,
            preview.offsetPx,
            width,
            releaseVelocityXPxPerSecond = 0f,
            minimumFlingVelocityPxPerSecond = 50f,
        )
        assertEquals(ZoomedPageTransitionPhase.CANCELLING, release.phase)
        assertEquals(0f, ThreePageStripPhysics.settleTargetPx(release, width), 0f)
    }

    @Test
    fun `REAL PAGE STRIP COMMIT settles exactly one viewport before rotation`() {
        val preview = nextDrag(nextBaseReady = true, delta = -600f)
        val release = ThreePageStripPhysics.release(
            preview.transition,
            preview.offsetPx,
            width,
            releaseVelocityXPxPerSecond = 0f,
            minimumFlingVelocityPxPerSecond = 50f,
        )
        assertEquals(-width, ThreePageStripPhysics.settleTargetPx(release, width), 0f)
    }

    @Test
    fun `next slot keeps physical identity when rotated to current`() {
        val before = ThreePageStripState(currentPage = 1, pageCount = 5)
        val oldNext = before.nextSlotId
        val after = before.committed(HorizontalPageTransitionDirection.NEXT)
        assertEquals(2, after.currentPage)
        assertEquals(oldNext, after.currentSlotId)
        assertEquals(before.currentSlotId, after.previousSlotId)
    }

    @Test
    fun `slot rotation plus offset rebase has zero visual jump`() {
        val before = ThreePageStripState(currentPage = 1, pageCount = 5)
        val oldNext = before.slots().first { it.role == PageStripSlotRole.NEXT }
        val beforeX = ThreePageStripPhysics.slotTranslationPx(oldNext.role, width, -width)
        val after = before.committed(HorizontalPageTransitionDirection.NEXT)
        val newCurrent = after.slots().first { it.stableId == oldNext.stableId }
        val afterX = ThreePageStripPhysics.slotTranslationPx(newCurrent.role, width, 0f)
        assertEquals(0f, beforeX, 0f)
        assertEquals(beforeX, afterX, 0f)
    }

    @Test
    fun `previous commit rotates in reverse while preserving target surface`() {
        val before = ThreePageStripState(currentPage = 2, pageCount = 5)
        val oldPrevious = before.previousSlotId
        val after = before.committed(HorizontalPageTransitionDirection.PREVIOUS)
        assertEquals(1, after.currentPage)
        assertEquals(oldPrevious, after.currentSlotId)
        assertEquals(before.currentSlotId, after.nextSlotId)
    }

    @Test
    fun `new pointer interrupts settle at exact displayed offset`() {
        val settling = PageStripTransition(
            gestureGeneration = 3L,
            phase = ZoomedPageTransitionPhase.CANCELLING,
            direction = HorizontalPageTransitionDirection.NEXT,
            targetPage = 2,
        )
        val interrupted = ThreePageStripPhysics.interrupt(
            previous = settling,
            displayedOffsetPx = -173f,
            currentPage = 1,
            pageCount = 3,
            generation = 4L,
        )
        assertEquals(ZoomedPageTransitionPhase.EDGE_PREVIEW, interrupted.phase)
        assertEquals(HorizontalPageTransitionDirection.NEXT, interrupted.direction)
        assertEquals(4L, interrupted.gestureGeneration)
    }

    @Test
    fun `REAL 1X moving slot pointer is rebased into stationary viewport coordinates`() {
        val firstViewportX = ThreePageStripPhysics.pointerXInViewport(
            localPointerX = 1_000f,
            role = PageStripSlotRole.CURRENT,
            viewportWidthPx = width,
            stripOffsetPx = 0f,
        )
        // The finger moved left by 10px while the slot also translated left by 10px. Its local
        // X remains 1000, but the stationary viewport coordinate must still report the -10px move.
        val secondViewportX = ThreePageStripPhysics.pointerXInViewport(
            localPointerX = 1_000f,
            role = PageStripSlotRole.CURRENT,
            viewportWidthPx = width,
            stripOffsetPx = -10f,
        )
        assertEquals(-10f, secondViewportX - firstViewportX, 0f)
    }

    @Test
    fun `REAL ONE GESTURE ONE PAGE clamps held preview without rotating slots`() {
        val before = ThreePageStripState(currentPage = 1, pageCount = 5)
        val preview = nextDrag(nextBaseReady = true, delta = -width * 1.6f)
        assertEquals(-width, preview.offsetPx, 0f)
        assertEquals(1, before.currentPage)
        assertEquals(ZoomedPageTransitionPhase.EDGE_PREVIEW, preview.transition.phase)
    }

    @Test
    fun `reverse through zero can reveal the other ready neighbour`() {
        val next = nextDrag(nextBaseReady = true, delta = -40f)
        val reversed = ThreePageStripPhysics.drag(
            transition = next.transition,
            displayedOffsetPx = next.offsetPx,
            rawDeltaX = 70f,
            rawDeltaY = 0f,
            edgeResidualX = 0f,
            currentPage = 1,
            pageCount = 3,
            viewportWidthPx = width,
            previousBaseReady = true,
            nextBaseReady = true,
        )
        assertEquals(HorizontalPageTransitionDirection.PREVIOUS, reversed.transition.direction)
        assertEquals(30f, reversed.offsetPx, 0f)
    }

    @Test
    fun `three full viewport slots touch without an uncovered coordinate gap`() {
        val offset = -437f
        val translations = PageStripSlotRole.entries.map {
            ThreePageStripPhysics.slotTranslationPx(it, width, offset)
        }
        assertEquals(width, translations[1] - translations[0], 0f)
        assertEquals(width, translations[2] - translations[1], 0f)
    }

    @Test
    fun `production has stable three slots no HorizontalPager and no per frame page index write`() {
        val screen = File("src/main/java/com/markleaf/app/ui/pdf/PdfViewerScreen.kt").readText()
        assertTrue(screen.contains("pageStrip.slots().forEach"))
        assertTrue(screen.contains("key(slot.stableId)"))
        assertTrue(screen.contains("ThreePageStripPhysics.slotTranslationPx("))
        assertTrue(screen.contains("pageStrip = pageStrip.committed("))
        assertFalse(screen.contains("HorizontalPager("))
        val slotModifier = screen.substringAfter("pageStrip.slots().forEach")
            .substringAfter("modifier = Modifier")
            .substringBefore(".pointerInput(")
        assertTrue(slotModifier.indexOf(".graphicsLayer") < slotModifier.indexOf(".background"))
        val dragBlock = screen.substringAfter("val stripDrag = ThreePageStripPhysics.drag(")
            .substringBefore("if (stripDrag.blockedByMissingBase")
        assertFalse(dragBlock.contains("onPageChanged("))
    }

    private fun nextDrag(nextBaseReady: Boolean, delta: Float): PageStripDragResult =
        ThreePageStripPhysics.drag(
            transition = ThreePageStripPhysics.idle(1L),
            displayedOffsetPx = 0f,
            rawDeltaX = delta,
            rawDeltaY = 0f,
            edgeResidualX = delta,
            currentPage = 1,
            pageCount = 3,
            viewportWidthPx = width,
            previousBaseReady = true,
            nextBaseReady = nextBaseReady,
        )
}
