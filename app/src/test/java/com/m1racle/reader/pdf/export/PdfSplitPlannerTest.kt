package com.m1racle.reader.pdf.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfSplitPlannerTest {

    private fun chapter(
        id: String,
        title: String,
        path: List<String>,
        start: Int,
        end: Int,
    ) = PdfSplitChapterSource(
        id = id,
        title = title,
        pathTitles = path,
        range = PdfSplitPageRange(start, end),
    )

    @Test
    fun pageCount_keepsShortTailAndCoversSelectionContinuously() {
        val plan = PdfSplitPlanner.byPageCount(
            documentDisplayName = "book.pdf",
            sourcePageCount = 10,
            pagesPerPart = 3,
            selection = PdfSplitPageRange(2, 9),
        )

        assertEquals(
            listOf(
                PdfSplitPageRange(2, 4),
                PdfSplitPageRange(5, 7),
                PdfSplitPageRange(8, 9),
            ),
            plan.parts.map { it.sourceRange },
        )
        assertEquals(8, plan.totalPages)
        assertContinuous(plan)
    }

    @Test
    fun pageCount_hugeChunkSizeAtNonZeroSelectionStart_doesNotOverflow() {
        val plan = PdfSplitPlanner.byPageCount(
            documentDisplayName = "book.pdf",
            sourcePageCount = 10,
            pagesPerPart = Int.MAX_VALUE,
            selection = PdfSplitPageRange(2, 9),
        )

        assertEquals(
            listOf(PdfSplitPageRange(2, 9)),
            plan.parts.map { it.sourceRange },
        )
        assertEquals(8, plan.totalPages)
        assertContinuous(plan)
    }

    @Test(expected = IllegalArgumentException::class)
    fun pageCount_zeroPagesPerPart_isRejected() {
        PdfSplitPlanner.byPageCount(
            documentDisplayName = "book.pdf",
            sourcePageCount = 10,
            pagesPerPart = 0,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun pageCount_negativePagesPerPart_isRejected() {
        PdfSplitPlanner.byPageCount(
            documentDisplayName = "book.pdf",
            sourcePageCount = 10,
            pagesPerPart = -1,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun pageCount_reversedSelection_isRejected() {
        PdfSplitPlanner.byPageCount(
            documentDisplayName = "book.pdf",
            sourcePageCount = 10,
            pagesPerPart = 3,
            selection = PdfSplitPageRange(8, 2),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun pageCount_outOfBoundsSelection_isRejected() {
        PdfSplitPlanner.byPageCount(
            documentDisplayName = "book.pdf",
            sourcePageCount = 10,
            pagesPerPart = 3,
            selection = PdfSplitPageRange(2, 10),
        )
    }

    @Test
    fun chapter_topLevel_omitsChildrenAndIncludesLeadingContent() {
        val chapters = listOf(
            chapter("part1", "第一篇", listOf("第一篇"), 4, 19),
            chapter("ch1", "第一章", listOf("第一篇", "第一章"), 4, 11),
            chapter("ch2", "第二章", listOf("第一篇", "第二章"), 12, 19),
            chapter("part2", "第二篇", listOf("第二篇"), 20, 24),
        )
        val plan = PdfSplitPlanner.byChapters(
            documentDisplayName = "book.pdf",
            sourcePageCount = 25,
            chapters = chapters,
            chapterLevel = PdfSplitChapterLevel.TopLevel,
        )

        assertEquals(
            listOf(PdfSplitPartKind.LEADING_CONTENT, PdfSplitPartKind.CHAPTER, PdfSplitPartKind.CHAPTER),
            plan.parts.map { it.kind },
        )
        assertEquals(
            listOf(
                PdfSplitPageRange(0, 3),
                PdfSplitPageRange(4, 19),
                PdfSplitPageRange(20, 24),
            ),
            plan.parts.map { it.sourceRange },
        )
        assertFalse(plan.parts.any { it.title == "第一章" || it.title == "第二章" })
        assertContinuous(plan)
    }

    @Test
    fun chapter_secondLevel_usesChildBoundariesWithoutParentDuplicate() {
        val chapters = listOf(
            chapter("part", "第一篇", listOf("第一篇"), 0, 9),
            chapter("ch1", "第一章", listOf("第一篇", "第一章"), 2, 5),
            chapter("ch2", "第二章", listOf("第一篇", "第二章"), 6, 9),
        )
        val plan = PdfSplitPlanner.byChapters(
            documentDisplayName = "book.pdf",
            sourcePageCount = 10,
            chapters = chapters,
            chapterLevel = PdfSplitChapterLevel.Depth(1),
        )

        assertEquals(3, plan.parts.size)
        assertEquals(PdfSplitPartKind.LEADING_CONTENT, plan.parts.first().kind)
        assertEquals(listOf("第一章", "第二章"), plan.parts.drop(1).map { it.title })
        assertContinuous(plan)
    }

    @Test
    fun chapter_samePage_mergesByDefaultAndCanKeepFirst() {
        val chapters = listOf(
            chapter("cover", "封面", listOf("封面"), 0, 0),
            chapter("introA", "前言", listOf("前言甲"), 1, 3),
            chapter("introB", "序言", listOf("前言乙"), 1, 3),
        )
        val merged = PdfSplitPlanner.byChapters(
            documentDisplayName = "book.pdf",
            sourcePageCount = 4,
            chapters = chapters,
            samePagePolicy = PdfSplitSamePagePolicy.MERGE,
        )
        assertEquals("前言 / 序言", merged.parts[1].title)
        assertEquals(2, merged.parts.size)

        val keepFirst = PdfSplitPlanner.byChapters(
            documentDisplayName = "book.pdf",
            sourcePageCount = 4,
            chapters = chapters,
            samePagePolicy = PdfSplitSamePagePolicy.KEEP_FIRST,
        )
        assertEquals("前言", keepFirst.parts[1].title)
        assertContinuous(keepFirst)
    }

    @Test
    fun chapter_lastBoundary_extendsThroughSelectionEnd() {
        val chapters = listOf(
            chapter("ch1", "第一章", listOf("第一章"), 0, 2),
            chapter("ch2", "第二章", listOf("第二章"), 3, 5),
        )
        val plan = PdfSplitPlanner.byChapters(
            documentDisplayName = "book.pdf",
            sourcePageCount = 8,
            chapters = chapters,
            selection = PdfSplitPageRange(1, 7),
        )
        assertEquals(PdfSplitPageRange(1, 2), plan.parts.first().sourceRange)
        assertEquals(PdfSplitPageRange(3, 7), plan.parts.last().sourceRange)
        assertContinuous(plan)
    }

    private fun assertContinuous(plan: PdfSplitPlan) {
        assertTrue(plan.parts.first().sourceRange.startPageIndex == plan.selection.startPageIndex)
        assertTrue(plan.parts.last().sourceRange.endPageIndex == plan.selection.endPageIndex)
        plan.parts.zipWithNext().forEach { (current, next) ->
            assertTrue(current.sourceRange.endPageIndex + 1 == next.sourceRange.startPageIndex)
        }
    }
}
