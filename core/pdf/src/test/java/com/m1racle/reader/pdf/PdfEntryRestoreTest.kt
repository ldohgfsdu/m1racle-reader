package com.m1racle.reader.pdf

import com.m1racle.reader.core.model.PdfReadingMode
import com.m1racle.reader.core.model.PdfViewportSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PdfEntryRestoreTest {
    private fun session(
        pageIndex: Int,
        readingMode: PdfReadingMode = PdfReadingMode.VERTICAL_CONTINUOUS,
        anchorY: Float = 0.4f,
    ) = PdfViewportSessionState(
        readingMode = readingMode,
        pageIndex = pageIndex,
        pageAnchorXFraction = 0.5f,
        pageAnchorYFraction = anchorY,
        scale = 1.7f,
        generation = 12L,
    )

    @Test
    fun staleSessionPageNeverOverridesNewerProgressPage() {
        val stale = session(pageIndex = 7)
        val resolved = resolvePdfEntryRestore(
            progressPageIndex = 57,
            progressIntraPageFraction = 0.25f,
            session = stale,
            pageCount = 384,
            readingMode = PdfReadingMode.VERTICAL_CONTINUOUS,
        )

        assertEquals(57, resolved.pageIndex)
        assertEquals(0.25f, resolved.intraPageFraction)
        assertNull(resolved.session)
    }

    @Test
    fun consistentSessionRefinesAnchorAndZoom() {
        val current = session(pageIndex = 57, anchorY = 0.62f)
        val resolved = resolvePdfEntryRestore(
            progressPageIndex = 57,
            progressIntraPageFraction = 0.25f,
            session = current,
            pageCount = 384,
            readingMode = PdfReadingMode.VERTICAL_CONTINUOUS,
        )

        assertEquals(57, resolved.pageIndex)
        assertEquals(0.62f, resolved.intraPageFraction)
        assertSame(current, resolved.session)
    }

    @Test
    fun sessionFromAnotherReadingModeIsIgnored() {
        val horizontal = session(pageIndex = 57, readingMode = PdfReadingMode.HORIZONTAL_PAGED)
        val resolved = resolvePdfEntryRestore(
            progressPageIndex = 57,
            progressIntraPageFraction = 0.1f,
            session = horizontal,
            pageCount = 384,
            readingMode = PdfReadingMode.VERTICAL_CONTINUOUS,
        )

        assertEquals(57, resolved.pageIndex)
        assertEquals(0.1f, resolved.intraPageFraction)
        assertNull(resolved.session)
    }

    @Test
    fun forwardNavigationIsNotClampedToTheSessionPage() {
        val older = session(pageIndex = 3)
        val resolved = resolvePdfEntryRestore(
            progressPageIndex = 300,
            progressIntraPageFraction = 0f,
            session = older,
            pageCount = 384,
            readingMode = PdfReadingMode.VERTICAL_CONTINUOUS,
        )

        assertEquals(300, resolved.pageIndex)
        assertNull(resolved.session)
    }

    @Test
    fun progressPageIsClampedToDocumentBounds() {
        val resolved = resolvePdfEntryRestore(
            progressPageIndex = 9_999,
            progressIntraPageFraction = 2f,
            session = null,
            pageCount = 10,
            readingMode = PdfReadingMode.HORIZONTAL_PAGED,
        )

        assertEquals(9, resolved.pageIndex)
        assertEquals(1f, resolved.intraPageFraction)
        assertNull(resolved.session)
    }
}
