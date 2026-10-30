package com.m1racle.reader.ui.annotation

import com.m1racle.reader.data.anchor.NormalizedRect
import com.m1racle.reader.pdf.PdfSelectablePage
import com.m1racle.reader.pdf.PdfTextSelectionRange
import kotlin.math.abs

enum class PdfSelectionHandle {
    ANCHOR,
    FOCUS,
}

data class PdfSelectionHandlePoint(
    val handle: PdfSelectionHandle,
    val x: Float,
    val y: Float,
)

data class PdfSelectionActionPlacement(
    val x: Float,
    val y: Float,
)

/** Page-local geometry shared by drawing and hit testing for the two selection handles. */
object PdfTextSelectionGeometry {

    fun handles(
        page: PdfSelectablePage,
        range: PdfTextSelectionRange,
        widthPx: Float,
        heightPx: Float,
        visualRadiusPx: Float,
    ): List<PdfSelectionHandlePoint> {
        if (widthPx <= 0f || heightPx <= 0f || visualRadiusPx <= 0f) return emptyList()
        if (page.validRange(range.anchor, range.focus) == null) return emptyList()
        val (first, last) = range.ordered()
        val firstRect = page.lines[first.line].glyphs[first.glyph].rect
        val lastRect = page.lines[last.line].glyphs[last.glyph].rect
        val inset = visualRadiusPx.coerceAtMost(minOf(widthPx, heightPx) / 2f)
        val usableWidth = (widthPx - inset * 2f).coerceAtLeast(0f)
        var startX = (firstRect.x * widthPx).coerceIn(inset, widthPx - inset)
        var endX = ((lastRect.x + lastRect.width) * widthPx).coerceIn(inset, widthPx - inset)
        if (abs(endX - startX) < visualRadiusPx * 2f) {
            val center = ((startX + endX) / 2f).coerceIn(inset, widthPx - inset)
            val halfGap = (visualRadiusPx * 0.75f).coerceAtMost(usableWidth / 2f)
            startX = (center - halfGap).coerceIn(inset, widthPx - inset)
            endX = (center + halfGap).coerceIn(inset, widthPx - inset)
        }
        val startY = ((firstRect.y + firstRect.height / 2f) * heightPx)
            .coerceIn(inset, heightPx - inset)
        val endY = ((lastRect.y + lastRect.height / 2f) * heightPx)
            .coerceIn(inset, heightPx - inset)
        val anchorIsFirst = range.anchor == first
        val startHandle = if (anchorIsFirst) PdfSelectionHandle.ANCHOR else PdfSelectionHandle.FOCUS
        val endHandle = if (anchorIsFirst) PdfSelectionHandle.FOCUS else PdfSelectionHandle.ANCHOR
        return listOf(
            PdfSelectionHandlePoint(startHandle, startX, startY),
            PdfSelectionHandlePoint(endHandle, endX, endY),
        )
    }

    fun hitHandle(
        page: PdfSelectablePage,
        range: PdfTextSelectionRange,
        x: Float,
        y: Float,
        widthPx: Float,
        heightPx: Float,
        visualRadiusPx: Float,
        hitRadiusPx: Float,
    ): PdfSelectionHandle? {
        if (hitRadiusPx <= 0f) return null
        val maxDistanceSquared = hitRadiusPx * hitRadiusPx
        return handles(page, range, widthPx, heightPx, visualRadiusPx)
            .map { point ->
                val dx = x - point.x
                val dy = y - point.y
                point to dx * dx + dy * dy
            }
            .filter { it.second <= maxDistanceSquared }
            .minByOrNull { it.second }
            ?.first
            ?.handle
    }

    fun actionBarPlacement(
        selectionRects: List<NormalizedRect>,
        widthPx: Float,
        heightPx: Float,
        barWidthPx: Float,
        barHeightPx: Float,
        gapPx: Float,
        marginPx: Float,
    ): PdfSelectionActionPlacement? {
        if (selectionRects.isEmpty() || widthPx <= 0f || heightPx <= 0f ||
            barWidthPx <= 0f || barHeightPx <= 0f
        ) {
            return null
        }
        val bounds = PdfSelectablePage.bounds(selectionRects)
        val maxX = (widthPx - barWidthPx - marginPx).coerceAtLeast(marginPx)
        val x = (bounds.centerX() * widthPx - barWidthPx / 2f).coerceIn(marginPx, maxX)
        val above = bounds.y * heightPx - barHeightPx - gapPx
        val below = (bounds.y + bounds.height) * heightPx + gapPx
        val maxY = (heightPx - barHeightPx - marginPx).coerceAtLeast(marginPx)
        val y = (if (above >= marginPx) above else below).coerceIn(marginPx, maxY)
        return PdfSelectionActionPlacement(x, y)
    }

    private fun NormalizedRect.centerX(): Float = x + width / 2f
}
