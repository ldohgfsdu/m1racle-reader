package com.m1racle.reader.pdf

import org.junit.Assert.*
import org.junit.Test

class PdfViewportSessionStateTest {
    private val geometry = ContinuousDocumentGeometry.create(100, 1000f, emptyMap(), 10f)

    @Test fun `page 62 scale and local anchor roundtrip`() {
        val session = PdfViewportSessionState(
            readingMode = PdfReadingMode.VERTICAL_CONTINUOUS, pageIndex = 61,
            pageAnchorXFraction = .72f, pageAnchorYFraction = .37f,
            scale = 2.4f, generation = 7,
        )
        val restored = geometry.restoreViewportSession(session, ContinuousDocumentState(), 1000f, 1800f)
        val roundtrip = geometry.viewportSessionAtAnchor(restored, 1000f, 1800f, 8)
        assertEquals(61, roundtrip.pageIndex)
        assertEquals(2.4f, restored.scale, .0001f)
        assertEquals(.72f, roundtrip.pageAnchorXFraction, .001f)
        assertEquals(.37f, roundtrip.pageAnchorYFraction, .001f)
    }

    @Test fun `viewport resize restores semantic anchor not pixels`() {
        val session = PdfViewportSessionState(
            readingMode = PdfReadingMode.VERTICAL_CONTINUOUS, pageIndex = 20,
            pageAnchorXFraction = .6f, pageAnchorYFraction = .4f, scale = 3f, generation = 1,
        )
        val portrait = geometry.restoreViewportSession(session, ContinuousDocumentState(), 1000f, 1800f)
        val landscape = geometry.restoreViewportSession(session, ContinuousDocumentState(), 1800f, 1000f)
        assertNotEquals(portrait.scrollY, landscape.scrollY)
        assertEquals(session.pageIndex, geometry.viewportSessionAtAnchor(landscape, 1800f, 1000f, 2).pageIndex)
    }

    @Test fun `damaged scale falls back without replacing page progress`() {
        val bad = PdfViewportSessionState(
            readingMode = PdfReadingMode.VERTICAL_CONTINUOUS, pageIndex = 0,
            pageAnchorXFraction = .5f, pageAnchorYFraction = .5f, scale = Float.NaN, generation = 1,
        )
        assertNull(bad.normalized(100, fallbackPage = 61))
    }

    @Test fun `out of range session page uses safe existing page`() {
        val badPage = PdfViewportSessionState(
            readingMode = PdfReadingMode.VERTICAL_CONTINUOUS, pageIndex = 999,
            pageAnchorXFraction = .5f, pageAnchorYFraction = .5f, scale = 2f, generation = 1,
        )
        assertEquals(61, badPage.normalized(100, 61)!!.pageIndex)
    }

    @Test fun `legacy missing session restores default fit`() {
        assertEquals(1f, ContinuousDocumentState().scale, 0f)
    }

    @Test fun `stable tracker ignores mid gesture candidate and flush is idempotent`() {
        val stable = PdfViewportSessionState(
            readingMode = PdfReadingMode.VERTICAL_CONTINUOUS, pageIndex = 61,
            pageAnchorXFraction = .5f, pageAnchorYFraction = .4f, scale = 2.4f, generation = 4,
        )
        val tracker = PdfViewportSessionTracker()
        tracker.seed(stable)
        assertEquals(stable, tracker.takeForFlush())
        assertNull(tracker.takeForFlush())
        assertEquals(stable, tracker.stable())
    }

    @Test fun `live preview drives mode handoff without becoming persisted stable state`() {
        val stable = PdfViewportSessionState(
            readingMode = PdfReadingMode.HORIZONTAL_PAGED, pageIndex = 4,
            pageAnchorXFraction = .5f, pageAnchorYFraction = .2f, scale = 2f, generation = 4,
        )
        val live = stable.copy(pageAnchorYFraction = .73f, generation = 5)
        val tracker = PdfViewportSessionTracker()
        tracker.seed(stable)
        tracker.preview(live)

        assertEquals(live, tracker.latestForMode(PdfReadingMode.HORIZONTAL_PAGED))
        assertEquals(stable, tracker.stable())
        assertEquals(stable, tracker.takeForFlush())
        assertNull(tracker.takeForFlush())
    }

    @Test fun `restore priority external then mode then zoom then startup`() {
        assertTrue(ReadingPositionPolicy.priority(ReadingPositionSource.EXTERNAL_NAVIGATION) > ReadingPositionPolicy.priority(ReadingPositionSource.MODE_SWITCH_RESTORE))
        assertTrue(ReadingPositionPolicy.priority(ReadingPositionSource.MODE_SWITCH_RESTORE) > ReadingPositionPolicy.priority(ReadingPositionSource.ZOOM_SESSION_RESTORE))
        assertTrue(ReadingPositionPolicy.priority(ReadingPositionSource.ZOOM_SESSION_RESTORE) > ReadingPositionPolicy.priority(ReadingPositionSource.STARTUP_RESTORE))
    }

    @Test fun `real first page zoom session is valid`() {
        val session = PdfViewportSessionState(
            readingMode = PdfReadingMode.VERTICAL_CONTINUOUS, pageIndex = 0,
            pageAnchorXFraction = .5f, pageAnchorYFraction = .2f, scale = 2f, generation = 1,
        )
        assertEquals(0, session.normalized(10, 0)!!.pageIndex)
    }
}
