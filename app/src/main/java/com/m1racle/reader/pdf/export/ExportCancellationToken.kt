package com.m1racle.reader.pdf.export

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Job-local export cancellation token.
 *
 * Each export run owns its own token: cancelling export A can never affect
 * export B, and B's start can never re-enable A's cancellation checkpoints.
 * (The previous shared `@Volatile var cancelRequested` on the exporter had
 * exactly that hazard: B reset the flag at entry, which — had a checkpoint
 * lacked the `!coroutineContext.isActive` guard — could let a cancelled A
 * continue running.)
 */
class ExportCancellationToken {

    private val cancelled = AtomicBoolean(false)

    fun cancel() {
        cancelled.set(true)
    }

    fun isCancelled(): Boolean = cancelled.get()
}
