package com.m1racle.reader.pdf

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

data class PdfGestureFrameSample(
    val eventUptimeMillis: Long,
    val observedUptimeMillis: Long,
    val rawDeltaPx: ZoomPanPoint,
    val legalDeltaPx: ZoomPanPoint,
    val residualPx: ZoomPanPoint,
    val pageTransitionOffsetPx: Float = 0f,
)

data class PdfGestureFrameSummary(
    val frameCount: Int,
    val inputLatencyP50Ms: Long,
    val inputLatencyP95Ms: Long,
    val maxInputLatencyMs: Long,
    val legalDragGainX: Float,
    val legalDragGainY: Float,
    val maxPageTransitionOffsetPx: Float,
)

/** Bounded high-frequency evidence. No logcat, allocation growth, or UI state readback per frame. */
class PdfGestureFrameTelemetryRing(private val capacity: Int = 240) {
    private val samples = arrayOfNulls<PdfGestureFrameSample>(capacity.coerceAtLeast(1))
    private var count = 0
    private var cursor = 0

    @Synchronized
    fun record(sample: PdfGestureFrameSample) {
        samples[cursor] = sample
        cursor = (cursor + 1) % samples.size
        count = (count + 1).coerceAtMost(samples.size)
    }

    @Synchronized
    fun clear() {
        samples.fill(null)
        count = 0
        cursor = 0
    }

    @Synchronized
    fun summary(): PdfGestureFrameSummary {
        var rawX = 0f
        var rawY = 0f
        var legalX = 0f
        var legalY = 0f
        var latency = 0L
        val latencies = ArrayList<Long>(count)
        var transition = 0f
        samples.forEach { sample ->
            sample ?: return@forEach
            if (abs(sample.residualPx.x) < 0.01f) {
                rawX += abs(sample.rawDeltaPx.x)
                legalX += abs(sample.legalDeltaPx.x)
            }
            if (abs(sample.residualPx.y) < 0.01f) {
                rawY += abs(sample.rawDeltaPx.y)
                legalY += abs(sample.legalDeltaPx.y)
            }
            val sampleLatency = (sample.observedUptimeMillis - sample.eventUptimeMillis).coerceAtLeast(0L)
            latencies += sampleLatency
            latency = max(latency, sampleLatency)
            transition = max(transition, abs(sample.pageTransitionOffsetPx))
        }
        latencies.sort()
        fun percentile(fraction: Double): Long {
            if (latencies.isEmpty()) return 0L
            val index = (ceil(fraction * latencies.size).toInt() - 1)
                .coerceIn(0, latencies.lastIndex)
            return latencies[index]
        }
        return PdfGestureFrameSummary(
            frameCount = count,
            inputLatencyP50Ms = percentile(0.50),
            inputLatencyP95Ms = percentile(0.95),
            maxInputLatencyMs = latency.coerceAtLeast(0L),
            legalDragGainX = if (rawX > 0f) legalX / rawX else 1f,
            legalDragGainY = if (rawY > 0f) legalY / rawY else 1f,
            maxPageTransitionOffsetPx = transition,
        )
    }
}
