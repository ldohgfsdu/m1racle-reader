package com.m1racle.reader.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween

/**
 * Shared motion language. Necessary feedback uses a 250ms ease-out transition.
 * Show and hide share the same deceleration curve.
 * No bounce, stretch, or overshoot lives here.
 *
 * Dock feedback uses the same curve; dragging still follows pointer events.
 */
object M1racleMotion {
    val easeOut = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val easeIn = easeOut

    const val FastMs = 250
    const val NormalMs = 250
    const val SlowMs = 250

    val fast = tween<Float>(FastMs, easing = easeOut)
    val normal = tween<Float>(NormalMs, easing = easeOut)
    val slow = tween<Float>(SlowMs, easing = easeOut)

    val fastIn = tween<Float>(FastMs, easing = easeIn)
    val normalIn = tween<Float>(NormalMs, easing = easeIn)
    val slowIn = tween<Float>(SlowMs, easing = easeIn)

}
