package com.markleaf.app.ui.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for [DisplayedBitmapRetentionPolicy] — the Batch B-1 governance of
 * the continuous viewport's display mirror.
 *
 * Scenarios mandated by the batch: long-scroll retention bound, page
 * transition release, zoom protection, horizontal-strip reuse, and edge
 * clamping. The policy is pure; the viewport integration (phase gating) is
 * exercised by the existing R7 continuous-view contract suite.
 */
class DisplayedBitmapRetentionPolicyTest {

    // ── long scroll retention ────────────────────────────────────────────────

    @Test
    fun longScroll_mirrorStaysBoundedAcrossFortyPageWalk() {
        var mirror = mutableMapOf<Int, String>()
        // Simulate a session that walks 0→39, merging each new window snapshot
        // additively (the old behavior) and then applying the retention prune.
        for (anchor in 0 until 40) {
            val incoming = listOf(anchor - 1, anchor, anchor + 1).filter { it in 0 until 100 }
            mirror = (mirror + incoming.associateWith { "bmp$it" }).toMutableMap()
            mirror = DisplayedBitmapRetentionPolicy.prune(
                mirror,
                DisplayedBitmapRetentionPolicy.keepWindow(anchor, 100),
            ).toMutableMap()
            assertTrue(
                "mirror size ${mirror.size} exceeded window after page $anchor",
                mirror.size <= 3,
            )
        }
        // Final state: exactly the tail window (anchor±1 of page 39).
        assertEquals(DisplayedBitmapRetentionPolicy.keepWindow(39, 100), mirror.keys)
    }

    @Test
    fun longScroll_offWindowPagesAreReleased() {
        var mirror = mapOf(0 to "a", 1 to "b", 2 to "c")
        for (anchor in 1..10) {
            val incoming = ((anchor - 1)..(anchor + 1)).associateWith { "bmp$it" }
            mirror = mirror + incoming
            mirror = DisplayedBitmapRetentionPolicy.prune(
                mirror,
                DisplayedBitmapRetentionPolicy.keepWindow(anchor, 100),
            )
        }
        assertTrue(mirror.keys.all { it in 9..11 })
    }

    // ── page transition release ──────────────────────────────────────────────

    @Test
    fun transition_forward_releasesTrailingNeighbors() {
        // Merge the new window snapshot first (viewport behavior), then prune:
        // pages 5/6 left the retention set and must be released.
        val merged = mapOf(5 to "p5", 6 to "p6", 7 to "p7") +
            listOf(7, 8, 9).associateWith { "new$it" }
        val pruned = DisplayedBitmapRetentionPolicy.prune(
            merged,
            DisplayedBitmapRetentionPolicy.keepWindow(8, 100),
        )
        assertEquals(setOf(7, 8, 9), pruned.keys)
    }

    @Test
    fun transition_backward_releasesLeadingNeighbors() {
        val merged = mapOf(7 to "p7", 8 to "p8", 9 to "p9") +
            listOf(5, 6, 7).associateWith { "new$it" }
        val pruned = DisplayedBitmapRetentionPolicy.prune(
            merged,
            DisplayedBitmapRetentionPolicy.keepWindow(6, 100),
        )
        assertEquals(setOf(5, 6, 7), pruned.keys)
    }

    // ── zoom protection ──────────────────────────────────────────────────────

    @Test
    fun zoom_composedPagesOutsideAnchorWindowAreProtected() {
        // Anchor 6 → window {5,6,7}; a pinch-zoom composes {6,7,8} visibly —
        // page 8 must survive even though it is outside the anchor window.
        val keep = DisplayedBitmapRetentionPolicy.retentionSet(
            anchorPage = 6,
            pageCount = 100,
            zoomComposed = setOf(6, 7, 8),
        )
        assertEquals(setOf(5, 6, 7, 8), keep)

        val pruned = DisplayedBitmapRetentionPolicy.prune(
            mapOf(5 to "p5", 6 to "p6", 7 to "p7", 8 to "p8"),
            keep,
        )
        assertEquals("all four composed pages survive the zoom", 4, pruned.size)
    }

    @Test
    fun zoom_emptyComposedSet_degradesToPlainWindow() {
        assertEquals(
            DisplayedBitmapRetentionPolicy.keepWindow(4, 100),
            DisplayedBitmapRetentionPolicy.retentionSet(4, 100, emptySet()),
        )
    }

    // ── horizontal strip reuse ───────────────────────────────────────────────

    @Test
    fun horizontalStrip_windowIsDirectionIndependent_threeSlots() {
        // The horizontal three-page strip occupies exactly current±1 — the same
        // slots the vertical window keeps, so strip pages are never pruned and
        // re-entering the strip reuses retained bitmaps without re-allocation.
        assertEquals(setOf(4, 5, 6), DisplayedBitmapRetentionPolicy.keepWindow(5, 100))
        assertEquals(setOf(4, 5, 6), DisplayedBitmapRetentionPolicy.keepWindow(5, 100))
    }

    // ── edges ────────────────────────────────────────────────────────────────

    @Test
    fun edges_anchorAndCountClamped() {
        assertEquals(emptySet<Int>(), DisplayedBitmapRetentionPolicy.keepWindow(0, 0))
        assertEquals(emptySet<Int>(), DisplayedBitmapRetentionPolicy.keepWindow(3, -1))
        assertEquals(setOf(0), DisplayedBitmapRetentionPolicy.keepWindow(-5, 1))
        assertEquals(setOf(0, 1), DisplayedBitmapRetentionPolicy.keepWindow(0, 2))
        assertEquals(setOf(98, 99), DisplayedBitmapRetentionPolicy.keepWindow(120, 100))
    }

    @Test
    fun prune_identityPreserved_whenNothingDropped() {
        val original = mapOf(4 to "a", 5 to "b")
        assertSame(
            original,
            DisplayedBitmapRetentionPolicy.prune(original, setOf(4, 5, 6)),
        )
        assertSame(
            original,
            DisplayedBitmapRetentionPolicy.prune(original, original.keys),
        )
    }

    @Test
    fun prune_emptyMapStaysEmpty() {
        assertTrue(DisplayedBitmapRetentionPolicy.prune(emptyMap<Int, String>(), setOf(1)).isEmpty())
    }

    @Test
    fun prune_neverKeepsStrayPages() {
        val pruned = DisplayedBitmapRetentionPolicy.prune(
            mapOf(1 to "x", 50 to "y", 51 to "z"),
            setOf(50, 51, 52),
        )
        assertFalse(pruned.containsKey(1))
        assertEquals(setOf(50, 51), pruned.keys)
    }
}
