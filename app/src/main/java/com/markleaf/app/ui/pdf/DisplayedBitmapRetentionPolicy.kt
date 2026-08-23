package com.markleaf.app.ui.pdf

/**
 * Batch B-1: retention policy for the continuous viewport's display mirror
 * (`displayedBitmaps` / `displayedMetadata` / `displayedAspects`).
 *
 * Problem being governed: the mirror was ADDITIVE-ONLY — the ViewModel side
 * windows its bitmap cache to current±1 (`PdfPageBitmapCache.retainOnly`),
 * but the viewport merged every incoming snapshot into its own map and never
 * removed pages that left the window. Long sessions over large documents
 * therefore accumulated one high-tier bitmap per visited page.
 *
 * Policy: keep a three-page window around the primary anchor page — the exact
 * window the cache and the horizontal three-page strip use — plus, while a
 * pinch-zoom is active, every page still visibly composed under the transform
 * (zoom protection: never blank out what the user is actively looking at).
 * Shrinking itself happens only in SETTLING/IDLE phases at the call site;
 * this object stays pure so the whole contract is JVM-testable.
 */
object DisplayedBitmapRetentionPolicy {

    /**
     * Retention window for [anchorPage]: the anchor plus one neighbor on each
     * side, clamped to `[0, pageCount)`. Direction-independent — the same
     * window serves forward/backward transitions and the horizontal strip.
     */
    fun keepWindow(anchorPage: Int, pageCount: Int): Set<Int> {
        if (pageCount <= 0) return emptySet()
        val anchor = anchorPage.coerceIn(0, pageCount - 1)
        val window = mutableSetOf<Int>()
        for (page in anchor - 1..anchor + 1) {
            if (page >= 0 && page < pageCount) window.add(page)
        }
        return window
    }

    /**
     * Final retention set: the anchor window plus [zoomComposed] — the pages
     * visibly composed under an active pinch-zoom (pass an empty set when
     * scale ≤ 1). Zoom-composed pages are honored even when they fall outside
     * the anchor window so an active transform never blanks on screen.
     */
    fun retentionSet(
        anchorPage: Int,
        pageCount: Int,
        zoomComposed: Set<Int>,
    ): Set<Int> {
        val base = keepWindow(anchorPage, pageCount)
        if (zoomComposed.isEmpty()) return base
        return base + zoomComposed.filter { it in 0 until pageCount }
    }

    /**
     * Drop entries whose page left the retention set.
     * Returns the SAME instance when nothing needs dropping — callers rely on
     * reference equality to avoid pointless snapshot writes/recomposition.
     */
    fun <T> prune(map: Map<Int, T>, keep: Set<Int>): Map<Int, T> {
        if (map.isEmpty() || map.keys.all { it in keep }) return map
        return map.filterKeys { it in keep }
    }
}
