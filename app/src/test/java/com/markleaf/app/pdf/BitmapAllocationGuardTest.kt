package com.markleaf.app.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

/**
 * Behavioral tests for the bounded OOM guard used at bitmap allocation
 * boundaries ([PdfPageRenderer.renderTile]/[PdfPageRenderer.renderPage]).
 *
 * Failure mode under test: `Bitmap.createBitmap` throws `OutOfMemoryError`
 * (an Error), which a `catch (Exception)` cannot catch; unhandled, it kills
 * the tile worker coroutine permanently and can crash the app. The guard
 * converts the OOM into an explicit failed result at the allocation boundary.
 * (Guard is generic; tests use String where production uses Bitmap.)
 */
class BitmapAllocationGuardTest {

    @Test
    fun oomAtAllocation_returnsOomResult_andDoesNotPropagate() {
        val calls = mutableListOf<String>()
        val result = BitmapAllocationGuard.allocateOr(
            allocate = {
                calls += "allocate"
                throw OutOfMemoryError("simulated bitmap OOM")
            },
            onOutOfMemory = { calls += "oom"; "OOM_RESULT" },
            body = { calls += "body"; "BODY" },
        )
        assertEquals("OOM_RESULT", result)
        assertEquals(listOf("allocate", "oom"), calls)
    }

    @Test
    fun oomInsideBody_isHandledLikeAllocationOom() {
        // Native allocations inside page.render can also OOM.
        val result = BitmapAllocationGuard.allocateOr(
            allocate = { "bitmap" },
            onOutOfMemory = { "OOM_RESULT" },
            body = { throw OutOfMemoryError("native render OOM") },
        )
        assertEquals("OOM_RESULT", result)
    }

    @Test
    fun successfulAllocation_runsBody() {
        val allocated = "bitmap-obj"
        val result = BitmapAllocationGuard.allocateOr(
            allocate = { allocated },
            onOutOfMemory = { "OOM_RESULT" },
            body = { b -> assertSame(allocated, b); "OK" },
        )
        assertEquals("OK", result)
    }

    @Test
    fun allocationRunsAtMostOnce_noRetryLoop() {
        // "最多一次 / 明确上限": an OOM must never trigger a retry loop.
        var allocateCount = 0
        BitmapAllocationGuard.allocateOr(
            allocate = {
                allocateCount++
                throw OutOfMemoryError("oom")
            },
            onOutOfMemory = { "OOM" },
            body = { "BODY" },
        )
        assertEquals(1, allocateCount)
    }

    @Test
    fun oomAfterAllocation_releasesAllocatedObject() {
        // Bitmap created OK, body (page.render) OOMs: the half-drawn bitmap
        // must be explicitly released BEFORE the OOM result is returned —
        // never left to an arbitrary future GC, never entering any cache.
        val released = mutableListOf<String>()
        val result = BitmapAllocationGuard.allocateOr(
            allocate = { "allocated-bitmap" },
            onOutOfMemory = { "OOM_RESULT" },
            onRelease = { released += it },
            body = { throw OutOfMemoryError("page.render OOM") },
        )
        assertEquals("OOM_RESULT", result)
        assertEquals(listOf("allocated-bitmap"), released)
    }

    @Test
    fun oomAtAllocation_doesNotReleaseAnything() {
        // createBitmap itself failed: there is no object to release.
        val released = mutableListOf<String>()
        BitmapAllocationGuard.allocateOr(
            allocate = { throw OutOfMemoryError("createBitmap OOM") },
            onOutOfMemory = { "OOM" },
            onRelease = { r: String -> released += r },
            body = { _: String -> "BODY" },
        )
        assertEquals(emptyList<String>(), released)
    }

    @Test
    fun successfulPath_neverReleases() {
        val released = mutableListOf<String>()
        BitmapAllocationGuard.allocateOr(
            allocate = { "bitmap" },
            onOutOfMemory = { "OOM" },
            onRelease = { released += it },
            body = { "OK" },
        )
        assertEquals(emptyList<String>(), released)
    }

    @Test
    fun workerSurvivesOom_nextRequestSucceeds() {
        // Queue-continuation semantics: first request OOMs, the next one
        // succeeds — the worker (loop calling allocateOr per request) lives on.
        var oomNext = true
        val results = (1..3).map { requestIndex ->
            BitmapAllocationGuard.allocateOr(
                allocate = {
                    if (oomNext) {
                        oomNext = false
                        throw OutOfMemoryError("oom")
                    }
                    "bitmap-$requestIndex"
                },
                onOutOfMemory = { "FAILED:$requestIndex" },
                body = { "SUCCESS:$requestIndex" },
            )
        }
        assertEquals(listOf("FAILED:1", "SUCCESS:2", "SUCCESS:3"), results)
    }

    @Test
    fun nonOomException_propagates_errorsNotHidden() {
        try {
            BitmapAllocationGuard.allocateOr(
                allocate = { throw IllegalStateException("real bug") },
                onOutOfMemory = { "OOM" },
                body = { "BODY" },
            )
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("real bug", e.message)
        }
    }
}
