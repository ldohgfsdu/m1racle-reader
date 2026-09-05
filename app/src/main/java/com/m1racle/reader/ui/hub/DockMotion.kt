package com.m1racle.reader.ui.hub

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.roundToInt

/** Transient pointer preview only. The navigation stack owns selection. */
@Stable
internal class DockDragPreview {
    var position: Float? by mutableStateOf(null)
        private set

    fun moveTo(position: Float) {
        this.position = position.coerceIn(0f, HubDestination.entries.lastIndex.toFloat())
    }

    fun cancel() {
        position = null
    }

    fun finish(): HubDestination? {
        val destination = position?.let { HubDestination.entries[it.roundToInt()] }
        cancel()
        return destination
    }
}

@Stable
internal class HubDockMotion(
    val selected: HubDestination,
    val position: State<Float>,
    val preview: DockDragPreview,
)

internal val LocalHubDockMotion = staticCompositionLocalOf<HubDockMotion> {
    error("GlassDock must be inside the navigation-owned HubDockMotionProvider")
}

/** Kept above NavHost, so changing pages cannot restart the selection animation. */
@Composable
internal fun HubDockMotionProvider(
    selected: HubDestination,
    content: @Composable () -> Unit,
) {
    val preview = remember { DockDragPreview() }
    LaunchedEffect(selected) { preview.cancel() }
    val position = animateFloatAsState(
        targetValue = preview.position ?: selected.ordinal.toFloat(),
        animationSpec = if (preview.position != null) tween(0) else spring(
            dampingRatio = 0.78f,
            stiffness = 420f,
            visibilityThreshold = 0.001f,
        ),
        label = "hubDockSelection",
    )
    val motion = remember(selected, position, preview) { HubDockMotion(selected, position, preview) }
    CompositionLocalProvider(LocalHubDockMotion provides motion, content = content)
}

/** Official LiquidBottomTabs pressed capsule is 78dp on a 56dp idle slot. */
internal const val DockCapsulePressedScale = 78f / 56f
internal const val DockContentPressedScale = 1.2f

/** Logical slot geometry is shared by rendering and touch handling, including RTL. */
internal fun dockPhysicalPosition(logicalPosition: Float, rtl: Boolean): Float =
    if (rtl) HubDestination.entries.lastIndex - logicalPosition else logicalPosition

/**
 * Velocity stretch from the official LiquidBottomTabs capsule layerBlock.
 * [velocity] is in tab-slots per second; positive is physical right.
 */
internal fun dockCapsuleScales(
    scaleX: Float,
    scaleY: Float,
    velocity: Float,
): Pair<Float, Float> {
    val unitVelocity = velocity / 10f
    val xMorph = (unitVelocity * 0.75f).coerceIn(-0.2f, 0.2f)
    val yMorph = (unitVelocity * 0.25f).coerceIn(-0.2f, 0.2f)
    return (scaleX / (1f - xMorph)) to (scaleY * (1f - yMorph))
}
