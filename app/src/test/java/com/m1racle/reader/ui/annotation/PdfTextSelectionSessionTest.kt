package com.m1racle.reader.ui.annotation

import com.m1racle.reader.pdf.PdfSelectablePage
import com.m1racle.reader.pdf.PdfTextSelectionRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PdfTextSelectionSessionTest {
    private val range = PdfTextSelectionRange(PdfSelectablePage.Hit(0, 0), PdfSelectablePage.Hit(0, 3))

    @Test
    fun selectingAnotherPageReplacesThePreviousSelection() {
        val session = PdfTextSelectionSession()
        val first = session.pageState(0)
        val second = session.pageState(1)
        session.setSelection(0, range)
        session.setSelection(1, range)
        assertNull(first.value)
        assertEquals(range, second.value)
    }

    @Test
    fun disposingAnOldPageKeepsTheNewPagesSelection() {
        val session = PdfTextSelectionSession()
        val second = session.pageState(1)
        session.setSelection(0, range)
        session.setSelection(1, range)
        session.clearPage(0)
        assertEquals(range, second.value)
    }

    @Test
    fun clearingTheDocumentClearsEveryPagesState() {
        val session = PdfTextSelectionSession()
        val first = session.pageState(0)
        val second = session.pageState(1)
        session.setSelection(0, range)
        session.clear()
        assertNull(first.value)
        assertNull(second.value)
        assertFalse(session.hasSelection())
    }

    @Test
    fun cancelledGestureRestoresTheRangeItStartedWith() {
        val session = PdfTextSelectionSession()
        val active = range.copy(focus = PdfSelectablePage.Hit(0, 5))
        session.setSelection(0, active)
        session.restoreAfterCancellation(0, active, range)
        assertEquals(range, session.pageState(0).value)
    }

    @Test
    fun cancelledOldGestureCannotOverwriteAnotherPagesSelection() {
        val session = PdfTextSelectionSession()
        session.setSelection(0, range)
        session.setSelection(1, range)
        session.restoreAfterCancellation(0, range, range)
        assertNull(session.pageState(0).value)
        assertEquals(range, session.pageState(1).value)
    }

    @Test
    fun disposedGestureCannotResurrectAClearedSelection() {
        val session = PdfTextSelectionSession()
        session.setSelection(0, range)
        session.clearPage(0)
        session.restoreAfterCancellation(0, range, range)
        assertFalse(session.hasSelection())
    }

    @Test
    fun cancelledGestureCannotOverwriteItsReplacementOnTheSamePage() {
        val session = PdfTextSelectionSession()
        val replacement = range.copy(focus = PdfSelectablePage.Hit(0, 6))
        session.setSelection(0, replacement)
        session.restoreAfterCancellation(0, range, range)
        assertEquals(replacement, session.pageState(0).value)
    }
}
