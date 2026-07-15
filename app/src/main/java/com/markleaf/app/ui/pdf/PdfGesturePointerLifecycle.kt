package com.markleaf.app.ui.pdf

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import com.markleaf.app.pdf.PdfGestureTransformController

/**
 * The single production down/up lifecycle for PDF transform gestures.
 *
 * Mode adapters keep geometry, render scheduling and Pager policy callbacks. The shared lifecycle
 * owns awaitEachGesture, the first stable pointer id, exception-safe controller teardown, and the
 * controller owns every pointer frame between them.
 */
internal suspend fun PointerInputScope.awaitPdfGestureLifecycle(
    controller: PdfGestureTransformController,
    pass: PointerEventPass = PointerEventPass.Main,
    gesture: suspend AwaitPointerEventScope.(PointerInputChange) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = pass)
        try {
            gesture(down)
        } finally {
            controller.endGesture()
        }
    }
}
