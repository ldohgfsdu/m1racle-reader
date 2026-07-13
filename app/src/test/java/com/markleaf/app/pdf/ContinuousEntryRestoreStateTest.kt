package com.markleaf.app.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R7_P0A1 pure logic: no Compose/PdfRenderer dependency. */
class ContinuousEntryRestoreStateTest {
    private fun seeded(page: Int = 47, fraction: Float = 0f): ContinuousEntryRestoreState =
        ContinuousEntryRestoreState().apply {
            seedLastStable(
                ReadingPosition(page, fraction, ReadingPositionSource.USER_CONTINUOUS_SCROLL, 0L),
                pageCount = 982,
            )
        }

    @Test fun `external mode startup and placeholder have fixed priority`() {
        assertTrue(
            ReadingPositionPolicy.priority(ReadingPositionSource.EXTERNAL_NAVIGATION) >
                ReadingPositionPolicy.priority(ReadingPositionSource.MODE_SWITCH_RESTORE),
        )
        assertTrue(
            ReadingPositionPolicy.priority(ReadingPositionSource.MODE_SWITCH_RESTORE) >
                ReadingPositionPolicy.priority(ReadingPositionSource.STARTUP_RESTORE),
        )
        assertTrue(
            ReadingPositionPolicy.priority(ReadingPositionSource.STARTUP_RESTORE) >
                ReadingPositionPolicy.priority(ReadingPositionSource.INITIAL_PLACEHOLDER),
        )
    }

    @Test fun `startup page 47 waits for geometry and page zero cannot persist`() {
        val state = seeded()
        val startup = state.requestRestore(47, 0.25f, ReadingPositionSource.STARTUP_RESTORE, 982)!!

        assertEquals(ContinuousEntryPhase.WAITING_GEOMETRY, state.snapshot().phase)
        assertNull(state.beginApplyIfReady(geometryReady = false))
        assertFalse(state.canPersist(geometryReady = false))
        assertEquals(47, state.snapshot().lastStableReadingPosition!!.pageIndex)
        assertEquals(47, startup.pageIndex)
    }

    @Test fun `restore applies only once then enters ready`() {
        val state = seeded()
        val request = state.requestRestore(47, 0.4f, ReadingPositionSource.STARTUP_RESTORE, 982)!!
        assertEquals(request, state.beginApplyIfReady(geometryReady = true))
        assertEquals(ContinuousEntryPhase.APPLYING_TARGET, state.snapshot().phase)

        assertTrue(state.completeRestore(request.generation, request, 982))
        assertEquals(ContinuousEntryPhase.READY, state.snapshot().phase)
        assertEquals(47, state.snapshot().lastStableReadingPosition!!.pageIndex)
        assertFalse(state.completeRestore(request.generation, request, 982))
        assertTrue(state.canPersist(geometryReady = true))
    }

    @Test fun `mode switch settled page 42 wins over persisted startup 47`() {
        val state = seeded(page = 47)
        val startup = state.requestRestore(47, 0f, ReadingPositionSource.STARTUP_RESTORE, 982)!!
        val mode = state.requestRestore(42, 0f, ReadingPositionSource.MODE_SWITCH_RESTORE, 982)!!

        assertTrue(mode.generation > startup.generation)
        assertEquals(42, state.snapshot().pendingRestorePosition!!.pageIndex)
        assertEquals(ReadingPositionSource.MODE_SWITCH_RESTORE, state.snapshot().pendingRestorePosition!!.source)
        assertNull(state.requestRestore(47, 0f, ReadingPositionSource.STARTUP_RESTORE, 982))
    }

    @Test fun `pending mode switch flush still uses old stable position`() {
        val state = seeded(page = 42)
        state.requestRestore(42, 0f, ReadingPositionSource.MODE_SWITCH_RESTORE, 982)

        assertEquals(42, state.snapshot().lastStableReadingPosition!!.pageIndex)
        assertFalse(state.canPersist(geometryReady = true))
    }

    @Test fun `placeholder page zero never becomes persistable but real user page zero does`() {
        val placeholder = ReadingPosition(0, 0f, ReadingPositionSource.INITIAL_PLACEHOLDER, 1L)
        assertFalse(
            ReadingPositionPolicy.canPersistReadingPosition(
                ContinuousEntryPhase.READY,
                pendingRestorePosition = null,
                geometryReady = true,
                position = placeholder,
            ),
        )
        val state = ContinuousEntryRestoreState()
        state.markReadyWithoutRestore()
        val userPageZero = state.recordUserStable(
            0,
            0.5f,
            ReadingPositionSource.USER_CONTINUOUS_SCROLL,
            982,
        )
        assertNotNull(userPageZero)
        assertTrue(state.canPersist(geometryReady = true))
    }

    @Test fun `old generation cannot override newer external navigation`() {
        val state = seeded()
        val mode = state.requestRestore(42, 0f, ReadingPositionSource.MODE_SWITCH_RESTORE, 982)!!
        val external = state.requestRestore(300, 0.75f, ReadingPositionSource.EXTERNAL_NAVIGATION, 982)!!
        state.beginApplyIfReady(true)

        assertFalse(state.completeRestore(mode.generation, mode, 982))
        assertTrue(state.completeRestore(external.generation, external, 982))
        assertEquals(300, state.snapshot().lastStableReadingPosition!!.pageIndex)
    }

    @Test fun `fraction codec and geometry anchor survive viewport changes`() {
        val fraction = 0.375f
        assertEquals(fraction, PdfReadingPositionCodec.decodeFraction(PdfReadingPositionCodec.encodeFraction(fraction)), 0.00001f)
        val geometry = ContinuousDocumentGeometry.create(
            pageCount = 100,
            basePageWidth = 1000f,
            aspects = emptyMap(),
            pageGap = 20f,
        )
        val page = 47
        val centerAnchor = geometry.pageTop(page) + fraction * geometry.pageHeight(page)
        val narrow = geometry.clamp(
            ContinuousDocumentState(scrollY = centerAnchor - 600f),
            viewportWidth = 1000f,
            viewportHeight = 1200f,
        )
        val wide = geometry.clamp(
            ContinuousDocumentState(scrollY = centerAnchor - 1000f),
            viewportWidth = 1400f,
            viewportHeight = 2000f,
        )
        assertEquals(page, geometry.pageAtViewportCenter(narrow, 1200f))
        assertEquals(page, geometry.pageAtViewportCenter(wide, 2000f))
    }
}
