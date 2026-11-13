package com.m1racle.reader.ui.epub

import com.m1racle.reader.design.RadixColors
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

private fun epubPaperEdgePath(corners: EpubDeviceCorners, size: Size): Path {
    val half = 0.5f
    fun radius(value: Float) = CornerRadius((value - half).coerceAtLeast(0f))
    return Path().apply {
        addRoundRect(RoundRect(half, half, size.width - half, size.height - half,
            radius(corners.topLeft), radius(corners.topRight),
            radius(corners.bottomRight), radius(corners.bottomLeft)))
    }
}

/** A movement-only physical-pixel rim; resting paper blends into the full window. */
internal fun Modifier.epubPaperEdge(corners: EpubDeviceCorners, dark: Boolean, shadow: () -> Float): Modifier =
    drawWithCache {
        val path = epubPaperEdgePath(corners, size)
        onDrawWithContent {
            drawContent()
            val currentShadow = shadow()
            if (currentShadow > 0f) {
                drawPath(path, if (dark) RadixColors.White.copy(alpha = 0.16f * currentShadow)
                    else RadixColors.Black.copy(alpha = 0.08f * currentShadow), style = Stroke(0.5f))
            }
        }
    }
