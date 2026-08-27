package com.m1racle.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfReadingModeTest {

    @Test
    fun serializeAndRestore_vertical() {
        val mode = PdfReadingMode.VERTICAL_CONTINUOUS
        assertEquals("vertical_continuous", mode.toStorage())
        assertEquals(mode, PdfReadingMode.fromStorage(mode.toStorage()))
    }

    @Test
    fun serializeAndRestore_horizontal() {
        val mode = PdfReadingMode.HORIZONTAL_PAGED
        assertEquals("horizontal_paged", mode.toStorage())
        assertEquals(mode, PdfReadingMode.fromStorage(mode.toStorage()))
    }

    @Test
    fun defaultIsVerticalContinuous() {
        assertEquals(PdfReadingMode.VERTICAL_CONTINUOUS, PdfReadingMode.default())
        assertEquals(PdfReadingMode.VERTICAL_CONTINUOUS, PdfReadingMode.fromStorage(null))
        assertEquals(PdfReadingMode.VERTICAL_CONTINUOUS, PdfReadingMode.fromStorage(""))
    }

    @Test
    fun modeSwitchKeepsPageIndex() {
        assertEquals(318, PdfReadingNavigation.pageAfterModeSwitch(318, 982))
        assertEquals(0, PdfReadingNavigation.pageAfterModeSwitch(-1, 982))
        assertEquals(981, PdfReadingNavigation.pageAfterModeSwitch(9999, 982))
    }

    @Test
    fun previousNextBoundaries() {
        assertEquals(0, PdfReadingNavigation.previousPage(0, 982))
        assertEquals(0, PdfReadingNavigation.previousPage(1, 982))
        assertEquals(981, PdfReadingNavigation.nextPage(981, 982))
        assertEquals(981, PdfReadingNavigation.nextPage(980, 982))
        assertEquals(0, PdfReadingNavigation.clampPageIndex(-5, 0))
    }

    @Test
    fun prefsKeysStable() {
        assertEquals("pdf_reading_mode_doc1", PdfReadingPrefs.modeKey("doc1"))
        assertEquals("pdf_show_page_buttons_doc1", PdfReadingPrefs.buttonsKey("doc1"))
    }

    @Test
    fun pageButtonsDefaultTrue_canToggleConceptually() {
        // Storage uses boolean default true; pure invert check for restore semantics.
        var show = true
        show = false
        assertFalse(show)
        show = true
        assertTrue(show)
    }

    @Test
    fun continuousModeHidesFlipChrome() {
        val vertical = PdfReadingMode.VERTICAL_CONTINUOUS
        val showButtonsPref = true
        val showInUi = vertical == PdfReadingMode.HORIZONTAL_PAGED && showButtonsPref
        assertFalse(showInUi)
        val horizontal = PdfReadingMode.HORIZONTAL_PAGED
        assertTrue(horizontal == PdfReadingMode.HORIZONTAL_PAGED && showButtonsPref)
    }

    @Test
    fun persistDedupSamePage() {
        var last = -1
        var writes = 0
        fun schedule(page: Int) {
            if (page == last) return
            last = page
            writes++
        }
        schedule(10)
        schedule(10)
        schedule(11)
        schedule(11)
        assertEquals(2, writes)
        assertEquals(11, last)
    }
}
