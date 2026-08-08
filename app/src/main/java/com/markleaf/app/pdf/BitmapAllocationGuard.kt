package com.markleaf.app.pdf

/**
 * Bounded [OutOfMemoryError] guard for the explicit high-memory allocation
 * boundary (`Bitmap.createBitmap` / renderer allocation) — the ONLY place
 * where an OOM may be handled. Never catches `Throwable`, never wraps whole
 * subsystems.
 *
 * Semantics:
 * - the allocation lambda runs at most once per call — there is NO retry here,
 *   so no OOM loop is possible; a degraded result is returned instead;
 * - an OOM thrown by [body] (e.g. native allocation inside `page.render`)
 *   is handled identically;
 * - when an OOM occurs AFTER allocation succeeded (bitmap created, body
 *   failed), [onRelease] is invoked on the allocated object BEFORE returning
 *   the OOM result — the half-drawn bitmap is explicitly released (recycled
 *   in production) instead of being left to an arbitrary future GC;
 * - any other exception propagates unchanged (real errors are not hidden).
 *
 * [B] is generic so the guard is unit-testable on the JVM without real
 * Bitmap allocation; production call sites use `Bitmap`.
 */
object BitmapAllocationGuard {

    inline fun <B, R> allocateOr(
        allocate: () -> B,
        onOutOfMemory: () -> R,
        body: (B) -> R,
        onRelease: (B) -> Unit = {},
    ): R {
        var allocated: B? = null
        return try {
            allocated = allocate()
            body(allocated)
        } catch (e: OutOfMemoryError) {
            allocated?.let(onRelease)
            onOutOfMemory()
        }
    }
}
