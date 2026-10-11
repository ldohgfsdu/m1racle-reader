package com.m1racle.reader.data.anchor

import com.m1racle.reader.pdf.PdfReadingMode
import com.m1racle.reader.pdf.PdfViewportSessionState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BookmarkTargetTest {
    @Test fun capturesPageScaleAndFocalPositionIndependently() {
        val saved = BookmarkTarget(DocumentAnchor.PdfPage(7), viewport = PdfViewportSessionState(
            readingMode = PdfReadingMode.VERTICAL_CONTINUOUS, pageIndex = 7,
            pageAnchorXFraction = .31f, pageAnchorYFraction = .72f,
            viewportAnchorXFraction = .41f, viewportAnchorYFraction = .53f,
            scale = 2.4f, generation = 0,
        ))
        val decoded = requireNotNull(BookmarkTarget.decode(saved.encode()))
        assertEquals(saved, decoded)
        assertEquals(7, (decoded.anchor as DocumentAnchor.PdfPage).page)
        assertEquals(2.4f, decoded.viewport!!.scale, 0f)
    }
    @Test fun markdownKeepsBlockAndExactScrollOffset() {
        val saved = BookmarkTarget(DocumentAnchor.MarkdownBlock(23), 137)
        assertEquals(saved, BookmarkTarget.decode(saved.encode()))
    }
    @Test fun existingCanonicalAnchorsRemainReadableAndMalformedDataIsRejected() {
        assertEquals(DocumentAnchor.PdfPage(3), BookmarkTarget.decode("pdf:3|-")!!.anchor)
        assertNull(BookmarkTarget.decode("{broken"))
        assertNull(BookmarkTarget.decode("{\"anchor\":\"nonsense\"}"))
    }
}
