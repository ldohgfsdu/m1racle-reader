/*
 * Copyright (C) 2026 sky-shunfengjun
 *
 * SPDX-License-Identifier: GPL-3.0-only AND Apache-2.0
 *
 * This file is part of RiseDiary. RiseDiary's own code is licensed
 * under GPL-3.0-only (see LICENSE).
 *
 * Portions of this file are adapted and modified from AndroidLiquidGlass
 * by Kyant0 (https://github.com/Kyant0/AndroidLiquidGlass), which is
 * licensed under the Apache License, Version 2.0. Those portions remain
 * subject to Apache-2.0 (see LICENSES/Apache-2.0.txt).
 */
package com.m1racle.reader.ui.hub.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/** One owner per animation; a new press cancels the previous settle. */
internal class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val acceptGesture: (Offset, IntSize) -> Boolean = { _, _ -> true },
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDragCancelled: DampedDragAnimation.() -> Unit = onDragStopped,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
    private val consumeDragChanges: Boolean = false,
    private val consumeInitialDown: Boolean = true,
    /** Press-progress motion; defaults are the production spring. QA variant 2 overrides. */
    private val pressInSpec: AnimationSpec<Float> = DefaultPressSpec,
    private val pressOutSpec: AnimationSpec<Float> = DefaultPressSpec,
) {
    companion object {
        val DefaultPressSpec: AnimationSpec<Float> = spring(1f, 1000f)
    }

    var value by mutableFloatStateOf(initialValue)
        private set
    private val deformationAnimation = Animatable(0f, 0.001f)
    private val pressAnimation = Animatable(0f, 0.001f)
    private val xAnimation = Animatable(initialScale, 0.001f)
    private val yAnimation = Animatable(initialScale, 0.001f)
    private val tracker = VelocityTracker()
    private var positionJob: Job? = null
    private var deformationJob: Job? = null
    private var feedbackJob: Job? = null
    private var requestedValue = initialValue
    var isPressed by mutableStateOf(false)
        private set
    val targetValue get() = requestedValue
    val pressProgress get() = pressAnimation.value
    val deformation get() = deformationAnimation.value
    val scaleX get() = xAnimation.value
    val scaleY get() = yAnimation.value

    val modifier = Modifier.pointerInput(this) {
        var accepted = false
        try {
            inspectDragGestures(
                onDragStart = { down ->
                    accepted = acceptGesture(down.position, size)
                    if (accepted) {
                    press()
                    tracker.resetTracking()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    onDragStarted(down.position)
                    }
                },
                onDragEnd = {
                    if (accepted) {
                    isPressed = false
                    onDragStopped()
                    release()
                    }
                },
                onDragCancel = {
                    if (accepted) {
                    isPressed = false
                    onDragCancelled()
                    release()
                    }
                },
            ) { change, amount ->
                if (accepted) {
                tracker.addPosition(change.uptimeMillis, change.position)
                onDrag(size, amount)
                if (amount != Offset.Zero) {
                    val slotWidth = size.width.coerceAtLeast(1) /
                        (valueRange.endInclusive - valueRange.start + 1f)
                    animateDeformation(dragDeformationTarget(tracker.calculateVelocity().x / slotWidth))
                }
                if (shouldConsumeDragChange(consumeDragChanges, consumeInitialDown, amount != Offset.Zero)) {
                    change.consume()
                }
                }
            }
        } finally {
            if (isPressed) {
                isPressed = false
                onDragCancelled()
                release()
            }
        }
    }

    fun press() {
        isPressed = true
        positionJob?.cancel()
        feedbackJob?.cancel()
        feedbackJob = animationScope.launch {
            launch { pressAnimation.animateTo(1f, pressInSpec) }
            launch { xAnimation.animateTo(pressedScale, spring(0.5f, 450f)) }
            launch { yAnimation.animateTo(pressedScale, spring(0.6f, 450f)) }
        }
    }

    fun release() {
        isPressed = false
        animateDeformation(0f)
        feedbackJob?.cancel()
        feedbackJob = animationScope.launch {
            launch { pressAnimation.animateTo(0f, pressOutSpec) }
            launch { xAnimation.animateTo(initialScale, spring(0.42f, 400f)) }
            launch { yAnimation.animateTo(initialScale, spring(0.5f, 400f)) }
        }
    }

    fun updateValue(value: Float) {
        requestedValue = value.coerceIn(valueRange)
        positionJob?.cancel()
        // Pointer movement owns position synchronously; springs are only for settling.
        this.value = requestedValue
    }

    fun animateToValue(value: Float) {
        requestedValue = value.coerceIn(valueRange)
        positionJob?.cancel()
        positionJob = animationScope.launch {
            animate(this@DampedDragAnimation.value, requestedValue,
                animationSpec = spring(1f, 1000f, visibilityThreshold)) { position, _ ->
                this@DampedDragAnimation.value = position
            }
        }
        if (!isPressed) release()
    }

    private fun animateDeformation(target: Float) {
        val incomingVelocity = deformationAnimation.velocity
        deformationJob?.cancel()
        deformationJob = animationScope.launch {
            deformationAnimation.animateTo(target, spring(0.55f, 1400f),
                initialVelocity = incomingVelocity)
            // A signed spring can squash past the resting shape and bounce back.
            // It also relaxes when a held pointer stops sending movement events.
            if (target != 0f) deformationAnimation.animateTo(0f, spring(0.4f, 600f))
        }
    }
}

/** Input speed is symmetric, but spring displacement stays signed for rebound. */
internal fun dragDeformationTarget(slotsPerSecond: Float): Float =
    (abs(slotsPerSecond) / 6f).coerceIn(0f, 1f)

internal fun opticalDeformationX(deformation: Float): Float =
    1f + 0.35f * deformation.coerceIn(-0.4f, 1.3f)

internal fun opticalDeformationY(deformation: Float): Float =
    1f - 0.18f * deformation.coerceIn(-0.4f, 1.3f)

internal fun shouldConsumeDragChange(
    consumeDragChanges: Boolean,
    consumeInitialDown: Boolean,
    hasMovement: Boolean,
): Boolean = consumeDragChanges && (consumeInitialDown || hasMovement)
