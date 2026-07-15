package com.markleaf.app.pdf

import kotlin.math.abs

/** Logical position occupied by one stable physical page surface. */
enum class PageStripSlotRole(val baseMultiplier: Int) {
    PREVIOUS(-1),
    CURRENT(0),
    NEXT(1),
}

/**
 * A physical slot keeps [stableId] while its logical role rotates after a completed settle.
 * This lets the already-visible neighbour become CURRENT without destroying and recreating it.
 */
data class PageStripSlot(
    val stableId: Int,
    val role: PageStripSlotRole,
    val pageIndex: Int?,
)

data class ThreePageStripState(
    val currentPage: Int,
    val pageCount: Int,
    val previousSlotId: Int = 0,
    val currentSlotId: Int = 1,
    val nextSlotId: Int = 2,
) {
    init {
        require(pageCount > 0)
        require(currentPage in 0 until pageCount)
        require(setOf(previousSlotId, currentSlotId, nextSlotId).size == 3)
    }

    fun slots(): List<PageStripSlot> = listOf(
        PageStripSlot(
            previousSlotId,
            PageStripSlotRole.PREVIOUS,
            (currentPage - 1).takeIf { it >= 0 },
        ),
        PageStripSlot(currentSlotId, PageStripSlotRole.CURRENT, currentPage),
        PageStripSlot(
            nextSlotId,
            PageStripSlotRole.NEXT,
            (currentPage + 1).takeIf { it < pageCount },
        ),
    )

    /**
     * Rotate physical surfaces only after the strip has settled to exactly one viewport.
     * The target neighbour keeps the same stable id while its base changes to zero.
     */
    fun committed(direction: HorizontalPageTransitionDirection): ThreePageStripState =
        when (direction) {
            HorizontalPageTransitionDirection.NEXT -> copy(
                currentPage = (currentPage + 1).coerceAtMost(pageCount - 1),
                previousSlotId = currentSlotId,
                currentSlotId = nextSlotId,
                nextSlotId = previousSlotId,
            )
            HorizontalPageTransitionDirection.PREVIOUS -> copy(
                currentPage = (currentPage - 1).coerceAtLeast(0),
                previousSlotId = nextSlotId,
                currentSlotId = previousSlotId,
                nextSlotId = currentSlotId,
            )
        }
}

data class PageStripTransition(
    val gestureGeneration: Long,
    val phase: ZoomedPageTransitionPhase = ZoomedPageTransitionPhase.IDLE,
    val direction: HorizontalPageTransitionDirection? = null,
    val targetPage: Int? = null,
    val releaseVelocityXPxPerSecond: Float = 0f,
) {
    val active: Boolean get() = phase != ZoomedPageTransitionPhase.IDLE
}

data class PageStripDragResult(
    val transition: PageStripTransition,
    val offsetPx: Float,
    val blockedByMissingBase: Boolean = false,
)

/** Pure state/coordinate math for the single-owner three-page strip. */
object ThreePageStripPhysics {
    const val HORIZONTAL_INTENT_RATIO = 1.4f
    const val COMMIT_DISTANCE_FRACTION = 0.5f
    const val FINAL_OFFSET_EPSILON_PX = 1f

    fun idle(generation: Long): PageStripTransition =
        PageStripTransition(gestureGeneration = generation)

    /** A new pointer takes over the exact displayed offset instead of jumping to an old target. */
    fun interrupt(
        previous: PageStripTransition,
        displayedOffsetPx: Float,
        currentPage: Int,
        pageCount: Int,
        generation: Long,
    ): PageStripTransition {
        val direction = directionFor(displayedOffsetPx, currentPage, pageCount)
        return if (direction == null || abs(displayedOffsetPx) <= FINAL_OFFSET_EPSILON_PX) {
            idle(generation)
        } else {
            previous.copy(
                gestureGeneration = generation,
                phase = ZoomedPageTransitionPhase.EDGE_PREVIEW,
                direction = direction,
                targetPage = targetFor(direction, currentPage),
                releaseVelocityXPxPerSecond = 0f,
            )
        }
    }

    /**
     * The first strip frame consumes only true edge residual. Once active, raw finger delta moves
     * the strip 1:1. Movement is refused until the target neighbour has a real base bitmap.
     */
    fun drag(
        transition: PageStripTransition,
        displayedOffsetPx: Float,
        rawDeltaX: Float,
        rawDeltaY: Float,
        edgeResidualX: Float,
        currentPage: Int,
        pageCount: Int,
        viewportWidthPx: Float,
        previousBaseReady: Boolean,
        nextBaseReady: Boolean,
    ): PageStripDragResult {
        if (transition.phase !in setOf(
                ZoomedPageTransitionPhase.IDLE,
                ZoomedPageTransitionPhase.EDGE_PREVIEW,
            )
        ) {
            return PageStripDragResult(transition, displayedOffsetPx)
        }
        val width = viewportWidthPx.coerceAtLeast(1f)
        val horizontalIntent = abs(rawDeltaX) > abs(rawDeltaY) * HORIZONTAL_INTENT_RATIO
        if (!transition.active && (!horizontalIntent || edgeResidualX == 0f)) {
            return PageStripDragResult(transition, displayedOffsetPx)
        }
        val appliedDelta = if (transition.active) rawDeltaX else edgeResidualX
        val proposed = (displayedOffsetPx + appliedDelta).coerceIn(-width, width)
        if (abs(proposed) <= FINAL_OFFSET_EPSILON_PX) {
            return PageStripDragResult(idle(transition.gestureGeneration), 0f)
        }
        val direction = directionFor(proposed, currentPage, pageCount)
            ?: return PageStripDragResult(transition, displayedOffsetPx)
        val ready = when (direction) {
            HorizontalPageTransitionDirection.PREVIOUS -> previousBaseReady
            HorizontalPageTransitionDirection.NEXT -> nextBaseReady
        }
        if (!ready) {
            return PageStripDragResult(
                transition = transition,
                offsetPx = displayedOffsetPx,
                blockedByMissingBase = true,
            )
        }
        return PageStripDragResult(
            transition = transition.copy(
                phase = ZoomedPageTransitionPhase.EDGE_PREVIEW,
                direction = direction,
                targetPage = targetFor(direction, currentPage),
            ),
            offsetPx = when (direction) {
                HorizontalPageTransitionDirection.PREVIOUS -> proposed.coerceIn(0f, width)
                HorizontalPageTransitionDirection.NEXT -> proposed.coerceIn(-width, 0f)
            },
        )
    }

    fun release(
        transition: PageStripTransition,
        displayedOffsetPx: Float,
        viewportWidthPx: Float,
        releaseVelocityXPxPerSecond: Float,
        minimumFlingVelocityPxPerSecond: Float,
    ): PageStripTransition {
        val direction = transition.direction
        if (transition.phase != ZoomedPageTransitionPhase.EDGE_PREVIEW || direction == null) {
            return transition
        }
        val directedVelocity = when (direction) {
            HorizontalPageTransitionDirection.PREVIOUS -> releaseVelocityXPxPerSecond
            HorizontalPageTransitionDirection.NEXT -> -releaseVelocityXPxPerSecond
        }
        val distanceFraction = abs(displayedOffsetPx) / viewportWidthPx.coerceAtLeast(1f)
        val commit = distanceFraction >= COMMIT_DISTANCE_FRACTION ||
            directedVelocity >= minimumFlingVelocityPxPerSecond.coerceAtLeast(0f)
        return transition.copy(
            phase = if (commit) ZoomedPageTransitionPhase.COMMITTING
            else ZoomedPageTransitionPhase.CANCELLING,
            releaseVelocityXPxPerSecond = releaseVelocityXPxPerSecond,
        )
    }

    fun settleTargetPx(transition: PageStripTransition, viewportWidthPx: Float): Float =
        if (transition.phase == ZoomedPageTransitionPhase.COMMITTING) {
            when (transition.direction) {
                HorizontalPageTransitionDirection.PREVIOUS -> viewportWidthPx.coerceAtLeast(1f)
                HorizontalPageTransitionDirection.NEXT -> -viewportWidthPx.coerceAtLeast(1f)
                null -> 0f
            }
        } else {
            0f
        }

    fun slotTranslationPx(
        role: PageStripSlotRole,
        viewportWidthPx: Float,
        stripOffsetPx: Float,
    ): Float = role.baseMultiplier * viewportWidthPx + stripOffsetPx

    /**
     * Pointer input is installed on a translated physical slot. Convert its moving local X back
     * into the stationary viewport coordinate space before feeding the shared gesture controller.
     * Without this rebase, the strip translation changes the next pointer sample and creates a
     * self-referential half-gain/reverse-delta loop while the finger is still down.
     */
    fun pointerXInViewport(
        localPointerX: Float,
        role: PageStripSlotRole,
        viewportWidthPx: Float,
        stripOffsetPx: Float,
    ): Float = localPointerX + slotTranslationPx(role, viewportWidthPx, stripOffsetPx)

    private fun directionFor(
        offsetPx: Float,
        currentPage: Int,
        pageCount: Int,
    ): HorizontalPageTransitionDirection? = when {
        offsetPx > 0f && currentPage > 0 -> HorizontalPageTransitionDirection.PREVIOUS
        offsetPx < 0f && currentPage < pageCount - 1 -> HorizontalPageTransitionDirection.NEXT
        else -> null
    }

    private fun targetFor(
        direction: HorizontalPageTransitionDirection,
        currentPage: Int,
    ): Int = when (direction) {
        HorizontalPageTransitionDirection.PREVIOUS -> currentPage - 1
        HorizontalPageTransitionDirection.NEXT -> currentPage + 1
    }
}
