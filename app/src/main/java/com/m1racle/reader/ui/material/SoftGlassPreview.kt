package com.m1racle.reader.ui.material

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens

/**
 * Preview-only backdrop chain. Not used by Reader, Home, Dock, or Dialog.
 *
 * Kyant applies each effect to the previous output, so the call order is the
 * optical order: blur, then saturation/brightness/contrast, then soft-light,
 * then edge lens. Tint and the directional rim are painted on the surface
 * after that. The lens shader leaves pixels inside the edge band undisplaced.
 */
@Composable
fun Modifier.softGlassPreview(
    backdrop: Backdrop,
    shape: Shape,
    material: ResolvedSoftGlass,
): Modifier {
    val density = LocalDensity.current.density
    val blurPx = SoftGlassComparison.rendererBlurPx(material.logicalBlur, density)
    val rimPx = SoftGlassComparison.dpToPx(material.highlight.rimWidth, density)
    val lensOn = material.refractionAmountPx > 0f &&
        material.refractionHeightPx > 0f &&
        supportsGlassLens(shape) &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val rendered = remember(
        backdrop,
        shape,
        material,
        blurPx,
        rimPx,
        lensOn,
    ) {
        val scale = if (material.pressedScale == 1f) {
            Modifier
        } else {
            Modifier.graphicsLayer {
                scaleX = material.pressedScale
                scaleY = material.pressedScale
            }
        }
        scale.dropShadow(
            shape = shape,
            shadow = Shadow(
                radius = material.shadowRadius,
                color = Color.Black.copy(alpha = material.shadowAlpha),
                offset = DpOffset(0.dp, 1.dp),
            ),
        ).drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                blur(blurPx)
                colorControls(
                    brightness = material.rendererBrightness,
                    contrast = material.rendererContrast,
                    saturation = material.rendererSaturation,
                )
                if (material.rendererSoftLightBrightness != 0f) {
                    colorControls(brightness = material.rendererSoftLightBrightness)
                }
                if (lensOn) {
                    lens(
                        refractionHeight = material.refractionHeightPx,
                        refractionAmount = material.refractionAmountPx,
                        depthEffect = false,
                        chromaticAberration = false,
                    )
                }
            },
            highlight = null,
            shadow = null,
            innerShadow = null,
            layerBlock = if (material.pressedScale == 1f) {
                null
            } else {
                {
                    scaleX *= material.pressedScale
                    scaleY *= material.pressedScale
                }
            },
            onDrawSurface = {
                drawRect(material.tint)
                drawRect(
                    brush = Brush.linearGradient(
                        colorStops = arrayOf(
                            0f to Color.White.copy(alpha = material.highlight.inner),
                            0.28f to Color.White.copy(alpha = material.highlight.inner * 0.25f),
                            0.62f to Color.Transparent,
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                )
                val outline = shape.createOutline(size, layoutDirection, this)
                drawOutline(
                    outline = outline,
                    brush = Brush.linearGradient(
                        colorStops = arrayOf(
                            0f to Color.White.copy(alpha = material.highlight.topLeft),
                            0.45f to Color.White.copy(alpha = material.highlight.mid),
                            1f to Color.White.copy(alpha = material.highlight.bottomRight),
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                    style = Stroke(width = rimPx),
                )
            },
        )
    }
    return this.then(rendered)
}
