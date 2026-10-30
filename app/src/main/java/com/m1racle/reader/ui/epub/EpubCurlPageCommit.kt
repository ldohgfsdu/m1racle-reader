package com.m1racle.reader.ui.epub

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangedIgnoreConsumed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * Separates a settled curl turn from an index change caused by initial restore,
 * reflow, or a programmatic snapTo.
 */
internal class EpubCurlPageCommit(
    private var current: Int,
) {
    private var pointerDown = false
    private var consumedMove = false
    private var gestureStartPage: Int? = null

    fun observe(page: Int, pageProgress: Float): Int? {
        current = page
        if (pageProgress != 0f || pointerDown) return null
        val start = gestureStartPage ?: return null
        val committed = page.takeIf { consumedMove && it != start }
        consumedMove = false
        gestureStartPage = null
        return committed
    }

    fun onPointerDown() {
        pointerDown = true
        consumedMove = false
        gestureStartPage = current
    }

    fun onPointerConsumedMove() {
        if (pointerDown) consumedMove = true
    }

    fun onPointerUp() {
        pointerDown = false
    }

    fun onPointerCancelled() {
        consumedMove = false
        gestureStartPage = null
        pointerDown = false
    }

    fun reset(page: Int) { onPointerCancelled(); current = page }
}

@Composable
internal fun Modifier.observeEpubCurlPointer(
    onDown: () -> Unit,
    onConsumedMove: () -> Unit,
    onUp: () -> Unit,
    onCancel: () -> Unit,
): Modifier {
    val currentDown by rememberUpdatedState(onDown)
    val currentMove by rememberUpdatedState(onConsumedMove)
    val currentUp by rememberUpdatedState(onUp)
    val currentCancel by rememberUpdatedState(onCancel)
    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            currentDown()
            var released = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    if (event.changes.any { it.positionChangedIgnoreConsumed() && it.isConsumed }) currentMove()
                    if (event.changes.none { it.pressed }) {
                        released = true
                        currentUp()
                        break
                    }
                }
            } finally {
                if (!released) currentCancel()
            }
        }
    }
}
