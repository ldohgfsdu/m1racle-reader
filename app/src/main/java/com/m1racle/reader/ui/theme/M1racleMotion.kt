package com.m1racle.reader.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween

/**
 * Shared motion language. Three durations, one easing pair.
 * Enter / show uses [easeOut]; exit / hide uses [easeIn].
 * No bounce, stretch, or overshoot lives here.
 */
object M1racleMotion {
    val easeOut = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val easeIn = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    const val FastMs = 120
    const val NormalMs = 180
    const val SlowMs = 220

    val fast = tween<Float>(FastMs, easing = easeOut)
    val normal = tween<Float>(NormalMs, easing = easeOut)
    val slow = tween<Float>(SlowMs, easing = easeOut)

    val fastIn = tween<Float>(FastMs, easing = easeIn)
    val normalIn = tween<Float>(NormalMs, easing = easeIn)
    val slowIn = tween<Float>(SlowMs, easing = easeIn)
}
