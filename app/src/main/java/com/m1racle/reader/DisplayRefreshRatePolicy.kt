package com.m1racle.reader

internal data class DisplayModeCandidate(
    val modeId: Int,
    val width: Int,
    val height: Int,
    val refreshRate: Float,
)

/** Prefer the highest real refresh rate without changing the active display resolution. */
internal fun selectHighestRefreshMode(
    currentWidth: Int,
    currentHeight: Int,
    candidates: List<DisplayModeCandidate>,
): DisplayModeCandidate? = candidates
    .asSequence()
    .filter { it.width == currentWidth && it.height == currentHeight }
    .filter { it.refreshRate.isFinite() && it.refreshRate > 0f }
    .maxWithOrNull(compareBy<DisplayModeCandidate> { it.refreshRate }.thenBy { -it.modeId })
