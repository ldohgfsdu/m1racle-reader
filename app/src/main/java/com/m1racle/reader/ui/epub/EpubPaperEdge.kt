package com.m1racle.reader.ui.epub

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

/** A movement-only physical-pixel rim; resting paper blends into the full window. */
internal fun Modifier.epubPaperEdge(corners: EpubDeviceCorners, visible: Boolean, dark: Boolean, shadow: Float): Modifier =
    drawWithCache {
        val half = 0.5f
        fun radius(value: Float) = CornerRadius((value-half).coerceAtLeast(0f))
        val path = Path().apply {
            addRoundRect(RoundRect(half, half, size.width-half, size.height-half,
                radius(corners.topLeft), radius(corners.topRight),
                radius(corners.bottomRight), radius(corners.bottomLeft)))
        }
        onDrawWithContent {
            drawContent()
            if (visible) drawPath(path, if (dark) Color.White.copy(alpha = 0.16f * shadow)
                else Color.Black.copy(alpha = 0.08f * shadow), style = Stroke(1f))
        }
    }
