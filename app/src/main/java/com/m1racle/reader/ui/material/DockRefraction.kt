/*
 * Copyright 2025 Kyant
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modified 2026-10-13: keep dispersion continuous across the pill axes.
 * SDF, refraction and seven-band reconstruction are from Backdrop 2.0.0,
 * internal/Shaders.kt, commit 65ab177e90e5c1d8c62e70cf7755841982da65f6.
 */
package com.m1racle.reader.ui.material

import android.graphics.RenderEffect
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.asAndroidRuntimeShader

/** Single-pass optical pickup of the actual captured Dock, with no painted fringe. */
@RequiresApi(33)
internal fun BackdropEffectScope.dockRefraction(height: Float, amount: Float, dispersion: Float) {
    if (height <= 0f || amount <= 0f) return
    padding = (padding - height).coerceAtLeast(0f)
    val outline = shape.createOutline(size, layoutDirection, this)
    val radii = when (outline) {
        is Outline.Rounded -> outline.roundRect.let {
            floatArrayOf(it.topLeftCornerRadius.x, it.topRightCornerRadius.x,
                it.bottomRightCornerRadius.x, it.bottomLeftCornerRadius.x)
        }
        is Outline.Rectangle -> floatArrayOf(0f, 0f, 0f, 0f)
        else -> error("Dock refraction requires a rounded rectangle")
    }
    val shader = obtainRuntimeShader("ReaderDockRefraction", DockRefractionShader)
    shader.setFloatUniform("size", size.width, size.height)
    shader.setFloatUniform("offset", -padding, -padding)
    shader.setFloatUniform("cornerRadii", radii)
    shader.setFloatUniform("refractionHeight", height)
    shader.setFloatUniform("refractionAmount", -amount)
    shader.setFloatUniform("depthEffect", 0f)
    shader.setFloatUniform("chromaticAberration", dispersion)
    val optical = RenderEffect.createRuntimeShaderEffect(shader.asAndroidRuntimeShader(), "content")
    val preceding = renderEffect
    renderEffect = (if (preceding == null) optical else RenderEffect.createChainEffect(
        optical, preceding.asAndroidRenderEffect())).asComposeRenderEffect()
}

// The upstream x*y angular attenuation made dispersion vanish at all four
// axis crossings. Dispersion now follows the actual refracted displacement;
// that displacement still goes smoothly to zero at the inner optical boundary.
internal const val DockRefractionShader = """

uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;


float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(coord, cornerRadii);
    
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);
    
    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));
    
    float2 refractedCoord = coord + d * grad;
    float dispersionIntensity = chromaticAberration;
    float2 dispersedCoord = d * grad * dispersionIntensity;
    
    half4 color = half4(0.0);
    
    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;
    
    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;
    
    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;
    
    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;
    
    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;
    
    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;
    
    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;
    
    return color;
}
"""
