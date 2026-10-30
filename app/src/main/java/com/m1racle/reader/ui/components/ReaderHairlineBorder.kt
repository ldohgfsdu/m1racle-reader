package com.m1racle.reader.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import com.m1racle.reader.ui.theme.ReaderUiHairlinePx

/** Foundation border rounds fractional pixels up; this draws a real, inset half-pixel stroke. */
fun Modifier.readerHairlineBorder(color: Color, shape: Shape = RectangleShape): Modifier = drawWithCache {
    val width = ReaderUiHairlinePx
    val inset = width / 2f
    fun Rect.inset() = Rect(left + inset, top + inset, right - inset, bottom - inset)
    fun CornerRadius.inset() = CornerRadius((x - inset).coerceAtLeast(0f), (y - inset).coerceAtLeast(0f))
    val original = shape.createOutline(size, layoutDirection, this)
    val outline = when (original) {
        is Outline.Rectangle -> Outline.Rectangle(original.rect.inset())
        is Outline.Rounded -> original.roundRect.let { r ->
            Outline.Rounded(RoundRect(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset,
                r.topLeftCornerRadius.inset(), r.topRightCornerRadius.inset(),
                r.bottomRightCornerRadius.inset(), r.bottomLeftCornerRadius.inset()))
        }
        is Outline.Generic -> original
    }
    onDrawWithContent {
        drawContent()
        if (size.minDimension >= width) {
            if (outline is Outline.Generic) {
                clipPath(outline.path) { drawOutline(outline, color, style = Stroke(width * 2f)) }
            } else {
                drawOutline(outline, color, style = Stroke(width))
            }
        }
    }
}
