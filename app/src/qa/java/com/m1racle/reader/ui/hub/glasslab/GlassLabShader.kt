/*
 * Lens SDF and spectral sampling adapted from AndroidLiquidGlass by Kyant.
 * Copyright 2025 Kyant. Licensed under the Apache License, Version 2.0.
 * https://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 * Source: https://github.com/Kyant0/AndroidLiquidGlass
 * The workbench adds continuous curvature, splay, chroma, and normal diagnostics.
 */
package com.m1racle.reader.ui.hub.glasslab

internal const val GlassLabLensShader = """
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float radius;
uniform float depth;
uniform float curvature;
uniform float refraction;
uniform float splay;
uniform float chroma;
uniform float diagnostic;
uniform float splitX;

float sdf(float2 p, float2 b, float r) {
    float2 q = abs(p) - (b - r);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
float2 gradient(float2 p, float2 b, float r) {
    float2 q = abs(p) - (b - r);
    if (q.x >= 0.0 || q.y >= 0.0) {
        return sign(p) * max(q, 0.0) / max(length(max(q, 0.0)), 0.001);
    }
    float x = step(q.y, q.x);
    return sign(p) * float2(x, 1.0 - x);
}
half4 main(float2 coord) {
    float2 local = coord + offset;
    float2 halfSize = size * 0.5;
    float2 p = local - halfSize;
    float sd = min(sdf(p, halfSize, radius), 0.0);
    float rim = clamp(1.0 + sd / max(depth, 0.001), 0.0, 1.0);
    float curved = pow(rim, 2.0 - curvature * 1.7);
    float distance = (1.0 - sqrt(max(0.0, 1.0 - curved * curved))) * -refraction;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 n = gradient(p, halfSize, gradRadius);
    float2 radial = p / max(length(p), 0.001);
    n = n + splay * radial;
    n /= max(length(n), 0.001);
    if (diagnostic > 0.5 && local.x >= splitX) {
        float2 surfaceNormal = n * curved;
        return half4(surfaceNormal.x * 0.5 + 0.5, -surfaceNormal.y * 0.5 + 0.5,
            sqrt(max(0.0, 1.0 - dot(surfaceNormal, surfaceNormal))), 1.0);
    }
    float2 refracted = coord + distance * n;
    float intensity = chroma * (p.x * p.y / max(halfSize.x * halfSize.y, 0.001));
    float2 dispersed = distance * n * intensity;
    half4 red = content.eval(refracted + dispersed);
    half4 orange = content.eval(refracted + dispersed * (2.0 / 3.0));
    half4 yellow = content.eval(refracted + dispersed * (1.0 / 3.0));
    half4 green = content.eval(refracted);
    half4 cyan = content.eval(refracted - dispersed * (1.0 / 3.0));
    half4 blue = content.eval(refracted - dispersed * (2.0 / 3.0));
    half4 purple = content.eval(refracted - dispersed);
    return half4(
        (red.r + orange.r + yellow.r) / 3.5 + purple.r / 7.0,
        orange.g / 7.0 + (yellow.g + green.g + cyan.g) / 3.5,
        (cyan.b + blue.b + purple.b) / 3.0,
        (red.a + orange.a + yellow.a + green.a + cyan.a + blue.a + purple.a) / 7.0);
}
"""
