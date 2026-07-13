package com.markleaf.app.pdf

import com.markleaf.app.data.local.PdfPrintPageAnchorEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintPageResolverTest {

    private fun anchor(pdfIndex: Int, print: Int, doc: String = "doc") = PdfPrintPageAnchorEntity(
        documentId = doc,
        pdfPageIndex = pdfIndex,
        printPageNumber = print,
        updatedAt = 0L,
    )

    private fun neikeSections(doc: String = "doc") =
        PrintPageSectionCatalog.sectionsFor(doc, "内科学（第10版）.pdf")

    @Test
    fun empty_returnsNull_neverPdfIndex() {
        assertNull(PrintPageResolver.resolve(0, emptyList()))
        assertNull(PrintPageResolver.resolve(632, emptyList()))
        // Must not impersonate PDF index as printed page
        assertNull(PrintPageResolver.resolve("doc", 632, emptyList()))
    }

    @Test
    fun singleAnchor_infersForward() {
        val anchors = listOf(anchor(pdfIndex = 10, print = 1))
        assertNull(PrintPageResolver.resolve(5, anchors))
        assertEquals(1, PrintPageResolver.resolve(10, anchors))
        assertEquals(6, PrintPageResolver.resolve(15, anchors))
        assertEquals(91, PrintPageResolver.resolve(100, anchors))
    }

    @Test
    fun textbookMapping_ui330_to_299_ui633_to_602() {
        // UI 330 → internal 329 → print 299 (anchor)
        // UI 633 → internal 632 → print 602
        val anchors = listOf(anchor(pdfIndex = 329, print = 299))
        assertEquals(299, PrintPageResolver.resolve(329, anchors))
        assertEquals(602, PrintPageResolver.resolve(632, anchors))
        assertEquals(
            602,
            PrintPageResolver.resolve(
                documentId = "doc",
                pdfPageIndex = 632,
                anchors = anchors,
            ),
        )
    }

    @Test
    fun bothModesSameResult_forSamePage() {
        val anchors = listOf(anchor(329, 299))
        val vertical = PrintPageResolver.resolve("doc", 632, anchors)
        val horizontal = PrintPageResolver.resolve("doc", 632, anchors)
        assertEquals(vertical, horizontal)
        assertEquals(602, vertical)
    }

    @Test
    fun documentIdFilter_ignoresOtherDocs() {
        val anchors = listOf(
            anchor(329, 299, doc = "other"),
            anchor(0, 1, doc = "doc"),
        )
        // Only "doc" anchors apply → from 0/1
        assertEquals(633, PrintPageResolver.resolve("doc", 632, anchors))
        // other doc's 329/299 ignored for "doc"
        assertNull(PrintPageResolver.resolve("missing", 632, anchors))
    }

    @Test
    fun multipleAnchors_usesNearestPrevious() {
        val anchors = listOf(
            anchor(0, 1),
            anchor(50, 100),
            anchor(80, 200),
        )
        assertEquals(1, PrintPageResolver.resolve(0, anchors))
        assertEquals(11, PrintPageResolver.resolve(10, anchors))
        assertEquals(100, PrintPageResolver.resolve(50, anchors))
        assertEquals(105, PrintPageResolver.resolve(55, anchors))
        assertEquals(200, PrintPageResolver.resolve(80, anchors))
        assertEquals(210, PrintPageResolver.resolve(90, anchors))
    }

    @Test
    fun isExactAnchor() {
        val anchors = listOf(anchor(3, 10))
        assertTrue(PrintPageResolver.isExactAnchor(3, anchors))
        assertFalse(PrintPageResolver.isExactAnchor(4, anchors))
        assertTrue(PrintPageResolver.isExactAnchor("doc", 3, anchors))
        assertFalse(PrintPageResolver.isExactAnchor("other", 3, anchors))
    }

    @Test
    fun pageJumpIsIndependentOfSettledPage() {
        // Settled scroll must not create a jump; only explicit jump requests do.
        val jumps = mutableListOf<Int>()
        fun onSettled(page: Int) { /* no jump */ }
        fun onJump(page: Int) { jumps += page }
        onSettled(10)
        onSettled(11)
        assertTrue(jumps.isEmpty())
        onJump(50)
        assertEquals(listOf(50), jumps)
    }

    @Test
    fun modeSwitchKeepsPageAndPrint() {
        val anchors = listOf(anchor(329, 299))
        val page = PdfReadingNavigation.pageAfterModeSwitch(632, 982)
        assertEquals(632, page)
        assertEquals(602, PrintPageResolver.resolve("doc", page, anchors))
    }

    // ── Section model (内科学) ──────────────────────────────────────────

    @Test
    fun neikeCatalog_hasFrontAndBodySections() {
        val sections = neikeSections()
        assertEquals(2, sections.size)
        val front = sections[0]
        val body = sections[1]
        assertEquals(0, front.startPdfPageIndex)
        assertEquals(30, front.endPdfPageIndex)
        assertFalse(front.useSectionBaseWithoutAnchor)
        assertEquals(31, body.startPdfPageIndex)
        assertNull(body.endPdfPageIndex)
        assertEquals(1, body.printedPageStart)
        assertTrue(body.useSectionBaseWithoutAnchor)
    }

    @Test
    fun section_ui33_isPrint2() {
        // UI PDF 33 → index 32 → body start 31 print 1 → print 2
        val r = PrintPageResolver.resolveDetailed(
            documentId = "doc",
            pdfPageIndex = 32,
            anchors = emptyList(),
            sections = neikeSections(),
        )
        assertEquals(2, r.printPageNumber)
        assertTrue(r.isInferred)
        assertFalse(r.isExactAnchor)
        assertEquals(31, r.section?.startPdfPageIndex)
    }

    @Test
    fun section_ui330_isPrint299() {
        // UI 330 → index 329 → 1 + (329 - 31) = 299
        val r = PrintPageResolver.resolveDetailed(
            documentId = "doc",
            pdfPageIndex = 329,
            anchors = emptyList(),
            sections = neikeSections(),
        )
        assertEquals(299, r.printPageNumber)
    }

    @Test
    fun section_ui633_isPrint602() {
        // UI 633 → index 632 → 1 + (632 - 31) = 602
        val r = PrintPageResolver.resolveDetailed(
            documentId = "doc",
            pdfPageIndex = 632,
            anchors = emptyList(),
            sections = neikeSections(),
        )
        assertEquals(602, r.printPageNumber)
    }

    @Test
    fun bodySection_doesNotInheritFrontAnchors() {
        // Front-matter anchor must not leak into body.
        val anchors = listOf(
            anchor(pdfIndex = 5, print = 99), // front section
            anchor(pdfIndex = 10, print = 200), // front
        )
        val bodyPage = 32 // UI 33
        val r = PrintPageResolver.resolveDetailed(
            documentId = "doc",
            pdfPageIndex = bodyPage,
            anchors = anchors,
            sections = neikeSections(),
        )
        // Without section: would be 200 + (32-10) = 222 — forbidden.
        assertEquals(2, r.printPageNumber)
        assertNotNull(r.section)
        assertEquals(31, r.section!!.startPdfPageIndex)
    }

    @Test
    fun frontSection_usesOnlyInSectionAnchors() {
        val anchors = listOf(
            anchor(pdfIndex = 5, print = 10),
            // body seed must not affect front
        )
        val r = PrintPageResolver.resolveDetailed(
            documentId = "doc",
            pdfPageIndex = 8,
            anchors = anchors,
            sections = neikeSections(),
        )
        assertEquals(13, r.printPageNumber) // 10 + (8-5)
        assertEquals(0, r.section?.startPdfPageIndex)
    }

    @Test
    fun frontSection_withoutAnchors_returnsNull() {
        val r = PrintPageResolver.resolveDetailed(
            documentId = "doc",
            pdfPageIndex = 3,
            anchors = emptyList(),
            sections = neikeSections(),
        )
        assertNull(r.printPageNumber)
    }

    @Test
    fun bodyAnchor_overridesSectionBase() {
        val anchors = listOf(anchor(pdfIndex = 100, print = 50))
        val r = PrintPageResolver.resolveDetailed(
            documentId = "doc",
            pdfPageIndex = 105,
            anchors = anchors,
            sections = neikeSections(),
        )
        assertEquals(55, r.printPageNumber)
        assertTrue(r.isInferred)
    }

    @Test
    fun lockedAcceptance_ui33_330_633() {
        val sections = neikeSections()
        assertEquals(
            2,
            PrintPageResolver.resolve("doc", 32, emptyList(), sections),
        )
        assertEquals(
            299,
            PrintPageResolver.resolve("doc", 329, emptyList(), sections),
        )
        assertEquals(
            602,
            PrintPageResolver.resolve("doc", 632, emptyList(), sections),
        )
    }
}
