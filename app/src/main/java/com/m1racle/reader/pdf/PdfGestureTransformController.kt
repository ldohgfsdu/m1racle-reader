package com.m1racle.reader.pdf

/** One production pointer/transform lifecycle shared by both PDF viewport adapters. */
enum class PdfGesturePhase { IDLE, DRAGGING, PINCHING, FLINGING, SPRINGING }

data class PdfGestureControllerSnapshot(
    val transform: ZoomPanTransform,
    val phase: PdfGesturePhase,
    val pointerEpoch: ZoomPanPointerEpoch?,
    val motionGeneration: Long,
)

data class PdfGestureControllerFrame(
    val snapshot: PdfGestureControllerSnapshot,
    val rebased: Boolean,
    val rawCentroidDeltaPx: ZoomPanPoint,
    val legalConsumedPx: ZoomPanPoint,
    val residualPx: ZoomPanPoint,
    val rawScaleFactor: Float,
    val focalAnchorDriftPx: Float,
)

data class PdfGesturePlatformFlingFrame(
    val snapshot: PdfGestureControllerSnapshot,
    val requestedDeltaPx: ZoomPanPoint,
    val appliedDeltaPx: ZoomPanPoint,
)

data class PdfGestureSpringFrame(
    val snapshot: PdfGestureControllerSnapshot,
    val settled: Boolean,
)

/**
 * Owns stable pointer ids, centroid/span history, focal anchoring and animation cancellation.
 *
 * Geometry and edge handoff remain adapter responsibilities. Adapters provide the transform origin,
 * legal bounds and overscroll policy, but never interpret pointer indices or implement pinch math.
 */
class PdfGestureTransformController(
    initialTransform: ZoomPanTransform = ZoomPanTransform(),
) {
    private var transform = initialTransform
    private var pointerEpoch: ZoomPanPointerEpoch? = null
    private var previousPointers: Map<Long, ZoomPanPoint> = emptyMap()
    private var phase: PdfGesturePhase = PdfGesturePhase.IDLE
    private var motionGeneration: Long = 0L
    private var springState: ZoomPanSpringState? = null

    fun snapshot(): PdfGestureControllerSnapshot = PdfGestureControllerSnapshot(
        transform = transform,
        phase = phase,
        pointerEpoch = pointerEpoch,
        motionGeneration = motionGeneration,
    )

    /** A new pointer lifecycle synchronously invalidates fling/spring/settle ownership. */
    fun beginGesture(
        currentTransform: ZoomPanTransform,
        initialPointers: List<ZoomPanPointer> = emptyList(),
        transformOriginViewportPx: ZoomPanPoint = ZoomPanPoint.Zero,
        boundsForScale: (Float) -> ZoomPanBounds,
        scaleRange: ZoomPanRange = ZoomPanPhysicsProfile.ScaleRange,
        overscrollPolicy: AxisOverscrollPolicy,
    ): PdfGestureControllerFrame {
        cancelMotion(currentTransform)
        phase = PdfGesturePhase.DRAGGING
        pointerEpoch = null
        previousPointers = emptyMap()
        return pointerFrame(
            pointers = initialPointers,
            transformOriginViewportPx = transformOriginViewportPx,
            boundsForScale = boundsForScale,
            scaleRange = scaleRange,
            overscrollPolicy = overscrollPolicy,
        )
    }

    fun pointerFrame(
        pointers: List<ZoomPanPointer>,
        transformOriginViewportPx: ZoomPanPoint,
        boundsForScale: (Float) -> ZoomPanBounds,
        scaleRange: ZoomPanRange = ZoomPanPhysicsProfile.ScaleRange,
        overscrollPolicy: AxisOverscrollPolicy,
    ): PdfGestureControllerFrame = reducePointerFrame(
        pointers = pointers,
        transformOriginViewportPx = transformOriginViewportPx,
        boundsForScale = boundsForScale,
        scaleRange = scaleRange,
        overscrollPolicy = overscrollPolicy,
        applyTransform = true,
    )

    /**
     * Advances stable pointer-id/centroid/span history while another adapter owns movement.
     *
     * Adapters may use this only when another visual surface owns movement. The shared pointer
     * lifecycle remains continuous while hidden overscroll is kept out of a later 1->2 rebase.
     */
    fun trackPointersWithoutTransform(
        pointers: List<ZoomPanPointer>,
        transformOriginViewportPx: ZoomPanPoint,
        boundsForScale: (Float) -> ZoomPanBounds,
        scaleRange: ZoomPanRange = ZoomPanPhysicsProfile.ScaleRange,
        overscrollPolicy: AxisOverscrollPolicy,
    ): PdfGestureControllerFrame = reducePointerFrame(
        pointers = pointers,
        transformOriginViewportPx = transformOriginViewportPx,
        boundsForScale = boundsForScale,
        scaleRange = scaleRange,
        overscrollPolicy = overscrollPolicy,
        applyTransform = false,
    )

    private fun reducePointerFrame(
        pointers: List<ZoomPanPointer>,
        transformOriginViewportPx: ZoomPanPoint,
        boundsForScale: (Float) -> ZoomPanBounds,
        scaleRange: ZoomPanRange,
        overscrollPolicy: AxisOverscrollPolicy,
        applyTransform: Boolean,
    ): PdfGestureControllerFrame {
        val before = transform
        val previousEpoch = pointerEpoch
        val rawCentroidDelta = centroidDelta(previousPointers, pointers)
        val reduced = ZoomPanPointerEpochReducer.frame(
            previous = previousEpoch,
            pointers = pointers,
            transform = before,
            transformOriginViewportPx = transformOriginViewportPx,
            boundsForScale = boundsForScale,
            scaleRange = scaleRange,
            overscrollPolicy = overscrollPolicy,
        )
        pointerEpoch = reduced.epoch
        if (applyTransform) transform = reduced.transform
        previousPointers = pointers.associate { it.id to it.positionPx }
        phase = when (reduced.epoch?.mode) {
            ZoomPanPointerMode.TWO_POINTER_PINCH -> PdfGesturePhase.PINCHING
            ZoomPanPointerMode.SINGLE_PAN -> PdfGesturePhase.DRAGGING
            null -> PdfGesturePhase.IDLE
        }
        val legalConsumed = transform.legalOffsetPx - before.legalOffsetPx
        val rawScaleFactor = if (before.scale > 0f) transform.scale / before.scale else 1f
        val residual = if (!applyTransform || reduced.rebased || phase == PdfGesturePhase.PINCHING) {
            ZoomPanPoint.Zero
        } else {
            rawCentroidDelta - legalConsumed
        }
        val focalAnchorDrift = if (
            applyTransform && !reduced.rebased && reduced.epoch?.mode == ZoomPanPointerMode.TWO_POINTER_PINCH
        ) {
            val epoch = reduced.epoch
            val selected = pointers.filter {
                it.id == epoch.primaryPointerId || it.id == epoch.secondaryPointerId
            }
            if (selected.size == 2) {
                val centroid = ZoomPanPoint(
                    (selected[0].positionPx.x + selected[1].positionPx.x) / 2f,
                    (selected[0].positionPx.y + selected[1].positionPx.y) / 2f,
                )
                val projected = projectAnchor(
                    epoch.focalAnchor,
                    transform,
                    transformOriginViewportPx,
                )
                kotlin.math.hypot(
                    (projected.x - centroid.x).toDouble(),
                    (projected.y - centroid.y).toDouble(),
                ).toFloat()
            } else {
                0f
            }
        } else {
            0f
        }
        return PdfGestureControllerFrame(
            snapshot = snapshot(),
            rebased = reduced.rebased,
            rawCentroidDeltaPx = if (reduced.rebased) ZoomPanPoint.Zero else rawCentroidDelta,
            legalConsumedPx = if (reduced.rebased) ZoomPanPoint.Zero else legalConsumed,
            residualPx = residual,
            rawScaleFactor = if (reduced.rebased) 1f else rawScaleFactor,
            focalAnchorDriftPx = focalAnchorDrift,
        )
    }

    fun endGesture(): PdfGestureControllerSnapshot {
        pointerEpoch = null
        previousPointers = emptyMap()
        // The adapter may synchronously hand ownership to a post-release fling/spring before the
        // pointer lifecycle's finally block runs. Teardown clears pointer identity, but must not
        // revoke that newly-started motion generation. A later pointer-down cancels it through
        // beginGesture -> cancelMotion.
        if (phase == PdfGesturePhase.DRAGGING || phase == PdfGesturePhase.PINCHING) {
            phase = PdfGesturePhase.IDLE
        }
        return snapshot()
    }

    fun replaceTransform(next: ZoomPanTransform): PdfGestureControllerSnapshot {
        transform = next
        return snapshot()
    }

    fun cancelMotion(currentTransform: ZoomPanTransform = transform): Long {
        motionGeneration += 1L
        transform = currentTransform
        springState = null
        if (phase == PdfGesturePhase.FLINGING || phase == PdfGesturePhase.SPRINGING) {
            phase = PdfGesturePhase.IDLE
        }
        return motionGeneration
    }

    fun beginPlatformFling(currentTransform: ZoomPanTransform = transform): Long {
        val generation = cancelMotion(currentTransform)
        phase = PdfGesturePhase.FLINGING
        return generation
    }

    fun applyPlatformFlingDelta(
        generation: Long,
        deltaPx: ZoomPanPoint,
        bounds: ZoomPanBounds,
        overscrollPolicy: AxisOverscrollPolicy,
    ): PdfGesturePlatformFlingFrame? {
        if (generation != motionGeneration || phase != PdfGesturePhase.FLINGING) return null
        val before = transform
        transform = ZoomPanPhysics.applyPan(
            transform = transform,
            rawDeltaPx = deltaPx,
            bounds = bounds,
            policy = overscrollPolicy,
        ).transform
        return PdfGesturePlatformFlingFrame(
            snapshot = snapshot(),
            requestedDeltaPx = deltaPx,
            appliedDeltaPx = transform.legalOffsetPx - before.legalOffsetPx,
        )
    }

    fun beginSpring(currentTransform: ZoomPanTransform = transform): Long {
        val generation = cancelMotion(currentTransform)
        springState = ZoomPanSpringState(currentTransform.visualOverscrollPx)
        phase = PdfGesturePhase.SPRINGING
        return generation
    }

    fun stepSpring(
        generation: Long,
        elapsedNanos: Long,
        config: ZoomPanSpringConfig,
        overscrollPolicy: AxisOverscrollPolicy,
    ): PdfGestureSpringFrame? {
        if (generation != motionGeneration || phase != PdfGesturePhase.SPRINGING) return null
        val step = ZoomPanPhysics.springStep(
            spring = springState ?: ZoomPanSpringState(transform.visualOverscrollPx),
            elapsedNanos = elapsedNanos,
            config = config,
        )
        springState = step.state
        transform = ZoomPanPhysics.withVisualOverscroll(
            transform = transform,
            visualOverscrollPx = step.state.positionPx,
            policy = overscrollPolicy,
        )
        return PdfGestureSpringFrame(snapshot(), step.settled)
    }

    fun finishMotion(generation: Long, settleOverscroll: Boolean): PdfGestureControllerSnapshot? {
        if (generation != motionGeneration) return null
        if (settleOverscroll) transform = ZoomPanPhysics.springTarget(transform)
        springState = null
        phase = PdfGesturePhase.IDLE
        return snapshot()
    }

    private fun centroidDelta(
        previous: Map<Long, ZoomPanPoint>,
        current: List<ZoomPanPointer>,
    ): ZoomPanPoint {
        if (previous.isEmpty() || current.isEmpty()) return ZoomPanPoint.Zero
        val shared = current.filter { it.id in previous }
        if (shared.isEmpty()) return ZoomPanPoint.Zero
        val currentCentroid = ZoomPanPoint(
            shared.sumOf { it.positionPx.x.toDouble() }.toFloat() / shared.size,
            shared.sumOf { it.positionPx.y.toDouble() }.toFloat() / shared.size,
        )
        val previousCentroid = ZoomPanPoint(
            shared.sumOf { previous.getValue(it.id).x.toDouble() }.toFloat() / shared.size,
            shared.sumOf { previous.getValue(it.id).y.toDouble() }.toFloat() / shared.size,
        )
        return currentCentroid - previousCentroid
    }

    private fun projectAnchor(
        anchor: ZoomPanFocalAnchor,
        target: ZoomPanTransform,
        origin: ZoomPanPoint,
    ): ZoomPanPoint = ZoomPanPoint(
        origin.x + anchor.contentX * target.scale + target.legalOffsetPx.x,
        origin.y + anchor.contentY * target.scale + target.legalOffsetPx.y,
    )
}
