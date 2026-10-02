package com.m1racle.reader.ui.pdf

import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
/** The parent owns long-press input; rows consume the same pointer for their highlight. */
internal val LocalPdfMenuHeldPointer = staticCompositionLocalOf<State<Offset?>?> { null }

/**
 * Bounded follow, same resistance in every direction. Magnitude stays below 1.
 * The reference clip moves the card a few pixels with the finger; it does not
 * travel one-to-one, and the offset returns when the finger lifts.
 */
internal fun pdfMenuStrain(distance: Offset, resistance: Float): Offset {
    require(resistance > 0f)
    return distance / (resistance + distance.getDistance())
}

/** The card follows the finger inside a short rubber bound, then rebounds. The gesture detector itself stays put. */
@Composable
internal fun PdfMenuElasticPress(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable (transform: GraphicsLayerScope.() -> Unit, settled: Boolean) -> Unit,
) {
    val resistance = with(LocalDensity.current) { 64.dp.toPx() }
    // The reference clip shifts the whole card with the finger by a few
    // pixels. Scaling the tall menu around its center pulls the top row the
    // other way, so the follow is translation only and capped.
    val maxFollow = with(LocalDensity.current) { 22.dp.toPx() }
    val pointer = remember { mutableStateOf<Offset?>(null) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var distance by remember { mutableStateOf(Offset.Zero) }
    val held = enabled && pointer.value != null
    // While the finger is down the card uses the latest strain. The spring is
    // only the release rebound; easing the held pose is what made it lag.
    val strainTarget = if (held) pdfMenuStrain(distance, resistance) else Offset.Zero
    val strain = animateOffsetAsState(
        targetValue = strainTarget,
        animationSpec = if (!enabled || held) {
            snap()
        } else {
            spring(dampingRatio = 0.55f, stiffness = 420f)
        },
        label = "menu elastic strain",
    )
    val shownStrain = if (held) strainTarget else strain.value
    val transform: GraphicsLayerScope.() -> Unit = remember(shownStrain, maxFollow) {
        {
            val follow = shownStrain * maxFollow
            translationX = follow.x
            translationY = follow.y
        }
    }
    val settled by remember {
        derivedStateOf { pointer.value == null && strain.value == Offset.Zero }
    }
    Box(
        modifier.onGloballyPositioned { coordinates = it }
            .pointerInput(resistance, enabled) {
                if (!enabled) return@pointerInput
                try {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { position ->
                            distance = Offset.Zero
                            pointer.value = coordinates?.localToRoot(position)
                        },
                        onDragEnd = { pointer.value = null },
                        onDragCancel = { pointer.value = null },
                        onDrag = { change, amount ->
                            change.consume()
                            distance += amount
                            pointer.value = coordinates?.localToRoot(change.position)
                        },
                    )
                } finally {
                    pointer.value = null
                }
            },
    ) {
        CompositionLocalProvider(LocalPdfMenuHeldPointer provides pointer) {
            content(transform, settled)
        }
    }
}
