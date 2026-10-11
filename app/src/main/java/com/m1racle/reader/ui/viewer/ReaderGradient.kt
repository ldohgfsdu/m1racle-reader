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

/**
 * Markdown top chrome. Blur/tint come from [GlassMaterialRole.ProgressiveChrome].
 * A page-colored veil separates controls without casting a gray/black shadow.
 * One smooth alpha mask blends the sampled blur back into the document.
 */
@Composable
internal fun Modifier.readerGradient(
    backdrop: LayerBackdrop,
    statusBarHeight: Dp = 0.dp,
    controlsVisibility: () -> Float = { 1f },
): Modifier {
    val environment = LocalGlassEnvironment.current
    val capability = LocalGlassCapability.current
    val spec = remember(environment, capability) {
        GlassMaterialEngine.resolve(GlassMaterialRole.ProgressiveChrome, environment, capability)
    }
    val pageColor = MaterialTheme.colorScheme.background
    val blurPx = with(LocalDensity.current) { spec.blur.toPx() }
    val statusHeightPx = with(LocalDensity.current) { statusBarHeight.toPx() }
    val minimumFadePx = with(LocalDensity.current) { 16.dp.toPx() }
    val fullFadePx = with(LocalDensity.current) { 48.dp.toPx() }
    val progressive = remember(capability) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && capability == GlassCapability.FULL_OPTICAL) ProgressiveChromeBlur() else null
    }
    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(pageColor.copy(alpha = spec.surfaceColor.alpha))
            val visibility = controlsVisibility().coerceIn(0f, 1f)
            val minimumExtent = (statusHeightPx + minimumFadePx).coerceAtMost(size.height)
            val extent = minimumExtent + (size.height - minimumExtent) * visibility
            val fadeHeight = minimumFadePx + (fullFadePx - minimumFadePx) * visibility
            val fadeStart = ((extent - fadeHeight) / extent).coerceIn(0f, 1f)
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
        .then(
            if (spec.capability == GlassCapability.SOLID_FALLBACK) Modifier
            else Modifier.drawPlainBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = {
                    if (spec.vibrancy) vibrancy()
                    if (android.os.Build.VERSION.SDK_INT >= 33 && progressive != null) progressive.apply(this, blurPx)
                    else blur(blurPx)
                },
            ),
        )
}
