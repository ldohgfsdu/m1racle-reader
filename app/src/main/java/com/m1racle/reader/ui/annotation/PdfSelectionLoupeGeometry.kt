package com.m1racle.reader.ui.annotation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.m1racle.reader.core.model.NormalizedRect
import kotlin.math.roundToInt

/**
 * Pure geometry for the selection loupe: which part of the page is magnified and where the
 * selected characters land inside the loupe. Kept separate from the drawing so the crop
 * clamping and magnification can be verified without a device.
 */
internal object PdfSelectionLoupeGeometry {

    /** [zoom] is relative to the page as currently displayed. */
    const val Zoom = 2f

    /**
     * Page-local region the loupe shows, centred on [focus] and clamped to the page so the
     * loupe never shows blank space outside the paper.
     */
    fun sourceRect(
        focus: Offset,
        loupeSize: Size,
        pageWidthPx: Float,
        pageHeightPx: Float,
        zoom: Float = Zoom,
    ): Rect {
        if (pageWidthPx <= 0f || pageHeightPx <= 0f || loupeSize.width <= 0f || loupeSize.height <= 0f) {
            return Rect.Zero
        }
        val safeZoom = zoom.coerceAtLeast(0.05f)
        val width = (loupeSize.width / safeZoom).coerceIn(0f, pageWidthPx)
        val height = (loupeSize.height / safeZoom).coerceIn(0f, pageHeightPx)
        val left = (focus.x - width / 2f).coerceIn(0f, (pageWidthPx - width).coerceAtLeast(0f))
        val top = (focus.y - height / 2f).coerceIn(0f, (pageHeightPx - height).coerceAtLeast(0f))
        return Rect(left, top, left + width, top + height)
    }

    /** Where a page-local point appears inside the loupe. */
    fun toLoupe(point: Offset, source: Rect, zoom: Float = Zoom): Offset =
        Offset((point.x - source.left) * zoom, (point.y - source.top) * zoom)

    /** Where a page-local rectangle appears inside the loupe. */
    fun toLoupe(rect: NormalizedRect, source: Rect, pageWidthPx: Float, pageHeightPx: Float, zoom: Float = Zoom): Rect {
        val topLeft = toLoupe(
            Offset(rect.x * pageWidthPx, rect.y * pageHeightPx),
            source,
            zoom,
        )
        return Rect(
            left = topLeft.x,
            top = topLeft.y,
            right = topLeft.x + rect.width * pageWidthPx * zoom,
            bottom = topLeft.y + rect.height * pageHeightPx * zoom,
        )
    }

    /** Bitmap pixel region that must be drawn for [source], given the page bitmap's resolution. */
    fun bitmapSource(source: Rect, pageWidthPx: Float, pageHeightPx: Float, bitmapWidth: Int, bitmapHeight: Int): IntRect {
        val perPageX = bitmapWidth / pageWidthPx
        val perPageY = bitmapHeight / pageHeightPx
        val left = (source.left * perPageX).roundToInt().coerceIn(0, (bitmapWidth - 1).coerceAtLeast(0))
        val top = (source.top * perPageY).roundToInt().coerceIn(0, (bitmapHeight - 1).coerceAtLeast(0))
        val width = (source.width * perPageX).roundToInt().coerceAtLeast(1)
            .coerceAtMost((bitmapWidth - left).coerceAtLeast(1))
        val height = (source.height * perPageY).roundToInt().coerceAtLeast(1)
            .coerceAtMost((bitmapHeight - top).coerceAtLeast(1))
        return IntRect(left, top, width, height)
    }

    data class IntRect(val left: Int, val top: Int, val width: Int, val height: Int)
}
