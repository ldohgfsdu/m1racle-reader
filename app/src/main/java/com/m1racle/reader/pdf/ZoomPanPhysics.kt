package com.m1racle.reader.pdf

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt

/** Product zoom ceiling shared without depending on either mode's geometry owner. */
const val MAX_ZOOM_PAN_SCALE = 4f

/** A viewport-pixel vector kept independent of Compose runtime/state types. */
data class ZoomPanPoint(
    val x: Float,
    val y: Float,
) {
    operator fun plus(other: ZoomPanPoint) = ZoomPanPoint(x + other.x, y + other.y)
    operator fun minus(other: ZoomPanPoint) = ZoomPanPoint(x - other.x, y - other.y)
    operator fun times(value: Float) = ZoomPanPoint(x * value, y * value)

    companion object {
        val Zero = ZoomPanPoint(0f, 0f)
    }
}

/** Legal content-translation bounds in viewport pixels. */
data class ZoomPanBounds(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
)

/**
 * Shared zoom/pan transform. Legal and display-only coordinates never overlap.
 *
 * Content translation is [legalOffsetPx] + [visualOverscrollPx]. Scale is applied around the
 * mode adapter's transform origin; neither translation is multiplied by scale again.
 */
data class ZoomPanTransform(
    val scale: Float = 1f,
    val legalOffsetPx: ZoomPanPoint = ZoomPanPoint.Zero,
    val rawOverscrollPx: ZoomPanPoint = ZoomPanPoint.Zero,
    val visualOverscrollPx: ZoomPanPoint = ZoomPanPoint.Zero,
) {
    val displayOffsetPx: ZoomPanPoint get() = legalOffsetPx + visualOverscrollPx
}

/** Product axis policy; it changes capability, never the shared pan math or parameters. */
data class AxisOverscrollPolicy(
    val horizontalEnabled: Boolean,
    val verticalEnabled: Boolean,
    val maxDistancePx: Float,
    val horizontalMaxDistancePx: Float = maxDistancePx,
    val verticalMaxDistancePx: Float = maxDistancePx,
) {
    internal fun enabled(axis: ZoomPanAxis): Boolean = when (axis) {
        ZoomPanAxis.X -> horizontalEnabled
        ZoomPanAxis.Y -> verticalEnabled
    }

    internal fun maxDistance(axis: ZoomPanAxis): Float = when (axis) {
        ZoomPanAxis.X -> horizontalMaxDistancePx
        ZoomPanAxis.Y -> verticalMaxDistancePx
    }.coerceAtLeast(0f)

    companion object {
        val Disabled = AxisOverscrollPolicy(false, false, 0f)
    }
}

data class ZoomPanRange(
    val minScale: Float,
    val maxScale: Float,
)

data class ZoomPanFocalAnchor(
    val contentX: Float,
    val contentY: Float,
)

data class ZoomPanPanResult(
    val transform: ZoomPanTransform,
    val legalConsumedPx: ZoomPanPoint,
    val residualPx: ZoomPanPoint,
)

/** Pure spring state; adapters own clocks/coroutines while the kernel owns integration. */
data class ZoomPanSpringState(
    val positionPx: ZoomPanPoint,
    val velocityPxPerSecond: ZoomPanPoint = ZoomPanPoint.Zero,
)

data class ZoomPanSpringConfig(
    val stiffness: Float,
    val dampingRatio: Float,
    val maxFrameSeconds: Float = 0.032f,
    val integrationStepSeconds: Float = 0.008f,
    val stopDistancePx: Float = 0.35f,
    val stopVelocityPxPerSecond: Float = 5f,
)

data class ZoomPanSpringStep(
    val state: ZoomPanSpringState,
    val settled: Boolean,
)

/**
 * The actual R7 continuous-viewport motion parameters, extracted without retuning.
 *
 * Direct contact is viewport-pixel 1:1 in legal bounds. Post-release motion is intentionally not
 * parameterized here: both adapters use Android's platform spline through PlatformPdfFlingRunner.
 */
object ZoomPanPhysicsProfile {
    val ScaleRange = ZoomPanRange(minScale = 1f, maxScale = MAX_ZOOM_PAN_SCALE)
    val ContinuousOverscrollPolicy = AxisOverscrollPolicy.Disabled
    val HorizontalSpring = ZoomPanSpringConfig(
        stiffness = 420f,
        dampingRatio = 0.82f,
    )
}

/** Pure viewport-pixel zoom/pan math shared by continuous and horizontal adapters. */
object ZoomPanPhysics {
    fun clampScale(scale: Float, range: ZoomPanRange): Float =
        scale.coerceIn(range.minScale, range.maxScale)

    /** Legal motion is always consumed 1:1; only residual may enter display overscroll. */
    fun applyPan(
        transform: ZoomPanTransform,
        rawDeltaPx: ZoomPanPoint,
        bounds: ZoomPanBounds,
        policy: AxisOverscrollPolicy,
    ): ZoomPanPanResult {
        val x = panAxis(
            legalOffset = transform.legalOffsetPx.x,
            rawOverscroll = transform.rawOverscrollPx.x,
            delta = rawDeltaPx.x,
            minOffset = bounds.minX,
            maxOffset = bounds.maxX,
            overscrollEnabled = policy.enabled(ZoomPanAxis.X),
            maxOverscrollPx = policy.maxDistance(ZoomPanAxis.X),
        )
        val y = panAxis(
            legalOffset = transform.legalOffsetPx.y,
            rawOverscroll = transform.rawOverscrollPx.y,
            delta = rawDeltaPx.y,
            minOffset = bounds.minY,
            maxOffset = bounds.maxY,
            overscrollEnabled = policy.enabled(ZoomPanAxis.Y),
            maxOverscrollPx = policy.maxDistance(ZoomPanAxis.Y),
        )
        return ZoomPanPanResult(
            transform = transform.copy(
                legalOffsetPx = ZoomPanPoint(x.legalOffset, y.legalOffset),
                rawOverscrollPx = ZoomPanPoint(x.rawOverscroll, y.rawOverscroll),
                visualOverscrollPx = ZoomPanPoint(x.visualOverscroll, y.visualOverscroll),
            ),
            legalConsumedPx = ZoomPanPoint(x.legalConsumed, y.legalConsumed),
            residualPx = ZoomPanPoint(x.residual, y.residual),
        )
    }

    /** Captures a page/document point using legal coordinates only. */
    fun captureFocalAnchor(
        transform: ZoomPanTransform,
        focalViewportPx: ZoomPanPoint,
        transformOriginViewportPx: ZoomPanPoint,
    ): ZoomPanFocalAnchor {
        val safeScale = transform.scale.coerceAtLeast(0.0001f)
        return ZoomPanFocalAnchor(
            contentX = (
                focalViewportPx.x - transformOriginViewportPx.x - transform.legalOffsetPx.x
                ) / safeScale,
            contentY = (
                focalViewportPx.y - transformOriginViewportPx.y - transform.legalOffsetPx.y
                ) / safeScale,
        )
    }

    /** Applies one stable content anchor at a new scale/current focal point and clamps once. */
    fun applyScaleFromAnchor(
        transform: ZoomPanTransform,
        newScale: Float,
        anchor: ZoomPanFocalAnchor,
        focalViewportPx: ZoomPanPoint,
        transformOriginViewportPx: ZoomPanPoint,
        newBounds: ZoomPanBounds,
        scaleRange: ZoomPanRange,
    ): ZoomPanTransform {
        val scale = clampScale(newScale, scaleRange)
        val desiredX = focalViewportPx.x - transformOriginViewportPx.x - anchor.contentX * scale
        val desiredY = focalViewportPx.y - transformOriginViewportPx.y - anchor.contentY * scale
        return ZoomPanTransform(
            scale = scale,
            legalOffsetPx = ZoomPanPoint(
                desiredX.coerceIn(newBounds.minX, newBounds.maxX),
                desiredY.coerceIn(newBounds.minY, newBounds.maxY),
            ),
        )
    }

    /** Convenience for a stationary focal point such as double-tap zoom. */
    fun applyScaleAtFocal(
        transform: ZoomPanTransform,
        newScale: Float,
        focalViewportPx: ZoomPanPoint,
        transformOriginViewportPx: ZoomPanPoint,
        newBounds: ZoomPanBounds,
        scaleRange: ZoomPanRange,
    ): ZoomPanTransform = applyScaleFromAnchor(
        transform = transform,
        newScale = newScale,
        anchor = captureFocalAnchor(transform, focalViewportPx, transformOriginViewportPx),
        focalViewportPx = focalViewportPx,
        transformOriginViewportPx = transformOriginViewportPx,
        newBounds = newBounds,
        scaleRange = scaleRange,
    )

    /** Geometry changes clamp legal position and never fold display overscroll into it. */
    fun clampToBounds(
        transform: ZoomPanTransform,
        bounds: ZoomPanBounds,
    ): ZoomPanTransform = transform.copy(
        legalOffsetPx = ZoomPanPoint(
            transform.legalOffsetPx.x.coerceIn(bounds.minX, bounds.maxX),
            transform.legalOffsetPx.y.coerceIn(bounds.minY, bounds.maxY),
        ),
    )

    /** A transform spring/settle target clears display-only state and preserves legal position. */
    fun springTarget(transform: ZoomPanTransform): ZoomPanTransform = transform.copy(
        rawOverscrollPx = ZoomPanPoint.Zero,
        visualOverscrollPx = ZoomPanPoint.Zero,
    )

    /** Adapter animation frame helper; the pure kernel never starts the animation/coroutine. */
    fun withVisualOverscroll(
        transform: ZoomPanTransform,
        visualOverscrollPx: ZoomPanPoint,
        policy: AxisOverscrollPolicy,
    ): ZoomPanTransform {
        fun visual(value: Float, enabled: Boolean, limit: Float): Float =
            if (enabled) value.coerceIn(-limit, limit) else 0f
        val horizontalLimit = policy.maxDistance(ZoomPanAxis.X)
        val verticalLimit = policy.maxDistance(ZoomPanAxis.Y)
        val visual = ZoomPanPoint(
            visual(visualOverscrollPx.x, policy.horizontalEnabled, horizontalLimit),
            visual(visualOverscrollPx.y, policy.verticalEnabled, verticalLimit),
        )
        return transform.copy(
            rawOverscrollPx = ZoomPanPoint(
                inverseRubberBand(visual.x, horizontalLimit),
                inverseRubberBand(visual.y, verticalLimit),
            ),
            visualOverscrollPx = visual,
        )
    }

    /** Monotonic, symmetric residual mapping that approaches but never exceeds [limitPx]. */
    fun rubberBand(rawOverscrollPx: Float, limitPx: Float): Float {
        val limit = limitPx.coerceAtLeast(0f)
        if (limit == 0f || rawOverscrollPx == 0f) return 0f
        return sign(rawOverscrollPx) * limit *
            (1f - exp(-RUBBER_INITIAL_GAIN * abs(rawOverscrollPx) / limit.coerceAtLeast(1f)))
    }

    /** Semi-implicit, sub-stepped damped spring integration toward zero display overscroll. */
    fun springStep(
        spring: ZoomPanSpringState,
        elapsedNanos: Long,
        config: ZoomPanSpringConfig,
    ): ZoomPanSpringStep {
        val elapsedSeconds = (elapsedNanos.coerceAtLeast(0L) / 1_000_000_000f)
            .coerceAtMost(config.maxFrameSeconds.coerceAtLeast(0.001f))
        if (elapsedSeconds == 0f) return ZoomPanSpringStep(spring, settled = false)
        val integrationStep = config.integrationStepSeconds.coerceAtLeast(0.001f)
        val stepCount = ceil(elapsedSeconds / integrationStep).toInt().coerceAtLeast(1)
        val dt = elapsedSeconds / stepCount
        val stiffness = config.stiffness.coerceAtLeast(0f)
        val damping = 2f * config.dampingRatio.coerceAtLeast(0f) * sqrt(stiffness)

        var position = spring.positionPx
        var velocity = spring.velocityPxPerSecond
        repeat(stepCount) {
            val acceleration = ZoomPanPoint(
                -stiffness * position.x - damping * velocity.x,
                -stiffness * position.y - damping * velocity.y,
            )
            velocity += acceleration * dt
            position += velocity * dt
        }
        val settled = abs(position.x) <= config.stopDistancePx &&
            abs(position.y) <= config.stopDistancePx &&
            abs(velocity.x) <= config.stopVelocityPxPerSecond &&
            abs(velocity.y) <= config.stopVelocityPxPerSecond
        return if (settled) {
            ZoomPanSpringStep(
                ZoomPanSpringState(ZoomPanPoint.Zero, ZoomPanPoint.Zero),
                settled = true,
            )
        } else {
            ZoomPanSpringStep(ZoomPanSpringState(position, velocity), settled = false)
        }
    }

    private data class AxisResult(
        val legalOffset: Float,
        val rawOverscroll: Float,
        val visualOverscroll: Float,
        val legalConsumed: Float,
        val residual: Float,
    )

    private fun panAxis(
        legalOffset: Float,
        rawOverscroll: Float,
        delta: Float,
        minOffset: Float,
        maxOffset: Float,
        overscrollEnabled: Boolean,
        maxOverscrollPx: Float,
    ): AxisResult {
        if (!overscrollEnabled) {
            val legal = (legalOffset + delta).coerceIn(minOffset, maxOffset)
            val consumed = legal - legalOffset
            return AxisResult(legal, 0f, 0f, consumed, delta - consumed)
        }

        var remaining = delta
        var raw = rawOverscroll
        var residual = 0f
        if (raw != 0f && remaining != 0f && sign(raw) != sign(remaining)) {
            val unwind = min(abs(raw), abs(remaining)) * sign(remaining)
            raw += unwind
            remaining -= unwind
            residual += unwind
            if (abs(raw) < 0.0001f) raw = 0f
        }

        var legal = legalOffset
        var legalConsumed = 0f
        if (remaining != 0f) {
            if (raw != 0f && sign(raw) == sign(remaining)) {
                raw += remaining
                residual += remaining
            } else {
                val clamped = (legal + remaining).coerceIn(minOffset, maxOffset)
                legalConsumed = clamped - legal
                val edgeResidual = remaining - legalConsumed
                legal = clamped
                raw += edgeResidual
                residual += edgeResidual
            }
        }
        return AxisResult(
            legalOffset = legal,
            rawOverscroll = raw,
            visualOverscroll = rubberBand(raw, maxOverscrollPx),
            legalConsumed = legalConsumed,
            residual = residual,
        )
    }

    private fun inverseRubberBand(visualOverscrollPx: Float, limitPx: Float): Float {
        if (limitPx <= 0f || visualOverscrollPx == 0f) return 0f
        val ratio = (abs(visualOverscrollPx) / limitPx).coerceIn(0f, 0.999999f)
        return sign(visualOverscrollPx) * -limitPx * ln(1f - ratio) / RUBBER_INITIAL_GAIN
    }

    private const val RUBBER_INITIAL_GAIN = 1.35f
}

data class ZoomPanPointer(
    val id: Long,
    val positionPx: ZoomPanPoint,
)

enum class ZoomPanPointerMode { SINGLE_PAN, TWO_POINTER_PINCH }

data class ZoomPanPointerEpoch(
    val epochId: Long,
    val mode: ZoomPanPointerMode,
    val activePointerIds: List<Long>,
    val primaryPointerId: Long?,
    val secondaryPointerId: Long?,
    val baseTransform: ZoomPanTransform,
    val baseCentroidPx: ZoomPanPoint,
    val baseSpanPx: Float,
    val focalAnchor: ZoomPanFocalAnchor,
)

data class ZoomPanPointerFrame(
    val epoch: ZoomPanPointerEpoch?,
    val transform: ZoomPanTransform,
    val rebased: Boolean,
    val shouldSettle: Boolean = false,
)

/** Stable-pair pointer epoch logic shared without copying either mode's pointer handler. */
object ZoomPanPointerEpochReducer {
    fun frame(
        previous: ZoomPanPointerEpoch?,
        pointers: List<ZoomPanPointer>,
        transform: ZoomPanTransform,
        transformOriginViewportPx: ZoomPanPoint,
        boundsForScale: (Float) -> ZoomPanBounds,
        scaleRange: ZoomPanRange,
        overscrollPolicy: AxisOverscrollPolicy,
    ): ZoomPanPointerFrame {
        if (pointers.isEmpty()) {
            return ZoomPanPointerFrame(
                epoch = null,
                transform = transform,
                rebased = false,
                shouldSettle = previous != null,
            )
        }
        val ids = pointers.map { it.id }.sorted()
        val pointerSetChanged = previous == null || previous.activePointerIds != ids
        val pairMissing = previous?.mode == ZoomPanPointerMode.TWO_POINTER_PINCH &&
            (previous.primaryPointerId !in ids || previous.secondaryPointerId !in ids)
        if (pointerSetChanged || pairMissing) {
            return ZoomPanPointerFrame(
                epoch = rebase(previous, pointers, transform, transformOriginViewportPx),
                transform = transform,
                rebased = true,
            )
        }

        val epoch = previous ?: return ZoomPanPointerFrame(
            rebase(null, pointers, transform, transformOriginViewportPx),
            transform,
            rebased = true,
        )
        val next = when (epoch.mode) {
            ZoomPanPointerMode.SINGLE_PAN -> {
                val pointer = pointers.first { it.id == epoch.primaryPointerId }
                val delta = pointer.positionPx - epoch.baseCentroidPx
                ZoomPanPhysics.applyPan(
                    transform = epoch.baseTransform,
                    rawDeltaPx = delta,
                    bounds = boundsForScale(epoch.baseTransform.scale),
                    policy = overscrollPolicy,
                ).transform
            }

            ZoomPanPointerMode.TWO_POINTER_PINCH -> {
                val a = pointers.first { it.id == epoch.primaryPointerId }
                val b = pointers.first { it.id == epoch.secondaryPointerId }
                val centroid = ZoomPanPoint(
                    (a.positionPx.x + b.positionPx.x) / 2f,
                    (a.positionPx.y + b.positionPx.y) / 2f,
                )
                val span = hypot(
                    a.positionPx.x - b.positionPx.x,
                    a.positionPx.y - b.positionPx.y,
                ).coerceAtLeast(0.0001f)
                val newScale = ZoomPanPhysics.clampScale(
                    epoch.baseTransform.scale * (span / epoch.baseSpanPx.coerceAtLeast(0.0001f)),
                    scaleRange,
                )
                ZoomPanPhysics.applyScaleFromAnchor(
                    transform = epoch.baseTransform,
                    newScale = newScale,
                    anchor = epoch.focalAnchor,
                    focalViewportPx = centroid,
                    transformOriginViewportPx = transformOriginViewportPx,
                    newBounds = boundsForScale(newScale),
                    scaleRange = scaleRange,
                )
            }
        }
        return ZoomPanPointerFrame(epoch, next, rebased = false)
    }

    private fun rebase(
        previous: ZoomPanPointerEpoch?,
        pointers: List<ZoomPanPointer>,
        transform: ZoomPanTransform,
        transformOriginViewportPx: ZoomPanPoint,
    ): ZoomPanPointerEpoch {
        val sorted = pointers.sortedBy { it.id }
        val ids = sorted.map { it.id }
        val priorPair = listOfNotNull(previous?.primaryPointerId, previous?.secondaryPointerId)
        val pair = if (sorted.size >= 2) {
            val retained = priorPair.filter { it in ids }
            (retained + ids.filterNot { it in retained }).take(2)
        } else {
            listOf(sorted.first().id)
        }
        val selected = sorted.filter { it.id in pair }
        val centroid = ZoomPanPoint(
            selected.sumOf { it.positionPx.x.toDouble() }.toFloat() / selected.size,
            selected.sumOf { it.positionPx.y.toDouble() }.toFloat() / selected.size,
        )
        val span = if (selected.size == 2) {
            hypot(
                selected[0].positionPx.x - selected[1].positionPx.x,
                selected[0].positionPx.y - selected[1].positionPx.y,
            )
        } else {
            0f
        }
        return ZoomPanPointerEpoch(
            epochId = (previous?.epochId ?: 0L) + 1L,
            mode = if (selected.size == 2) {
                ZoomPanPointerMode.TWO_POINTER_PINCH
            } else {
                ZoomPanPointerMode.SINGLE_PAN
            },
            activePointerIds = ids,
            primaryPointerId = pair.firstOrNull(),
            secondaryPointerId = pair.getOrNull(1),
            baseTransform = transform,
            baseCentroidPx = centroid,
            baseSpanPx = span,
            focalAnchor = ZoomPanPhysics.captureFocalAnchor(
                transform,
                centroid,
                transformOriginViewportPx,
            ),
        )
    }
}

internal enum class ZoomPanAxis { X, Y }
