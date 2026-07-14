package com.markleaf.app.markdown

import java.util.concurrent.atomic.AtomicLong

data class MarkdownPerformanceSnapshot(
    val parseCount: Long,
    val tableSolveCount: Long,
    val tableSolveTotalNanos: Long,
    val cellMeasureCount: Long,
    val cacheHitCount: Long,
    val cacheMissCount: Long,
) {
    val tableSolveTotalMs: Double get() = tableSolveTotalNanos / 1_000_000.0
}

/** Process-local counters used by host-side QA; no Compose state is observed. */
object MarkdownPerformance {
    private val parseCount = AtomicLong()
    private val tableSolveCount = AtomicLong()
    private val tableSolveTotalNanos = AtomicLong()
    private val cellMeasureCount = AtomicLong()
    private val cacheHitCount = AtomicLong()
    private val cacheMissCount = AtomicLong()

    fun recordParse() = parseCount.incrementAndGet()
    fun recordTableSolve(durationNanos: Long, measuredCells: Long) {
        tableSolveCount.incrementAndGet()
        tableSolveTotalNanos.addAndGet(durationNanos.coerceAtLeast(0L))
        cellMeasureCount.addAndGet(measuredCells.coerceAtLeast(0L))
    }
    fun recordCacheHit() = cacheHitCount.incrementAndGet()
    fun recordCacheMiss() = cacheMissCount.incrementAndGet()

    fun snapshot(): MarkdownPerformanceSnapshot = MarkdownPerformanceSnapshot(
        parseCount = parseCount.get(),
        tableSolveCount = tableSolveCount.get(),
        tableSolveTotalNanos = tableSolveTotalNanos.get(),
        cellMeasureCount = cellMeasureCount.get(),
        cacheHitCount = cacheHitCount.get(),
        cacheMissCount = cacheMissCount.get(),
    )

    fun reset() {
        parseCount.set(0)
        tableSolveCount.set(0)
        tableSolveTotalNanos.set(0)
        cellMeasureCount.set(0)
        cacheHitCount.set(0)
        cacheMissCount.set(0)
    }

    inline fun <T> trace(section: String, block: () -> T): T {
        val tracing = try {
            android.os.Trace.beginSection(section)
            true
        } catch (_: Throwable) {
            false
        }
        return try {
            block()
        } finally {
            if (tracing) {
                try {
                    android.os.Trace.endSection()
                } catch (_: Throwable) {
                    // JVM host tests do not provide the Android trace runtime.
                }
            }
        }
    }
}
