package com.m1racle.reader.ui.pdf

/**
 * Generation gate for export UI state.
 *
 * The exporter instances are shared across export jobs, and a cancelled job
 * keeps running inside its `withContext(Dispatchers.IO)` until the next
 * cancellation checkpoint — its late `onState` callbacks must never touch the
 * UI state of a newer job. Every `begin()`/`cancel()` bumps the generation;
 * callbacks captured under an older generation are dropped by [isCurrent].
 */
class ExportStateGate {

    private var generation: Long = 0L

    /** Start a new export: invalidates every callback of previous jobs. */
    fun begin(): Long = ++generation

    /** Cancel the current export: late callbacks of it become stale. */
    fun cancel(): Long = ++generation

    /** True only for callbacks captured under the current generation. */
    fun isCurrent(capturedGeneration: Long): Boolean = capturedGeneration == generation
}
