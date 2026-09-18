package com.m1racle.reader.ui.hub

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset
import com.m1racle.reader.ui.theme.M1racleMotion
import kotlin.math.roundToInt

/**
 * Hub tab content motion. Dock stays outside NavHost, so only the page
 * body moves. Direction follows the dock order; travel is a short fade
 * plus an 8% slide — not a full-width push.
 */
internal object HubTabTransitions {
    const val SlideFraction = 0.08f

    fun enter(fromRoute: String?, toRoute: String?): EnterTransition? {
        val direction = direction(fromRoute, toRoute) ?: return null
        if (direction == 0) return EnterTransition.None
        return fadeIn(tween(M1racleMotion.NormalMs, easing = M1racleMotion.easeOut)) +
            slideInHorizontally(
                animationSpec = tween(M1racleMotion.NormalMs, easing = M1racleMotion.easeOut),
                initialOffsetX = { full -> (full * SlideFraction * direction).roundToInt() },
            )
    }

    fun exit(fromRoute: String?, toRoute: String?): ExitTransition? {
        val direction = direction(fromRoute, toRoute) ?: return null
        if (direction == 0) return ExitTransition.None
        return fadeOut(tween(M1racleMotion.FastMs, easing = M1racleMotion.easeIn)) +
            slideOutHorizontally(
                animationSpec = tween<IntOffset>(M1racleMotion.FastMs, easing = M1racleMotion.easeIn),
                targetOffsetX = { full -> (full * SlideFraction * -direction).roundToInt() },
            )
    }

    /** +1 = destination is to the right of origin; 0 = same tab; null = not a hub pair. */
    fun direction(fromRoute: String?, toRoute: String?): Int? {
        val from = HubDestination.fromRoute(fromRoute) ?: return null
        val to = HubDestination.fromRoute(toRoute) ?: return null
        return (to.ordinal - from.ordinal).let { delta ->
            when {
                delta > 0 -> 1
                delta < 0 -> -1
                else -> 0
            }
        }
    }
}
