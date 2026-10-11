package com.m1racle.reader.ui.epub

import androidx.compose.ui.unit.IntSize

/** Physical line bounds from the same text layout that paints the page. */
internal data class EpubLine(val top: Float, val bottom: Float, val start: Int, val end: Int)

internal data class EpubRowMetrics(
    val spineIndex: Int,
    val chapterStart: Boolean,
    val lines: List<EpubLine>,
    val continuation: Boolean = false,
    val keepWithNext: Boolean = false,
)

internal data class EpubPageSlice(
    val row: Int,
    val start: Int,
    val end: Int,
    val sourceTop: Float,
    val sourceBottom: Float,
    val pageTop: Float,
)

internal data class EpubPage(val spineIndex: Int, val slices: List<EpubPageSlice>) {
    val height: Float get() = slices.maxOfOrNull { it.pageTop + it.sourceBottom - it.sourceTop } ?: 0f
}

/** Never cut a line or estimate pages from character/paragraph counts. */
internal fun paginateEpub(rows: List<EpubRowMetrics>, pageHeight: Float, paragraphGap: Float): List<EpubPage> {
    require(pageHeight > 0f && pageHeight.isFinite())
    val pages = mutableListOf<EpubPage>()
    var slices = mutableListOf<EpubPageSlice>()
    var used = 0f
    var spine = rows.firstOrNull()?.spineIndex ?: 0
    // Keep a heading chain with the first following body line when it can fit.
    // Compute backwards once; long runs of headings must not make pagination quadratic.
    val keepHeight = FloatArray(rows.size)
    for (index in rows.indices.reversed()) {
        val row = rows[index]
        val ownHeight = row.lines.sumOf { (it.bottom - it.top).toDouble() }.toFloat()
        val next = rows.getOrNull(index + 1)
        keepHeight[index] = if (row.keepWithNext) {
            ownHeight + if (next != null && !next.chapterStart && next.spineIndex == row.spineIndex) {
                (if (next.continuation) 0f else paragraphGap) + keepHeight[index + 1]
            } else 0f
        } else row.lines.firstOrNull()?.let { it.bottom - it.top } ?: 0f
    }
    fun finish() {
        if (slices.isNotEmpty()) pages.add(EpubPage(spine, slices.toList()))
        slices = mutableListOf()
        used = 0f
    }
    rows.forEachIndexed { rowIndex, row ->
        if (row.chapterStart || row.spineIndex != spine) finish()
        spine = row.spineIndex
        if (row.keepWithNext && slices.isNotEmpty()) {
            val ownHeight = row.lines.sumOf { (it.bottom - it.top).toDouble() }.toFloat()
            val required = keepHeight[rowIndex].takeIf { it <= pageHeight } ?: ownHeight
            val gap = if (row.continuation) 0f else paragraphGap
            if (required <= pageHeight && used + gap + required > pageHeight) finish()
        }
        row.lines.forEach { line ->
            val height = line.bottom - line.top
            val previous = slices.lastOrNull()
            var gap = if (previous != null && previous.row != rowIndex && !row.continuation) paragraphGap else 0f
            if (used + gap + height > pageHeight && slices.isNotEmpty()) {
                finish()
                gap = 0f
            }
            val last = slices.lastOrNull()
            if (last != null && last.row == rowIndex) {
                slices[slices.lastIndex] = last.copy(end = line.end, sourceBottom = line.bottom)
            } else {
                slices.add(EpubPageSlice(rowIndex, line.start, line.end, line.top, line.bottom, used + gap))
            }
            used += gap + height
        }
    }
    finish()
    return pages
}

internal fun List<EpubPage>.pageForText(row: Int, offset: Int): Int =
    indexOfLast { page ->
        val first = page.slices.first()
        first.row < row || (first.row == row && first.start <= offset)
    }.coerceAtLeast(0)

/** The existing persisted EPUB position is row index + pixel offset within that row. */
internal fun List<EpubPage>.pageForPosition(row: Int, offset: Int): Int =
    indexOfLast { page ->
        val first = page.slices.first()
        first.row < row || (first.row == row && first.sourceTop <= offset)
    }.coerceAtLeast(0)

internal enum class EpubPageTap { Previous, Next, ToggleChrome }

/** Left/right thirds turn the page; the center toggles chrome. Matches Apple Books / 微信读书. */
internal fun epubPageTap(x: Float, width: Float): EpubPageTap {
    if (width <= 0f) return EpubPageTap.ToggleChrome
    val t = x / width
    return when {
        t < 0.28f -> EpubPageTap.Previous
        t > 0.72f -> EpubPageTap.Next
        else -> EpubPageTap.ToggleChrome
    }
}

/** Fit within both page dimensions while preserving the image aspect ratio. */
internal fun epubFitImage(intrinsicWidth: Int, intrinsicHeight: Int, maxWidth: Int, maxHeight: Int): IntSize {
    val width = maxWidth.coerceAtLeast(1)
    val height = maxHeight.coerceAtLeast(1)
    if (intrinsicWidth <= 0 || intrinsicHeight <= 0) return IntSize(width, (height * 0.4f).toInt().coerceAtLeast(1))
    val scale = minOf(width.toDouble() / intrinsicWidth, height.toDouble() / intrinsicHeight)
    return IntSize((intrinsicWidth * scale).toInt().coerceIn(1, width),
        (intrinsicHeight * scale).toInt().coerceIn(1, height))
}

/**
 * Apple Books slide: the lower numbered sheet is always on top.
 * Forward turns slide it away; backward turns bring it back over the next sheet.
 * HorizontalPager owns the gesture/settling; the sheet underneath follows at 25% speed.
 */
internal data class EpubPageTurnLayer(
    val translationX: Float,
    val zIndex: Float,
    val edgeShadow: Float,
    val paperLift: Float = 0f,
)

internal fun epubPageTurnLayer(
    pageOffset: Float,
    currentPageOffsetFraction: Float,
    currentPage: Int,
    readingPage: Int,
    width: Float,
): EpubPageTurnLayer {
    val scrollFromReadingPage = currentPage - readingPage + currentPageOffsetFraction
    // The committed reading page stays stable through the half-page crossing
    // and the drag -> fling handoff; Pager's settledPage does not.
    // The same geometry applies all the way to exact rest. Switching branches
    // within .002/.999 pages snapped the under-sheet and its corner at handoff.
    val underneath = pageOffset > 0f
    val distance = kotlin.math.abs(scrollFromReadingPage).coerceIn(0f, 1f)
    val lift = minOf(distance / 0.06f, (1f - distance) / 0.06f, 1f).coerceAtLeast(0f)
    val relativePage = pageOffset + currentPageOffsetFraction + currentPage - readingPage
    return EpubPageTurnLayer(
        translationX = if (underneath) -0.75f * pageOffset * width else 0f,
        // Preloaded sheets overlap after parallax. Keep the nearest under-sheet
        // above later sheets, otherwise a second neighbour covers its paper.
        zIndex = if (underneath) -pageOffset else 1f,
        edgeShadow = if (underneath) 0f else lift,
        // Feedback follows the distance from the committed page, including
        // fling and cancellation. Crossing half a page never changes identity.
        paperLift = if (kotlin.math.abs(relativePage) < 0.01f) (1f - (1f - distance) * (1f - distance)) else 0f,
    )
}

internal fun epubViewportSettled(previous: IntSize, next: IntSize): Boolean {
    if (previous == IntSize.Zero) return false
    return kotlin.math.abs(previous.width - next.width) <= 2 &&
        kotlin.math.abs(previous.height - next.height) <= 2
}
