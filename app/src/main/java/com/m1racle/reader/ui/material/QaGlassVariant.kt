package com.m1racle.reader.ui.material

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.m1racle.reader.BuildConfig

/**
 * QA-only glass experiment (design 2026-10-10, QA_GLASS_VARIANTS.md).
 *
 * Applies to the PDF floating controls (Control / Capsule roles) and the home
 * Dock. [BASELINE] is the untouched production recipe. The selection is only
 * loaded or changed when [BuildConfig.QA_GLASS_VARIANTS_ENABLED] (qa build type);
 * every other build keeps the defaults, so release renders exactly as before.
 */
enum class QaGlassVariant(val label: String) {
    /** 档 0: current recipe, no change. */
    BASELINE("0 现状"),
    /** 档 1a: material unchanged; PDF chrome light/dark follows the PDF page, not the system theme. */
    FOLLOW_PAGE("1a 跟页面"),
    /** 档 1b: night glass #242424 @ 0.75, day unchanged. */
    SOLID_NIGHT("1b 夜间实"),
    /** 档 2: Coolapk-like frosted plate. */
    COOLAPK("2 酷安"),
    /**
     * 档 3 (2026-10-10): every glass surface uses the capsule plate (day white 0.32,
     * night #242424 0.22) plus brightness compression, so text can stay pure black / white.
     */
    UNIFIED("3 全部统一"),
}

/** 档 3 blur. 24dp was dropped (emulator RenderThread hangs; 嘉轩 2026-10-10). */
enum class QaUnifiedBlur(val label: String, val radius: Dp) {
    B4("模糊 4dp", 4.dp),
    B12("模糊 12dp", 12.dp),
}

/** 档 3 effect order. Spec §3.1 is vibrancy first, compression last (default). */
enum class QaCompressOrder(val label: String) {
    VIBRANCY_FIRST("先饱和后压缩"),
    COMPRESS_FIRST("先压缩后饱和"),
}

/** Night selected lens in the home Dock. */
enum class QaDockNightLens(val label: String) {
    /** Current: white 10 %. */
    WHITE_10("A 白10%"),
    /** Black 12 % + 1dp white stroke @ 0.35; selected label white bold. */
    BLACK_12_STROKE("B 黑12%+描边"),
}

@Immutable
data class QaGlassSelection(
    val variant: QaGlassVariant = QaGlassVariant.BASELINE,
    val dockNightLens: QaDockNightLens = QaDockNightLens.WHITE_10,
    /** "PDF 夜间反色". true = production (invert PDF pages in dark theme). */
    val pdfNightInvert: Boolean = true,
    /** 档 3 only. */
    val unifiedBlur: QaUnifiedBlur = QaUnifiedBlur.B4,
    /** 档 3 only. */
    val compressOrder: QaCompressOrder = QaCompressOrder.VIBRANCY_FIRST,
)

/** Whether PDF pages are inverted. Outside QA the selection is always default -> theme only. */
fun qaPdfInvertPages(themeDark: Boolean, selection: QaGlassSelection): Boolean =
    themeDark && selection.pdfNightInvert

/**
 * Per-role material override. Null fields keep the production value.
 * Alphas are surface (tint) opacity; highlight is the white rim.
 */
@Immutable
data class QaGlassOverride(
    val lightTint: Color? = null,
    val lightAlpha: Float? = null,
    val darkTint: Color? = null,
    val darkAlpha: Float? = null,
    val blur: Dp? = null,
    val highlightWidth: Dp? = null,
    val lightHighlightAlpha: Float? = null,
    val darkHighlightAlpha: Float? = null,
    val shadow: Dp? = null,
    val lightShadowAlpha: Float? = null,
    val darkShadowAlpha: Float? = null,
    /**
     * Drop the production pressed tint (#AAAAAA night) and fixed pressed alpha 0.40,
     * which would make a denser plate lighter / grey on press. Press then uses the
     * engine boost ([pressedSurfaceAlphaBoost], production +0.04).
     */
    val clearPressedTint: Boolean = false,
    /** Whole-surface press: surface alpha boost (production 0.04). */
    val pressedSurfaceAlphaBoost: Float? = null,
    /** Whole-surface press: rim alpha boost (production 0.10). */
    val pressedHighlightAlphaBoost: Float? = null,
    /** Whole-surface press scale (replaces the role's pressedScale). */
    val pressedScale: Float? = null,
    /** Backdrop brightness compression (colorControls): out = (in - 0.5) * contrast + 0.5 + brightness. */
    val lightBrightness: Float? = null,
    val darkBrightness: Float? = null,
    val lightContrast: Float? = null,
    val darkContrast: Float? = null,
    /** Secondary text alpha; 1 = same as primary text. */
    val mutedAlpha: Float? = null,
    /** true: vibrancy runs before colorControls, so compression is the last color step. */
    val compressAfterVibrancy: Boolean? = null,
)

/**
 * 档 3 numbers (design + 工程师 review, 2026-10-10). Compression maps the blurred
 * backdrop into day 0.55..1.0 / night 0..0.35 before the plate is drawn; worst case
 * on pure black / white is about 9.8 (black text, day) and 8.4 (white text, night).
 */
object QaUnified {
    val LightTint = Color.White
    const val LightAlpha = 0.32f
    val DarkTint = Color(0xFF242424)
    const val DarkAlpha = 0.22f
    const val LightContrast = 0.45f
    const val LightBrightness = 0.275f
    const val DarkContrast = 0.35f
    const val DarkBrightness = -0.325f
    /** Production press boost; no grey #AAAAAA pressed tint. */
    const val PressSurfaceAlphaBoost = 0.04f
    val Roles: Set<GlassMaterialRole> = setOf(
        GlassMaterialRole.Control,
        GlassMaterialRole.Capsule,
        GlassMaterialRole.Dock,
        GlassMaterialRole.Menu,
        GlassMaterialRole.SettingsCard,
    )
    // ExpandedMenu and Dialog render in their own window (sampleBackdrop = false):
    // no backdrop to blur or compress, so a 0.32 plate would be unreadable. Left production.
}

val QaGlassSelection.unified: Boolean get() = variant == QaGlassVariant.UNIFIED

/**
 * Variant 2 (Coolapk) geometry and press motion, QA_GLASS_VARIANTS.md "档 2 补充".
 * Only read when [QaGlassVariant.COOLAPK] is selected; variant 0 keeps production sizes.
 * Press numbers were estimated from recordings; Mobile tunes them in the QA build.
 */
object QaCoolapk {
    // Material only (2026-10-10): every size, margin and label stays production.

    // Press: whole surface brightens and grows, not just the touched cell.
    const val PressSurfaceAlphaBoost = 0.08f
    const val PressRimAlphaBoost = 0.15f
    const val PressScale = 1.03f
    /** The held Dock cell's icon/label grows further. */
    const val PressedCellScale = 1.10f
    const val ImportPressScale = 1.05f
    const val ImportPressOverlayAlpha = 0.08f
    /** Press in: 120ms decelerate. Release: spring damping 0.7, stiffness 500 (~250ms). */
    const val PressInMillis = 120
    const val ReleaseDampingRatio = 0.7f
    const val ReleaseStiffness = 500f
}

val QaGlassSelection.coolapk: Boolean get() = variant == QaGlassVariant.COOLAPK

/**
 * Variant 2 import "+": dense accent-tinted glass (Coolapk density, our blue).
 * Day #0055B3 with a white glyph, night #6AAEFF with a dark glyph, both at 0.80.
 * Rim matches the variant 2 capsule.
 */
object QaCoolapkImport {
    val LightTint = Color(0xFF0055B3)
    val DarkTint = Color(0xFF6AAEFF)
    const val TintAlpha = 0.80f
    val LightGlyph = Color.White
    val DarkGlyph = Color(0xFF1C1C1E)
    val RimWidth: Dp = 1.dp
    const val LightRimAlpha = 0.50f
    const val DarkRimAlpha = 0.25f
}

fun qaCoolapkImportTint(dark: Boolean): Color =
    (if (dark) QaCoolapkImport.DarkTint else QaCoolapkImport.LightTint).copy(alpha = QaCoolapkImport.TintAlpha)

fun qaCoolapkImportGlyph(dark: Boolean): Color =
    if (dark) QaCoolapkImport.DarkGlyph else QaCoolapkImport.LightGlyph

fun qaCoolapkImportRim(dark: Boolean): Color =
    Color.White.copy(alpha = if (dark) QaCoolapkImport.DarkRimAlpha else QaCoolapkImport.LightRimAlpha)

/** Primary text on glass from the spec: dark #1C1C1E, light #F2F2F2. */
object QaGlassText {
    val OnLightGlass = Color(0xFF1C1C1E)
    val OnDarkGlass = Color(0xFFF2F2F2)
}



/** Pure mapping from the spec. Null = production recipe for this role. */
fun qaGlassOverride(role: GlassMaterialRole, selection: QaGlassSelection): QaGlassOverride? {
    val floating = role == GlassMaterialRole.Control ||
        role == GlassMaterialRole.Capsule ||
        role == GlassMaterialRole.Dock
    if (role == GlassMaterialRole.SelectionLens) {
        return if (selection.dockNightLens == QaDockNightLens.BLACK_12_STROKE) {
            QaGlassOverride(darkTint = Color.Black, darkAlpha = 0.12f)
        } else {
            null
        }
    }
    if (selection.variant == QaGlassVariant.UNIFIED) {
        if (role !in QaUnified.Roles) return null
        return QaGlassOverride(
            lightTint = QaUnified.LightTint,
            lightAlpha = QaUnified.LightAlpha,
            darkTint = QaUnified.DarkTint,
            darkAlpha = QaUnified.DarkAlpha,
            blur = selection.unifiedBlur.radius,
            clearPressedTint = true,
            pressedSurfaceAlphaBoost = QaUnified.PressSurfaceAlphaBoost,
            lightBrightness = QaUnified.LightBrightness,
            darkBrightness = QaUnified.DarkBrightness,
            lightContrast = QaUnified.LightContrast,
            darkContrast = QaUnified.DarkContrast,
            mutedAlpha = 1f,
            compressAfterVibrancy = selection.compressOrder == QaCompressOrder.VIBRANCY_FIRST,
        )
    }
    if (!floating) return null
    return when (selection.variant) {
        QaGlassVariant.BASELINE, QaGlassVariant.FOLLOW_PAGE, QaGlassVariant.UNIFIED -> null
        QaGlassVariant.SOLID_NIGHT -> QaGlassOverride(
            darkTint = Color(0xFF242424),
            darkAlpha = 0.75f,
            clearPressedTint = true,
        )
        QaGlassVariant.COOLAPK -> QaGlassOverride(
            lightTint = Color.White,
            lightAlpha = 0.70f,
            darkTint = Color(0xFF1C1C1E),
            darkAlpha = 0.70f,
            blur = 24.dp,
            highlightWidth = 1.dp,
            lightHighlightAlpha = 0.50f,
            darkHighlightAlpha = 0.25f,
            // Spec: 12dp, 4dp down. The engine's drop shadow offset is radius / 3 = 4dp.
            shadow = 12.dp,
            lightShadowAlpha = 0.12f,
            darkShadowAlpha = 0.30f,
            clearPressedTint = true,
            pressedSurfaceAlphaBoost = QaCoolapk.PressSurfaceAlphaBoost,
            pressedHighlightAlphaBoost = QaCoolapk.PressRimAlphaBoost,
            // Dock owns its own press transform (OpticalDockTabs); keep its engine scale at 1.
            pressedScale = if (role == GlassMaterialRole.Dock) null else QaCoolapk.PressScale,
        )
    }
}

/** Whether PDF chrome renders as dark glass. 1a follows the page; others the theme. */
fun qaPdfChromeDark(variant: QaGlassVariant, themeDark: Boolean, pagesDark: Boolean): Boolean =
    if (variant == QaGlassVariant.FOLLOW_PAGE) pagesDark else themeDark

/** Primary text on floating glass; null = keep the theme color (baseline). */
fun qaGlassPrimaryText(variant: QaGlassVariant, glassDark: Boolean): Color? =
    if (variant == QaGlassVariant.BASELINE) null
    // 档 3: pure black / white, Coolapk style.
    else if (variant == QaGlassVariant.UNIFIED) (if (glassDark) Color.White else Color.Black)
    else if (glassDark) QaGlassText.OnDarkGlass else QaGlassText.OnLightGlass

/** Page number on the PDF capsule: primary text in every variant except baseline. */
fun qaPdfPageNumberText(variant: QaGlassVariant, glassDark: Boolean): Color? =
    qaGlassPrimaryText(variant, glassDark)

/** Night Dock selected label: white bold with lens B, else null (production blue). */
fun qaDockSelectedText(lens: QaDockNightLens, dark: Boolean): Color? =
    if (dark && lens == QaDockNightLens.BLACK_12_STROKE) QaGlassText.OnDarkGlass else null

/** 1dp white stroke alpha for the night lens B; 0 = no stroke. */
fun qaDockNightLensStrokeAlpha(lens: QaDockNightLens, dark: Boolean): Float =
    if (dark && lens == QaDockNightLens.BLACK_12_STROKE) 0.35f else 0f
