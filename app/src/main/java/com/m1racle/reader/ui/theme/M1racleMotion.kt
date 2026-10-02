package com.m1racle.reader.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Shared motion language. Three durations, one easing pair.
 * Enter / show uses [easeOut]; exit / hide uses [easeIn].
 * No bounce, stretch, or overshoot lives here.
 *
 * [dockPickup] and [dockSelection] are the two sanctioned springs. The hub dock
 * is the one surface that moves a real object under the finger, so pressing and
 * releasing it needs physical settle rather than a duration. Every other
 * transition stays on the tweens above. Feature code must reference a token here
 * instead of declaring its own `spring(...)`, so motion has exactly one
 * authority and the dock's two springs cannot drift apart.
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

    /** The dock capsule grows under the finger, then returns to rest. */
    val dockPickup: AnimationSpec<Float> = spring(dampingRatio = 0.8f, stiffness = 500f)

    /** The selected dock slot slides to its destination and settles there. */
    val dockSelection: AnimationSpec<Float> = spring(dampingRatio = 0.82f, stiffness = 420f)
}
