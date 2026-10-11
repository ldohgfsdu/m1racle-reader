package com.m1racle.reader.ui.epub

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.*
import org.junit.Test

class EpubPaginationTest {
    @Test fun `consecutive headings move with following text instead of leaving an orphan`() {
        val rows = listOf(row(count = 3), row(count = 1).copy(keepWithNext = true),
            row(count = 1).copy(keepWithNext = true), row(count = 4))
        val pages = paginateEpub(rows, 100f, 10f)
        assertEquals(listOf(0), pages.first().slices.map { it.row })
        assertEquals(listOf(1, 2, 3), pages[1].slices.map { it.row })
        assertTrue(pages.all { it.height <= 100f })
        rows.indices.forEach { r ->
            assertEquals(rows[r].lines.last().end,
                pages.flatMap { it.slices }.filter { it.row == r }.sumOf { it.end - it.start })
        }
    }

    @Test fun `oversized heading chains remain readable without empty pages or overflow`() {
        val rows = List(12) { row(count = 2).copy(keepWithNext = true) } + row(count = 5)
        val pages = paginateEpub(rows, 100f, 10f)
        assertTrue(pages.all { it.slices.isNotEmpty() && it.height <= 100f })
        assertEquals(290, pages.flatMap { it.slices }.sumOf { it.end - it.start })
    }

    private fun row(spine: Int = 0, count: Int, chapter: Boolean = false, lineHeight: Float = 20f) =
        EpubRowMetrics(spine, chapter, List(count) { EpubLine(it * lineHeight, (it + 1) * lineHeight, it * 10, (it + 1) * 10) })

    @Test fun `long paragraph splits only between lines without losing or duplicating text`() {
        val pages = paginateEpub(listOf(row(count = 23)), 100f, 10f)
        assertEquals(5, pages.size)
        assertEquals((0 until 230).toList(), pages.flatMap { p -> p.slices.flatMap { (it.start until it.end).toList() } })
        assertTrue(pages.all { it.height <= 100f })
        assertEquals(listOf(0f, 100f, 200f, 300f, 400f), pages.map { it.slices.first().sourceTop })
    }

    @Test fun `paragraph spacing is counted but never creates an empty page`() {
        val pages = paginateEpub(listOf(row(count = 2), row(count = 3)), 100f, 10f)
        assertEquals(2, pages.size)
        assertEquals(90f, pages.first().height)
        assertEquals(20f, pages.last().height)
        assertEquals(20, pages.first().slices.last().end)
    }

    @Test fun `chapter starts a fresh page and keeps real spine index after non linear entries`() {
        val pages = paginateEpub(listOf(row(0, 1, true), row(0, 1), row(2, 1, true), row(2, 1)), 500f, 10f)
        assertEquals(listOf(0, 2), pages.map { it.spineIndex })
        assertEquals(listOf(0, 2), pages.map { it.slices.first().row })
        assertEquals(1, pages.pageForText(3, 0))
    }

    @Test fun `persisted row and pixel offset restore the exact page including paragraph continuations`() {
        val pages = paginateEpub(listOf(row(count = 1), row(count = 30)), 100f, 10f)
        pages.forEachIndexed { index, page ->
            val first = page.slices.first()
            assertEquals(index, pages.pageForPosition(first.row, kotlin.math.ceil(first.sourceTop).toInt()))
        }
    }

    @Test fun `text anchor stays on containing page after font and viewport changes`() {
        val before = paginateEpub(listOf(row(count = 40)), 100f, 10f)
        val anchor = before[3].slices.first().start
        val after = paginateEpub(listOf(row(count = 40, lineHeight = 32f)), 80f, 10f)
        val page = after[after.pageForText(0, anchor)]
        assertTrue(page.slices.any { anchor in it.start until it.end })
        assertTrue(after.size > before.size)
    }

    @Test fun `oversized single line is retained rather than skipped or looped forever`() {
        val pages = paginateEpub(listOf(row(count = 2, lineHeight = 120f)), 40f, 10f)
        assertEquals(2, pages.size)
        assertTrue(pages.all { it.height == 120f })
    }

    @Test fun `image fits page width and never exceeds one page height`() {
        assertEquals(IntSize(400, 200), epubFitImage(100, 50, 400, 300))
        assertEquals(IntSize(150, 300), epubFitImage(100, 200, 400, 300))
        assertEquals(IntSize(400, 120), epubFitImage(0, 0, 400, 300))
    }

    @Test fun `page tap uses left right thirds and center chrome toggle`() {
        assertEquals(EpubPageTap.Previous, epubPageTap(10f, 100f))
        assertEquals(EpubPageTap.ToggleChrome, epubPageTap(50f, 100f))
        assertEquals(EpubPageTap.Next, epubPageTap(90f, 100f))
    }

    @Test fun `forward slides the current sheet away while backward brings the previous sheet over it`() {
        val current = epubPageTurnLayer(-0.3f, 0.3f, 0, 0, 100f)
        val next = epubPageTurnLayer(0.7f, 0.3f, 0, 0, 100f)
        assertEquals(0f, current.translationX, 0.01f)
        assertEquals(1f, current.zIndex, 0f)
        assertEquals(-52.5f, next.translationX, 0.01f)
        assertEquals(-0.7f, next.zIndex, 0f)
        assertTrue(current.edgeShadow > 0f)

        val staying = epubPageTurnLayer(0.3f, -0.3f, 0, 0, 100f)
        val previous = epubPageTurnLayer(-0.7f, -0.3f, 0, 0, 100f)
        assertEquals(-22.5f, staying.translationX, 0.01f)
        assertEquals(0f, previous.translationX, 0.01f)
        assertEquals(1f, previous.zIndex, 0f)
    }

    @Test fun `settled top sheet covers its following sheet without a shadow`() {
        listOf(-1f, 0f, 1f).forEach { offset ->
            val layer = epubPageTurnLayer(offset, 0f, 0, 0, 100f)
            assertEquals(if (offset > 0) -75f else 0f, layer.translationX, 0f)
            assertEquals(0f, layer.edgeShadow, 0.01f)
        }
        val offscreenPrev = epubPageTurnLayer(-1.2f, 0.3f, 0, 0, 100f)
        assertEquals(0f, offscreenPrev.translationX, 0.01f)
    }

    @Test fun `underlying page does not jump at the first or last two pixels of a turn`() {
        val width = 1080f
        for (progress in listOf(0f, .0005f, .001f, .002f, .998f, .999f, .9995f, 1f)) {
            val current = if (progress < .5f) 0 else 1
            val offset = 1f - progress
            val next = epubPageTurnLayer(offset, progress-current, current, 0, width)
            // Native pager location plus compensation must remain at quarter speed,
            // including both old early-return thresholds.
            assertEquals(offset * width * .25f, offset * width + next.translationX, .0001f)
        }
    }

    @Test fun `preloaded second neighbour cannot cover the original under sheet during backward drag`() {
        val original = epubPageTurnLayer(.62f, .38f, 0, 1, 1080f)
        val later = epubPageTurnLayer(1.62f, .38f, 0, 1, 1080f)
        assertTrue(original.zIndex > later.zIndex)
        assertTrue(original.paperLift > 0f)
        assertEquals(0f, later.paperLift, 0f)
    }

    @Test fun `viewport ignores one pixel jitter that would reflow the book`() {
        val base = IntSize(1080, 1920)
        assertTrue(epubViewportSettled(base, IntSize(1080, 1921)))
        assertTrue(epubViewportSettled(base, IntSize(1079, 1920)))
        assertFalse(epubViewportSettled(IntSize.Zero, base))
        assertFalse(epubViewportSettled(base, IntSize(1080, 1800)))
    }

    @Test fun `only the original paper lifts while text and target sheet keep their own colors`() {
        for (progress in listOf(0.1f, 0.49f, 0.51f, 0.9f)) {
            val forwardCurrent = if (progress < 0.5f) 0 else 1
            val backwardCurrent = if (progress < 0.5f) 1 else 0
            val leaving = epubPageTurnLayer(-progress, progress - forwardCurrent, forwardCurrent, 0, 100f)
            val next = epubPageTurnLayer(1 - progress, progress - forwardCurrent, forwardCurrent, 0, 100f)
            val staying = epubPageTurnLayer(progress, 1 - progress - backwardCurrent, backwardCurrent, 1, 100f)
            val previous = epubPageTurnLayer(progress - 1, 1 - progress - backwardCurrent, backwardCurrent, 1, 100f)
            assertEquals(1f - (1f-progress)*(1f-progress), leaving.paperLift, 0.001f)
            assertEquals(1f - (1f-progress)*(1f-progress), staying.paperLift, 0.001f)
            assertEquals(0f, next.paperLift, 0.001f)
            assertEquals(0f, previous.paperLift, 0.001f)
        }
    }

    @Test fun `forward moving sheet and following next sheet stay continuous across the half way switch`() {
        // The pager changes currentPage from 0 to 1, fraction from +0.49 to -0.49.
        val before = epubPageTurnLayer(-0.49f, 0.49f, 0, 0, 100f)
        val after = epubPageTurnLayer(-0.51f, -0.49f, 1, 0, 100f)
        assertEquals(1f, before.zIndex, 0f)
        assertEquals(1f, after.zIndex, 0f)
        assertEquals(0f, before.translationX, 0.001f)
        assertEquals(0f, after.translationX, 0.001f)
        val next = epubPageTurnLayer(0.49f, -0.49f, 1, 0, 100f)
        assertEquals(-0.49f, next.zIndex, 0f)
        assertEquals(12.25f, 49f + next.translationX, 0.001f)
    }

    @Test fun `backward cover follows across current page half way switch`() {
        val before = epubPageTurnLayer(0.49f, -0.49f, 1, 1, 100f)
        val after = epubPageTurnLayer(0.51f, 0.49f, 0, 1, 100f)
        assertEquals(-0.49f, before.zIndex, 0f)
        assertEquals(-0.51f, after.zIndex, 0f)
        assertEquals(-38.25f, after.translationX, 0.001f)
    }

    @Test fun `cancelled turn returns to a flat resting sheet without a remaining shadow`() {
        val dragging = epubPageTurnLayer(-0.25f, 0.25f, 8, 8, 100f)
        assertTrue(dragging.edgeShadow > 0f)
        assertEquals(EpubPageTurnLayer(0f, 1f, 0f), epubPageTurnLayer(0f, 0f, 8, 8, 100f))
        val almostResting = epubPageTurnLayer(-0.01f, 0.01f, 8, 8, 100f)
        assertTrue(almostResting.paperLift < dragging.paperLift)
        assertTrue(almostResting.edgeShadow < dragging.edgeShadow)
    }
}
