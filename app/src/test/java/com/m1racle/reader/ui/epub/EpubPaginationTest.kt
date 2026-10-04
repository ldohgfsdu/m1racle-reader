package com.m1racle.reader.ui.epub

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.*
import org.junit.Test

class EpubPaginationTest {
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

    @Test fun `cover turn pins the leaving page and slides the incoming page over it`() {
        val current = epubPageTurnLayer(-0.3f, 0.3f, 0, 0, 100f)
        val next = epubPageTurnLayer(0.7f, 0.3f, 0, 0, 100f)
        assertEquals(30f, current.translationX, 0.01f)
        assertEquals(0f, current.zIndex, 0f)
        assertEquals(0f, next.translationX, 0.01f)
        assertEquals(1f, next.zIndex, 0f)
        assertTrue(next.edgeShadow > 0f)

        val staying = epubPageTurnLayer(0.3f, -0.3f, 0, 0, 100f)
        val previous = epubPageTurnLayer(-0.7f, -0.3f, 0, 0, 100f)
        assertEquals(-30f, staying.translationX, 0.01f)
        assertEquals(0f, previous.translationX, 0.01f)
        assertEquals(1f, previous.zIndex, 0f)
    }

    @Test fun `settled pager does not pull neighboring pages onto the current page`() {
        listOf(-1f, 0f, 1f).forEach { offset ->
            val layer = epubPageTurnLayer(offset, 0f, 0, 0, 100f)
            assertEquals(0f, layer.translationX, 0.01f)
            assertEquals(0f, layer.edgeShadow, 0.01f)
        }
        val offscreenPrev = epubPageTurnLayer(-1.2f, 0.3f, 0, 0, 100f)
        assertEquals(0f, offscreenPrev.translationX, 0.01f)
    }

    @Test fun `viewport ignores one pixel jitter that would reflow the book`() {
        val base = IntSize(1080, 1920)
        assertTrue(epubViewportSettled(base, IntSize(1080, 1921)))
        assertTrue(epubViewportSettled(base, IntSize(1079, 1920)))
        assertFalse(epubViewportSettled(IntSize.Zero, base))
        assertFalse(epubViewportSettled(base, IntSize(1080, 1800)))
    }

    @Test fun `cover keeps paper undimmed throughout forward and backward drags`() {
        for (progress in listOf(0.1f, 0.49f, 0.51f, 0.9f)) {
            val forwardCurrent = if (progress < 0.5f) 0 else 1
            val backwardCurrent = if (progress < 0.5f) 1 else 0
            assertEquals(EpubPageTurnLayer(progress * 100f, 0f, 0f),
                epubPageTurnLayer(-progress, progress - forwardCurrent, forwardCurrent, 0, 100f))
            assertEquals(EpubPageTurnLayer(-progress * 100f, 0f, 0f),
                epubPageTurnLayer(progress, 1 - progress - backwardCurrent, backwardCurrent, 1, 100f))
        }
    }

    @Test fun `forward cover stays pinned across current page half way switch`() {
        // The pager changes currentPage from 0 to 1, fraction from +0.49 to -0.49.
        val before = epubPageTurnLayer(-0.49f, 0.49f, 0, 0, 100f)
        val after = epubPageTurnLayer(-0.51f, -0.49f, 1, 0, 100f)
        assertEquals(0f, before.zIndex, 0f)
        assertEquals(0f, after.zIndex, 0f)
        assertEquals(51f, after.translationX, 0.001f)
        assertEquals(1f, epubPageTurnLayer(0.49f, 0.51f, 0, 0, 100f).zIndex, 0f)
    }

    @Test fun `backward cover stays pinned across current page half way switch`() {
        val before = epubPageTurnLayer(0.49f, -0.49f, 1, 1, 100f)
        val after = epubPageTurnLayer(0.51f, 0.49f, 0, 1, 100f)
        assertEquals(0f, before.zIndex, 0f)
        assertEquals(0f, after.zIndex, 0f)
        assertEquals(-51f, after.translationX, 0.001f)
    }
}
