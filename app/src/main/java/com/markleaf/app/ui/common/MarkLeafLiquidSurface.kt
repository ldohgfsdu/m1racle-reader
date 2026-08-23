package com.markleaf.app.ui.common

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

/**
 * Liquid Glass hierarchy — one token family, named surface levels.
 *
 * Home floating controls ascend: [QuietControl] (card ···) → [PrimaryControl]
 * (FAB) → [Popover] (overflow menu). Reader chrome keeps [Control] / [Parent]
 * / [Foreground] — their tokens are frozen (r26.1), do not retune.
 */
enum class LiquidSurfaceLevel {
    /** Home card overflow ··· — quietest control; default ≈ card background. */
    QuietControl,
    /** Home FAB — primary floating action, one density above quiet. */
    PrimaryControl,
    /** Home overflow popover — one surface level above [QuietControl]. */
    Popover,
    /** Reader back / more / FAB circles. */
    Control,
    /** Reader parent menus / compact cards. */
    Parent,
    /** Reader foreground nested child / advanced panels. */
    Foreground,
}

/**
 * Root + parent-export backdrop host for glass-on-glass
 * (official Backdrop exportedBackdrop pattern).
 */
@Immutable
data class LiquidBackdropHost(
    val root: LayerBackdrop,
    val parentExport: LayerBackdrop,
)

val LocalLiquidBackdropHost =
    staticCompositionLocalOf<LiquidBackdropHost?> { null }

/**
 * Shared liquid tokens.
 *
 * Reader cards follow Kyant catalog **Dialog** (light Regular frost):
 * brightness lift + blur + mild lens + Highlight.Plain + FAFAFA@~0.60.
 * That is the milky popover in the WeiXin reference — not a thick plate
 * and not a hollow specular rim.
 */
object LiquidGlassTokens {
    // ── Reader levels (Dialog-style Regular frost) ───────────────────────
    const val ParentSurfaceAlpha = 0.60f
    const val ForegroundSurfaceAlpha = 0.66f
    const val ControlSurfaceAlpha = 0.50f
    /** Night Regular frost: charcoal plate, page glyphs still ghost through. */
    const val ParentDarkSurfaceAlpha = 0.58f
    const val ForegroundDarkSurfaceAlpha = 0.64f
    const val ControlDarkSurfaceAlpha = 0.48f

    val ParentBlur = 16.dp
    val ForegroundBlur = 16.dp
    val ControlBlur = 12.dp

    val ParentLensHeight = 16.dp
    val ParentLensAmount = 32.dp
    val ForegroundLensHeight = 16.dp
    val ForegroundLensAmount = 32.dp
    val ControlLensHeight = 10.dp
    val ControlLensAmount = 20.dp

    val ParentShadow = 16.dp
    val ForegroundShadow = 18.dp
    val ControlShadow = 8.dp

    /** Dialog light surface tint (not pure white). */
    val LightSurfaceTint = Color(0xFFFAFAFA)
    /** Night Regular frost — graphite, not pitch black. */
    val DarkSurfaceTint = Color(0xFF1C1C1C)
    const val LightBrightness = 0.20f
    /** Slightly darken the sampled page so the card reads as frost, not gray film. */
    const val DarkBrightness = -0.08f
    const val RecipeSaturation = 1.25f
    const val DarkSaturation = 1.10f
    const val DarkHighlightAlpha = 0.20f
    const val DarkShadowAlpha = 0.42f
    const val LightShadowAlpha = 0.10f

    // ── Home floating control levels (quiet → primary → popover) ─────────
    const val QuietControlSurfaceAlpha = 0.36f
    const val PrimaryControlSurfaceAlpha = 0.44f
    const val PopoverSurfaceAlpha = 0.56f

    val QuietControlBlur = 10.dp
    val QuietControlLensHeight = 8.dp
    val QuietControlLensAmount = 16.dp
    val QuietControlShadow = 3.dp

    val PrimaryControlBlur = 14.dp
    val PrimaryControlLensHeight = 12.dp
    val PrimaryControlLensAmount = 24.dp
    val PrimaryControlShadow = 6.dp

    val PopoverBlur = 18.dp
    val PopoverLensHeight = 16.dp
    val PopoverLensAmount = 32.dp
    val PopoverShadow = 10.dp

    /** Pressed/selected boost — coverage + edge highlight/refraction. */
    const val PressedSurfaceAlphaBoost = 0.06f
    const val PressedHighlightAlphaBoost = 0.18f

}

/** Resolved per-level liquid parameters (single source of truth). */
@Immutable
internal data class LiquidLevelTokens(
    val surfaceAlpha: Float,
    val blur: Dp,
    val lensHeight: Dp,
    val lensAmount: Dp,
    val shadow: Dp,
    val highlightWidth: Dp,
    val highlightAlpha: Float,
    val depthEffect: Boolean,
)

internal fun resolvedSurfaceAlpha(
    level: LiquidSurfaceLevel,
    pressed: Boolean,
    dark: Boolean = false,
): Float {
    val base = if (dark) {
        when (level) {
            LiquidSurfaceLevel.Parent -> LiquidGlassTokens.ParentDarkSurfaceAlpha
            LiquidSurfaceLevel.Foreground -> LiquidGlassTokens.ForegroundDarkSurfaceAlpha
            LiquidSurfaceLevel.Control -> LiquidGlassTokens.ControlDarkSurfaceAlpha
            LiquidSurfaceLevel.Popover -> 0.56f
            else -> liquidLevelTokens(level).surfaceAlpha
        }
    } else {
        liquidLevelTokens(level).surfaceAlpha
    }
    val boost = if (pressed) LiquidGlassTokens.PressedSurfaceAlphaBoost else 0f
    return (base + boost).coerceIn(0f, 0.92f)
}

internal fun liquidLevelTokens(level: LiquidSurfaceLevel): LiquidLevelTokens = when (level) {
    LiquidSurfaceLevel.QuietControl -> LiquidLevelTokens(
        surfaceAlpha = LiquidGlassTokens.QuietControlSurfaceAlpha,
        blur = LiquidGlassTokens.QuietControlBlur,
        lensHeight = LiquidGlassTokens.QuietControlLensHeight,
        lensAmount = LiquidGlassTokens.QuietControlLensAmount,
        shadow = LiquidGlassTokens.QuietControlShadow,
        highlightWidth = 0.25.dp,
        highlightAlpha = 0.22f,
        depthEffect = false,
    )
    LiquidSurfaceLevel.PrimaryControl -> LiquidLevelTokens(
        surfaceAlpha = LiquidGlassTokens.PrimaryControlSurfaceAlpha,
        blur = LiquidGlassTokens.PrimaryControlBlur,
        lensHeight = LiquidGlassTokens.PrimaryControlLensHeight,
        lensAmount = LiquidGlassTokens.PrimaryControlLensAmount,
        shadow = LiquidGlassTokens.PrimaryControlShadow,
        highlightWidth = 0.5.dp,
        highlightAlpha = 0.45f,
        depthEffect = true,
    )
    LiquidSurfaceLevel.Popover -> LiquidLevelTokens(
        surfaceAlpha = LiquidGlassTokens.PopoverSurfaceAlpha,
        blur = LiquidGlassTokens.PopoverBlur,
        lensHeight = LiquidGlassTokens.PopoverLensHeight,
        lensAmount = LiquidGlassTokens.PopoverLensAmount,
        shadow = LiquidGlassTokens.PopoverShadow,
        highlightWidth = 0.5.dp,
        highlightAlpha = 0.50f,
        depthEffect = true,
    )
    LiquidSurfaceLevel.Control -> LiquidLevelTokens(
        surfaceAlpha = LiquidGlassTokens.ControlSurfaceAlpha,
        blur = LiquidGlassTokens.ControlBlur,
        lensHeight = LiquidGlassTokens.ControlLensHeight,
        lensAmount = LiquidGlassTokens.ControlLensAmount,
        shadow = LiquidGlassTokens.ControlShadow,
        highlightWidth = 0.5.dp,
        highlightAlpha = 0.30f,
        depthEffect = false,
    )
    LiquidSurfaceLevel.Parent -> LiquidLevelTokens(
        surfaceAlpha = LiquidGlassTokens.ParentSurfaceAlpha,
        blur = LiquidGlassTokens.ParentBlur,
        lensHeight = LiquidGlassTokens.ParentLensHeight,
        lensAmount = LiquidGlassTokens.ParentLensAmount,
        shadow = LiquidGlassTokens.ParentShadow,
        highlightWidth = 0.5.dp,
        highlightAlpha = 0.30f,
        depthEffect = false,
    )
    LiquidSurfaceLevel.Foreground -> LiquidLevelTokens(
        surfaceAlpha = LiquidGlassTokens.ForegroundSurfaceAlpha,
        blur = LiquidGlassTokens.ForegroundBlur,
        lensHeight = LiquidGlassTokens.ForegroundLensHeight,
        lensAmount = LiquidGlassTokens.ForegroundLensAmount,
        shadow = LiquidGlassTokens.ForegroundShadow,
        highlightWidth = 0.5.dp,
        highlightAlpha = 0.30f,
        depthEffect = false,
    )
}

/**
 * THE single MarkLeaf liquid surface entry for floating chrome.
 * Content layers (PDF, list rows, thumbnails) must NOT use this.
 *
 * @param sampleBackdrop When false, paint a soft translucent plate only — never
 *   call [drawBackdrop]. Required for any chrome that is **inside** the same
 *   composition subtree as [com.kyant.backdrop.backdrops.layerBackdrop] (e.g. home
 *   list-row ··· buttons). Sampling a LayerBackdrop from within that layer is a
 *   circular record/draw and crashes (RenderThread / backdrop circular render).
 *   Real glass sampling is only safe for siblings/overlays outside the recorded layer
 *   (PDF chrome, home FAB).
 * @param pressed When true (pressed/selected), enhance surface coverage and
 *   edge highlight/refraction — quiet controls stay quiet until touched.
 */
@Composable
fun Modifier.markLeafLiquidSurface(
    shape: Shape,
    level: LiquidSurfaceLevel = LiquidSurfaceLevel.Parent,
    /** When true (Parent menu only), export glass for nested Child sampling. */
    exportForChild: Boolean = false,
    sampleBackdrop: Boolean = true,
    pressed: Boolean = false,
): Modifier {
    val host = LocalLiquidBackdropHost.current
    if (!sampleBackdrop || host == null) {
        // Soft translucent plate, same alpha family — no LayerBackdrop sampling.
        return this.then(
            softFallbackSurface(shape, level, pressed),
        )
    }
    return liquidDraw(
        modifier = this,
        host = host,
        shape = shape,
        level = level,
        exportForChild = exportForChild,
        pressed = pressed,
    )
}

@Composable
private fun softFallbackSurface(
    shape: Shape,
    level: LiquidSurfaceLevel,
    pressed: Boolean,
): Modifier {
    // Fallback without backdrop sampling — used only if host missing.
    val dark = isSystemInDarkTheme()
    val tokens = liquidLevelTokens(level)
    val alpha = resolvedSurfaceAlpha(level, pressed, dark)
    val color = if (dark) {
        LiquidGlassTokens.DarkSurfaceTint.copy(alpha = alpha)
    } else {
        LiquidGlassTokens.LightSurfaceTint.copy(alpha = alpha)
    }
    return Modifier
        .shadow(elevation = tokens.shadow, shape = shape)
        .clip(shape)
        .background(color, shape)
}

@Composable
private fun liquidDraw(
    modifier: Modifier,
    host: LiquidBackdropHost,
    shape: Shape,
    level: LiquidSurfaceLevel,
    exportForChild: Boolean,
    pressed: Boolean,
): Modifier {
    val density = LocalDensity.current
    val dark = isSystemInDarkTheme()
    val tokens = liquidLevelTokens(level)
    // Child samples PDF + parent glass. parentExport alone is empty past the
    // parent bounds, so the nested card would read as fully see-through.
    val backdrop: Backdrop = if (level == LiquidSurfaceLevel.Foreground) {
        rememberCombinedBackdrop(host.root, host.parentExport)
    } else {
        host.root
    }
    val exported: LayerBackdrop? =
        if (exportForChild && level == LiquidSurfaceLevel.Parent) host.parentExport else null

    val blurPx = with(density) { tokens.blur.toPx() }
    val lensH = with(density) { tokens.lensHeight.toPx() }
    val lensA = with(density) { tokens.lensAmount.toPx() }
    val surfaceAlpha = resolvedSurfaceAlpha(level, pressed, dark)
    val surfaceColor = if (dark) {
        LiquidGlassTokens.DarkSurfaceTint.copy(alpha = surfaceAlpha)
    } else {
        LiquidGlassTokens.LightSurfaceTint.copy(alpha = surfaceAlpha)
    }
    val softShadow = Shadow(
        radius = tokens.shadow,
        color = Color.Black.copy(
            alpha = if (dark) {
                LiquidGlassTokens.DarkShadowAlpha
            } else {
                LiquidGlassTokens.LightShadowAlpha
            },
        ),
    )
    val highlightAlpha = (
        if (dark) LiquidGlassTokens.DarkHighlightAlpha else tokens.highlightAlpha
        ) + if (pressed) LiquidGlassTokens.PressedHighlightAlphaBoost else 0f
    val edgeHighlight = Highlight.Plain.copy(
        width = tokens.highlightWidth,
        alpha = highlightAlpha.coerceIn(0f, 0.7f),
    )
    val allowLens = shapeSupportsLiquidLens(shape)

    return modifier.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(
                brightness = if (dark) {
                    LiquidGlassTokens.DarkBrightness
                } else {
                    LiquidGlassTokens.LightBrightness
                },
                saturation = if (dark) {
                    LiquidGlassTokens.DarkSaturation
                } else {
                    LiquidGlassTokens.RecipeSaturation
                },
            )
            blur(blurPx)
            if (allowLens && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lens(
                    refractionHeight = lensH,
                    refractionAmount = lensA,
                    depthEffect = tokens.depthEffect,
                )
            }
        },
        highlight = { edgeHighlight },
        shadow = { softShadow },
        exportedBackdrop = exported,
        onDrawSurface = { drawRect(surfaceColor) },
    )
}

/** Safe for Backdrop lens() — CornerBasedShape only (not CapsulePopoverShape). */
fun shapeSupportsLiquidLens(shape: Shape): Boolean {
    if (shape is CornerBasedShape) return true
    return shape.javaClass.name == "com.kyant.shapes.RoundedRectangularShape"
}
