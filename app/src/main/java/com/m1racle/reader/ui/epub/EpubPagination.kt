package com.m1racle.reader.ui.epub

import androidx.compose.ui.unit.IntSize

/** Physical line bounds from the same text layout that paints the page. */
internal data class EpubLine(val top: Float, val bottom: Float, val start: Int, val end: Int)

internal data class EpubRowMetrics(
    val spineIndex: Int,
    val chapterStart: Boolean,
    val lines: List<EpubLine>,
    val continuation: Boolean = false,
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
    fun finish() {
        if (slices.isNotEmpty()) pages.add(EpubPage(spine, slices.toList()))
        slices = mutableListOf()
        used = 0f
    }
    rows.forEachIndexed { rowIndex, row ->
        if (row.chapterStart || row.spineIndex != spine) finish()
        spine = row.spineIndex
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
 * Cover page-turn on top of HorizontalPager's default dual-slide.
 * The leaving page stays put; the incoming page slides over it.
 * That is 微信读书/掌阅「覆盖」, not a carousel/roller translation of both pages.
 */
internal data class EpubPageTurnLayer(
    val translationX: Float,
    val zIndex: Float,
    val edgeShadow: Float,
)

internal fun epubPageTurnLayer(
    pageOffset: Float,
    currentPageOffsetFraction: Float,
    currentPage: Int,
    settledPage: Int,
    width: Float,
): EpubPageTurnLayer {
    val scrollFromSettledPage = currentPage - settledPage + currentPageOffsetFraction
    // Direction is relative to settledPage, which stays stable through the half-page crossing.
    // At rest, and for fully off-screen pages, keep HorizontalPager's layout.
    // Pinning every offset<=0 page pulled the previous page onto the current one
    // and flipped whenever currentPageOffsetFraction jittered around 0.
    if (kotlin.math.abs(scrollFromSettledPage) < 0.002f || kotlin.math.abs(pageOffset) >= 0.999f) {
        return EpubPageTurnLayer(0f, 0f, 0f)
    }
    val goingForward = scrollFromSettledPage > 0f
    val pinned = if (goingForward) pageOffset < 0f else pageOffset > 0f
    val progress = (1f - kotlin.math.abs(pageOffset)).coerceIn(0f, 1f)
    return EpubPageTurnLayer(
        translationX = if (pinned) -pageOffset * width else 0f,
        zIndex = if (pinned) 0f else 1f,
        edgeShadow = if (pinned) 0f else progress,
    )
}

internal fun epubViewportSettled(previous: IntSize, next: IntSize): Boolean {
    if (previous == IntSize.Zero) return false
    return kotlin.math.abs(previous.width - next.width) <= 2 &&
        kotlin.math.abs(previous.height - next.height) <= 2
}
