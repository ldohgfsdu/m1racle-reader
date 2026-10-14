package com.m1racle.reader.ui.viewer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.m1racle.reader.ui.material.GlassCapability
import com.m1racle.reader.ui.material.GlassMaterialEngine
import com.m1racle.reader.ui.material.GlassMaterialRole
import com.m1racle.reader.ui.material.LocalGlassCapability
import com.m1racle.reader.ui.material.LocalGlassEnvironment

internal val ReaderChromeFadeHeight = 32.dp

/**
 * Markdown top chrome. Blur/tint come from [GlassMaterialRole.ProgressiveChrome].
 * A light page-colored veil preserves the colors of the scrolling document.
 * Its fixed depth profile is independent of the controls' visibility.
 */
@Composable
internal fun Modifier.readerGradient(
    backdrop: LayerBackdrop,
    statusBarHeight: Dp = 0.dp,
): Modifier {
    val environment = LocalGlassEnvironment.current
    val capability = LocalGlassCapability.current
    val spec = remember(environment, capability) {
        GlassMaterialEngine.resolve(GlassMaterialRole.ProgressiveChrome, environment, capability)
    }
    val pageColor = MaterialTheme.colorScheme.background
    val blurPx = with(LocalDensity.current) { spec.blur.toPx() }
    val statusHeightPx = with(LocalDensity.current) { statusBarHeight.toPx() }
    val fullFadePx = with(LocalDensity.current) { ReaderChromeFadeHeight.toPx() }
    val progressive = remember(capability) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && capability == GlassCapability.FULL_OPTICAL) ProgressiveChromeBlur() else null
    }

    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val extent = size.height
            // Keep the controls' background quiet, then release the content color
            // with the same smooth depth curve as the blur.
            if (spec.capability == GlassCapability.SOLID_FALLBACK) {
                drawRect(pageColor.copy(alpha = spec.surfaceColor.alpha))
            } else {
                val tintStart = statusHeightPx.coerceIn(0f, extent)
                val stops = Array(17) { index ->
                    val depth = index / 16f
                    val y = tintStart + (extent - tintStart) * depth
                    val strength = 1f - depth * depth * (3f - 2f * depth)
                    y / extent.coerceAtLeast(1f) to pageColor.copy(alpha = spec.surfaceColor.alpha * strength)
                }
                drawRect(Brush.verticalGradient(
                    0f to pageColor.copy(alpha = spec.surfaceColor.alpha),
                    *stops,
                    endY = extent,
                ))
            }
            // The optical shader already reaches the original, unblurred image
            // at the lower edge. Fading its opacity would expose sharp glyphs
            // beneath the blur and give each line a second, glowing silhouette.
            if (progressive == null) {
                val fadeStart = ((extent - fullFadePx) / extent.coerceAtLeast(1f)).coerceIn(0f, 1f)
                val fadeSpan = 1f - fadeStart
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White,
                        fadeStart to Color.White,
                        (fadeStart + fadeSpan * 0.35f) to Color.White.copy(alpha = 0.72f),
                        (fadeStart + fadeSpan * 0.70f) to Color.White.copy(alpha = 0.22f),
                        1f to Color.Transparent,
                        endY = extent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
        .then(
            if (spec.capability == GlassCapability.SOLID_FALLBACK) Modifier
            else Modifier.drawPlainBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = {
                    if (spec.vibrancy) vibrancy()
                    if (android.os.Build.VERSION.SDK_INT >= 33 && progressive != null) {
                        progressive.apply(this, blurPx, statusHeightPx, size.height, pageColor)
                    }
                    else blur(blurPx)
                },
            ),
        )
}
