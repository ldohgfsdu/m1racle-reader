package com.m1racle.reader.ui.material

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Preview-only HyperLight-style soft glass.
 *
 * Production recipes stay in [GlassMaterialEngine]. Nothing here is a
 * production role, and the comparison page is the only caller.
 */
enum class SoftGlassCandidate {
    H0,
    H1,
    H2,
}

enum class SoftGlassRole {
    Control,
    Panel,
}

/**
 * Preview-only production mapping. Not an H0/H1/H2 chip and not a production role.
 * [SoftGlassCandidate.H0] remains the family baseline these numbers were tuned from.
 */
enum class SoftGlassMapping {
    ControlCandidate,
    PanelCandidate,
}

/**
 * Directional rim for the preview renderer. Not a uniform stroke, and not
 * part of the production highlight API.
 */
@Immutable
data class DirectionalHighlightSpec(
    val topLeft: Float,
    val mid: Float,
    val bottomRight: Float,
    val inner: Float,
    val rimWidth: Dp,
)

/**
 * Resolved preview material. Design brightness is a factor around 1.
 * Renderer brightness is Kyant's additive offset (0 = identity).
 */
@Immutable
data class ResolvedSoftGlass(
    val candidate: SoftGlassCandidate,
    val role: SoftGlassRole,
    val requestedDark: Boolean,
    val usesDarkRecipe: Boolean,
    val pressed: Boolean,
    val logicalBlur: Dp,
    val tint: Color,
    val tintAlpha: Float,
    val designSaturation: Float,
    val designBrightness: Float,
    val designContrast: Float,
    val designSoftLight: Float,
    val rendererSaturation: Float,
    val rendererBrightness: Float,
    val rendererContrast: Float,
    val rendererSoftLightBrightness: Float,
    /** Max edge displacement passed to the backdrop lens. Center stays 0. */
    val refractionAmountPx: Float,
    /** Edge band width. Samples farther inside than this are not displaced. */
    val refractionHeightPx: Float,
    val centerRefractionPx: Float,
    val highlight: DirectionalHighlightSpec,
    val shadowAlpha: Float,
    val shadowRadius: Dp,
    val foregroundAlpha: Float,
    val pressedScale: Float,
    /** Non-null only for the production-mapping preview, never for raw H0/H1/H2. */
    val mapping: SoftGlassMapping? = null,
)

object SoftGlassComparison {
    const val TintCap = 0.20f
    const val RoleBlurDeltaDp = 3f
    const val RoleTintDelta = 0.02f
    const val DarkBlurExtraDp = 3f
    const val DarkSoftLightScale = 0.5f

    /**
     * Kyant `colorControls` brightness is an additive offset, 0 = identity.
     * Design brightness is a multiplier around 1, so 1.05 maps to +0.05.
     */
    const val BrightnessIdentity = 1f

    /**
     * Soft-light strength is not a Kyant brightness unit. 1.0 strength becomes
     * 0.35 additive brightness, so the design 0.13 stays a lift instead of a
     * white plate.
     */
    const val SoftLightToBrightness = 0.35f

    /** Kyant lens already leaves the center untouched outside this band. */
    const val EdgeRefractionBandPx = 8f

    const val PressedScale = 0.988f
    const val PressedHighlightBoost = 0.02f
    const val ForegroundAlpha = 1f

    private val LightTintRgb = Color.White
    private val DarkTintRgb = Color(0xFF101418)
    private val RimWidth = 0.75.dp
    private val MappingRimWidth = 0.5.dp
    private val ShadowRadius = 2.dp

    private data class Baseline(
        val blurDp: Float,
        val tintAlpha: Float,
        val saturation: Float,
        val brightness: Float,
        val contrast: Float,
        val softLight: Float,
        val refractionPx: Float,
        val highlightTopLeft: Float,
        val highlightMid: Float,
        val highlightBottomRight: Float,
        val innerHighlight: Float,
        val shadowAlpha: Float,
    )

    private val lightPanel = mapOf(
        SoftGlassCandidate.H0 to Baseline(
            blurDp = 32f,
            tintAlpha = 0.13f,
            saturation = 1.20f,
            brightness = 1.05f,
            contrast = 0.95f,
            softLight = 0.13f,
            refractionPx = 1.0f,
            highlightTopLeft = 0.28f,
            highlightMid = 0.11f,
            highlightBottomRight = 0.05f,
            innerHighlight = 0.08f,
            shadowAlpha = 0.06f,
        ),
        SoftGlassCandidate.H1 to Baseline(
            blurDp = 26f,
            tintAlpha = 0.09f,
            saturation = 1.25f,
            brightness = 1.04f,
            contrast = 0.96f,
            softLight = 0.10f,
            refractionPx = 0.8f,
            highlightTopLeft = 0.25f,
            highlightMid = 0.10f,
            highlightBottomRight = 0.04f,
            innerHighlight = 0.06f,
            shadowAlpha = 0.05f,
        ),
        SoftGlassCandidate.H2 to Baseline(
            blurDp = 40f,
            tintAlpha = 0.17f,
            saturation = 1.14f,
            brightness = 1.06f,
            contrast = 0.93f,
            softLight = 0.15f,
            refractionPx = 1.2f,
            highlightTopLeft = 0.30f,
            highlightMid = 0.12f,
            highlightBottomRight = 0.06f,
            innerHighlight = 0.09f,
            shadowAlpha = 0.07f,
        ),
    )

    private val mappingLight = mapOf(
        SoftGlassMapping.ControlCandidate to Baseline(
            blurDp = 28f,
            tintAlpha = 0.10f,
            saturation = 1.20f,
            brightness = 1.04f,
            contrast = 0.96f,
            softLight = 0.10f,
            refractionPx = 0.5f,
            highlightTopLeft = 0.22f,
            highlightMid = 0.08f,
            highlightBottomRight = 0.03f,
            innerHighlight = 0.05f,
            shadowAlpha = 0.04f,
        ),
        SoftGlassMapping.PanelCandidate to Baseline(
            blurDp = 35f,
            tintAlpha = 0.15f,
            saturation = 1.12f,
            brightness = 1.04f,
            contrast = 0.94f,
            softLight = 0.12f,
            refractionPx = 0f,
            highlightTopLeft = 0.18f,
            highlightMid = 0.06f,
            highlightBottomRight = 0.02f,
            innerHighlight = 0.06f,
            shadowAlpha = 0.05f,
        ),
    )

    fun resolve(
        candidate: SoftGlassCandidate,
        role: SoftGlassRole,
        dark: Boolean,
        pressed: Boolean = false,
    ): ResolvedSoftGlass {
        val light = checkNotNull(lightPanel[candidate])
        val usesDarkRecipe = dark && candidate == SoftGlassCandidate.H0
        val panel = if (usesDarkRecipe) darkPanel(light) else light
        val adjusted = panel.copy(
            blurDp = panel.blurDp - if (role == SoftGlassRole.Control) RoleBlurDeltaDp else 0f,
            tintAlpha = panel.tintAlpha - if (role == SoftGlassRole.Control) RoleTintDelta else 0f,
        )
        return materialize(
            baseline = adjusted,
            candidate = candidate,
            role = role,
            dark = dark,
            pressed = pressed,
            usesDarkRecipe = usesDarkRecipe,
            mapping = null,
            rimWidth = RimWidth,
            tintRgb = if (usesDarkRecipe) DarkTintRgb else LightTintRgb,
        )
    }

    /**
     * Role mapping inside the H0 family. Control leans toward H1.
     * Panel leans toward H0 but with less saturation and a slightly higher neutral tint.
     * Dark reuses the H0 dark step (blur +3dp, neutral dark tint, brightness 0.91).
     * It does not add another dark candidate.
     */
    fun resolveMapping(
        mapping: SoftGlassMapping,
        dark: Boolean,
        pressed: Boolean = false,
    ): ResolvedSoftGlass {
        val light = checkNotNull(mappingLight[mapping])
        val baseline = if (dark) darkenMapping(light, mapping) else light
        return materialize(
            baseline = baseline,
            candidate = SoftGlassCandidate.H0,
            role = if (mapping == SoftGlassMapping.ControlCandidate) {
                SoftGlassRole.Control
            } else {
                SoftGlassRole.Panel
            },
            dark = dark,
            pressed = pressed,
            usesDarkRecipe = dark,
            mapping = mapping,
            rimWidth = MappingRimWidth,
            tintRgb = if (dark) DarkTintRgb else LightTintRgb,
        )
    }

    fun mappingSchemeLine(dark: Boolean, density: Float): String {
        val control = resolveMapping(SoftGlassMapping.ControlCandidate, dark)
        val panel = resolveMapping(SoftGlassMapping.PanelCandidate, dark)
        val head = if (dark) "映射 Dark" else "映射"
        return "$head · Control ${formatDp(control.logicalBlur.value)}dp tint ${formatAlpha(control.tintAlpha)} · " +
            "Panel ${formatDp(panel.logicalBlur.value)}dp tint ${formatAlpha(panel.tintAlpha)}"
    }

    fun mappingSummary(dark: Boolean, density: Float): String {
        val control = resolveMapping(SoftGlassMapping.ControlCandidate, dark)
        val panel = resolveMapping(SoftGlassMapping.PanelCandidate, dark)
        return buildString {
            appendLine(mappingSchemeLine(dark, density))
            appendLine(mappingDetail("ControlCandidate", control, density))
            appendLine(mappingDetail("PanelCandidate", panel, density))
            append("暗色沿 H0 Dark：blur +3dp，tint 改中性深色，brightness 0.91。不是新的暗色候选。")
        }
    }

    /** Linear. Kyant blur radius px = logical dp × density. No extra gain. */
    fun rendererBlurPx(logicalBlur: Dp, density: Float): Float = logicalBlur.value * density

    fun dpToPx(dp: Dp, density: Float): Float = dp.value * density

    fun schemeLine(candidate: SoftGlassCandidate, dark: Boolean, density: Float): String {
        val panel = resolve(candidate, SoftGlassRole.Panel, dark)
        val head = when {
            panel.usesDarkRecipe -> "${candidate.name} Dark"
            dark -> "${candidate.name} · 暗色未展开"
            else -> candidate.name
        }
        val px = formatPx(rendererBlurPx(panel.logicalBlur, density))
        return "$head · blur ${formatDp(panel.logicalBlur.value)}dp/${px}px · " +
            "tint ${formatAlpha(panel.tintAlpha)} · sat ${formatFactor(panel.designSaturation)} · " +
            "bright ${formatFactor(panel.designBrightness)} · contrast ${formatFactor(panel.designContrast)} · " +
            "highlight ${formatAlpha(panel.highlight.topLeft)} · " +
            "refraction ${formatRefraction(panel.refractionAmountPx)}px"
    }

    fun parameterSummary(candidate: SoftGlassCandidate, dark: Boolean, density: Float): String {
        val panel = resolve(candidate, SoftGlassRole.Panel, dark)
        val control = resolve(candidate, SoftGlassRole.Control, dark)
        return buildString {
            appendLine(schemeLine(candidate, dark, density))
            appendLine(
                "logicalBlur ${formatDp(panel.logicalBlur.value)}dp / " +
                    "rendererBlur ${formatPx(rendererBlurPx(panel.logicalBlur, density))}px " +
                    "(px = dp × density ${formatFactor(density)}; linear)",
            )
            appendLine(
                "bright design ${formatFactor(panel.designBrightness)} → renderer " +
                    formatSigned(panel.rendererBrightness) +
                    " (Kyant additive, 0 = identity)",
            )
            appendLine(
                "softLight design ${formatFactor(panel.designSoftLight)} → renderer brightness " +
                    formatSigned(panel.rendererSoftLightBrightness) +
                    " (× $SoftLightToBrightness)",
            )
            appendLine(
                "refraction amount ${formatRefraction(panel.refractionAmountPx)}px / " +
                    "band ${formatRefraction(panel.refractionHeightPx)}px / center 0",
            )
            appendLine(
                "highlight ${formatAlpha(panel.highlight.topLeft)} / " +
                    "${formatAlpha(panel.highlight.mid)} / " +
                    "${formatAlpha(panel.highlight.bottomRight)} " +
                    "inner ${formatAlpha(panel.highlight.inner)}",
            )
            appendLine(
                "tint design ${formatAlpha(panel.tintAlpha)} / painted ${formatPainted(panel.tint.alpha)} " +
                    "(Compose sRGB packs alpha into 8 bits)",
            )
            appendLine(
                "panel blur ${formatDp(panel.logicalBlur.value)}dp tint ${formatAlpha(panel.tintAlpha)} · " +
                    "control blur ${formatDp(control.logicalBlur.value)}dp tint ${formatAlpha(control.tintAlpha)}",
            )
            appendLine(
                "shadow ${formatAlpha(panel.shadowAlpha)} / ${formatDp(panel.shadowRadius.value)}dp · " +
                    "foreground ${formatFactor(panel.foregroundAlpha)}",
            )
            if (dark && !panel.usesDarkRecipe) {
                append("暗色仅展开 H0。此候选仍是浅色配方，浅色参数未被改写。")
            }
        }.trimEnd()
    }

    private fun darkenMapping(light: Baseline, mapping: SoftGlassMapping): Baseline {
        val tintAlpha = when (mapping) {
            SoftGlassMapping.ControlCandidate -> 0.14f
            SoftGlassMapping.PanelCandidate -> 0.18f
        }
        val saturation = when (mapping) {
            SoftGlassMapping.ControlCandidate -> 1.14f
            SoftGlassMapping.PanelCandidate -> 1.10f
        }
        return light.copy(
            blurDp = light.blurDp + DarkBlurExtraDp,
            tintAlpha = tintAlpha,
            saturation = saturation,
            brightness = 0.91f,
            contrast = light.contrast,
            softLight = light.softLight * DarkSoftLightScale,
        )
    }

    private fun materialize(
        baseline: Baseline,
        candidate: SoftGlassCandidate,
        role: SoftGlassRole,
        dark: Boolean,
        pressed: Boolean,
        usesDarkRecipe: Boolean,
        mapping: SoftGlassMapping?,
        rimWidth: Dp,
        tintRgb: Color,
    ): ResolvedSoftGlass {
        require(baseline.tintAlpha <= TintCap) { "soft glass tint ${baseline.tintAlpha} exceeds $TintCap" }
        require(baseline.tintAlpha >= 0f) { "soft glass tint ${baseline.tintAlpha} is negative" }
        return ResolvedSoftGlass(
            candidate = candidate,
            role = role,
            requestedDark = dark,
            usesDarkRecipe = usesDarkRecipe,
            pressed = pressed,
            logicalBlur = baseline.blurDp.dp,
            tint = tintRgb.copy(alpha = baseline.tintAlpha),
            tintAlpha = baseline.tintAlpha,
            designSaturation = baseline.saturation,
            designBrightness = baseline.brightness,
            designContrast = baseline.contrast,
            designSoftLight = baseline.softLight,
            rendererSaturation = baseline.saturation,
            rendererBrightness = baseline.brightness - BrightnessIdentity,
            rendererContrast = baseline.contrast,
            rendererSoftLightBrightness = baseline.softLight * SoftLightToBrightness,
            refractionAmountPx = baseline.refractionPx,
            refractionHeightPx = if (baseline.refractionPx > 0f) EdgeRefractionBandPx else 0f,
            centerRefractionPx = 0f,
            highlight = DirectionalHighlightSpec(
                topLeft = baseline.highlightTopLeft + if (pressed) PressedHighlightBoost else 0f,
                mid = baseline.highlightMid + if (pressed) PressedHighlightBoost * 0.5f else 0f,
                bottomRight = baseline.highlightBottomRight,
                inner = baseline.innerHighlight,
                rimWidth = rimWidth,
            ),
            shadowAlpha = baseline.shadowAlpha,
            shadowRadius = ShadowRadius,
            foregroundAlpha = ForegroundAlpha,
            pressedScale = if (pressed) PressedScale else 1f,
            mapping = mapping,
        )
    }

    private fun mappingDetail(name: String, spec: ResolvedSoftGlass, density: Float): String =
        "$name blur ${formatDp(spec.logicalBlur.value)}dp/" +
            "${formatPx(rendererBlurPx(spec.logicalBlur, density))}px · " +
            "tint ${formatAlpha(spec.tintAlpha)} · sat ${formatFactor(spec.designSaturation)} · " +
            "bright ${formatFactor(spec.designBrightness)} · contrast ${formatFactor(spec.designContrast)} · " +
            "soft ${formatFactor(spec.designSoftLight)} · " +
            "highlight ${formatAlpha(spec.highlight.topLeft)}/${formatAlpha(spec.highlight.bottomRight)} · " +
            "inner ${formatAlpha(spec.highlight.inner)} · " +
            "refraction ${formatRefraction(spec.refractionAmountPx)}px · " +
            "shadow ${formatAlpha(spec.shadowAlpha)} · foreground ${formatFactor(spec.foregroundAlpha)}"

    private fun darkPanel(light: Baseline): Baseline = light.copy(
        blurDp = light.blurDp + DarkBlurExtraDp,
        tintAlpha = 0.18f,
        saturation = 1.14f,
        brightness = 0.91f,
        contrast = 0.96f,
        softLight = light.softLight * DarkSoftLightScale,
    )

    private fun formatDp(value: Float): String {
        val nearest = value.roundToInt()
        return if (abs(value - nearest) < 0.001f) nearest.toString()
        else String.format(Locale.ROOT, "%.2f", value)
    }

    private fun formatPx(value: Float): String {
        val nearest = value.roundToInt()
        return if (abs(value - nearest) < 0.05f) nearest.toString()
        else String.format(Locale.ROOT, "%.1f", value)
    }

    private fun formatAlpha(value: Float): String {
        val text = String.format(Locale.ROOT, "%.2f", value)
        return when {
            text.startsWith("-0") -> "-" + text.drop(2)
            text.startsWith("0") -> text.drop(1)
            else -> text
        }
    }

    private fun formatFactor(value: Float): String = String.format(Locale.ROOT, "%.2f", value)

    private fun formatRefraction(value: Float): String = String.format(Locale.ROOT, "%.1f", value)

    private fun formatPainted(value: Float): String = String.format(Locale.ROOT, "%.4f", value)

    private fun formatSigned(value: Float): String {
        val text = String.format(Locale.ROOT, "%.4f", value)
        return if (value > 0f && !text.startsWith("+")) "+$text" else text
    }
}
