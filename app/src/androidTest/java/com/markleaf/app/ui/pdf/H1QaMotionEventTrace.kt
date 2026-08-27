package com.markleaf.app.ui.pdf

import android.app.Instrumentation
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent

internal data class H1NormalizedPointer(
    val id: Int,
    val x: Float,
    val y: Float,
)

/** Deterministic QA-only multi-pointer MotionEvent traces backed by UiAutomation injection. */
internal class H1QaMotionEventTrace(
    private val instrumentation: Instrumentation,
    private val viewport: Rect,
    private val frameIntervalMs: Long = 16L,
    private val displayId: Int = 0,
) {
    private var downTimeMs = 0L
    private var eventTimeMs = 0L

    fun pinch1To2Point5(movingCentroid: Boolean = false) {
        val leftStart = H1NormalizedPointer(0, 0.42f, 0.36f)
        val rightStart = H1NormalizedPointer(1, 0.58f, 0.36f)
        begin(leftStart)
        pointerDown(listOf(leftStart, rightStart), actionIndex = 1)
        repeat(20) { index ->
            val t = (index + 1) / 20f
            val center = 0.5f + if (movingCentroid) 0.06f * t else 0f
            val halfSpan = 0.08f + 0.12f * t
            move(
                listOf(
                    H1NormalizedPointer(0, center - halfSpan, 0.36f + 0.04f * t),
                    H1NormalizedPointer(1, center + halfSpan, 0.36f + 0.04f * t),
                ),
            )
        }
        val leftEnd = H1NormalizedPointer(0, if (movingCentroid) 0.36f else 0.30f, 0.40f)
        val rightEnd = H1NormalizedPointer(1, if (movingCentroid) 0.76f else 0.70f, 0.40f)
        pointerUp(listOf(leftEnd, rightEnd), actionIndex = 1)
        end(leftEnd)
    }

    fun pinchMovingCentroid() = pinch1To2Point5(movingCentroid = true)

    /** Deterministically clamps any restored zoom session back to 1x with a fixed centroid. */
    fun pinchToOne() {
        val leftStart = H1NormalizedPointer(0, 0.25f, 0.40f)
        val rightStart = H1NormalizedPointer(1, 0.75f, 0.40f)
        begin(leftStart)
        pointerDown(listOf(leftStart, rightStart), actionIndex = 1)
        repeat(16) { index ->
            val t = (index + 1) / 16f
            move(
                listOf(
                    H1NormalizedPointer(0, 0.25f + 0.20f * t, 0.40f),
                    H1NormalizedPointer(1, 0.75f - 0.20f * t, 0.40f),
                ),
            )
        }
        val leftEnd = H1NormalizedPointer(0, 0.45f, 0.40f)
        val rightEnd = H1NormalizedPointer(1, 0.55f, 0.40f)
        pointerUp(listOf(leftEnd, rightEnd), actionIndex = 1)
        end(leftEnd)
    }

    /** Keeps both pointers down while assertions inspect production logs before ACTION_UP. */
    fun pinchHoldThreeSeconds(beforeRelease: () -> Unit) {
        val left = H1NormalizedPointer(0, 0.38f, 0.40f)
        val right = H1NormalizedPointer(1, 0.62f, 0.40f)
        begin(left)
        pointerDown(listOf(left, right), actionIndex = 1)
        repeat(90) { index ->
            val t = ((index + 1).coerceAtMost(30)) / 30f
            val drift = (index % 12) * 0.0008f
            move(
                listOf(
                    H1NormalizedPointer(0, 0.38f - 0.13f * t + drift, 0.40f),
                    H1NormalizedPointer(1, 0.62f + 0.13f * t + drift, 0.40f),
                ),
            )
        }
        val leftEnd = H1NormalizedPointer(0, 0.25f, 0.40f)
        val rightEnd = H1NormalizedPointer(1, 0.75f, 0.40f)
        try {
            beforeRelease()
        } finally {
            pointerUp(listOf(leftEnd, rightEnd), actionIndex = 1)
            end(leftEnd)
        }
    }

    fun zoomedEdgeDragLeft() = singleSwipe(0.875f, 0.45f, 0.125f, 0.45f)

    fun zoomedEdgeDragRight() = singleSwipe(0.15f, 0.45f, 0.90f, 0.45f)

    fun verticalEdgeBounce() {
        singleSwipe(0.50f, 0.78f, 0.50f, 0.18f, frames = 32)
        singleSwipe(0.50f, 0.18f, 0.50f, 0.82f, frames = 32)
    }

    fun zoomedPageHandoff() =
        singleSwipe(0.82f, 0.45f, 0.08f, 0.45f, frames = 36, accelerating = true)

    /** R22.3: accelerated PREVIOUS-page handoff (mirror of [zoomedPageHandoff]). */
    fun zoomedPageHandoffBack() =
        singleSwipe(0.18f, 0.45f, 0.92f, 0.45f, frames = 36, accelerating = true)

    fun cancelledEdgeHandoff() =
        singleSwipe(0.55f, 0.45f, 0.49f, 0.45f, frames = 14)

    fun pointer2To1To2() {
        val first = H1NormalizedPointer(0, 0.42f, 0.40f)
        val second = H1NormalizedPointer(1, 0.58f, 0.40f)
        begin(first)
        pointerDown(listOf(first, second), 1)
        repeat(10) { index ->
            val t = (index + 1) / 10f
            move(
                listOf(
                    H1NormalizedPointer(0, 0.42f - 0.08f * t, 0.40f),
                    H1NormalizedPointer(1, 0.58f + 0.08f * t, 0.40f),
                ),
            )
        }
        val firstExpanded = H1NormalizedPointer(0, 0.34f, 0.40f)
        val secondExpanded = H1NormalizedPointer(1, 0.66f, 0.40f)
        pointerUp(listOf(firstExpanded, secondExpanded), 1)
        repeat(6) { index ->
            move(listOf(H1NormalizedPointer(0, 0.34f + 0.015f * (index + 1), 0.40f)))
        }
        val firstMoved = H1NormalizedPointer(0, 0.43f, 0.40f)
        val replacement = H1NormalizedPointer(2, 0.75f, 0.40f)
        pointerDown(listOf(firstMoved, replacement), 1)
        repeat(8) { index ->
            val t = (index + 1) / 8f
            move(
                listOf(
                    H1NormalizedPointer(0, 0.43f - 0.05f * t, 0.40f),
                    H1NormalizedPointer(2, 0.75f + 0.05f * t, 0.40f),
                ),
            )
        }
        val firstEnd = H1NormalizedPointer(0, 0.38f, 0.40f)
        val replacementEnd = H1NormalizedPointer(2, 0.80f, 0.40f)
        pointerUp(listOf(firstEnd, replacementEnd), 1)
        end(firstEnd)
    }

    fun singleSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        frames: Int = 24,
        accelerating: Boolean = false,
    ) {
        val start = H1NormalizedPointer(0, startX, startY)
        begin(start)
        repeat(frames) { index ->
            val linear = (index + 1) / frames.toFloat()
            val t = if (accelerating) linear * linear else linear
            move(
                listOf(
                    H1NormalizedPointer(
                        0,
                        startX + (endX - startX) * t,
                        startY + (endY - startY) * t,
                    ),
                ),
            )
        }
        end(H1NormalizedPointer(0, endX, endY))
    }

    /** Finishes with a stationary tail so the measured transform contains drag, not fling. */
    fun singleDragWithStationaryTail(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        frames: Int = 20,
        stationaryFrames: Int = 10,
    ) {
        val start = H1NormalizedPointer(0, startX, startY)
        val end = H1NormalizedPointer(0, endX, endY)
        begin(start)
        repeat(frames) { index ->
            val t = (index + 1) / frames.toFloat()
            move(
                listOf(
                    H1NormalizedPointer(
                        0,
                        startX + (endX - startX) * t,
                        startY + (endY - startY) * t,
                    ),
                ),
            )
        }
        repeat(stationaryFrames) { move(listOf(end)) }
        end(end)
    }

    /** Holds the last drag frame so tests can inspect the live strip before ACTION_UP. */
    fun singleDragHold(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        frames: Int = 20,
        beforeRelease: () -> Unit,
    ) {
        val start = H1NormalizedPointer(0, startX, startY)
        val end = H1NormalizedPointer(0, endX, endY)
        begin(start)
        repeat(frames) { index ->
            val t = (index + 1) / frames.toFloat()
            move(
                listOf(
                    H1NormalizedPointer(
                        0,
                        startX + (endX - startX) * t,
                        startY + (endY - startY) * t,
                    ),
                ),
            )
        }
        try {
            beforeRelease()
        } finally {
            end(end)
        }
    }

    /**
     * Keeps ACTION_DOWN active across an arbitrary X path so QA can cross the commit threshold,
     * reverse slightly, and inspect every production frame before ACTION_UP.
     */
    fun singleDragHoldWithPath(
        startX: Float,
        startY: Float,
        pathX: List<Float>,
        allowOutsideViewport: Boolean = false,
        onFrame: (frame: Int, pointerX: Float) -> Unit = { _, _ -> },
        beforeRelease: () -> Unit,
    ) {
        require(pathX.isNotEmpty())
        val start = H1NormalizedPointer(0, startX, startY)
        begin(start)
        pathX.forEachIndexed { index, x ->
            move(
                listOf(H1NormalizedPointer(0, x, startY)),
                clampToViewport = !allowOutsideViewport,
            )
            onFrame(index + 1, x)
        }
        val end = H1NormalizedPointer(0, pathX.last(), startY)
        try {
            beforeRelease()
        } finally {
            end(end, clampToViewport = !allowOutsideViewport)
        }
    }

    /** Samples real display frames while the pointer remains down. */
    fun singleDragSampledHold(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        frames: Int,
        sampleFrames: Set<Int>,
        onSample: (frame: Int, expectedOffsetFraction: Float) -> Unit,
        beforeRelease: () -> Unit,
    ) {
        val start = H1NormalizedPointer(0, startX, startY)
        val end = H1NormalizedPointer(0, endX, endY)
        begin(start)
        repeat(frames) { index ->
            val frame = index + 1
            val t = frame / frames.toFloat()
            move(
                listOf(
                    H1NormalizedPointer(
                        0,
                        startX + (endX - startX) * t,
                        startY + (endY - startY) * t,
                    ),
                ),
            )
            if (frame in sampleFrames) onSample(frame, (endX - startX) * t)
        }
        try {
            beforeRelease()
        } finally {
            end(end)
        }
    }

    private fun begin(pointer: H1NormalizedPointer) {
        downTimeMs = SystemClock.uptimeMillis()
        eventTimeMs = downTimeMs
        inject(MotionEvent.ACTION_DOWN, 0, listOf(pointer))
    }

    private fun pointerDown(pointers: List<H1NormalizedPointer>, actionIndex: Int) {
        advance()
        inject(MotionEvent.ACTION_POINTER_DOWN, actionIndex, pointers)
    }

    private fun move(
        pointers: List<H1NormalizedPointer>,
        clampToViewport: Boolean = true,
    ) {
        advance()
        inject(MotionEvent.ACTION_MOVE, 0, pointers, clampToViewport)
    }

    private fun pointerUp(pointers: List<H1NormalizedPointer>, actionIndex: Int) {
        advance()
        inject(MotionEvent.ACTION_POINTER_UP, actionIndex, pointers)
    }

    private fun end(pointer: H1NormalizedPointer, clampToViewport: Boolean = true) {
        advance()
        inject(MotionEvent.ACTION_UP, 0, listOf(pointer), clampToViewport)
    }

    private fun advance() {
        SystemClock.sleep(frameIntervalMs)
        eventTimeMs += frameIntervalMs
    }

    private fun inject(
        actionMasked: Int,
        actionIndex: Int,
        pointers: List<H1NormalizedPointer>,
        clampToViewport: Boolean = true,
    ) {
        val properties = Array(pointers.size) { index ->
            MotionEvent.PointerProperties().apply {
                id = pointers[index].id
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val coords = Array(pointers.size) { index ->
            val point = pointers[index]
            MotionEvent.PointerCoords().apply {
                val normalizedX = if (clampToViewport) point.x.coerceIn(0f, 1f) else point.x
                val normalizedY = if (clampToViewport) point.y.coerceIn(0f, 1f) else point.y
                x = viewport.left + normalizedX * viewport.width()
                y = viewport.top + normalizedY * viewport.height()
                pressure = 1f
                size = 1f
            }
        }
        val action = actionMasked or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        val event = MotionEvent.obtain(
            downTimeMs,
            eventTimeMs,
            action,
            pointers.size,
            properties,
            coords,
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0,
        )
        try {
            android.view.InputEvent::class.java
                .getDeclaredMethod("setDisplayId", Int::class.javaPrimitiveType)
                .apply { isAccessible = true }
                .invoke(event, displayId)
            check(instrumentation.uiAutomation.injectInputEvent(event, true)) {
                "MotionEvent injection failed action=$actionMasked pointers=${pointers.map { it.id }}"
            }
        } finally {
            event.recycle()
        }
    }
}
