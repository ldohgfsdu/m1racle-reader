package com.markleaf.app.markdown

import com.markleaf.app.BuildConfig

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class MarkdownBlockComposeStats(
    val type: String,
    val count: Long,
    val totalNanos: Long,
) {
    val totalMs: Double get() = totalNanos / 1_000_000.0
}

data class MarkdownPerformanceSnapshot(
    val parseCount: Long,
    val tableSolveCount: Long,
    val tableSolveTotalNanos: Long,
    val cellMeasureCount: Long,
    val cacheHitCount: Long,
    val cacheMissCount: Long,
    val inlineParseCount: Long,
    val inlineParseFastPathCount: Long,
    val blockCompose: List<MarkdownBlockComposeStats>,
) {
    val tableSolveTotalMs: Double get() = tableSolveTotalNanos / 1_000_000.0

    /** Block type with the highest compose wall time, or null when empty. */
    fun dominantBlockType(): String? =
        blockCompose.maxByOrNull { it.totalNanos }?.type
}

/** Process-local counters used by host-side QA; no Compose state is observed. */
object MarkdownPerformance {
    private val parseCount = AtomicLong()
    private val tableSolveCount = AtomicLong()
    private val tableSolveTotalNanos = AtomicLong()
    private val cellMeasureCount = AtomicLong()
    private val cacheHitCount = AtomicLong()
    private val cacheMissCount = AtomicLong()
    private val inlineParseCount = AtomicLong()
    private val inlineParseFastPathCount = AtomicLong()
    private val blockComposeCount = ConcurrentHashMap<String, AtomicLong>()
    private val blockComposeNanos = ConcurrentHashMap<String, AtomicLong>()

    // A2 release hygiene: recording (counters, nanoTime, Trace sections) is
    // compiled against a per-variant constant — false in release, so R8 strips
    // the bodies and their call-site costs; qa keeps the full instrumentation.
    private val enabled get() = BuildConfig.READER_PERFORMANCE_EXPERIMENTS_ENABLED

    fun recordParse() {
        if (!enabled) return
        parseCount.incrementAndGet()
    }

    fun recordTableSolve(durationNanos: Long, measuredCells: Long) {
        if (!enabled) return
        tableSolveCount.incrementAndGet()
        tableSolveTotalNanos.addAndGet(durationNanos.coerceAtLeast(0L))
        cellMeasureCount.addAndGet(measuredCells.coerceAtLeast(0L))
    }
    fun recordCacheHit() {
        if (!enabled) return
        cacheHitCount.incrementAndGet()
    }

    fun recordCacheMiss() {
        if (!enabled) return
        cacheMissCount.incrementAndGet()
    }
    fun recordInlineParse(usedFastPath: Boolean) {
        if (!enabled) return
        inlineParseCount.incrementAndGet()
        if (usedFastPath) inlineParseFastPathCount.incrementAndGet()
    }

    fun recordBlockCompose(type: String, durationNanos: Long) {
        if (!enabled) return
        val key = type.ifBlank { "unknown" }
        blockComposeCount.getOrPut(key) { AtomicLong() }.incrementAndGet()
        blockComposeNanos.getOrPut(key) { AtomicLong() }.addAndGet(durationNanos.coerceAtLeast(0L))
    }

    fun snapshot(): MarkdownPerformanceSnapshot {
        val types = (blockComposeCount.keys + blockComposeNanos.keys).toSortedSet()
        val blockStats = types.map { type ->
            MarkdownBlockComposeStats(
                type = type,
                count = blockComposeCount[type]?.get() ?: 0L,
                totalNanos = blockComposeNanos[type]?.get() ?: 0L,
            )
        }
        return MarkdownPerformanceSnapshot(
            parseCount = parseCount.get(),
            tableSolveCount = tableSolveCount.get(),
            tableSolveTotalNanos = tableSolveTotalNanos.get(),
            cellMeasureCount = cellMeasureCount.get(),
            cacheHitCount = cacheHitCount.get(),
            cacheMissCount = cacheMissCount.get(),
            inlineParseCount = inlineParseCount.get(),
            inlineParseFastPathCount = inlineParseFastPathCount.get(),
            blockCompose = blockStats,
        )
    }

    fun reset() {
        parseCount.set(0)
        tableSolveCount.set(0)
        tableSolveTotalNanos.set(0)
        cellMeasureCount.set(0)
        cacheHitCount.set(0)
        cacheMissCount.set(0)
        inlineParseCount.set(0)
        inlineParseFastPathCount.set(0)
        blockComposeCount.clear()
        blockComposeNanos.clear()
    }

    /** Compact one-line summary for adb logcat or host tests. */
    fun formatBlockComposeSummary(): String {
        val s = snapshot()
        val blocks = s.blockCompose.joinToString(separator = ",") { stat ->
            "${stat.type}:${stat.count}:${"%.3f".format(java.util.Locale.US, stat.totalMs)}"
        }
        return "dominant=${s.dominantBlockType()} " +
            "inlineParse=${s.inlineParseCount} fastPath=${s.inlineParseFastPathCount} " +
            "tableSolve=${s.tableSolveCount} cacheHit=${s.cacheHitCount} " +
            "blocks=[$blocks]"
    }

    /** QA log line for adb; no-op unless the QA experiments flag is compiled in. */
    fun dumpBlockCompose(tag: String = "MarkdownBlockPerf") {
        if (!enabled) return
        val line = formatBlockComposeSummary()
        try {
            Log.i(tag, line)
        } catch (_: Throwable) {
            // JVM unit tests lack android.util.Log native backend.
        }
    }

    inline fun <T> trace(section: String, block: () -> T): T {
        // BuildConfig field is a public constant: safe for a public-API inline.
        if (!com.markleaf.app.BuildConfig.READER_PERFORMANCE_EXPERIMENTS_ENABLED) return block()
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

    /**
     * Time a block compose path and attribute to [type] (e.g. "paragraph").
     * Safe on JVM hosts: Trace is optional; counters always update.
     */
    inline fun <T> traceBlockCompose(type: String, block: () -> T): T {
        val started = System.nanoTime()
        return try {
            trace("MdBlock:$type", block)
        } finally {
            recordBlockCompose(type, System.nanoTime() - started)
        }
    }
}
