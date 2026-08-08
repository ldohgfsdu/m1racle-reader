package com.markleaf.app.ui.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * r26.1 exclusive state machine + Basic→Advanced two-phase PREPARING.
 */
class ReaderMoreStateMachineTest {

    private val readingMode = ReaderMoreState.INLINE_READING_MODE
    private val readingUi = ReaderMoreState.INLINE_READING_UI
    private val pageMapping = ReaderMoreState.CHILD_PAGE_MAPPING
    private val toc = ReaderMoreState.CHILD_TOC
    private val thumbs = ReaderMoreState.CHILD_THUMBNAILS
    private val export = ReaderMoreState.CHILD_EXPORT

    @Test
    fun `CLOSED toggles open to MAIN`() {
        assertEquals(ReaderMoreState.MAIN, ReaderMoreStateMachine.onToggleOpen(ReaderMoreState.CLOSED))
    }

    @Test
    fun `MAIN section toggle opens inline`() {
        assertEquals(readingMode, ReaderMoreStateMachine.onSectionToggle(ReaderMoreState.MAIN, readingMode))
        assertEquals(readingUi, ReaderMoreStateMachine.onSectionToggle(ReaderMoreState.MAIN, readingUi))
    }

    @Test
    fun `same section toggle collapses to MAIN`() {
        assertEquals(ReaderMoreState.MAIN, ReaderMoreStateMachine.onSectionToggle(readingMode, readingMode))
    }

    @Test
    fun `open child from MAIN goes straight to CHILD`() {
        assertEquals(pageMapping, ReaderMoreStateMachine.onOpenChild(ReaderMoreState.MAIN, pageMapping))
        assertEquals(toc, ReaderMoreStateMachine.onOpenChild(ReaderMoreState.MAIN, toc))
        assertFalse(ReaderMoreStateMachine.onOpenChild(ReaderMoreState.MAIN, export).isPreparing())
    }

    @Test
    fun `open child from Basic goes PREPARING not simultaneous advanced`() {
        val fromMode = ReaderMoreStateMachine.onOpenChild(readingMode, pageMapping)
        assertTrue(fromMode.isPreparing())
        assertEquals(pageMapping, fromMode.preparingTarget())
        assertFalse(fromMode.isInline())
        assertFalse(fromMode.isChild())

        val fromUi = ReaderMoreStateMachine.onOpenChild(readingUi, toc)
        assertEquals(ReaderMoreState.PREPARING_CHILD_TOC, fromUi)
        assertEquals(toc, fromUi.preparingTarget())
    }

    @Test
    fun `preparing ready promotes to CHILD`() {
        assertEquals(
            pageMapping,
            ReaderMoreStateMachine.onPreparingReady(ReaderMoreState.PREPARING_CHILD_PAGE_MAPPING),
        )
        assertEquals(
            thumbs,
            ReaderMoreStateMachine.onPreparingReady(ReaderMoreState.PREPARING_CHILD_THUMBNAILS),
        )
    }

    @Test
    fun `basic and advanced mutually exclusive by construction`() {
        ReaderMoreState.entries.forEach { s ->
            assertFalse("state $s", s.isInline() && s.isChild())
            assertFalse("state $s", s.isInline() && s.isPreparing())
            assertFalse("state $s", s.isChild() && s.isPreparing())
        }
    }

    @Test
    fun `section toggle while child open is no-op for machine - host closes child first`() {
        assertEquals(toc, ReaderMoreStateMachine.onSectionToggle(toc, readingMode))
        // Still child — host owns serial close then expand.
        assertTrue(toc.isChild())
    }

    @Test
    fun `child collapse returns MAIN`() {
        assertEquals(ReaderMoreState.MAIN, ReaderMoreStateMachine.onChildCollapse(pageMapping))
    }

    @Test
    fun `matrix Basic to each Advanced goes preparing`() {
        val advanced = listOf(pageMapping, toc, thumbs, export)
        for (basic in listOf(readingMode, readingUi)) {
            for (adv in advanced) {
                val next = ReaderMoreStateMachine.onOpenChild(basic, adv)
                assertTrue("$basic → $adv", next.isPreparing())
                assertEquals(adv, next.preparingTarget())
                val open = ReaderMoreStateMachine.onPreparingReady(next)
                assertEquals(adv, open)
                assertTrue(open.isChild())
                assertFalse(open.isInline())
            }
        }
    }

    @Test
    fun `every advanced owns distinct source row`() {
        assertEquals(listOf(2, 3, 4, 5), listOf(pageMapping, toc, thumbs, export).map { childRowIndex(it) })
    }

    @Test
    fun `advanced to advanced switches directly - never preparing`() {
        val advanced = listOf(pageMapping, toc, thumbs, export)
        for (from in advanced) {
            for (to in advanced) {
                if (from == to) continue
                val next = ReaderMoreStateMachine.onOpenChild(from, to)
                assertEquals("$from → $to", to, next)
                assertFalse("$from → $to must not prepare", next.isPreparing())
                assertTrue("$from → $to must stay child", next.isChild())
            }
        }
    }

    @Test
    fun `advanced to same child keeps child`() {
        assertEquals(pageMapping, ReaderMoreStateMachine.onOpenChild(pageMapping, pageMapping))
        assertTrue(pageMapping.isChild())
    }

    @Test
    fun `main to each advanced opens child directly`() {
        val advanced = listOf(pageMapping, toc, thumbs, export)
        for (adv in advanced) {
            val next = ReaderMoreStateMachine.onOpenChild(ReaderMoreState.MAIN, adv)
            assertEquals(adv, next)
            assertTrue(next.isChild())
            assertFalse(next.isPreparing())
        }
    }
}
