package com.m1racle.reader.ui.pdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** Wheel tuning. One notch is one [STEP_DP] of content. */
object PdfWheelTuning {
    const val STEP_DP = 64f
}

/**
 * Mouse wheel and trackpad scrolling for gesture surfaces that do not use
 * `Modifier.verticalScroll` (the continuous PDF viewport drives its own scroll
 * state, so the platform default has nothing to attach to).
 *
 * Android reports wheel motion through `AXIS_VSCROLL`, roughly ±1 per notch per
 * event, which Compose exposes as `PointerInputChange.scrollDelta`. A trackpad
 * sends fractional values, so the delta is scaled rather than quantised.
 *
 * [onScroll] receives a content-space pixel delta where positive means "toward
 * the start of the document". It returns true when the delta was consumed, which
 * also stops the event from travelling further up the tree.
 */
@Composable
fun pdfMouseWheelModifier(onScroll: (Float) -> Boolean): Modifier {
    val currentOnScroll by rememberUpdatedState(onScroll)
    return Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Scroll) continue
                val change = event.changes.firstOrNull() ?: continue
                val notches = change.scrollDelta.y
                if (notches == 0f) continue
                if (currentOnScroll(notches * PdfWheelTuning.STEP_DP.dp.toPx())) {
                    change.consume()
                }
            }
        }
    }
}
