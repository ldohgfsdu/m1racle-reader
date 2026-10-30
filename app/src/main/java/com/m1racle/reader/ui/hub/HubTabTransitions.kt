package com.m1racle.reader.ui.hub

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition

/** Hub pages switch directly; the dock provides navigation feedback. */
internal object HubTabTransitions {
    fun enter(fromRoute: String?, toRoute: String?): EnterTransition? =
        direction(fromRoute, toRoute)?.let { EnterTransition.None }

    fun exit(fromRoute: String?, toRoute: String?): ExitTransition? =
        direction(fromRoute, toRoute)?.let { ExitTransition.None }

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
