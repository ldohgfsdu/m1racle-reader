package com.m1racle.reader.pdf

import com.m1racle.reader.testing.ProductionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scroll-start helpers (moved to AnchoredNestedGeometry.kt after the dead
 * AdvancedGeometry target-rect API was deleted in r26). These helpers place
 * the current item near the upper third of the outline list / thumb grid.
 */
class AdvancedGeometryTest {

    @Test
    fun `thumbnail grid opens with the current page as the first cell`() {
        assertEquals(3, ThumbnailGridColumns)
        // Page 30 (index 29) must be item 29, never a row-scaled index.
        assertEquals(29, thumbnailInitialItemIndex(29))
        assertEquals(0, thumbnailInitialItemIndex(0))
        assertEquals(22, thumbnailInitialItemIndex(22))
        assertEquals(thumbnailGridRow(29), thumbnailInitialRow(29))
        assertEquals(29 / ThumbnailGridColumns, thumbnailGridRow(29))
    }

    @Test
    fun `thumbnail card wires LazyGrid to current page item index`() {
        val card = ProductionSource.text("com/m1racle/reader/ui/pdf/ReaderAdvancedCard.kt")
        assertTrue(card.contains("thumbnailInitialItemIndex("))
        assertTrue(card.contains("GridCells.Fixed(ThumbnailGridColumns)"))
        assertTrue(card.contains("ContentScale.Crop"))
        assertFalse(
            card.contains("initialFirstVisibleItemIndex = thumbnailInitialRow"),
        )
        assertFalse(card.contains("GridCells.Fixed(2)"))
        assertFalse(
            "re-scroll on pageIndex fights the user's drag",
            card.contains("LaunchedEffect(params.currentPageIndex)"),
        )
        assertFalse(
            "re-scroll on currentId yank the TOC while the PDF page changes",
            card.contains("LaunchedEffect(currentId, viewport.outerStartIndex)"),
        )
        assertFalse(
            "re-scroll on currentId yank the TOC while the PDF page changes",
            card.contains("LaunchedEffect(currentId, startIndex)"),
        )
    }

    @Test
    fun `outline card is a complete catalog with current chapter at open`() {
        val card = ProductionSource.text("com/m1racle/reader/ui/pdf/ReaderAdvancedCard.kt")
        assertFalse(card.contains("stickyHeader"))
        assertFalse(card.contains("outlineViewport"))
        assertFalse(card.contains("NestedOutlineSectionList"))
        assertTrue(card.contains("outlineScrollStartIndex"))
        assertTrue(card.contains("items(flatAll"))
        assertFalse(card.contains("outlineInitialRow"))
        assertTrue(card.contains("pdf_outline_current_badge"))
    }
}
