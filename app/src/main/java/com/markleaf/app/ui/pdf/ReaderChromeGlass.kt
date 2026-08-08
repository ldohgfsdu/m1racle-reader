package com.markleaf.app.ui.pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Single glass recipe for EVERY floating Reader Chrome surface: back circle,
 * more circle, Parent (Main) menu, and every nested/foreground child card.
 *
 * Hierarchy is elevation + a small opacity delta only — never a second surface
 * color family or hard black outline. Light glass targets ~88–94% coverage so
 * PDF faces are not readable through menu text.
 */
@Immutable
data class ReaderGlassSurfaceSpec(
    val containerColor: Color,
    val containerAlpha: Float,
    /**
     * RGB base for the hairline (typically Black or White, alpha ignored).
     * Drawn stroke uses [borderAlpha] via [appliedGlassBorderColor].
     */
    val borderColor: Color,
    /** Drawn hairline alpha: light 0.03–0.07, dark 0.08–0.12. */
    val borderAlpha: Float,
    val borderWidth: Dp = 1.dp,
    val elevation: Dp = 6.dp,
    val spotShadowAlpha: Float = 0.14f,
    val ambientShadowAlpha: Float = 0.08f,
    val contentColor: Color,
    /** Muted icon/secondary text alpha for THIS theme (light ≈ 55–62%, dark ≈ 70–78%). */
    val contentMutedAlpha: Float,
    val tonalElevation: Dp = 0.dp,
)

/**
 * Shared chrome tokens — one source for border/alpha/elevation bands.
 * BackButton, ParentCard, and ChildCard must all derive border from here
 * (via [readerChromeGlassSpec]); never hardcode Color.Black strokes.
 */
object ReaderChromeTokens {
    /** Light glass base coverage (88–94% band). */
    const val LightBaseAlpha = 0.91f
    /** Child sits slightly denser than Parent (same hue). */
    const val ChildAlphaDelta = 0.04f
    /** Light hairline border — almost invisible (0.03–0.07). */
    const val LightBorderAlpha = 0.045f
    /** Dark hairline border (0.08–0.12). */
    const val DarkBorderAlpha = 0.10f
    const val ParentElevationDp = 10f
    const val ChildElevationDp = 14f
    const val ChromeElevationDp = 6f
    /** Soft shadows — never a hard black rim. */
    const val ParentSpotShadow = 0.12f
    const val ParentAmbientShadow = 0.08f
    const val ChildSpotShadow = 0.15f
    const val ChildAmbientShadow = 0.10f
    /** Secondary / muted content. */
    const val LightMutedAlpha = 0.58f
    const val DarkMutedAlpha = 0.75f
    /** Child header divider max strength. */
    const val DividerAlpha = 0.06f
    const val CornerParentChildDp = 28f
    const val CornerInputDp = 16f
    const val CornerButtonDp = 18f
}

/** Shared base alpha for all reader chrome glass (light 88–94% band). */
const val ReaderGlassBaseAlpha = ReaderChromeTokens.LightBaseAlpha

/**
 * Theme-aware muted content alpha (single token): light ≈ 55–62%,
 * dark ≈ 70–78%. Every chrome component must use this for secondary icons/text.
 */
@Composable
fun readerChromeMutedAlpha(): Float =
    if (isSystemInDarkTheme()) ReaderChromeTokens.DarkMutedAlpha else ReaderChromeTokens.LightMutedAlpha

/**
 * Pure border base + alpha for the shared chrome hairline.
 * Base is opaque Black/White; alpha is the drawn strength (never pre-baked into
 * the color and then overwritten with 1f).
 */
fun resolveGlassBorder(dark: Boolean): Pair<Color, Float> =
    if (dark) {
        Color.White to ReaderChromeTokens.DarkBorderAlpha
    } else {
        Color.Black to ReaderChromeTokens.LightBorderAlpha
    }

/**
 * Effective stroke color applied by [Modifier.readerGlassSurface].
 * This is the single source of truth for the drawn border alpha.
 */
fun appliedGlassBorderColor(spec: ReaderGlassSurfaceSpec): Color =
    spec.borderColor.copy(alpha = spec.borderAlpha)

/** Theme-aware shared hairline border color (pre-resolved for convenience). */
@Composable
fun readerChromeBorderColor(): Color {
    val (base, alpha) = resolveGlassBorder(isSystemInDarkTheme())
    return base.copy(alpha = alpha)
}

/**
 * Elevation levels for the same glass family — Main vs child differ only here.
 * Parent ~8–10dp; Child ~12–16dp soft elevation.
 */
enum class ReaderGlassElevation {
    /** Circles / compact chrome. */
    Chrome,
    /** Main more menu (ParentCard). */
    Main,
    /** Nested child card (slightly above Main). */
    Child,
}

/**
 * THE shared glass factory. All floating chrome surfaces must call this
 * (or [readerChromeGlassSpec] with defaults). Child may pass [Child] elevation
 * only — same tint, border, and content color family.
 */
@Composable
fun readerChromeGlassSpec(
    containerColor: Color = MaterialTheme.colorScheme.surface,
    containerAlpha: Float = ReaderGlassBaseAlpha,
    tonalElevation: Dp = 0.dp,
    elevation: Dp = 6.dp,
    spotShadowAlpha: Float = ReaderChromeTokens.ParentSpotShadow,
    ambientShadowAlpha: Float = ReaderChromeTokens.ParentAmbientShadow,
    level: ReaderGlassElevation? = null,
): ReaderGlassSurfaceSpec {
    val scheme = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val elev = when (level) {
        ReaderGlassElevation.Chrome -> ReaderChromeTokens.ChromeElevationDp.dp
        ReaderGlassElevation.Main -> ReaderChromeTokens.ParentElevationDp.dp
        ReaderGlassElevation.Child -> ReaderChromeTokens.ChildElevationDp.dp
        null -> elevation
    }
    // Child: same glass, slightly denser (still same hue family).
    val alpha = when (level) {
        ReaderGlassElevation.Child ->
            (containerAlpha + ReaderChromeTokens.ChildAlphaDelta).coerceIn(0.88f, 0.97f)
        else -> containerAlpha.coerceIn(0.88f, 0.94f)
    }
    val spot = if (level == ReaderGlassElevation.Child) {
        ReaderChromeTokens.ChildSpotShadow
    } else {
        spotShadowAlpha
    }
    val ambient = if (level == ReaderGlassElevation.Child) {
        ReaderChromeTokens.ChildAmbientShadow
    } else {
        ambientShadowAlpha
    }
    // Opaque base + separate hairline alpha — surface applies via
    // appliedGlassBorderColor (never Color.Black.copy(alpha=1f)).
    val (borderBase, borderA) = resolveGlassBorder(dark)
    return ReaderGlassSurfaceSpec(
        containerColor = containerColor,
        containerAlpha = alpha,
        borderColor = borderBase,
        borderAlpha = borderA,
        borderWidth = 1.dp,
        elevation = elev,
        spotShadowAlpha = spot,
        ambientShadowAlpha = ambient,
        contentColor = scheme.onSurface,
        contentMutedAlpha = readerChromeMutedAlpha(),
        tonalElevation = tonalElevation,
    )
}

/**
 * Applies the shared glass recipe: soft shadow + clip + dense glass surface + hairline.
 * A positive [ReaderGlassSurfaceSpec.tonalElevation] lifts the container color one tonal step
 * toward surfaceContainerHigh (Material tonal elevation).
 */
@Composable
fun Modifier.readerGlassSurface(spec: ReaderGlassSurfaceSpec, shape: Shape): Modifier {
    val scheme = MaterialTheme.colorScheme
    val container = if (spec.tonalElevation > 0.dp) {
        lerp(
            start = spec.containerColor,
            stop = scheme.surfaceContainerHigh,
            fraction = (spec.tonalElevation / 6.dp).coerceIn(0f, 1f),
        )
    } else {
        spec.containerColor
    }
    // MUST use appliedGlassBorderColor — borderColor is RGB base, borderAlpha is hairline.
    val stroke = appliedGlassBorderColor(spec)
    return this
        .shadow(
            elevation = spec.elevation,
            shape = shape,
            spotColor = Color.Black.copy(alpha = spec.spotShadowAlpha),
            ambientColor = Color.Black.copy(alpha = spec.ambientShadowAlpha),
        )
        .clip(shape)
        .background(container.copy(alpha = spec.containerAlpha), shape)
        .border(spec.borderWidth, stroke, shape)
}
