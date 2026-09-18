package com.m1racle.reader.ui.viewer

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
 * The DstIn falloff is a legacy mask (R36.x OPTICAL_REFINEMENT_PENDING), not a
 * finished progressive glass.
 */
@Composable
internal fun Modifier.readerGradient(backdrop: LayerBackdrop): Modifier {
    val environment = LocalGlassEnvironment.current
    val capability = LocalGlassCapability.current
    val spec = remember(environment, capability) {
        GlassMaterialEngine.resolve(GlassMaterialRole.ProgressiveChrome, environment, capability)
    }
    val blurPx = with(LocalDensity.current) { spec.blur.toPx() }
    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(spec.surfaceColor)
            drawRect(
                Brush.verticalGradient(
                    0f to Color.White,
                    0.55f to Color.White,
                    0.78f to Color.White.copy(alpha = 0.5f),
                    1f to Color.Transparent,
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
                    blur(blurPx)
                },
            ),
        )
}
