package com.m1racle.reader.ui.viewer

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.kyant.backdrop.BackdropEffectScope

/** Two separable Gaussian passes. Sampling radius falls continuously to zero with depth. */
@RequiresApi(33)
internal class ProgressiveChromeBlur {
    private val program = """
        uniform shader content;
        uniform float2 axis;
        uniform float radius;
        uniform float height;
        uniform float top;
        half4 main(float2 p) {
            float strength = 1.0 - smoothstep(0.0, height, p.y - top);
            float2 step = axis * radius * strength / 4.0;
            half4 c = content.eval(p) * 0.227027;
            c += (content.eval(p + step) + content.eval(p - step)) * 0.194595;
            c += (content.eval(p + step * 2.0) + content.eval(p - step * 2.0)) * 0.121622;
            c += (content.eval(p + step * 3.0) + content.eval(p - step * 3.0)) * 0.054054;
            c += (content.eval(p + step * 4.0) + content.eval(p - step * 4.0)) * 0.016216;
            return c;
        }
    """.trimIndent()
    private val horizontal = RuntimeShader(program).apply { setFloatUniform("axis", 1f, 0f) }
    private val vertical = RuntimeShader(program).apply { setFloatUniform("axis", 0f, 1f) }
    private var dimensions: Triple<Float, Float, Float>? = null
    private var cached: RenderEffect? = null
    fun apply(scope: BackdropEffectScope, radius: Float) = with(scope) {
        if (radius <= 0f || size.height <= 0f) return@with
        padding = maxOf(padding, radius)
        val key = Triple(radius, size.height, padding)
        if (dimensions != key) {
            for (shader in listOf(horizontal, vertical)) {
                shader.setFloatUniform("radius", radius)
                shader.setFloatUniform("height", size.height)
                shader.setFloatUniform("top", padding)
            }
            cached = RenderEffect.createChainEffect(
                RenderEffect.createRuntimeShaderEffect(vertical, "content"),
                RenderEffect.createRuntimeShaderEffect(horizontal, "content"))
            dimensions = key
        }
        val blur = requireNotNull(cached)
        renderEffect = (renderEffect?.let { RenderEffect.createChainEffect(blur, it.asAndroidRenderEffect()) } ?: blur).asComposeRenderEffect()
    }
}
