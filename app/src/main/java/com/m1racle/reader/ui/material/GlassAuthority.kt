package com.m1racle.reader.ui.material

import android.os.Build
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Where glass is shown. Only the resolver may map this onto tint/shadow/lens.
 * APP_* = hub / settings. READER_* = document chrome. Same dark bit currently
 * yields the same numbers (R36.1 visual equivalence).
 */
enum class GlassEnvironment {
    APP_LIGHT,
    APP_DARK,
    READER_LIGHT,
    READER_DARK,
    ;

    val isDark: Boolean
        get() = this == APP_DARK || this == READER_DARK

    companion object {
        fun app(dark: Boolean): GlassEnvironment = if (dark) APP_DARK else APP_LIGHT

        fun reader(dark: Boolean): GlassEnvironment = if (dark) READER_DARK else READER_LIGHT
    }
}

/**
 * Device/window sampling ability. R36.1 does not change FULL_OPTICAL looks.
 * SOLID_FALLBACK is the cross-window / no-host path already used by Dialog.
 */
enum class GlassCapability {
    FULL_OPTICAL,
    BLUR_ONLY,
    SOLID_FALLBACK,
}

/** Shared physical direction, resolved only by GlassMaterialEngine. */
enum class GlassOpticalFamily {
    INTERACTIVE,
    CONTENT,
    PANEL,
    FALLBACK,
}

/** Settings comparison presets. Values live in [GlassMaterialEngine], not in the preview UI. */
enum class GlassPreviewPreset {
    CurrentThick,
    ProgressiveLegacy,
    CapsuleReference,
    Tint86, Tint80, Tint70, Tint60, Tint50, Tint45,
    Blur32, Blur40, Tint35,

}

/**
 * Content glyphs are not a function of [GlassMaterialSpec.surfaceColor] alpha.
 * Whole-control hide/show uses a separate visibility alpha at the animation layer.
 */
data class GlassForegroundPolicy(
    val contentAlpha: Float = 1f,
    val mutedAlpha: Float,
)

object GlassCapabilityProbe {
    /** Window/shape constraints must be applied before choosing a material. */
    fun forSurface(
        device: GlassCapability,
        sampleBackdrop: Boolean,
        hasBackdrop: Boolean,
        supportsLens: Boolean,
    ): GlassCapability = when {
        !sampleBackdrop || !hasBackdrop || device == GlassCapability.SOLID_FALLBACK ->
            GlassCapability.SOLID_FALLBACK
        !supportsLens || device == GlassCapability.BLUR_ONLY -> GlassCapability.BLUR_ONLY
        else -> GlassCapability.FULL_OPTICAL
    }

    fun detect(): GlassCapability = when {
        Build.VERSION.SDK_INT < 31 -> GlassCapability.SOLID_FALLBACK
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> GlassCapability.BLUR_ONLY
        else -> GlassCapability.FULL_OPTICAL
    }
}

val LocalGlassEnvironment = staticCompositionLocalOf { GlassEnvironment.APP_LIGHT }

val LocalGlassCapability = staticCompositionLocalOf { GlassCapability.FULL_OPTICAL }
