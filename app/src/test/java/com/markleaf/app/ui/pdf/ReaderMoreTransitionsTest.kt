package com.markleaf.app.ui.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mutual exclusion + Back priority pure logic (shipped [ReaderMoreTransitions]).
 *
 * Gates: open Child collapses inline path; section while Child open closes Child
 * first then expands inline; Back is Child → inline → Parent dismiss.
 */
class ReaderMoreTransitionsTest {

    private val readingMode = ReaderMoreState.INLINE_READING_MODE
    private val readingUi = ReaderMoreState.INLINE_READING_UI
    private val pageMapping = ReaderMoreState.CHILD_PAGE_MAPPING
    private val toc = ReaderMoreState.CHILD_TOC

    @Test
    fun `back priority closes child before inline before parent`() {
        assertEquals(
            ReaderMoreBackAction.CLOSE_CHILD,
            ReaderMoreTransitions.onBack(pageMapping),
        )
        assertEquals(
            ReaderMoreBackAction.COLLAPSE_INLINE,
            ReaderMoreTransitions.onBack(readingMode),
        )
        assertEquals(
            ReaderMoreBackAction.COLLAPSE_INLINE,
            ReaderMoreTransitions.onBack(readingUi),
        )
        assertEquals(
            ReaderMoreBackAction.DISMISS_PARENT,
            ReaderMoreTransitions.onBack(ReaderMoreState.MAIN),
        )
        assertEquals(
            ReaderMoreBackAction.CLOSE_CAPSULE,
            ReaderMoreTransitions.onBack(ReaderMoreState.MAIN, capsuleExpanded = true),
        )
    }

    @Test
    fun `back while child closing does not re-trigger child close`() {
        // childClosing already true → treat as dismiss/parent path via requestClose.
        // onBack still reports CLOSE_CHILD only when !childClosing.
        assertEquals(
            ReaderMoreBackAction.DISMISS_PARENT,
            ReaderMoreTransitions.onBack(pageMapping, childClosing = true),
        )
    }

    @Test
    fun `section toggle while child open closes child first with pending inline`() {
        val d = ReaderMoreTransitions.onSectionToggle(pageMapping, readingUi)
        assertTrue(d.closeChildFirst)
        assertEquals(readingUi, d.pendingInline)
        assertNull(d.immediateState)
    }

    @Test
    fun `section toggle from main opens inline immediately`() {
        val d = ReaderMoreTransitions.onSectionToggle(ReaderMoreState.MAIN, readingMode)
        assertFalse(d.closeChildFirst)
        assertEquals(readingMode, d.immediateState)
        assertNull(d.pendingInline)
    }

    @Test
    fun `open child from Basic prepares first - from Main opens immediately`() {
        val fromMain = ReaderMoreTransitions.onOpenChild(ReaderMoreState.MAIN, toc)
        assertEquals(toc, fromMain.nextChild)
        assertFalse("Main has canonical bounds — no prepare phase", fromMain.collapseInlineFirst)

        val fromInline = ReaderMoreTransitions.onOpenChild(readingMode, pageMapping)
        assertTrue(fromInline.nextChild.isPreparing())
        assertEquals(pageMapping, fromInline.nextChild.preparingTarget())
        assertTrue(fromInline.collapseInlineFirst)
        assertFalse(fromInline.nextChild.isInline())
        assertFalse(fromInline.nextChild.isChild())
    }

    @Test
    fun `after child collapsed applies pending inline or MAIN`() {
        assertEquals(readingMode, ReaderMoreTransitions.afterChildCollapsed(readingMode))
        assertEquals(readingUi, ReaderMoreTransitions.afterChildCollapsed(readingUi))
        assertEquals(ReaderMoreState.MAIN, ReaderMoreTransitions.afterChildCollapsed(null))
        // Non-inline pending ignored → MAIN
        assertEquals(ReaderMoreState.MAIN, ReaderMoreTransitions.afterChildCollapsed(toc))
    }

    @Test
    fun `enum mutual exclusion still holds - never both expanded states`() {
        // One enum value cannot be both inline and child.
        ReaderMoreState.entries.forEach { s ->
            assertFalse(s.isInline() && s.isChild())
        }
    }
}
