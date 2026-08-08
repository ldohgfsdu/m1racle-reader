package com.markleaf.app.ui.pdf

/**
 * Per-kind preferred/max heights for foreground ChildCards.
 *
 * preferredHeight = header (MenuRowHeight) + body floor that fits the product
 * content without large empty slabs (PageMapping wrap-ish; others taller).
 * maxHeight = window − status/nav safe insets − outer margin; never into
 * gesture navigation. Lists/grids always own remaining body height and scroll.
 */
object AdvancedChildLayoutSpec {

    /** Level-1 header row inside every Child (matches Parent row). */
    const val HeaderHeightDp = 52f

    /**
     * Preferred total Child height (header + body), before maxHeight clamp.
     * PageMapping stays compact; TOC/Thumbs/Export allow taller content.
     */
    fun preferredHeightDp(state: ReaderMoreState): Float = when (state) {
        // Header + status + label + 56dp field + clear/save actions (~ wrap).
        ReaderMoreState.CHILD_PAGE_MAPPING -> HeaderHeightDp + 200f
        // Header + count + toolbar + ~5–6 outline rows.
        ReaderMoreState.CHILD_TOC -> HeaderHeightDp + 360f
        // Header + status + ~1.5–2 thumbnail rows (2-col grid).
        ReaderMoreState.CHILD_THUMBNAILS -> HeaderHeightDp + 340f
        // Header + chapter + 2 radios + start/end fields + actions.
        ReaderMoreState.CHILD_EXPORT -> HeaderHeightDp + 360f
        else -> HeaderHeightDp + 160f
    }

    /**
     * Absolute ceiling for any Child:
     * `windowHeight − statusInset − navInset − margin` (margin default 20dp).
     */
    fun maxHeightDp(
        windowHeightDp: Float,
        statusInsetDp: Float,
        navInsetDp: Float,
        marginDp: Float = 20f,
    ): Float = (windowHeightDp - statusInsetDp - navInsetDp - marginDp)
        .coerceAtLeast(HeaderHeightDp + 120f)

    /** Clamp preferred into available max; used by popover target height. */
    fun clampedHeightDp(state: ReaderMoreState, maxHeightDp: Float): Float =
        preferredHeightDp(state).coerceAtMost(maxHeightDp).coerceAtLeast(HeaderHeightDp + 80f)

    /**
     * Body region max height given total child height budget
     * (total − header − optional 1dp divider).
     */
    fun bodyMaxHeightDp(totalHeightDp: Float): Float =
        (totalHeightDp - HeaderHeightDp - 1f).coerceAtLeast(48f)
}
