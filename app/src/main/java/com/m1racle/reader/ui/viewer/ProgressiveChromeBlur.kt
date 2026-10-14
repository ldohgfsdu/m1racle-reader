package com.m1racle.reader.ui.viewer

import android.graphics.BlendMode
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.kyant.backdrop.BackdropEffectScope
import kotlin.math.ceil

/** Native Gaussian scales blended continuously from the status area into the document. */
@RequiresApi(33)
internal class ProgressiveChromeBlur {
    private val source = RuntimeShader("""
        uniform shader content;
        layout(color) uniform half4 pageColor;
        half4 main(float2 p) {
            // The document ends at the viewport. Continue its page color rather
            // than stretching a departing glyph or exposing the sharp underlay.
            half4 c = content.eval(p);
            return c + pageColor * (1.0 - c.a);
        }
    """.trimIndent())

    private val masks = List(5) { level ->
        RuntimeShader("""
            uniform shader content;
            uniform float level;
            uniform float top;
            uniform float start;
            uniform float4 knots;
            half4 main(float2 p) {
                float y = p.y - top;
                float a = smoothstep(start, knots.x, y);
                float b = smoothstep(knots.x, knots.y, y);
                float c = smoothstep(knots.y, knots.z, y);
                float d = smoothstep(knots.z, knots.w, y);
                float weight = level < 0.5 ? 1.0 - a :
                    level < 1.5 ? a - b :
                    level < 2.5 ? b - c :
                    level < 3.5 ? c - d : d;
                return content.eval(p) * half(weight);
            }
        """.trimIndent()).apply { setFloatUniform("level", level.toFloat()) }
    }
    private var dimensions: List<Float>? = null
    private var cached: RenderEffect? = null
    fun apply(scope: BackdropEffectScope, radius: Float, start: Float, end: Float, pageColor: Color) = with(scope) {
        if (radius <= 0f || size.height <= 0f) return@with
        val peak = radius * .5f
        // Native Gaussian filters need room for their tails. Integral padding
        // also keeps Backdrop's recorded layer and its source pixels aligned.
        padding = maxOf(padding, ceil(peak * 3f + 1f))
        val key = listOf(radius, start, end, padding, size.width, size.height, pageColor.red, pageColor.green, pageColor.blue, pageColor.alpha)
        if (dimensions != key) {
            val extent = maxOf(end - start, 1f)
            source.setColorUniform("pageColor", pageColor.toArgb())
            val input = RenderEffect.createRuntimeShaderEffect(source, "content")
            val scales = floatArrayOf(peak, peak / 3f, peak / 9f, peak / 27f, 0f)
            val branches = masks.mapIndexed { index, mask ->
                mask.setFloatUniform("top", padding)
                mask.setFloatUniform("start", start)
                mask.setFloatUniform("knots", start + extent * .42f,
                    start + extent * .82f, start + extent * .91f, start + extent)
                val sigma = scales[index]
                val filtered = if (sigma > 0f) {
                    RenderEffect.createBlurEffect(sigma, sigma, input, Shader.TileMode.DECAL)
                } else input
                RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(mask, "content"), filtered)
            }
            // The five weights sum to one. PLUS blends premultiplied colors
            // without adding a second, sharp copy of the document underneath.
            cached = branches.reduce { accumulated, branch ->
                RenderEffect.createBlendModeEffect(accumulated, branch, BlendMode.PLUS)
            }
            dimensions = key
        }
        val blur = requireNotNull(cached)
        renderEffect = (renderEffect?.let { RenderEffect.createChainEffect(blur, it.asAndroidRenderEffect()) } ?: blur).asComposeRenderEffect()
    }
}
