package com.m1racle.reader.ui.viewer

import android.os.Trace
import java.util.concurrent.atomic.AtomicInteger

private val anchorJumpTraceSequence = AtomicInteger()

/** Async sections survive suspension; each concurrent jump owns its cookie. */
internal suspend fun <T> traceAnchorJump(block: suspend () -> T): T {
    val cookie = anchorJumpTraceSequence.incrementAndGet()
    Trace.beginAsyncSection("AnchorJump", cookie)
    try {
        return block()
    } finally {
        Trace.endAsyncSection("AnchorJump", cookie)
    }
}
