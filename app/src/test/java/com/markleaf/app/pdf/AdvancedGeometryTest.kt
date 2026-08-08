package com.markleaf.app.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scroll-start helpers (moved to AnchoredNestedGeometry.kt after the dead
 * AdvancedGeometry target-rect API was deleted in r26). These helpers place
 * the current item near the upper third of the outline list / thumb grid.
 */
class AdvancedGeometryTest {

    @Test
    fun `thumbnail grid positions current page near the upper third`() {
        // current = 23 (1-based) → index 22 → row 11 → first visible row 9
        // → visible pages around 18–25, current 23 well inside the first screen.
        assertEquals(11, thumbnailGridRow(22))
        assertEquals(9, thumbnailInitialRow(22))
        assertTrue(thumbnailInitialRow(22) <= thumbnailGridRow(22))
        // Early pages keep a small context window without going negative.
        assertEquals(0, thumbnailInitialRow(0))
        assertEquals(0, thumbnailInitialRow(1))
    }

    @Test
    fun `outline initial row keeps context above the current item`() {
        assertEquals(0, outlineInitialRow(0))
        assertEquals(2, outlineInitialRow(5))
        assertEquals(10, outlineInitialRow(13))
    }
}
