package com.m1racle.reader.ui.hub.glasslab

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.listSaver
import kotlin.math.min

/** Experiment state is independent from the production GlassMaterial recipes. */
@Immutable
internal data class GlassLabParameters(
    val width: Float = 180f,
    val height: Float = 160f,
    val radius: Float = 40f,
    val scale: Float = 1f,
    val depth: Float = 20f,
    val curvature: Float = .7f,
    val refraction: Float = 28f,
    val splay: Float = 1f,
    val chroma: Float = .2f,
    val blur: Float = 4f,
    val opacity: Float = .12f,
    val highlight: Float = .65f,
    val shadow: Float = .16f,
    val lightAngle: Float = 45f,
) {
    fun values() = listOf(width, height, radius, scale, depth, curvature, refraction,
        splay, chroma, blur, opacity, highlight, shadow, lightAngle)

    fun sanitized(): GlassLabParameters = from(values())

    companion object {
        val Regular = GlassLabParameters(blur = 12f, opacity = .3f, depth = 16f, refraction = 20f, chroma = .08f)
        val Clear = GlassLabParameters(blur = 1f, opacity = .03f, depth = 22f, refraction = 32f, chroma = .12f)
        val Saver = listSaver<GlassLabParameters, Float>(save = { it.values() }, restore = { from(it) })

        fun from(values: List<Float>): GlassLabParameters {
            val defaults = GlassLabParameters().values()
            val bounded = GlassLabControl.entries.mapIndexed { i, control ->
                val v = values.getOrNull(i)?.takeIf { it.isFinite() } ?: defaults[i]
                v.coerceIn(control.min, control.max)
            }
            return GlassLabParameters(bounded[0], bounded[1], bounded[2], bounded[3], bounded[4],
                bounded[5], bounded[6], bounded[7], bounded[8], bounded[9], bounded[10],
                bounded[11], bounded[12], bounded[13])
        }
    }
}

internal enum class GlassLabControl(val label: String, val min: Float, val max: Float, val unit: String = "", val decimal: Boolean = false) {
    Width("Width", 80f, 320f, "dp"),
    Height("Height", 64f, 280f, "dp"),
    Radius("BorderRadius", 0f, 140f, "dp"),
    Scale("Scale", .4f, 1.4f, decimal = true),
    Depth("Depth", 1f, 80f, "dp"),
    Curvature("Curvature", .05f, 1f, decimal = true),
    Refraction("Refraction", 0f, 80f, "dp"),
    Splay("Splay", 0f, 2f, decimal = true),
    Chroma("Chroma", 0f, 1.5f, decimal = true),
    Blur("Blur", 0f, 32f, "dp"),
    Opacity("Opacity", 0f, .9f, decimal = true),
    Highlight("Highlight", 0f, 1f, decimal = true),
    Shadow("Shadow", 0f, .5f, decimal = true),
    LightAngle("Light angle", 0f, 360f, "°");

    fun value(p: GlassLabParameters) = p.values()[ordinal]
    fun update(p: GlassLabParameters, value: Float): GlassLabParameters =
        GlassLabParameters.from(p.values().toMutableList().also { it[ordinal] = value })
}

internal data class GlassLabGeometry(val width: Float, val height: Float, val radius: Float, val fit: Float)

/** Keep the complete specimen inside the canvas at any viewport and slider extremes. */
internal fun glassLabGeometry(p: GlassLabParameters, availableWidth: Float, availableHeight: Float): GlassLabGeometry {
    val fit = min(1f, min((availableWidth - 32f).coerceAtLeast(1f) / (p.width * p.scale),
        (availableHeight - 32f).coerceAtLeast(1f) / (p.height * p.scale)))
    val width = p.width * p.scale * fit
    val height = p.height * p.scale * fit
    return GlassLabGeometry(width, height, min(p.radius * p.scale * fit, min(width, height) / 2f), fit)
}
