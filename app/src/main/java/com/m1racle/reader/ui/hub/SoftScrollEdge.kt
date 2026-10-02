package com.m1racle.reader.ui.hub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.effects.blur
import com.m1racle.reader.ui.material.LocalGlassBackdropHost
import com.m1racle.reader.ui.material.GlassBackdropSource
import kotlin.math.ceil
import kotlin.math.sqrt

/** Spatially blend real backdrop blurs. No scrim, tint, lens, border or touch handler. */
@Composable
internal fun SoftScrollEdge(export: LayerBackdrop, modifier: Modifier = Modifier) {
    val backdrop = LocalGlassBackdropHost.current?.root ?: return
    val density = LocalDensity.current
    val navigationInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    GlassBackdropSource(export, modifier.fillMaxWidth().height(144.dp + navigationInset)
        .testTag("dock-soft-scroll-edge")) {
        // Three overlapping blur bands approximate a variable blur on Android 12+.
        // Each band samples the original page, never another band or the dock.
        for (band in 0..2) {
            val radius = with(density) { listOf(2.dp, 8.dp, 20.dp)[band].toPx() }
            val surface = remember(backdrop, radius, band) {
                Modifier
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithCache {
                        val mask = Brush.verticalGradient(
                            *Array(17) { step ->
                                val progress = step / 16f
                                progress to Color.White.copy(alpha = scrollEdgeOpacity(band, progress))
                            },
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(mask, blendMode = BlendMode.DstIn)
                        }
                    }
                    .drawPlainBackdrop(
                        backdrop = backdrop,
                        shape = { RectangleShape },
                        effects = {
                            val radii = scrollEdgeBlurRadii(radius)
                            padding = maxOf(padding, ceil(radii.sum()))
                            radii.forEach { blur(it) }
                        },
                    )
            }
            Box(Modifier.fillMaxSize().then(surface))
        }
    }
}

/** Smooth transparent-to-blurred transition, increasingly close to the screen edge. */
internal fun scrollEdgeOpacity(band: Int, progress: Float): Float {
    val start = band * 0.20f
    val end = 0.50f + band * 0.25f
    val t = ((progress - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
internal fun scrollEdgeBlurRadii(radius: Float): List<Float> {
    if (radius <= 0f) return emptyList()
    val targetSigma = radius * 0.57735f + 0.5f
    if (targetSigma <= 4f) return listOf(radius)
    var remainingVariance = targetSigma.toDouble() * targetSigma
    var sigma = 4.0
    return buildList {
        // Leave enough variance for a final radius > 0 (Android sigma > 0.5).
        while (remainingVariance > sigma * sigma + 0.25) {
            add(((sigma - 0.5) / 0.57735).toFloat())
            remainingVariance -= sigma * sigma
            sigma *= 4.0
        }
        add(((sqrt(remainingVariance) - 0.5) / 0.57735).toFloat())
    }
}
