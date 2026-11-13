package com.m1racle.reader.ui.annotation

import com.m1racle.reader.core.model.NormalizedRect
import com.m1racle.reader.pdf.PdfSelectionEdge
import com.m1racle.reader.pdf.PdfSelectablePage
import com.m1racle.reader.pdf.PdfTextSelectionRange

enum class PdfSelectionHandle {
    ANCHOR,
    FOCUS,
}

data class PdfSelectionHandlePoint(
    val handle: PdfSelectionHandle,
    /** Centre of the round body, also the centre of the touch target. */
    val x: Float,
    val y: Float,
    /** Square corner attached directly to the selected glyph boundary. */
    val tipX: Float,
    val tipY: Float,
    val isLeft: Boolean,
    val above: Boolean,
    val radiusPx: Float,
)

data class PdfSelectionActionPlacement(
    val x: Float,
    val y: Float,
)

/**
 * Action-bar sizing expressed in page-local (pre-scale) coordinates.
 *
 * The annotation layer lives inside the viewport's GPU scale transform, so a
 * dp-sized child would grow with the page bitmap. Multiplying these values by
 * the page scale always yields the same on-screen pixels, independent of zoom.
 * [inverseScale] is applied to the bar's own graphics layer to undo the parent
 * transform without changing its layout box or selection hit-testing.
 */
data class PdfSelectionActionMetrics(
    val inverseScale: Float,
    val barWidthPx: Float,
    val barHeightPx: Float,
    val gapPx: Float,
    val marginPx: Float,
)

/** Page-local geometry shared by drawing and hit testing for the two selection handles. */
object PdfTextSelectionGeometry {

    /**
     * Android selection handles attach a square corner to the text edge, with
     * the round body outside the selection. Start/end identities follow the
     * pointer even when the ordered range reverses. Boundary adjustment moves
     * the body or changes its side; it never moves the text attachment point.
     */
    fun handles(
        page: PdfSelectablePage,
        range: PdfTextSelectionRange,
        widthPx: Float,
        heightPx: Float,
        visualRadiusPx: Float,
    ): List<PdfSelectionHandlePoint> {
        if (!widthPx.isFinite() || !heightPx.isFinite() || !visualRadiusPx.isFinite() ||
            widthPx <= 0f || heightPx <= 0f || visualRadiusPx <= 0f
        ) return emptyList()
        if (page.validRange(range.anchor, range.focus) == null) return emptyList()
        val (first, last) = range.ordered()
        val firstRect = page.lines[first.line].glyphs[first.glyph].rect
        val lastRect = page.lines[last.line].glyphs[last.glyph].rect
        val radius = visualRadiusPx.coerceAtMost(minOf(widthPx, heightPx) / 4f)
        val diameter = radius * 2f
        val anchorIsFirst = range.anchor == first
        fun point(rect: NormalizedRect, start: Boolean): PdfSelectionHandlePoint {
            val tipX = ((if (start) rect.x else rect.x + rect.width) * widthPx)
                .coerceIn(0f, widthPx)
            val leftFits = tipX >= diameter
            val rightFits = tipX + diameter <= widthPx
            val isLeft = if (start) leftFits || !rightFits else !rightFits && leftFits
            val above = (rect.y + rect.height) * heightPx + diameter > heightPx
            val tipY = (if (above) rect.y else rect.y + rect.height) * heightPx
            val handle = if (start == anchorIsFirst) PdfSelectionHandle.ANCHOR else PdfSelectionHandle.FOCUS
            return PdfSelectionHandlePoint(
                handle = handle,
                x = tipX + if (isLeft) -radius else radius,
                y = tipY + if (above) -radius else radius,
                tipX = tipX,
                tipY = tipY,
                isLeft = isLeft,
                above = above,
                radiusPx = radius,
            )
        }
        return listOf(point(firstRect, true), point(lastRect, false))
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
    ): PdfSelectionHandle? = hitHandlePoint(
        page = page,
        range = range,
        x = x,
        y = y,
        widthPx = widthPx,
        heightPx = heightPx,
        visualRadiusPx = visualRadiusPx,
        hitRadiusPx = hitRadiusPx,
    )?.handle

    fun hitHandlePoint(
        page: PdfSelectablePage,
        range: PdfTextSelectionRange,
        x: Float,
        y: Float,
        widthPx: Float,
        heightPx: Float,
        visualRadiusPx: Float,
        hitRadiusPx: Float,
    ): PdfSelectionHandlePoint? {
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
    }

    /**
     * The controlled glyph centre keeps vertical dragging on its text line. Horizontal
     * dragging uses the glyph edge, so changing character widths cannot add a fixed bias.
     */
    fun handleGlyphCenter(
        page: PdfSelectablePage,
        range: PdfTextSelectionRange,
        handle: PdfSelectionHandle,
        widthPx: Float,
        heightPx: Float,
    ): androidx.compose.ui.geometry.Offset? {
        if (widthPx <= 0f || heightPx <= 0f) return null
        if (page.validRange(range.anchor, range.focus) == null) return null
        val hit = when (handle) {
            PdfSelectionHandle.ANCHOR -> range.anchor
            PdfSelectionHandle.FOCUS -> range.focus
        }
        val rect = page.lines[hit.line].glyphs[hit.glyph].rect
        return androidx.compose.ui.geometry.Offset(
            x = (rect.x + rect.width / 2f) * widthPx,
            y = (rect.y + rect.height / 2f) * heightPx,
        )
    }

    fun hitGlyphForHandleDrag(
        page: PdfSelectablePage,
        range: PdfTextSelectionRange,
        handle: PdfSelectionHandle,
        preferred: PdfSelectablePage.Hit? = null,
        downX: Float,
        downY: Float,
        currentX: Float,
        currentY: Float,
        widthPx: Float,
        heightPx: Float,
    ): PdfSelectablePage.Hit? {
        val glyphCenter = handleGlyphCenter(page, range, handle, widthPx, heightPx) ?: return null
        if (widthPx <= 0f || heightPx <= 0f) return null
        val endpoint = preferred ?: when (handle) {
            PdfSelectionHandle.ANCHOR -> range.anchor
            PdfSelectionHandle.FOCUS -> range.focus
        }
        val original = when (handle) {
            PdfSelectionHandle.ANCHOR -> range.anchor
            PdfSelectionHandle.FOCUS -> range.focus
        }
        val originalIsStart = (handle == PdfSelectionHandle.ANCHOR) == (range.anchor == range.ordered().first)
        val rect = page.lines[original.line].glyphs[original.glyph].rect
        val boundaryX = (rect.x + if (originalIsStart) 0f else rect.width) * widthPx
        return page.hit(
            x = (currentX + boundaryX - downX) / widthPx,
            y = (currentY + glyphCenter.y - downY) / heightPx,
            tolerance = -1f,
            prefer = endpoint,
            edge = if (originalIsStart) PdfSelectionEdge.START else PdfSelectionEdge.END,
            oppositeEndpoint = when (handle) {
                PdfSelectionHandle.ANCHOR -> range.focus
                PdfSelectionHandle.FOCUS -> range.anchor
            },
        )
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

    /** Guard against a zero/negative scale from an unsettled viewport session. */
    private const val MIN_PAGE_SCALE = 0.05f

    fun fixedScreenActionBarMetrics(
        barWidthPx: Float,
        barHeightPx: Float,
        gapPx: Float,
        marginPx: Float,
        pageScale: Float,
    ): PdfSelectionActionMetrics {
        val safeScale = if (pageScale.isFinite() && pageScale > MIN_PAGE_SCALE) {
            pageScale
        } else {
            MIN_PAGE_SCALE
        }
        val inverseScale = 1f / safeScale
        return PdfSelectionActionMetrics(
            inverseScale = inverseScale,
            barWidthPx = barWidthPx * inverseScale,
            barHeightPx = barHeightPx * inverseScale,
            gapPx = gapPx * inverseScale,
            marginPx = marginPx * inverseScale,
        )
    }

    private fun NormalizedRect.centerX(): Float = x + width / 2f
}
