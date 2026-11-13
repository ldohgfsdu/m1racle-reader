package com.m1racle.reader.ui.annotation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.m1racle.reader.core.model.NormalizedRect
import kotlin.math.roundToInt

internal val PdfSelectionLoupeWidth = 148.dp
internal val PdfSelectionLoupeHeight = 72.dp

/** Screen-space gap between the finger and the loupe's lower edge. */
internal val PdfSelectionLoupeGap = 44.dp

/**
 * Magnified copy of the page above the finger while a selection is being dragged.
 *
 * It draws the same page bitmap the reader shows, so the enlarged glyphs are the real
 * characters under the finger, plus the current character's boundaries and the selected
 * lines so the user can judge the range without looking under their hand.
 *
 * The caller sizes this box in page-local pixels (the desired screen size divided by the
 * page scale), so the loupe keeps a fixed on-screen size at any zoom. [inverseScale] is
 * that same 1/pageScale factor and keeps the border, shadow and strokes screen-sized too.
 */
@Composable
internal fun PdfSelectionLoupe(
    pageBitmap: ImageBitmap,
    colorFilter: ColorFilter?,
    pageWidthPx: Float,
    pageHeightPx: Float,
    focusPagePx: Offset,
    hitGlyphRect: NormalizedRect?,
    selectedRects: List<NormalizedRect>,
    paperColor: Color,
    darkPaper: Boolean,
    inverseScale: Float,
    modifier: Modifier = Modifier,
) {
    val corner = (10.dp * inverseScale).coerceAtLeast(1.dp)
    Box(
        modifier = modifier
            .shadow(6.dp * inverseScale, RoundedCornerShape(corner))
            .clip(RoundedCornerShape(corner))
            .background(paperColor),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (pageWidthPx <= 0f || pageHeightPx <= 0f ||
                pageBitmap.width <= 0 || pageBitmap.height <= 0
            ) {
                return@Canvas
            }
            val source = PdfSelectionLoupeGeometry.sourceRect(
                focus = focusPagePx,
                loupeSize = size,
                pageWidthPx = pageWidthPx,
                pageHeightPx = pageHeightPx,
            )
            if (source.width <= 0f || source.height <= 0f) return@Canvas
            val bitmapSource = PdfSelectionLoupeGeometry.bitmapSource(
                source = source,
                pageWidthPx = pageWidthPx,
                pageHeightPx = pageHeightPx,
                bitmapWidth = pageBitmap.width,
                bitmapHeight = pageBitmap.height,
            )
            drawImage(
                image = pageBitmap,
                srcOffset = IntOffset(bitmapSource.left, bitmapSource.top),
                srcSize = IntSize(bitmapSource.width, bitmapSource.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                colorFilter = colorFilter,
                filterQuality = FilterQuality.High,
            )

            val blend = if (darkPaper) BlendMode.Screen else BlendMode.Multiply
            selectedRects.forEach { rect ->
                val bounds = PdfSelectionLoupeGeometry.toLoupe(
                    rect, source, pageWidthPx, pageHeightPx,
                )
                drawRect(
                    color = PdfSelectionFill,
                    topLeft = Offset(bounds.left, bounds.top),
                    size = Size(bounds.width, bounds.height),
                    blendMode = blend,
                )
            }

            hitGlyphRect?.let { rect ->
                val bounds = PdfSelectionLoupeGeometry.toLoupe(
                    rect, source, pageWidthPx, pageHeightPx,
                )
                val stroke = (1.5.dp * inverseScale).toPx()
                drawLine(
                    color = PdfSelectionBoundary,
                    start = Offset(bounds.left, bounds.top),
                    end = Offset(bounds.left, bounds.bottom),
                    strokeWidth = stroke,
                )
                drawLine(
                    color = PdfSelectionBoundary,
                    start = Offset(bounds.right, bounds.top),
                    end = Offset(bounds.right, bounds.bottom),
                    strokeWidth = stroke,
                )
            }
        }
    }
}
